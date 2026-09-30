// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.domain

import app.freetotake.domain.location.CatalogLocationMode
import app.freetotake.domain.location.GeoPoint
import app.freetotake.domain.location.LocationAccess
import app.freetotake.domain.location.LocationPolicy
import app.freetotake.domain.onboarding.OnboardingEvent as E
import app.freetotake.domain.onboarding.OnboardingFlow
import app.freetotake.domain.onboarding.OnboardingStep as S
import app.freetotake.domain.prefs.AppPreferences
import app.freetotake.domain.prefs.InMemoryKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OnboardingFlowTest {

    @Test fun r_1_1_20_first_launch_starts_with_splash_returning_user_skips_onboarding() {
        assertEquals(S.SPLASH, OnboardingFlow.initialStep(onboardingCompleted = false))
        assertEquals(S.DONE, OnboardingFlow.initialStep(onboardingCompleted = true))
    }

    /** Superseded in 1.8.2 (Figma "Complete onboarding"): pages advance with Next; only the splash is timed. */
    @Test fun r_1_1_21_sequence_next_in_spec_order() {
        assertEquals(S.WELCOME, OnboardingFlow.reduce(S.SPLASH, E.AutoAdvanceElapsed))
        var s = S.WELCOME
        val seen = mutableListOf(s)
        repeat(5) { s = OnboardingFlow.reduce(s, E.NextTapped); seen += s }
        // v1.17: REQUEST now leads to the broccoli page.
        assertEquals(listOf(S.WELCOME, S.GIVEAWAY, S.MEETUP, S.REQUEST, S.BROCCOLI, S.BROCCOLI), seen)
        assertEquals(S.WELCOME, OnboardingFlow.reduce(S.WELCOME, E.AutoAdvanceElapsed), "pages never auto-advance")
    }

    @Test fun r_1_1_22_timings_splash_2s_pages_manual() {
        assertEquals(2_000L, S.SPLASH.autoAdvanceMs)
        listOf(S.WELCOME, S.GIVEAWAY, S.MEETUP, S.REQUEST).forEach { assertNull(it.autoAdvanceMs) }
    }

    @Test fun r_1_1_23_next_on_pages_1_to_3_start_on_final() {
        // v1.17 (Figma "Broccoli"): the request page says Next; Start moved to the broccoli page.
        assertEquals(listOf(S.BROCCOLI), S.entries.filter { it.showsStartButton })
        assertEquals(listOf(S.WELCOME, S.GIVEAWAY, S.MEETUP, S.REQUEST), S.entries.filter { it.showsNextButton })
        assertEquals(5, S.entries.count { it.isPage })
        assertEquals(4, S.BROCCOLI.pageIndex)
        assertEquals(3, S.REQUEST.pageIndex)
    }

    /** 1.8.2: Start → notifications dialog → approximate location dialog → catalog, for any outcome. */
    @Test fun r_1_1_24_start_then_notifications_then_location_then_catalog() {
        val afterStart = OnboardingFlow.reduce(S.BROCCOLI, E.StartTapped)
        assertEquals(S.NOTIFICATION_PERMISSION, afterStart)
        val afterNotif = OnboardingFlow.reduce(afterStart, E.NotificationPermissionResolved)
        assertEquals(S.LOCATION_PERMISSION, afterNotif)
        assertEquals(S.LOCATION_PERMISSION, OnboardingFlow.reduce(afterStart, E.LocationPermissionResolved).let { OnboardingFlow.reduce(it, E.NotificationPermissionResolved) }, "location never before notifications")
        assertEquals(S.DONE, OnboardingFlow.reduce(afterNotif, E.LocationPermissionResolved))
        assertTrue(OnboardingFlow.shouldMarkCompleted(S.DONE))
        assertFalse(OnboardingFlow.shouldMarkCompleted(S.LOCATION_PERMISSION))
        assertFalse(OnboardingFlow.shouldMarkCompleted(S.NOTIFICATION_PERMISSION))
    }

    /** 1.8.2: Skip → same permission sequence (was: jump to final screen). Back goes one step. */
    @Test fun r_1_1_25_skip_goes_to_permissions_back_goes_one_step() {
        listOf(S.WELCOME, S.GIVEAWAY, S.MEETUP).forEach { assertEquals(S.NOTIFICATION_PERMISSION, OnboardingFlow.reduce(it, E.Skip)) }
        assertEquals(S.REQUEST, OnboardingFlow.reduce(S.REQUEST, E.Skip), "no Skip on the final screen")
        assertEquals(S.MEETUP, OnboardingFlow.reduce(S.REQUEST, E.Back))
        assertEquals(S.WELCOME, OnboardingFlow.reduce(S.GIVEAWAY, E.Back))
        assertEquals(S.WELCOME, OnboardingFlow.reduce(S.WELCOME, E.Back))
        assertEquals(S.SPLASH, OnboardingFlow.reduce(S.SPLASH, E.Skip), "no skip on splash")
    }

    @Test fun r_1_8_2_01_no_permission_before_pages_finished_or_skipped() {
        listOf(S.SPLASH, S.WELCOME, S.GIVEAWAY, S.MEETUP, S.REQUEST).forEach { assertFalse(it.isPermissionStep) }
        listOf(S.SPLASH, S.WELCOME, S.GIVEAWAY, S.MEETUP).forEach { assertEquals(it, OnboardingFlow.reduce(it, E.NotificationPermissionResolved)) }
    }

    @Test fun r_1_1_26_start_ignored_outside_final_screen() {
        listOf(S.SPLASH, S.WELCOME, S.GIVEAWAY, S.MEETUP).forEach {
            assertEquals(it, OnboardingFlow.reduce(it, E.StartTapped))
        }
    }

    @Test fun r_1_1_27_denied_location_never_blocks_catalog() {
        LocationAccess.entries.forEach { assertTrue(LocationPolicy.canBrowseCatalog(it)) }
        assertEquals(CatalogLocationMode.MANUAL_AREA, LocationPolicy.catalogMode(LocationAccess.DENIED))
        assertEquals(CatalogLocationMode.NEARBY, LocationPolicy.catalogMode(LocationAccess.APPROXIMATE))
    }

    @Test fun r_1_1_28_meetup_point_requires_explicit_user_choice() {
        assertFalse(LocationPolicy.canPublishMeetupPoint(null))
        assertTrue(LocationPolicy.canPublishMeetupPoint(GeoPoint(40.4, -3.7)))
    }

    @Test fun r_1_1_29_onboarding_flag_and_location_access_persist() {
        val store = InMemoryKeyValueStore()
        val prefs = AppPreferences(store)
        assertFalse(prefs.onboardingCompleted)
        assertEquals(LocationAccess.NOT_ASKED, prefs.locationAccess)
        prefs.onboardingCompleted = true
        prefs.locationAccess = LocationAccess.DENIED
        val reopened = AppPreferences(store) // simulates next app launch
        assertTrue(reopened.onboardingCompleted)
        assertEquals(LocationAccess.DENIED, reopened.locationAccess)
    }

    @Test fun r_1_17_01_reward_screen_once_after_onboarding_even_if_skipped() {
        assertEquals(S.REQUEST, OnboardingFlow.reduce(S.BROCCOLI, E.Back))
        assertEquals(S.REQUEST, OnboardingFlow.reduce(S.REQUEST, E.StartTapped), "Start only on the broccoli page")
        // Skip still goes to permissions → DONE, and the reward screen must still appear.
        assertTrue(app.freetotake.domain.onboarding.BroccoliReward.mustShow(onboardingDone = true, rewardSeen = false))
        assertFalse(app.freetotake.domain.onboarding.BroccoliReward.mustShow(onboardingDone = true, rewardSeen = true))
        assertFalse(app.freetotake.domain.onboarding.BroccoliReward.mustShow(onboardingDone = false, rewardSeen = false))
    }
}
