// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.domain

import app.freetotake.domain.F.NOW
import app.freetotake.domain.catalog.CardBadge
import app.freetotake.domain.catalog.CardStates
import app.freetotake.domain.catalog.Favorites
import app.freetotake.domain.catalog.MyClaims
import app.freetotake.domain.catalog.ClaimFilter
import app.freetotake.domain.catalog.ClaimSort
import app.freetotake.domain.catalog.MyClaimsSession
import app.freetotake.domain.model.ClaimStatus
import app.freetotake.domain.model.DomainError
import app.freetotake.domain.model.DomainEvent
import app.freetotake.domain.model.GiverNotice
import app.freetotake.domain.model.ItemStatus
import app.freetotake.domain.model.Timestamp
import app.freetotake.domain.rules.ClaimRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** v1.7 — spec 0.9 + authoritative 0.10 (card states in My Claims vs public catalog). */
class CardStatesTest {

    @Test fun r_1_7_01_public_card_available_with_heart_no_trash() {
        val s = CardStates.public(F.item(), myClaim = null, now = NOW)
        assertEquals(listOf(CardBadge.AVAILABLE), s.badges)
        assertTrue(s.showHeart); assertFalse(s.showTrash); assertFalse(s.greyed)
    }

    @Test fun r_1_7_02_my_claims_pending_and_open_shows_pending_available_heart_trash() {
        val s = CardStates.myClaims(F.item(), F.claim(), NOW)
        assertEquals(listOf(CardBadge.PENDING, CardBadge.AVAILABLE), s.badges)
        assertTrue(s.showHeart); assertTrue(s.showTrash); assertFalse(s.greyed)
    }

    @Test fun r_1_7_03_every_my_claims_card_has_trash() {
        listOf(ClaimStatus.PENDING_APPROVAL, ClaimStatus.APPROVED, ClaimStatus.REJECTED).forEach {
            assertTrue(CardStates.myClaims(F.item(), F.claim(status = it), NOW).showTrash, "$it")
        }
    }

    @Test fun r_1_7_04_rejected_is_greyed_and_removable() {
        val s = CardStates.myClaims(F.item(), F.claim(status = ClaimStatus.REJECTED), NOW)
        assertTrue(s.greyed); assertTrue(s.showTrash); assertEquals(listOf(CardBadge.REJECTED), s.badges)
        assertEquals(1, MyClaims.of(F.alice.id, listOf(F.claim(status = ClaimStatus.REJECTED)), listOf(F.item())).size)
    }

    /** 1.7.1 (answer Q18): pending but someone else selected → "Rejected for you", greyed, removable. */
    @Test fun r_1_7_05_recipient_selected_shows_rejected_for_you() {
        val s = CardStates.myClaims(F.item(ItemStatus.RESERVED), F.claim(), NOW)
        assertEquals(listOf(CardBadge.REJECTED), s.badges); assertEquals("Rejected for you", CardBadge.REJECTED.label)
        assertFalse(s.showHeart); assertTrue(s.showTrash); assertTrue(s.greyed)
    }

    @Test fun r_1_7_06_remove_pending_leaves_queue_notifies_publisher_and_hides() {
        val t = ClaimRules.removeFromMyClaims(F.alice.id, F.item(), F.claim(), NOW).ok()
        val c = t.changedClaims.single()
        assertEquals(ClaimStatus.CANCELLED_BY_TAKER, c.status)   // no longer approvable
        assertTrue(c.hiddenByTaker)
        assertEquals(listOf(DomainEvent.NotifyGiver(F.giver.id, F.item().id, GiverNotice.TAKER_LEFT_QUEUE)), t.events)
        assertTrue(MyClaims.of(F.alice.id, listOf(c), listOf(F.item())).isEmpty())
    }

    @Test fun r_1_7_07_remove_rejected_only_hides() {
        val t = ClaimRules.removeFromMyClaims(F.alice.id, F.item(), F.claim(status = ClaimStatus.REJECTED), NOW).ok()
        assertEquals(ClaimStatus.REJECTED, t.changedClaims.single().status)
        assertTrue(t.changedClaims.single().hiddenByTaker)
        assertTrue(t.events.isEmpty())
    }

    @Test fun r_1_7_08_remove_approved_follows_8h_rule_and_only_owner_can_remove() {
        val approved = F.claim(status = ClaimStatus.APPROVED, pickupAt = Timestamp(NOW.epochMillis + 2 * F.HOUR))
        val t = ClaimRules.removeFromMyClaims(F.alice.id, F.item(ItemStatus.RESERVED), approved, NOW).ok()
        assertEquals(ClaimStatus.CANCELLED_LATE, t.changedClaims.single().status)
        assertEquals(ItemStatus.AWAITING_GIVER_DECISION, t.item.status)
        ClaimRules.removeFromMyClaims(F.bob.id, F.item(), F.claim(), NOW).assertErr(DomainError.NOT_CLAIM_OWNER)
    }

    @Test fun r_1_7_09_reserved_takes_no_new_requests() {
        ClaimRules.submit(F.session(F.bob), F.item(ItemStatus.RESERVED), emptyList(), app.freetotake.domain.model.ClaimId("x"), NOW)
            .assertErr(DomainError.ITEM_NOT_CLAIMABLE)
    }

    @Test fun r_1_7_10_filters_and_favorites_toggle() {
        val p = CardStates.myClaims(F.item(), F.claim(), NOW); val r = CardStates.myClaims(F.item(), F.claim(status = ClaimStatus.REJECTED), NOW)
        assertTrue(ClaimFilter.ALL.matches(r)); assertTrue(ClaimFilter.PENDING.matches(p)); assertFalse(ClaimFilter.APPROVED.matches(p))
        assertTrue(ClaimFilter.REJECTED.matches(r))
        assertTrue(ClaimFilter.REJECTED.matches(CardStates.myClaims(F.item(ItemStatus.RESERVED), F.claim(), NOW)))
        assertEquals(setOf("a"), Favorites.toggle(emptySet(), "a"))
        assertEquals(emptySet(), Favorites.toggle(setOf("a"), "a"))
    }

    @Test fun r_1_7_11_approved_trash_until_pickup_time_then_none() {
        val approved = F.claim(status = ClaimStatus.APPROVED, pickupAt = Timestamp(NOW.epochMillis + F.HOUR))
        assertTrue(CardStates.myClaims(F.item(ItemStatus.RESERVED), approved, NOW).showTrash)
        val passed = approved.copy(pickupAt = Timestamp(NOW.epochMillis - 1))
        assertFalse(CardStates.myClaims(F.item(ItemStatus.RESERVED), passed, NOW).showTrash)
        ClaimRules.removeFromMyClaims(F.alice.id, F.item(ItemStatus.RESERVED), passed, NOW).assertErr(DomainError.PICKUP_PASSED)
    }

    @Test fun r_1_7_12_rejected_cannot_resubmit() {
        ClaimRules.submit(F.session(F.alice), F.item(), listOf(F.claim(status = ClaimStatus.REJECTED)), app.freetotake.domain.model.ClaimId("n"), NOW)
            .assertErr(DomainError.ALREADY_REJECTED)
    }

    @Test fun r_1_7_13_my_claims_sort_and_filter_session() {
        val old = MyClaims.Entry(F.claim(id = "old").copy(createdAt = Timestamp(1)), F.item())
        val new = MyClaims.Entry(F.claim(id = "new").copy(createdAt = Timestamp(5)), F.item())
        val rej = MyClaims.Entry(F.claim(id = "rej", status = ClaimStatus.REJECTED).copy(createdAt = Timestamp(3)), F.item())
        var s = MyClaimsSession()
        assertEquals(listOf("new", "rej", "old"), s.visible(listOf(old, new, rej), NOW).map { it.claim.id.value })
        s = s.openSheet().pick(ClaimSort.OLDEST_REQUESTED_FIRST)
        assertEquals(ClaimSort.RECENTLY_REQUESTED_FIRST, s.cancel().sort)          // Cancel discards pending only
        s = s.apply().select(ClaimFilter.PENDING)
        assertEquals(listOf("old", "new"), s.visible(listOf(old, new, rej), NOW).map { it.claim.id.value })
        assertEquals(listOf("Recently requested first", "Oldest requested first"), ClaimSort.entries.map { it.label })
    }
}
