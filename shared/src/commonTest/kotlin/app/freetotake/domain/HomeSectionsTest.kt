// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Dina Elokhova (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.domain

import app.freetotake.domain.F.NOW
import app.freetotake.domain.catalog.CarouselCard
import app.freetotake.domain.catalog.FeedScope
import app.freetotake.domain.catalog.FullCatalog
import app.freetotake.domain.catalog.HomeCarousel
import app.freetotake.domain.catalog.HomeCopy
import app.freetotake.domain.catalog.MyClaims
import app.freetotake.domain.catalog.MyGiveaways
import app.freetotake.domain.location.AreaLabel
import app.freetotake.domain.model.ClaimId
import app.freetotake.domain.model.ClaimStatus
import app.freetotake.domain.model.ItemId
import app.freetotake.domain.model.ItemStatus
import app.freetotake.domain.model.Timestamp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeSectionsTest {

    private fun item(id: String, giver: app.freetotake.domain.model.User = F.giver, city: String? = "Barcelona",
                     category: String? = null, status: ItemStatus = ItemStatus.AVAILABLE, created: Long = 0) =
        F.item(status).copy(id = ItemId(id), giverId = giver.id, area = city?.let { AreaLabel(it, null) },
            category = category, createdAt = Timestamp(NOW.epochMillis - created))

    @Test fun r_1_4_01_empty_state_copy_matches_spec_and_design() {
        assertEquals("", HomeCopy.MY_GIVEAWAYS_EMPTY, "v1.13: Start card has no text")
        assertEquals("Nothing here yet.", HomeCopy.MY_CLAIMS_EMPTY)
        assertEquals("Nothing here yet.", HomeCopy.AVAILABLE_EMPTY)
    }

    @Test fun r_1_4_02_my_giveaways_only_own_live_publications() {
        val items = listOf(item("mine", F.alice), item("given", F.alice, status = ItemStatus.GIVEN), item("other", F.bob))
        assertEquals(listOf("mine"), MyGiveaways.of(F.alice.id, items).map { it.id.value })
        assertEquals(emptyList(), MyGiveaways.of(null, items), "no user (sign-in deferred) → empty state + Start")
    }

    @Test fun r_1_4_03_my_claims_are_open_requests_for_other_users_items() {
        val chair = item("chair", F.giver)
        val lamp = item("lamp", F.giver)
        val own = item("own", F.alice)
        val claims = listOf(
            F.claim(F.alice, id = "c1").copy(itemId = chair.id, createdAt = Timestamp(NOW.epochMillis - 10)),
            F.claim(F.alice, ClaimStatus.APPROVED, NOW.plusHours(9), id = "c2").copy(itemId = lamp.id),
            F.claim(F.alice, ClaimStatus.CANCELLED_BY_TAKER, id = "c3").copy(itemId = lamp.id),
            F.claim(F.alice, id = "c4").copy(itemId = own.id),
            F.claim(F.bob, id = "c5").copy(itemId = chair.id),
        )
        val mine = MyClaims.of(F.alice.id, claims, listOf(chair, lamp, own))
        assertEquals(listOf("c2", "c1"), mine.map { it.claim.id.value }, "newest first, open only, not own items")
        assertEquals(emptyList(), MyClaims.of(null, claims, listOf(chair)))
    }

    @Test fun r_1_4_04_carousel_rule_is_5_total_last_view_more_supersedes_8() {
        val feed = (1..12).map { item("i$it", created = it.toLong()) }
        val cards = HomeCarousel.build(feed)
        assertEquals(5, cards.size)
        assertEquals(4, cards.count { it is CarouselCard.Listing })
        assertEquals(CarouselCard.ViewMore, cards.last())
    }

    @Test fun r_1_4_05_full_catalog_no_location_shows_everything_active() {
        val feed = listOf(item("bcn"), item("mad", city = "Madrid"), item("gone", status = ItemStatus.WITHDRAWN))
        assertEquals(setOf("bcn", "mad"), FullCatalog.items(feed, FeedScope.All, NOW).map { it.id.value }.toSet())
        assertEquals(12, FullCatalog.items((1..12).map { item("x$it") }, FeedScope.All, NOW).size, "no 5-card cap in catalog")
    }

    @Test fun r_1_4_06_full_catalog_with_location_limited_to_city() {
        val feed = listOf(item("bcn"), item("mad", city = "Madrid"))
        assertEquals(listOf("mad"), FullCatalog.items(feed, FeedScope.City("madrid"), NOW).map { it.id.value })
    }

    @Test fun r_1_4_07_category_chips_from_feed_and_filtering() {
        val feed = listOf(item("a", category = "Kids toys"), item("b", category = "Furniture"), item("c", category = "kids toys"), item("d", city = "Madrid", category = "Food"))
        assertEquals(listOf("All", "Kids toys", "Furniture"), FullCatalog.categories(feed, FeedScope.City("Barcelona"), NOW))
        assertEquals(setOf("a", "c"), FullCatalog.items(feed, FeedScope.All, NOW, "Kids toys").map { it.id.value }.toSet())
        assertEquals(4, FullCatalog.items(feed, FeedScope.All, NOW, "All").size)
    }

    /** Superseded in 1.7 (spec 0.10): reserved listings are hidden from the public catalog. */
    @Test fun r_1_4_08_reserved_listings_hidden_from_public_catalog() {
        val r = item("r", status = ItemStatus.RESERVED)
        assertTrue(FullCatalog.isReserved(r))
        assertFalse(FullCatalog.isReserved(item("a")))
        assertEquals(listOf("a"), FullCatalog.items(listOf(r, item("a")), FeedScope.All, NOW).map { it.id.value })
    }
}
