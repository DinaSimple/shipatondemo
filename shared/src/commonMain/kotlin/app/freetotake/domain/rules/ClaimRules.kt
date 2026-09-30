// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
// Copyright (C) 2026 Dina Elokhova (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.domain.rules

import app.freetotake.domain.model.Claim
import app.freetotake.domain.model.ClaimId
import app.freetotake.domain.model.ClaimStatus
import app.freetotake.domain.model.DomainError
import app.freetotake.domain.model.DomainEvent
import app.freetotake.domain.model.GiverNotice
import app.freetotake.domain.model.DomainResult
import app.freetotake.domain.model.Item
import app.freetotake.domain.model.ItemStatus
import app.freetotake.domain.model.Session
import app.freetotake.domain.model.Timestamp
import app.freetotake.domain.model.UserId

/** Result of any rule application: the new item state plus every claim that changed. */
data class Transition(
    val item: Item,
    val changedClaims: List<Claim>,
    /** True when an approved slot was freed and other queued takers may be approved. */
    val slotReleasedToQueue: Boolean = false,
    /** Notifications etc. to be delivered by the data layer. */
    val events: List<DomainEvent> = emptyList(),
)

/**
 * Pure business rules for the give/take flow. No I/O — the same rules are mirrored
 * server-side in Supabase RPCs (supabase/migrations) so clients can't bypass them.
 */
object ClaimRules {

    /** An approved taker may cancel and free the slot only if pickup is MORE than this far away. */
    const val LATE_CANCEL_WINDOW_HOURS: Long = 8
    private const val LATE_CANCEL_WINDOW_MS: Long = LATE_CANCEL_WINDOW_HOURS * 3_600_000L

    // ---------- TAKE role ----------

    fun submit(
        session: Session,
        item: Item,
        takerClaimsForItem: List<Claim>,
        newId: ClaimId,
        now: Timestamp,
        message: String? = null,
    ): DomainResult<Claim> {
        val user = (session as? Session.Authenticated)?.user
            ?: return err(DomainError.LOGIN_REQUIRED)
        if (item.giverId == user.id) return err(DomainError.CANNOT_CLAIM_OWN_ITEM)
        if (!item.status.acceptsClaims) return err(DomainError.ITEM_NOT_CLAIMABLE)
        if (takerClaimsForItem.any { it.takerId == user.id && it.status.isActive }) {
            return err(DomainError.ALREADY_HAS_ACTIVE_CLAIM)
        }
        // Answer Q18: a rejected requester cannot request the same giveaway again.
        if (takerClaimsForItem.any { it.takerId == user.id && it.status == ClaimStatus.REJECTED }) {
            return err(DomainError.ALREADY_REJECTED)
        }
        return DomainResult.Ok(
            Claim(
                id = newId,
                itemId = item.id,
                takerId = user.id,
                status = ClaimStatus.PENDING_APPROVAL,
                createdAt = now,
                message = message?.trim()?.takeIf { it.isNotEmpty() },
            )
        )
    }

    fun cancelByTaker(actor: UserId, item: Item, claim: Claim, now: Timestamp): DomainResult<Transition> {
        if (claim.itemId != item.id) return err(DomainError.CLAIM_ITEM_MISMATCH)
        if (claim.takerId != actor) return err(DomainError.NOT_CLAIM_OWNER)
        return when (claim.status) {
            ClaimStatus.PENDING_APPROVAL -> ok(
                Transition(
                    item, listOf(claim.copy(status = ClaimStatus.CANCELLED_BY_TAKER)),
                    events = listOf(DomainEvent.NotifyGiver(item.giverId, item.id, GiverNotice.TAKER_LEFT_QUEUE)),
                )
            )
            ClaimStatus.APPROVED -> {
                val pickup = claim.pickupAt ?: return err(DomainError.INVALID_TRANSITION)
                // Answer Q19: no cancelling once the pickup time has come (claim goes to the archive).
                if (now >= pickup) return err(DomainError.PICKUP_PASSED)
                if (isEarlyEnough(pickup, now)) {
                    ok(
                        Transition(
                            item = item.copy(status = ItemStatus.AVAILABLE),
                            changedClaims = listOf(claim.copy(status = ClaimStatus.CANCELLED_BY_TAKER)),
                            slotReleasedToQueue = true,
                            events = listOf(
                                DomainEvent.NotifyGiver(item.giverId, item.id, GiverNotice.PICKUP_CANCELLED_REOPENED)
                            ),
                        )
                    )
                } else {
                    ok(
                        Transition(
                            item = item.copy(status = ItemStatus.AWAITING_GIVER_DECISION),
                            changedClaims = listOf(claim.copy(status = ClaimStatus.CANCELLED_LATE)),
                            slotReleasedToQueue = false,
                            events = listOf(
                                DomainEvent.NotifyGiver(item.giverId, item.id, GiverNotice.PICKUP_CANCELLED_REPUBLISH_NEEDED)
                            ),
                        )
                    )
                }
            }
            else -> err(DomainError.INVALID_TRANSITION)
        }
    }

    /**
     * Trash on a My Claims card (0.9): an open request is cancelled first (leaves the queue / 8h rule,
     * publisher notified), a rejected one is just removed. Either way it disappears from My Claims.
     */
    fun removeFromMyClaims(actor: UserId, item: Item, claim: Claim, now: Timestamp): DomainResult<Transition> {
        if (claim.takerId != actor) return err(DomainError.NOT_CLAIM_OWNER)
        return when (claim.status) {
            ClaimStatus.PENDING_APPROVAL, ClaimStatus.APPROVED -> when (val r = cancelByTaker(actor, item, claim, now)) {
                is DomainResult.Ok -> ok(r.value.copy(changedClaims = r.value.changedClaims.map { it.copy(hiddenByTaker = true) }))
                is DomainResult.Err -> r
            }
            ClaimStatus.REJECTED -> ok(Transition(item, listOf(claim.copy(hiddenByTaker = true))))
            else -> err(DomainError.INVALID_TRANSITION)
        }
    }

    /** Strictly more than 8h between the cancel moment and the pickup time. */
    fun isEarlyEnough(pickupAt: Timestamp, now: Timestamp): Boolean =
        (pickupAt - now) > LATE_CANCEL_WINDOW_MS

    // ---------- GIVE role ----------

    fun approve(
        actor: UserId,
        item: Item,
        claim: Claim,
        allClaimsForItem: List<Claim>,
        pickupAt: Timestamp,
        now: Timestamp,
    ): DomainResult<Transition> {
        if (claim.itemId != item.id) return err(DomainError.CLAIM_ITEM_MISMATCH)
        if (item.giverId != actor) return err(DomainError.NOT_ITEM_OWNER)
        if (claim.status != ClaimStatus.PENDING_APPROVAL) return err(DomainError.INVALID_TRANSITION)
        if (item.status != ItemStatus.AVAILABLE && item.status != ItemStatus.AWAITING_GIVER_DECISION) {
            return err(
                if (item.status == ItemStatus.RESERVED) DomainError.ITEM_ALREADY_RESERVED
                else DomainError.ITEM_NOT_CLAIMABLE
            )
        }
        if (allClaimsForItem.any { it.status == ClaimStatus.APPROVED && it.id != claim.id }) {
            return err(DomainError.ITEM_ALREADY_RESERVED)
        }
        if (pickupAt <= now) return err(DomainError.PICKUP_TIME_IN_PAST)
        return ok(
            Transition(
                item = item.copy(status = ItemStatus.RESERVED),
                // 0.13 (design): "You can choose only one person. Others will be auto-rejected."
                changedClaims = listOf(claim.copy(status = ClaimStatus.APPROVED, pickupAt = pickupAt)) +
                    allClaimsForItem.filter { it.id != claim.id && it.status == ClaimStatus.PENDING_APPROVAL }
                        .map { it.copy(status = ClaimStatus.REJECTED) },
            )
        )
    }

    fun reject(actor: UserId, item: Item, claim: Claim): DomainResult<Transition> {
        if (claim.itemId != item.id) return err(DomainError.CLAIM_ITEM_MISMATCH)
        if (item.giverId != actor) return err(DomainError.NOT_ITEM_OWNER)
        if (claim.status != ClaimStatus.PENDING_APPROVAL) return err(DomainError.INVALID_TRANSITION)
        return ok(Transition(item, listOf(claim.copy(status = ClaimStatus.REJECTED))))
    }

    /** Giver confirms hand-over. Item becomes GIVEN; everyone else still queued is closed. */
    fun complete(actor: UserId, item: Item, claim: Claim, allClaimsForItem: List<Claim>): DomainResult<Transition> {
        if (claim.itemId != item.id) return err(DomainError.CLAIM_ITEM_MISMATCH)
        if (item.giverId != actor) return err(DomainError.NOT_ITEM_OWNER)
        if (claim.status != ClaimStatus.APPROVED || item.status != ItemStatus.RESERVED) {
            return err(DomainError.INVALID_TRANSITION)
        }
        val closed = allClaimsForItem
            .filter { it.id != claim.id && it.status.isActive }
            .map { it.copy(status = ClaimStatus.CLOSED) }
        return ok(
            Transition(
                item = item.copy(status = ItemStatus.GIVEN),
                changedClaims = listOf(claim.copy(status = ClaimStatus.COMPLETED)) + closed,
            )
        )
    }

    /** Giver re-opens an item after a late cancellation. */
    fun reopen(actor: UserId, item: Item): DomainResult<Transition> {
        if (item.giverId != actor) return err(DomainError.NOT_ITEM_OWNER)
        if (item.status != ItemStatus.AWAITING_GIVER_DECISION) return err(DomainError.INVALID_TRANSITION)
        return ok(Transition(item.copy(status = ItemStatus.AVAILABLE), emptyList(), slotReleasedToQueue = true))
    }

    /** Giver removes the post. All active claims are closed. */
    fun withdraw(actor: UserId, item: Item, allClaimsForItem: List<Claim>): DomainResult<Transition> {
        if (item.giverId != actor) return err(DomainError.NOT_ITEM_OWNER)
        if (item.status.isTerminal) return err(DomainError.INVALID_TRANSITION)
        val closed = allClaimsForItem.filter { it.status.isActive }.map { it.copy(status = ClaimStatus.CLOSED) }
        return ok(Transition(item.copy(status = ItemStatus.WITHDRAWN), closed))
    }

    private fun <T> ok(v: T): DomainResult<T> = DomainResult.Ok(v)
    private fun err(e: DomainError): DomainResult<Nothing> = DomainResult.Err(e)
}
