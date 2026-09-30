// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
// Copyright (C) 2026 Dina Elokhova (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.domain

import app.freetotake.domain.model.ClaimStatus
import app.freetotake.domain.model.ItemStatus
import app.freetotake.domain.model.Session
import app.freetotake.domain.request.DateKey
import app.freetotake.domain.request.LocalNow
import app.freetotake.domain.request.PickupSlot
import app.freetotake.domain.request.PickupStatusPolicy
import app.freetotake.domain.request.RequestCheck
import app.freetotake.domain.request.RequestDraft
import app.freetotake.domain.request.RequestRules
import app.freetotake.domain.request.ShareLink
import app.freetotake.domain.request.SlotPicker
import app.freetotake.domain.request.TimeOfDay
import app.freetotake.domain.catalog.ExampleListing
import app.freetotake.domain.catalog.MyClaims
import app.freetotake.domain.model.ClaimId
import app.freetotake.domain.model.DomainResult
import app.freetotake.domain.model.Timestamp
import app.freetotake.domain.request.ExampleRequest
import app.freetotake.domain.request.MyClaimsCopy
import app.freetotake.domain.rules.ClaimRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RequestFlowTest {
    // Week of Mon 21 Sep 2026 … Sun 27 Sep 2026 (design shows Mon 21 – Sun 27)
    private val mon = DateKey(2026, 9, 21)
    private val wed = DateKey(2026, 9, 23)
    private val fri = DateKey(2026, 9, 25)
    private fun t(h: Int, m: Int = 0) = TimeOfDay(h, m)
    private val slots = listOf(
        PickupSlot(wed, t(8, 30)), PickupSlot(wed, t(9)), PickupSlot(wed, t(9, 30)),
        PickupSlot(fri, t(9)), PickupSlot(fri, t(10)),
        PickupSlot(DateKey(2026, 9, 20), t(9)), // past → never offered
    )
    private val now = LocalNow(mon, t(12))

    @Test fun r_1_6_01_calendar_helpers() {
        assertEquals(0, mon.dayOfWeek); assertEquals(2, wed.dayOfWeek); assertEquals(6, DateKey(2026, 9, 27).dayOfWeek)
        assertEquals(DateKey(2026, 10, 1), DateKey(2026, 9, 30).plusDays(1))
        assertEquals(DateKey(2027, 1, 1), DateKey(2026, 12, 31).plusDays(1))
        assertEquals(DateKey(2028, 2, 29), DateKey(2028, 2, 28).plusDays(1))
        assertEquals("09:30", t(9, 30).label())
    }

    @Test fun r_1_6_02_date_strip_is_full_week_with_only_publisher_dates_enabled() {
        val d = SlotPicker.dates(slots, now)
        assertEquals(7, d.size); assertEquals(mon, d.first().date)
        assertEquals(listOf(wed, fri), d.filter { it.enabled }.map { it.date })
    }

    @Test fun r_1_6_03_times_enabled_only_for_selected_date_and_future() {
        val tm = SlotPicker.times(slots, wed, now)
        assertEquals(listOf("08:30", "09:00", "09:30", "10:00"), tm.map { it.time.label() }, "union of publisher times")
        assertEquals(listOf("08:30", "09:00", "09:30"), tm.filter { it.enabled }.map { it.time.label() })
        assertTrue(SlotPicker.times(slots, null, now).none { it.enabled }, "pick a date first")
        val lateWed = LocalNow(wed, t(9))
        assertEquals(listOf("09:30"), SlotPicker.times(slots, wed, lateWed).filter { it.enabled }.map { it.time.label() }, "past times excluded")
    }

    @Test fun r_1_16_4_01_example_from_available_giveaways_shows_request_form() {
        val demo = app.freetotake.domain.request.ExampleClaim.claim(null, mon, Timestamp(0))
        assertNull(app.freetotake.domain.request.ExampleClaim.shownOnDetails(demo, openedFromMyClaims = false), "Available Giveaways → form + Send request")
        assertEquals(demo, app.freetotake.domain.request.ExampleClaim.shownOnDetails(demo, openedFromMyClaims = true), "My claims → sent request view")
        val sent = demo.copy(id = ClaimId("local-1"))
        assertEquals(sent, app.freetotake.domain.request.ExampleClaim.shownOnDetails(sent, openedFromMyClaims = false), "a request the user sent is always shown")
    }

    @Test fun r_1_16_2_01_single_available_date_preselected_with_first_time() {
        val one = listOf(PickupSlot(fri, t(10)), PickupSlot(fri, t(12)))
        assertEquals(RequestDraft(fri, t(10)), SlotPicker.preselect(one, now, RequestDraft()), "only date → selected, first time → selected")
    }

    @Test fun r_1_16_2_02_several_dates_no_date_preselected() {
        assertEquals(RequestDraft(), SlotPicker.preselect(slots, now, RequestDraft()), "several dates → user picks")
    }

    @Test fun r_1_16_2_03_picked_date_gets_first_free_time_and_keeps_user_choice() {
        assertEquals(t(8, 30), SlotPicker.preselect(slots, now, RequestDraft(date = wed)).time, "first enabled time on that date")
        assertEquals(t(9, 30), SlotPicker.preselect(slots, now, RequestDraft(wed, t(9, 30))).time, "user choice kept")
        assertEquals(t(9, 30), SlotPicker.preselect(slots, LocalNow(wed, t(9)), RequestDraft(date = wed)).time, "past times skipped")
    }

    @Test fun r_1_6_04_single_option_is_preselected() {
        assertEquals(PickupSlot(fri, t(10)), SlotPicker.defaultSelection(listOf(PickupSlot(fri, t(10))), now))
        assertNull(SlotPicker.defaultSelection(slots, now))
    }

    @Test fun r_1_6_05_slot_required_and_must_be_publisher_option() {
        val item = F.item()
        val s = F.session(F.alice)
        assertEquals(RequestCheck.SlotRequired, RequestRules.check(s, item, RequestDraft(date = wed), slots, now))
        assertEquals(RequestCheck.SlotNotOffered, RequestRules.check(s, item, RequestDraft(wed, t(11)), slots, now))
        assertEquals(RequestCheck.SlotNotOffered, RequestRules.check(s, item, RequestDraft(DateKey(2026, 9, 20), t(9)), slots, now), "past slot")
        assertEquals(RequestCheck.Ok, RequestRules.check(s, item, RequestDraft(wed, t(9), "Hello"), slots, now))
        assertFalse(RequestRules.canSend(RequestDraft(date = wed)))
        assertTrue(RequestRules.canSend(RequestDraft(wed, t(9))))
    }

    @Test fun r_1_6_06_note_limit_provisional_1000() {
        assertEquals(1_000, RequestRules.NOTE_MAX)
        val item = F.item(); val s = F.session(F.alice)
        assertEquals(RequestCheck.Ok, RequestRules.check(s, item, RequestDraft(wed, t(9), "x".repeat(1000)), slots, now))
        assertEquals(RequestCheck.NoteTooLong, RequestRules.check(s, item, RequestDraft(wed, t(9), "x".repeat(1001)), slots, now))
    }

    @Test fun r_1_6_07_guest_with_valid_form_gets_login_sheet() {
        assertEquals(RequestCheck.LoginRequired, RequestRules.check(Session.Guest, F.item(), RequestDraft(wed, t(9)), slots, now))
        assertEquals(RequestCheck.SlotRequired, RequestRules.check(Session.Guest, F.item(), RequestDraft(), slots, now), "form errors first")
        assertEquals("Log in to finish this action", RequestRules.LOGIN_SHEET_TITLE)
    }

    @Test fun r_1_6_08_own_item_and_closed_item_rejected() {
        assertEquals(RequestCheck.OwnItem, RequestRules.check(F.session(F.giver), F.item(), RequestDraft(wed, t(9)), slots, now))
        assertEquals(RequestCheck.NotClaimable, RequestRules.check(F.session(F.alice), F.item(ItemStatus.GIVEN), RequestDraft(wed, t(9)), slots, now))
    }

    @Test fun r_1_6_09_pending_is_dimmed_and_cannot_collect_until_approved() {
        assertEquals("Pending for approval", PickupStatusPolicy.label(ClaimStatus.PENDING_APPROVAL))
        assertTrue(PickupStatusPolicy.dimmed(ClaimStatus.PENDING_APPROVAL))
        assertFalse(PickupStatusPolicy.canCollect(ClaimStatus.PENDING_APPROVAL))
        assertTrue(PickupStatusPolicy.canCollect(ClaimStatus.APPROVED)); assertFalse(PickupStatusPolicy.dimmed(ClaimStatus.APPROVED))
        assertFalse(PickupStatusPolicy.canCollect(ClaimStatus.REJECTED))
    }

    @Test fun r_1_6_10_success_copy_and_share_placeholder() {
        assertEquals("Your request has been sent!", RequestRules.SENT_TITLE)
        assertEquals("Go to homepage", RequestRules.GO_HOME)
        assertTrue(ShareLink.forItem(F.item()).contains(F.item().id.value))
        assertEquals(3, RequestRules.DESCRIPTION_COLLAPSED_LINES)
    }

    @Test fun r_1_6_11_example_has_future_demo_availability() {
        val today = DateKey(2026, 9, 27)
        val slots = ExampleRequest.slots(today)
        assertTrue(slots.isNotEmpty())
        assertTrue(slots.all { it.date > today })
        val dates = SlotPicker.dates(slots, LocalNow(today, TimeOfDay(12, 0)))
        assertTrue(dates.any { it.enabled } && dates.any { !it.enabled }) // available + unavailable days shown
        assertNull(SlotPicker.defaultSelection(slots, LocalNow(today, TimeOfDay(12, 0)))) // several options → none preselected
    }

    @Test fun r_1_6_12_example_request_is_pending_and_dimmed_in_my_claims() {
        val user = F.session(F.alice)
        val r = ClaimRules.submit(user, ExampleListing.item, emptyList(), ClaimId("local-1"), Timestamp(1), null)
        val claim = r.ok()
        val entries = MyClaims.of(F.alice.id, listOf(claim), listOf(ExampleListing.item))
        assertEquals(1, entries.size)
        assertEquals("Pending for approval", PickupStatusPolicy.label(entries[0].claim.status))
        assertTrue(PickupStatusPolicy.dimmed(entries[0].claim.status))
        assertFalse(PickupStatusPolicy.canCollect(entries[0].claim.status))
        assertEquals("My claims", MyClaimsCopy.TITLE)
    }

    @Test fun r_1_6_13_db_status_mapping_and_submitted_label() {
        assertEquals(ClaimStatus.PENDING_APPROVAL, ClaimStatus.fromDb("pending_approval"))
        assertEquals(ClaimStatus.CANCELLED_LATE, ClaimStatus.fromDb("cancelled_late"))
        assertEquals("You submitted request on April, 4 2026.", MyClaimsCopy.submittedLabel(DateKey(2026, 4, 4)))
        assertEquals(DateKey(2026, 4, 4), DateKey(1970, 1, 1).plusDays(20547))
    }
}

