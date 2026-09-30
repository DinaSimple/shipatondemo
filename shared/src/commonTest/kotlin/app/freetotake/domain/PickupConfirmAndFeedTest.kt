// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.domain

import app.freetotake.domain.F.NOW
import app.freetotake.domain.catalog.CarouselCard
import app.freetotake.domain.catalog.FeedPolicy
import app.freetotake.domain.catalog.FeedScope
import app.freetotake.domain.catalog.HomeCarousel
import app.freetotake.domain.location.AreaLabel
import app.freetotake.domain.location.GeoPoint
import app.freetotake.domain.location.LocationAccess
import app.freetotake.domain.location.PickupConfirmation
import app.freetotake.domain.location.PickupPoint
import app.freetotake.domain.location.PickupSelectionValidator
import app.freetotake.domain.location.PickupSource
import app.freetotake.domain.location.PickupValidation
import app.freetotake.domain.model.ItemId
import app.freetotake.domain.model.ItemStatus
import app.freetotake.domain.model.Timestamp
import app.freetotake.domain.rules.AccessDecision
import app.freetotake.domain.rules.AccessPolicy
import app.freetotake.domain.rules.AuthIntegrationPoint
import app.freetotake.domain.rules.AuthMode
import app.freetotake.domain.rules.AuthModePolicy
import app.freetotake.domain.model.Session
import app.freetotake.domain.rules.UserAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PickupConfirmAndFeedTest {

    private val bcn = AreaLabel("Barcelona", "08019")
    private val cafe = PickupPoint(GeoPoint(41.40, 2.20), "Poblenou Bar Sol", "Rambla del Poblenou 125", bcn)

    // ---------- 0.5 Continue validation ----------

    @Test fun r_1_3_01_continue_only_for_valid_selection() {
        assertEquals(PickupValidation.Valid, PickupSelectionValidator.validate(cafe, PickupSource.DIRECTORY, false))
        assertEquals(PickupValidation.Valid, PickupSelectionValidator.validate(cafe.copy(placeName = null), PickupSource.MAP_PIN, false))
        assertEquals(PickupValidation.NoSelection, PickupSelectionValidator.validate(null, null, false))
        assertEquals(PickupValidation.Pending, PickupSelectionValidator.validate(cafe, PickupSource.MAP_PIN, resolving = true))
        assertTrue(PickupSelectionValidator.canContinue(PickupValidation.Valid))
        listOf(PickupValidation.NoSelection, PickupValidation.Pending, PickupValidation.Unresolved, PickupValidation.OutOfRange)
            .forEach { assertFalse(PickupSelectionValidator.canContinue(it)) }
    }

    @Test fun r_1_3_02_unresolved_map_point_or_nameless_directory_hit_is_invalid() {
        val bare = PickupPoint(GeoPoint(41.4, 2.2))
        assertEquals(PickupValidation.Unresolved, PickupSelectionValidator.validate(bare, PickupSource.MAP_PIN, false))
        assertEquals(PickupValidation.Unresolved, PickupSelectionValidator.validate(bare.copy(address = "x"), PickupSource.DIRECTORY, false))
        assertEquals(PickupValidation.OutOfRange, PickupSelectionValidator.validate(PickupPoint(GeoPoint(123.0, 2.0), "X"), PickupSource.DIRECTORY, false))
    }

    @Test fun r_1_3_03_confirmation_message_and_auto_return_after_3s() {
        assertEquals("Ready to pick up point for your free giveaway successfully added.", PickupConfirmation.MESSAGE)
        assertEquals(3_000L, PickupConfirmation.AUTO_RETURN_MS)
    }

    // ---------- 0.5 Home content ----------

    private fun item(id: String, city: String?, status: ItemStatus = ItemStatus.AVAILABLE, created: Long = 0, expires: Timestamp? = null) =
        F.item(status).copy(id = ItemId(id), area = city?.let { AreaLabel(it, null) }, createdAt = Timestamp(NOW.epochMillis - created), expiresAt = expires)

    @Test fun r_1_3_10_carousel_max_5_cards_last_is_view_more() {
        val feed = (1..9).map { item("i$it", "Barcelona", created = it.toLong()) }
        val cards = HomeCarousel.build(feed)
        assertEquals(5, cards.size)
        assertEquals(CarouselCard.ViewMore, cards.last())
        assertEquals(listOf("i1", "i2", "i3", "i4"), cards.dropLast(1).map { (it as CarouselCard.Listing).item.id.value })
        assertEquals(listOf(CarouselCard.Listing(feed[0]), CarouselCard.ViewMore), HomeCarousel.build(feed.take(1)))
        assertEquals(emptyList(), HomeCarousel.build(emptyList()), "empty → empty state, no View More")
        assertEquals("View More", HomeCarousel.VIEW_MORE_LABEL)
    }

    @Test fun r_1_3_11_no_location_shows_all_active_listings() {
        assertEquals(FeedScope.All, FeedPolicy.scope(null, LocationAccess.DENIED, null))
        assertEquals(FeedScope.All, FeedPolicy.scope(null, LocationAccess.DENIED, bcn), "area ignored without permission")
        val feed = listOf(
            item("a", "Barcelona"), item("b", "Madrid"),
            item("given", "Madrid", ItemStatus.GIVEN), item("withdrawn", null, ItemStatus.WITHDRAWN),
            item("late", "Madrid", ItemStatus.AWAITING_GIVER_DECISION),
            item("expired", "Madrid", expires = Timestamp(NOW.epochMillis - 1)),
            item("reserved", "Madrid", ItemStatus.RESERVED),
        )
        assertEquals(setOf("a", "b"), FeedPolicy.apply(feed, FeedScope.All, NOW).map { it.id.value }.toSet())
    }

    @Test fun r_1_3_12_selected_location_shows_same_city_only() {
        assertEquals(FeedScope.City("Barcelona"), FeedPolicy.scope(cafe, LocationAccess.DENIED, null))
        assertEquals(FeedScope.City("Madrid"), FeedPolicy.scope(null, LocationAccess.APPROXIMATE, AreaLabel("Madrid", "28001")))
        assertEquals(FeedScope.City("Barcelona"), FeedPolicy.scope(cafe, LocationAccess.APPROXIMATE, AreaLabel("Madrid", null)), "chosen point wins")
        val feed = listOf(item("a", "Barcelona"), item("b", "barcelona "), item("c", "Madrid"), item("d", null))
        assertEquals(setOf("a", "b"), FeedPolicy.apply(feed, FeedScope.City("Barcelona"), NOW).map { it.id.value }.toSet())
    }

    @Test fun r_1_3_13_feed_orders_available_first_then_newest() {
        val feed = listOf(item("old", "X", created = 100), item("res", "X", ItemStatus.RESERVED, created = 1), item("new", "X", created = 5))
        assertEquals(listOf("new", "old"), FeedPolicy.apply(feed, FeedScope.All, NOW).map { it.id.value })
    }

    // ---------- Sign-in placeholder (deferred by product decision) ----------

    @Test fun r_1_3_20_auth_deferred_placeholder_points_are_tracked() {
        assertEquals(AuthMode.ENFORCED, AuthModePolicy.CURRENT, "v1.12: sign-in exists")
        // Rules still say these actions need an account — only the prompt is deferred.
        val decision = AccessPolicy.check(Session.Guest, UserAction.POST_ITEM)
        assertTrue(decision is AccessDecision.LoginRequired)
        assertFalse(AuthModePolicy.shouldPrompt(AuthMode.DEFERRED, decision))
        assertTrue(AuthModePolicy.shouldPrompt(AuthMode.ENFORCED, decision))
        // Every integration point is still open; this test must be updated when each is implemented.
        assertTrue(AuthIntegrationPoint.entries.filter { it.implemented }.size == AuthIntegrationPoint.entries.size - 1)
        assertTrue(AuthIntegrationPoint.PICKUP_POINT_SYNC in AuthIntegrationPoint.entries)
        assertTrue(AuthIntegrationPoint.CREATE_GIVEAWAY in AuthIntegrationPoint.entries)
    }
}
