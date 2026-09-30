// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
// Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.domain

import app.freetotake.domain.F.HOUR
import app.freetotake.domain.F.NOW
import app.freetotake.domain.model.ClaimId
import app.freetotake.domain.model.ClaimStatus
import app.freetotake.domain.model.DomainError
import app.freetotake.domain.model.ItemStatus
import app.freetotake.domain.model.Session
import app.freetotake.domain.model.Timestamp
import app.freetotake.domain.rules.ClaimRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClaimRulesTest {

    // ---------- submit ----------

    @Test fun r_1_0_02_guest_cannot_submit() {
        ClaimRules.submit(Session.Guest, F.item(), emptyList(), ClaimId("x"), NOW).assertErr(DomainError.LOGIN_REQUIRED)
    }

    @Test fun r_1_0_04_giver_cannot_claim_own_item() {
        ClaimRules.submit(F.session(F.giver), F.item(), emptyList(), ClaimId("x"), NOW)
            .assertErr(DomainError.CANNOT_CLAIM_OWN_ITEM)
    }

    @Test fun r_1_0_05_one_active_claim_per_taker_per_item() {
        ClaimRules.submit(F.session(F.alice), F.item(), listOf(F.claim()), ClaimId("x"), NOW)
            .assertErr(DomainError.ALREADY_HAS_ACTIVE_CLAIM)
        // a cancelled claim does not block a new one
        val again = ClaimRules.submit(
            F.session(F.alice), F.item(),
            listOf(F.claim(status = ClaimStatus.CANCELLED_BY_TAKER)), ClaimId("x2"), NOW,
        ).ok()
        assertEquals(ClaimStatus.PENDING_APPROVAL, again.status)
    }

    @Test fun r_1_0_06_new_claim_is_pending_approval() {
        val c = ClaimRules.submit(F.session(F.alice), F.item(), emptyList(), ClaimId("x"), NOW, "  ").ok()
        assertEquals(ClaimStatus.PENDING_APPROVAL, c.status)
        assertEquals(F.alice.id, c.takerId)
        assertNull(c.message, "blank message should be dropped")
    }

    /** Superseded in 1.7 (spec 0.10): a reserved listing (recipient selected) takes no new requests. */
    @Test fun r_1_0_07_no_new_requests_once_recipient_selected_or_closed() {
        ClaimRules.submit(F.session(F.bob), F.item(), emptyList(), ClaimId("x"), NOW).ok()
        listOf(ItemStatus.RESERVED, ItemStatus.AWAITING_GIVER_DECISION, ItemStatus.GIVEN, ItemStatus.WITHDRAWN).forEach {
            ClaimRules.submit(F.session(F.bob), F.item(it), emptyList(), ClaimId("x"), NOW)
                .assertErr(DomainError.ITEM_NOT_CLAIMABLE)
        }
    }

    // ---------- approve / reject ----------

    @Test fun r_1_0_08_only_giver_can_approve_or_reject() {
        ClaimRules.approve(F.bob.id, F.item(), F.claim(), listOf(F.claim()), NOW.plusHours(24), NOW)
            .assertErr(DomainError.NOT_ITEM_OWNER)
        ClaimRules.reject(F.bob.id, F.item(), F.claim()).assertErr(DomainError.NOT_ITEM_OWNER)
    }

    @Test fun r_1_0_09_approve_reserves_item_and_only_one_approval_per_item() {
        val a = F.claim(F.alice)
        val b = F.claim(F.bob)
        val t = ClaimRules.approve(F.giver.id, F.item(), a, listOf(a, b), NOW.plusHours(24), NOW).ok()
        assertEquals(ItemStatus.RESERVED, t.item.status)
        val approved = t.changedClaims.first()
        assertEquals(ClaimStatus.APPROVED, approved.status)
        // 1.10 (spec 0.13 design): every other pending request is auto-rejected
        assertEquals(listOf(ClaimStatus.REJECTED), t.changedClaims.drop(1).map { it.status })
        assertEquals(NOW.plusHours(24), approved.pickupAt)

        ClaimRules.approve(F.giver.id, t.item, b, listOf(approved, b), NOW.plusHours(24), NOW)
            .assertErr(DomainError.ITEM_ALREADY_RESERVED)
    }

    @Test fun r_1_0_09b_pickup_must_be_in_future() {
        ClaimRules.approve(F.giver.id, F.item(), F.claim(), listOf(F.claim()), NOW, NOW)
            .assertErr(DomainError.PICKUP_TIME_IN_PAST)
    }

    @Test fun r_1_0_09c_reject_pending_only() {
        assertEquals(
            ClaimStatus.REJECTED,
            ClaimRules.reject(F.giver.id, F.item(), F.claim()).ok().changedClaims.single().status,
        )
        ClaimRules.reject(F.giver.id, F.item(), F.claim(status = ClaimStatus.APPROVED, pickupAt = NOW.plusHours(9)))
            .assertErr(DomainError.INVALID_TRANSITION)
    }

    // ---------- cancel by taker ----------

    @Test fun r_1_0_10_pending_claim_can_always_be_cancelled() {
        val t = ClaimRules.cancelByTaker(F.alice.id, F.item(), F.claim(), NOW).ok()
        assertEquals(ClaimStatus.CANCELLED_BY_TAKER, t.changedClaims.single().status)
        assertEquals(ItemStatus.AVAILABLE, t.item.status)
    }

    @Test fun r_1_0_10b_only_owner_of_claim_can_cancel() {
        ClaimRules.cancelByTaker(F.bob.id, F.item(), F.claim(F.alice), NOW).assertErr(DomainError.NOT_CLAIM_OWNER)
    }

    @Test fun r_1_0_11_approved_cancel_more_than_8h_ahead_releases_slot_to_queue() {
        val approved = F.claim(status = ClaimStatus.APPROVED, pickupAt = Timestamp(NOW.epochMillis + 8 * HOUR + 1))
        val t = ClaimRules.cancelByTaker(F.alice.id, F.item(ItemStatus.RESERVED), approved, NOW).ok()
        assertTrue(t.slotReleasedToQueue)
        assertEquals(ItemStatus.AVAILABLE, t.item.status)
        assertEquals(ClaimStatus.CANCELLED_BY_TAKER, t.changedClaims.single().status)
    }

    @Test fun r_1_0_12_approved_cancel_exactly_8h_or_less_does_not_release_slot() {
        // 1.7.1 (answer Q19): at/after the pickup time cancelling is no longer possible (see r_1_7_11).
        listOf(8 * HOUR, 8 * HOUR - 1, HOUR, 1L).forEach { delta ->
            val approved = F.claim(status = ClaimStatus.APPROVED, pickupAt = Timestamp(NOW.epochMillis + delta))
            val t = ClaimRules.cancelByTaker(F.alice.id, F.item(ItemStatus.RESERVED), approved, NOW).ok()
            assertFalse(t.slotReleasedToQueue, "delta=$delta must not release")
            assertEquals(ItemStatus.AWAITING_GIVER_DECISION, t.item.status)
            assertEquals(ClaimStatus.CANCELLED_LATE, t.changedClaims.single().status)
        }
    }

    @Test fun r_1_0_12b_window_constant_is_8_hours() {
        assertEquals(8L, ClaimRules.LATE_CANCEL_WINDOW_HOURS)
    }

    @Test fun r_1_0_13_giver_can_reopen_or_approve_next_after_late_cancel() {
        val item = F.item(ItemStatus.AWAITING_GIVER_DECISION)
        assertEquals(ItemStatus.AVAILABLE, ClaimRules.reopen(F.giver.id, item).ok().item.status)
        ClaimRules.reopen(F.alice.id, item).assertErr(DomainError.NOT_ITEM_OWNER)
        val bob = F.claim(F.bob)
        val t = ClaimRules.approve(F.giver.id, item, bob, listOf(bob), NOW.plusHours(2), NOW).ok()
        assertEquals(ItemStatus.RESERVED, t.item.status)
    }

    @Test fun r_1_0_14_terminal_claims_cannot_be_cancelled() {
        listOf(ClaimStatus.REJECTED, ClaimStatus.COMPLETED, ClaimStatus.CLOSED, ClaimStatus.CANCELLED_LATE).forEach {
            ClaimRules.cancelByTaker(F.alice.id, F.item(), F.claim(status = it), NOW)
                .assertErr(DomainError.INVALID_TRANSITION)
        }
    }

    // ---------- complete / withdraw ----------

    @Test fun r_1_0_15_complete_marks_given_and_closes_queue() {
        val a = F.claim(F.alice, ClaimStatus.APPROVED, NOW.plusHours(1))
        val b = F.claim(F.bob)
        val t = ClaimRules.complete(F.giver.id, F.item(ItemStatus.RESERVED), a, listOf(a, b)).ok()
        assertEquals(ItemStatus.GIVEN, t.item.status)
        assertEquals(
            mapOf(a.id to ClaimStatus.COMPLETED, b.id to ClaimStatus.CLOSED),
            t.changedClaims.associate { it.id to it.status },
        )
    }

    @Test fun r_1_0_16_withdraw_closes_all_active_claims() {
        val a = F.claim(F.alice, ClaimStatus.APPROVED, NOW.plusHours(1))
        val b = F.claim(F.bob)
        val old = F.claim(F.alice, ClaimStatus.REJECTED, id = "old")
        val t = ClaimRules.withdraw(F.giver.id, F.item(ItemStatus.RESERVED), listOf(a, b, old)).ok()
        assertEquals(ItemStatus.WITHDRAWN, t.item.status)
        assertEquals(setOf(a.id, b.id), t.changedClaims.map { it.id }.toSet())
        assertTrue(t.changedClaims.all { it.status == ClaimStatus.CLOSED })
        ClaimRules.withdraw(F.giver.id, t.item, emptyList()).assertErr(DomainError.INVALID_TRANSITION)
    }
}
