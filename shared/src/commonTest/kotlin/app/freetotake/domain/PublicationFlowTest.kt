// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Dina Elokhova (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.domain

import app.freetotake.domain.catalog.Category
import app.freetotake.domain.publish.PublicationDraft
import app.freetotake.domain.publish.PublicationRules
import app.freetotake.domain.publish.PublishFlow
import app.freetotake.domain.publish.PublishStep
import app.freetotake.domain.request.DateKey
import app.freetotake.domain.request.LocalNow
import app.freetotake.domain.request.TimeOfDay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class PublicationFlowTest {
    private val today = DateKey(2026, 9, 28)
    private val late = LocalNow(today, TimeOfDay(17, 15))
    private fun draft(from: Int = 10, to: Int = 11, date: DateKey = today) = PublicationDraft(
        title = "test", category = Category.entries.first(), date = date, from = TimeOfDay(from, 0), to = TimeOfDay(to, 0),
        pickup = app.freetotake.domain.location.PickupPoint(app.freetotake.domain.location.GeoPoint(41.39, 2.17)),
    )

    @Test fun r_1_16_9_01_hint_names_time_already_passed() {
        assertEquals("This time has already passed today. Choose a later time or another day.", PublicationRules.hint(draft(), late))
        assertNull(PublicationRules.hint(draft(18, 19), late), "valid form → no hint")
        assertEquals("Fill in title, category, date, time and meetup location to continue.", PublicationRules.hint(PublicationDraft(), late))
    }

    @Test fun r_1_16_9_02_guest_next_asks_login_then_resumes_on_photos() {
        assertEquals(PublishFlow.NextAction.ASK_LOGIN, PublishFlow.onNext(authenticated = false))
        assertEquals(PublishFlow.NextAction.GO_TO_PHOTOS, PublishFlow.onNext(authenticated = true))
        assertEquals(PublishStep.PHOTOS, PublishFlow.resumeStep(draft(18, 19), late))
        assertEquals(PublishStep.FORM, PublishFlow.resumeStep(PublicationDraft(), late))
    }

    @Test fun r_1_16_9_03_debug_user_without_server_session_gets_mocked_publish() {
        assertTrue(PublishFlow.mockPublish(debugUser = true, hasServerSession = false))
        assertFalse(PublishFlow.mockPublish(debugUser = true, hasServerSession = true))
        assertFalse(PublishFlow.mockPublish(debugUser = false, hasServerSession = false))
    }
}
