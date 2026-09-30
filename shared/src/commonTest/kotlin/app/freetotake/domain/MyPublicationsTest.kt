// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
// Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.domain

import app.freetotake.domain.F.NOW
import app.freetotake.domain.model.ItemStatus
import app.freetotake.domain.model.Timestamp
import app.freetotake.domain.publish.Collector
import app.freetotake.domain.publish.MeetupDetails
import app.freetotake.domain.publish.MyPublicationsRules as R
import app.freetotake.domain.publish.PickupSchedule
import app.freetotake.domain.publish.PublicationOverview
import app.freetotake.domain.publish.PublicationSection as S
import app.freetotake.domain.publish.PublicationsTab
import app.freetotake.domain.request.DateKey
import app.freetotake.domain.request.TimeOfDay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** v1.9 — spec 0.12 My Publications: Active and Archived. */
class MyPublicationsTest {
    private val H = F.HOUR
    private fun p(status: ItemStatus = ItemStatus.AVAILABLE, pending: Int = 0, collector: Collector? = null, start: Timestamp? = Timestamp(NOW.epochMillis + 24 * H), expires: Timestamp? = null) =
        PublicationOverview(F.item(status).copy(expiresAt = expires), PickupSchedule(DateKey(2026, 10, 1), TimeOfDay(21, 50), TimeOfDay(22, 0)), start, pending, collector)

    @Test fun r_1_9_01_tabs_active_default_and_sections() {
        assertEquals(PublicationsTab.ACTIVE, PublicationsTab.DEFAULT)
        assertEquals(S.WAITING, R.section(p(), NOW))
        assertEquals(S.ACTION_NEEDED, R.section(p(pending = 16), NOW))
        assertEquals(S.PENDING_COLLECTION, R.section(p(ItemStatus.RESERVED, collector = Collector("alice1945", Timestamp(NOW.epochMillis + 5 * H))), NOW))
        assertEquals(S.ACTION_NEEDED, R.section(p(ItemStatus.AWAITING_GIVER_DECISION), NOW))
        listOf(ItemStatus.GIVEN, ItemStatus.WITHDRAWN).forEach { assertEquals(S.ARCHIVED, R.section(p(it), NOW)) }
        assertEquals(S.ARCHIVED, R.section(p(expires = Timestamp(NOW.epochMillis - 1)), NOW))
    }

    @Test fun r_1_9_02_badges_and_copy() {
        assertEquals("0 pending requests", R.pendingLabel(0))
        assertEquals("1 pending request", R.pendingLabel(1))
        assertEquals("16 pending requests", R.pendingLabel(16))
        assertEquals("Waiting for claims", S.WAITING.subtitle)
        assertEquals("Nothing here yet.", R.EMPTY)
        assertTrue(R.COLLECTOR_NOTICE.startsWith("Sorry, the publisher removed this publication."))
    }

    @Test fun r_1_9_03_grouping_order_and_tabs() {
        val list = listOf(p(ItemStatus.GIVEN), p(pending = 2), p(), p(ItemStatus.RESERVED, collector = Collector("a", Timestamp(NOW.epochMillis + 9 * H))))
        assertEquals(listOf(S.WAITING, S.ACTION_NEEDED, S.PENDING_COLLECTION), R.grouped(list, PublicationsTab.ACTIVE, NOW).map { it.first })
        assertEquals(listOf(S.ARCHIVED), R.grouped(list, PublicationsTab.ARCHIVED, NOW).map { it.first })
        assertTrue(R.grouped(emptyList(), PublicationsTab.ACTIVE, NOW).isEmpty())
    }

    @Test fun r_1_9_04_cancel_until_two_hours_before_pickup_start() {
        val assigned = { h: Long -> p(ItemStatus.RESERVED, collector = Collector("a", Timestamp(NOW.epochMillis + h))) }
        assertTrue(R.canCancel(assigned(2 * H + 1), NOW))
        assertFalse(R.canCancel(assigned(2 * H), NOW))
        assertFalse(R.canCancel(assigned(H), NOW))
        assertTrue(R.canCancel(p(start = Timestamp(NOW.epochMillis + 3 * H)), NOW))
        assertFalse(R.canCancel(p(start = Timestamp(NOW.epochMillis + H)), NOW))
        assertFalse(R.canCancel(p(ItemStatus.WITHDRAWN), NOW))
    }

    @Test fun r_1_9_05_edit_before_assignment_archived_not_clickable() {
        assertTrue(R.canEdit(p(), NOW)); assertTrue(R.canEdit(p(pending = 3), NOW))
        assertFalse(R.canEdit(p(ItemStatus.RESERVED, collector = Collector("a", NOW)), NOW))
        assertFalse(R.isClickable(p(ItemStatus.GIVEN), NOW)); assertTrue(R.isClickable(p(), NOW))
    }

    @Test fun r_1_9_06_labels_and_edit_prefill() {
        assertEquals("today", R.dateLabel(DateKey(2026, 6, 6), DateKey(2026, 6, 6)))
        assertEquals("06/06/26", R.dateLabel(DateKey(2026, 6, 6), DateKey(2026, 6, 1)))
        assertEquals("9:50PM-10:00PM", R.timeRangeLabel(PickupSchedule(DateKey(2026, 6, 6), TimeOfDay(21, 50), TimeOfDay(22, 0))))
        val item = F.item().copy(photoUrls = listOf("https://x/item-photos/u/1.jpg"), photoPaths = listOf("u/1.jpg"), category = "food")
        val d = R.draftFor(p().copy(item = item), null, MeetupDetails(notes = "bell"))
        assertEquals(item.id.value, d.editingItemId)
        assertEquals(1, d.photoCount); assertEquals("u/1.jpg", d.storedPhotos.single().path)
        assertEquals("bell", d.notes); assertEquals(TimeOfDay(21, 50), d.from)
    }
}
