// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
// Copyright (C) 2026 Dina Elokhova (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.domain

import app.freetotake.domain.location.AreaLabel
import app.freetotake.domain.location.GeoPoint
import app.freetotake.domain.location.HomeLocation
import app.freetotake.domain.location.HomeLocationResolver
import app.freetotake.domain.location.LocationAccess
import app.freetotake.domain.location.PickupPoint
import app.freetotake.domain.location.PickupPrivacy
import app.freetotake.domain.location.PlaceSearchPolicy
import app.freetotake.domain.location.PlaceSuggestion
import app.freetotake.domain.media.ImagePolicy
import app.freetotake.domain.model.ClaimStatus
import app.freetotake.domain.model.Session
import app.freetotake.domain.onboarding.OnboardingFlow
import app.freetotake.domain.onboarding.OnboardingStep
import app.freetotake.domain.prefs.AppPreferences
import app.freetotake.domain.prefs.InMemoryKeyValueStore
import app.freetotake.domain.rules.AuthGate
import app.freetotake.domain.rules.PendingAction
import app.freetotake.domain.rules.UserAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeLocationTest {

    private val bcn = AreaLabel("Barcelona", "08019")
    private val chosen = PickupPoint(GeoPoint(41.4036, 2.2046), "Poblenou Bar Sol", "Rambla del Poblenou 125", bcn)

    @Test fun r_1_2_01_area_label_formats_city_and_postal_code() {
        assertEquals("Barcelona, 08019", bcn.format())
        assertEquals("Barcelona", AreaLabel("Barcelona", " ").format())
        assertEquals("08019", AreaLabel(null, "08019").format())
        assertNull(AreaLabel(null, null).format())
    }

    @Test fun r_1_2_02_home_shows_device_area_when_permission_and_location_available() {
        assertEquals(HomeLocation.DeviceArea("Barcelona, 08019"),
            HomeLocationResolver.resolve(null, LocationAccess.APPROXIMATE, bcn, lookupInProgress = false))
        assertEquals(HomeLocation.Detecting,
            HomeLocationResolver.resolve(null, LocationAccess.APPROXIMATE, null, lookupInProgress = true))
        assertEquals(HomeLocation.NotSet,
            HomeLocationResolver.resolve(null, LocationAccess.APPROXIMATE, null, lookupInProgress = false))
    }

    @Test fun r_1_2_03_denied_permission_means_manual_choice_never_device_area() {
        assertEquals(HomeLocation.NotSet, HomeLocationResolver.resolve(null, LocationAccess.DENIED, bcn, false))
        assertEquals(HomeLocation.NotSet, HomeLocationResolver.resolve(null, LocationAccess.NOT_ASKED, bcn, false))
        assertEquals(HomeLocation.Chosen(chosen), HomeLocationResolver.resolve(chosen, LocationAccess.DENIED, null, false))
    }

    @Test fun r_1_2_04_user_choice_overrides_device_area() {
        assertEquals(HomeLocation.Chosen(chosen), HomeLocationResolver.resolve(chosen, LocationAccess.PRECISE, bcn, false))
        assertEquals("Poblenou Bar Sol, Rambla del Poblenou 125", chosen.displayText())
    }

    @Test fun r_1_2_05_chosen_pickup_and_onboarding_persist_independently_of_auth() {
        val store = InMemoryKeyValueStore()
        val prefs = AppPreferences(store)
        prefs.chosenPickup = chosen
        prefs.onboardingCompleted = true
        // sign in, then sign out: auth state lives elsewhere and never touches prefs
        val gate = AuthGate()
        gate.attempt(Session.Guest, PendingAction(UserAction.POST_ITEM))
        gate.onAuthenticated(F.session(F.alice))
        val relaunch = AppPreferences(store)
        assertTrue(relaunch.onboardingCompleted)
        assertEquals(OnboardingStep.DONE, OnboardingFlow.initialStep(relaunch.onboardingCompleted))
        assertEquals(chosen, relaunch.chosenPickup)
        relaunch.chosenPickup = null
        assertNull(AppPreferences(store).chosenPickup)
    }

    @Test fun r_1_2_06_exact_pickup_visible_only_to_giver_and_approved_taker() {
        val item = F.item()
        val pending = F.claim(F.alice)
        val approved = F.claim(F.bob, ClaimStatus.APPROVED, NOW_PLUS)
        assertTrue(PickupPrivacy.canSeeExact(F.giver.id, item, emptyList()))
        assertFalse(PickupPrivacy.canSeeExact(null, item, listOf(approved)), "guest")
        assertFalse(PickupPrivacy.canSeeExact(F.alice.id, item, listOf(pending, approved)), "pending taker")
        assertTrue(PickupPrivacy.canSeeExact(F.bob.id, item, listOf(pending, approved)), "approved taker")
        assertFalse(PickupPrivacy.canSeeExact(F.bob.id, item, listOf(approved.copy(status = ClaimStatus.CANCELLED_LATE))))
    }

    @Test fun r_1_2_07_public_point_is_coarse() {
        assertEquals(GeoPoint(41.4, 2.2), PickupPrivacy.approximate(GeoPoint(41.40361, 2.20462)))
    }

    @Test fun r_1_2_08_search_starts_from_initial_letters_and_ranks_prefix_first() {
        assertFalse(PlaceSearchPolicy.shouldSearch("P"))
        assertTrue(PlaceSearchPolicy.shouldSearch(" Po "))
        fun s(n: String) = PlaceSuggestion(n, null, GeoPoint(0.0, 0.0), AreaLabel(null, null))
        val ranked = PlaceSearchPolicy.rank("pobl", listOf(s("Bar Poblenou"), s("Casa Sol"), s("Poblenou street"), s("Poblenou Bar Sol")))
        assertEquals(listOf("Poblenou street", "Poblenou Bar Sol", "Bar Poblenou", "Casa Sol"), ranked.map { it.name })
        assertEquals("Giveaway pickup point", PlaceSearchPolicy.PROMPT)
    }

    @Test fun r_1_2_09_search_ignores_case_and_accents_and_highlights_prefix() {
        assertEquals("placa catalunya", PlaceSearchPolicy.fold("Plaça Catalunya"))
        assertEquals(0 until 4, PlaceSearchPolicy.highlight("Plaça Catalunya", "plac"))
        assertEquals(0 until 5, PlaceSearchPolicy.highlight("Poblenou street", "Poble"))
        assertNull(PlaceSearchPolicy.highlight("Casa Sol", "pob"))
    }

    @Test fun r_1_2_10_images_heic_accepted_compressed_max_three() {
        assertTrue(ImagePolicy.isAccepted("image/heic"))
        assertTrue(ImagePolicy.isAccepted("image/heif"))
        assertTrue(ImagePolicy.isAccepted("application/octet-stream", "IMG_0001.HEIC"))
        assertFalse(ImagePolicy.isAccepted("image/gif", "a.gif"))
        assertTrue(ImagePolicy.isHeif(null, "photo.heic"))
        assertEquals(1600 to 1200, ImagePolicy.targetSize(4032, 3024))
        assertEquals(1200 to 1600, ImagePolicy.targetSize(3024, 4032))
        assertEquals(800 to 600, ImagePolicy.targetSize(800, 600), "never upscale")
        assertTrue(ImagePolicy.canAddPhoto(2))
        assertFalse(ImagePolicy.canAddPhoto(3))
    }

    private companion object {
        val NOW_PLUS = F.NOW.plusHours(10)
    }
}
