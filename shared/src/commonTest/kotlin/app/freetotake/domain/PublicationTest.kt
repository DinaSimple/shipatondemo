// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
// Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.domain

import app.freetotake.domain.catalog.Category
import app.freetotake.domain.location.AreaLabel
import app.freetotake.domain.location.GeoPoint
import app.freetotake.domain.location.PickupPoint
import app.freetotake.domain.publish.Clock12
import app.freetotake.domain.publish.DateSheet
import app.freetotake.domain.publish.DraftIssue
import app.freetotake.domain.publish.MonthGrid
import app.freetotake.domain.publish.PublicationDraft
import app.freetotake.domain.publish.PublicationRules
import app.freetotake.domain.publish.PublishCopy
import app.freetotake.domain.request.DateKey
import app.freetotake.domain.request.LocalNow
import app.freetotake.domain.request.TimeOfDay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** v1.8 — spec 0.11 Create publication flow. */
class PublicationTest {
    private val today = DateKey(2026, 9, 27)
    private val now = LocalNow(today, TimeOfDay(12, 0))
    private val pickup = PickupPoint(GeoPoint(41.4, 2.2), "Poblenou", "Poblenou, 19", AreaLabel("Barcelona", "08005"))
    private val valid = PublicationDraft(
        title = "Free toys", category = Category.KIDS_TOYS, date = today.plusDays(1),
        from = TimeOfDay(16, 0), to = TimeOfDay(17, 30), pickup = pickup,
    )

    @Test fun r_1_8_01_required_form_fields_description_optional() {
        assertTrue(PublicationRules.canContinue(valid, now))
        val issues = PublicationRules.issues(PublicationDraft(), now)
        assertEquals(setOf(DraftIssue.TITLE_MISSING, DraftIssue.CATEGORY_MISSING, DraftIssue.DATE_MISSING, DraftIssue.TIME_MISSING, DraftIssue.LOCATION_MISSING), issues.toSet())
    }

    @Test fun r_1_8_02_description_max_1000_no_expand() {
        assertTrue(PublicationRules.canContinue(valid.copy(description = "x".repeat(1000)), now))
        assertEquals(listOf(DraftIssue.DESCRIPTION_TOO_LONG), PublicationRules.issues(valid.copy(description = "x".repeat(1001)), now))
    }

    @Test fun r_1_8_03_one_category_from_catalog_list_without_all() {
        assertEquals(listOf("Kids Toys", "Furniture", "Food", "Books", "Clothes", "Random"), Category.entries.map { it.label })
    }

    @Test fun r_1_8_04_date_sheet_apply_commits_close_keeps_previous() {
        val first = DateSheet.open(null, today).pick(today.plusDays(3))
        assertNull(first.close())                                   // placeholder stays
        assertEquals(today.plusDays(3), first.apply())
        val again = DateSheet.open(today.plusDays(3), today).pick(today.plusDays(5))
        assertEquals(today.plusDays(3), again.close())              // saved date unchanged
    }

    @Test fun r_1_8_05_calendar_sunday_first_past_days_disabled() {
        val grid = MonthGrid.of(DateKey(2026, 9, 1), today)
        assertEquals(DateKey(2026, 8, 30), grid.first().date)       // 1 Sep 2026 is a Tuesday
        assertEquals(0, grid.size % 7)
        assertFalse(grid.first { it.date == DateKey(2026, 9, 26) }.selectable)
        assertTrue(grid.first { it.date == today }.selectable)
        assertFalse(grid.last().inMonth)
    }

    @Test fun r_1_8_06_time_range_valid_and_not_in_past() {
        assertEquals(listOf(DraftIssue.TIME_RANGE_INVALID), PublicationRules.issues(valid.copy(from = TimeOfDay(17, 0), to = TimeOfDay(17, 0)), now))
        assertEquals(listOf(DraftIssue.TIME_IN_PAST), PublicationRules.issues(valid.copy(date = today, from = TimeOfDay(11, 0), to = TimeOfDay(13, 0)), now))
        assertEquals(listOf(DraftIssue.DATE_IN_PAST), PublicationRules.issues(valid.copy(date = DateKey(2026, 9, 26)), now))
    }

    @Test fun r_1_8_07_clock12_conversion_and_labels() {
        assertEquals(TimeOfDay(16, 30), Clock12(4, 30, pm = true).to24())
        assertEquals(TimeOfDay(0, 0), Clock12(12, 0, pm = false).to24())
        assertEquals(TimeOfDay(12, 0), Clock12(12, 0, pm = true).to24())
        assertNull(Clock12(13, 0, pm = false).to24())
        assertEquals("4:30 pm", Clock12.label(TimeOfDay(16, 30)))
        assertEquals("September, 20", PublicationRules.dateLabel(DateKey(2026, 9, 20)))
    }

    @Test fun r_1_8_08_range_becomes_30_min_request_slots() {
        val s = PublicationRules.slots(today, TimeOfDay(16, 0), TimeOfDay(17, 30))
        assertEquals(listOf("16:00", "16:30", "17:00"), s.map { it.time.label() })
    }

    /** 1.8.1 (answer Q23): photos are required to publish (not to leave the form step). */
    @Test fun r_1_8_09_photos_required_to_publish_max_three() {
        var d = valid
        repeat(3) { d = PublicationRules.addPhoto(d, "uri$it")!! }
        assertNull(PublicationRules.addPhoto(d, "uri4"))
        assertTrue(PublicationRules.canContinue(valid.copy(photos = emptyList()), now))
        assertEquals(listOf(DraftIssue.PHOTO_MISSING), PublicationRules.publishIssues(valid, now))
        assertTrue(PublicationRules.canPublish(valid.copy(photos = listOf("uri")), now))
        assertTrue(PublishCopy.MAX_PHOTOS_ALERT.contains("not more than 3"))
    }

    @Test fun r_1_8_10_copy_and_lifetime() {
        assertEquals("Log in to create a publication", PublishCopy.LOGIN_TITLE)
        assertEquals("Ready!", PublishCopy.READY_TITLE)
        assertEquals(14, PublicationRules.LIFETIME_DAYS)
        assertEquals(14, PublicationRules.PHOTO_RETENTION_AFTER_EXPIRY_DAYS)
        assertTrue(PublishCopy.PHOTOS_CLEANUP_NOTE.contains("14 days after"))
    }
}
