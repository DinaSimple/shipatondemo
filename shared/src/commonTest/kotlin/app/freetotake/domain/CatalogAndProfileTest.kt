// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
// Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.domain

import app.freetotake.domain.F.HOUR
import app.freetotake.domain.F.NOW
import app.freetotake.domain.catalog.CatalogChip
import app.freetotake.domain.catalog.CatalogFilter
import app.freetotake.domain.model.ClaimId
import app.freetotake.domain.model.ClaimStatus
import app.freetotake.domain.model.DomainEvent
import app.freetotake.domain.model.GiverNotice
import app.freetotake.domain.model.ItemId
import app.freetotake.domain.model.ItemStatus
import app.freetotake.domain.model.Timestamp
import app.freetotake.domain.model.User
import app.freetotake.domain.model.UserId
import app.freetotake.domain.rules.ClaimRules
import app.freetotake.domain.rules.UserAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CatalogAndProfileTest {

    @Test fun r_1_1_30_my_claims_chip_shows_only_listings_user_requested() {
        val chair = F.item()
        val lamp = F.item().copy(id = ItemId("item-2"), title = "Lamp")
        val claims = listOf(F.claim(F.alice), F.claim(F.bob).copy(itemId = lamp.id))
        val mine = CatalogFilter.myClaimedItemIds(F.alice.id, claims)
        assertEquals(listOf(chair), CatalogFilter.apply(listOf(chair, lamp), CatalogChip.MY_CLAIMS, mine, emptySet()))
        assertEquals(listOf(chair, lamp), CatalogFilter.apply(listOf(chair, lamp), CatalogChip.ALL, mine, emptySet()))
        assertTrue(CatalogChip.MY_CLAIMS.requiredAction.requiresAuth)
        assertTrue(!CatalogChip.ALL.requiredAction.requiresAuth)
    }

    @Test fun r_1_1_31_submitting_request_does_not_add_to_favorites() {
        val favorites = emptySet<ItemId>()
        val claim = ClaimRules.submit(F.session(F.alice), F.item(), emptyList(), ClaimId("c"), NOW).ok()
        val mine = CatalogFilter.myClaimedItemIds(F.alice.id, listOf(claim))
        assertEquals(setOf(F.item().id), mine)
        assertEquals(emptyList(), CatalogFilter.apply(listOf(F.item()), CatalogChip.FAVORITES, mine, favorites))
    }

    @Test fun r_1_1_32_requester_shown_by_public_name_else_generated_nickname() {
        assertEquals("SunnyOtter0042", User(UserId("u"), "SunnyOtter0042").displayName)
        assertEquals("SunnyOtter0042", User(UserId("u"), "SunnyOtter0042", publicName = "  ").displayName)
        assertEquals("Dina", User(UserId("u"), "SunnyOtter0042", publicName = "Dina").displayName)
    }

    @Test fun r_1_1_33_giver_notified_on_approved_cancel_with_correct_notice() {
        val early = F.claim(status = ClaimStatus.APPROVED, pickupAt = Timestamp(NOW.epochMillis + 9 * HOUR))
        val late = F.claim(status = ClaimStatus.APPROVED, pickupAt = Timestamp(NOW.epochMillis + 2 * HOUR))
        val item = F.item(ItemStatus.RESERVED)
        assertEquals(
            listOf(DomainEvent.NotifyGiver(F.giver.id, item.id, GiverNotice.PICKUP_CANCELLED_REOPENED)),
            ClaimRules.cancelByTaker(F.alice.id, item, early, NOW).ok().events,
        )
        assertEquals(
            listOf(DomainEvent.NotifyGiver(F.giver.id, item.id, GiverNotice.PICKUP_CANCELLED_REPUBLISH_NEEDED)),
            ClaimRules.cancelByTaker(F.alice.id, item, late, NOW).ok().events,
        )
        // 1.7 (spec 0.9): a pending requester leaving the queue now notifies the publisher too.
        assertEquals(
            listOf(DomainEvent.NotifyGiver(F.giver.id, F.item().id, GiverNotice.TAKER_LEFT_QUEUE)),
            ClaimRules.cancelByTaker(F.alice.id, F.item(), F.claim(), NOW).ok().events,
        )
    }

    @Test fun r_1_1_34_review_requests_is_giver_only_protected_action() {
        assertTrue(UserAction.REVIEW_REQUESTS.requiresAuth)
    }
}
