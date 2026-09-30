// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.domain.catalog

import app.freetotake.domain.model.Claim
import app.freetotake.domain.model.ClaimStatus
import app.freetotake.domain.model.Item
import app.freetotake.domain.model.Timestamp

/** Status chips drawn on a card photo. */
enum class CardBadge(val label: String) {
    AVAILABLE("Available"),
    /** Educational example card (1.8.4). */
    EXAMPLE("Example"),
    PENDING("Pending for approval"),
    APPROVED("Approved"),
    /** Rejected, or the publisher selected someone else while this request was pending (answer Q18). */
    REJECTED("Rejected for you"),
    /** v1.15.4 demo clothes card in My giveaways (lime). */
    PENDING_MY_APPROVAL("Pending my approval"),
    /** v1.14: collected (meeting time passed / marked given). */
    FINISHED("Finished"),
}

/** Everything a card shows besides title/description (spec 0.9 + authoritative 0.10). */
data class CardState(
    val badges: List<CardBadge>,
    val showHeart: Boolean,
    val showTrash: Boolean,
    val greyed: Boolean,
)

object CardStates {
    /**
     * Ordinary public catalog / "Giveaways nearby": the user has not requested it →
     * "Available" + heart, never a trash can. A listing the user already requested shows its My Claims state.
     */
    fun public(item: Item, myClaim: Claim?, now: Timestamp): CardState =
        if (ExampleListing.isExample(item) && myClaim == null) CardState(listOf(CardBadge.EXAMPLE), showHeart = false, showTrash = false, greyed = false)
        else myClaim?.takeIf { !it.hiddenByTaker && (it.status.isActive || it.status == ClaimStatus.REJECTED) }?.let { myClaims(item, it, now) }
            ?: CardState(listOf(CardBadge.AVAILABLE), showHeart = item.status.acceptsClaims, showTrash = false, greyed = false)

    /**
     * My Claims: trash on the card; pending + still open → also "Available" + heart.
     * Pending but the publisher selected someone else → "Rejected for you" (greyed, removable).
     * Approved → trash cancels until the pickup time; after it, no trash (card goes to the claims archive — TODO).
     */
    fun myClaims(item: Item, claim: Claim, now: Timestamp): CardState = when (claim.status) {
        ClaimStatus.PENDING_APPROVAL ->
            if (item.status.acceptsClaims) CardState(listOf(CardBadge.PENDING, CardBadge.AVAILABLE), showHeart = true, showTrash = true, greyed = false)
            else rejected
        ClaimStatus.APPROVED -> CardState(listOf(CardBadge.APPROVED), showHeart = false, showTrash = !pickupPassed(claim, now), greyed = false)
        ClaimStatus.REJECTED -> rejected
        ClaimStatus.COMPLETED -> CardState(listOf(CardBadge.FINISHED), showHeart = false, showTrash = false, greyed = false)
        else -> CardState(emptyList(), showHeart = false, showTrash = false, greyed = true)
    }

    /**
     * v1.15.4 — one control per section (product decision):
     *  Available Giveaways / catalog: heart only, one status ("Example" or "Available"); your request lives in My claims.
     */
    fun available(item: Item): CardState =
        CardState(listOf(CardBadge.AVAILABLE), showHeart = ExampleListing.isExample(item) || item.status.acceptsClaims, showTrash = false, greyed = false)

    /**
     * My claims: trash only (never a heart), one tag. v1.16.1: a pending request shows "Available" (lime) — the item is
     * still open; approved / rejected / finished keep their own single tag. Filters still use [myClaims].
     */
    fun myClaimCard(item: Item, claim: Claim, now: Timestamp): CardState {
        val s = myClaims(item, claim, now)
        val tag = when {
            CardBadge.PENDING in s.badges -> CardBadge.AVAILABLE
            else -> s.badges.firstOrNull { it != CardBadge.AVAILABLE }
        }
        return s.copy(badges = listOfNotNull(tag), showHeart = false)
    }

    /** My giveaways (publisher): trash only. */
    fun myGiveaway(example: Boolean): CardState =
        CardState(if (example) listOf(CardBadge.PENDING_MY_APPROVAL) else emptyList(), showHeart = false, showTrash = true, greyed = false)

    fun pickupPassed(claim: Claim, now: Timestamp): Boolean = claim.pickupAt?.let { now >= it } ?: false

    private val rejected = CardState(listOf(CardBadge.REJECTED), showHeart = false, showTrash = true, greyed = true)
}

/** "My claims" screen filter chips (Figma "My claims screen"); matched on what the card shows. */
enum class ClaimFilter(val label: String) {
    ALL("All"), PENDING("Pending"), APPROVED("Approved"), REJECTED("Rejected"), FINISHED("Finished");

    fun matches(state: CardState): Boolean = when (this) {
        ALL -> true
        PENDING -> CardBadge.PENDING in state.badges
        APPROVED -> CardBadge.APPROVED in state.badges
        REJECTED -> CardBadge.REJECTED in state.badges
        FINISHED -> CardBadge.FINISHED in state.badges
    }
}

/** Figma "Sorting for my claims". */
enum class ClaimSort(val label: String) {
    RECENTLY_REQUESTED_FIRST("Recently requested first"),
    OLDEST_REQUESTED_FIRST("Oldest requested first");

    companion object { val DEFAULT = RECENTLY_REQUESTED_FIRST }
}

/** Screen-session state (same Apply/Cancel semantics as the catalog sort sheet). */
data class MyClaimsSession(
    val filter: ClaimFilter = ClaimFilter.ALL,
    val sort: ClaimSort = ClaimSort.DEFAULT,
    val pendingSort: ClaimSort? = null,
) {
    fun select(f: ClaimFilter) = copy(filter = f)
    fun openSheet() = copy(pendingSort = sort)
    fun pick(o: ClaimSort) = if (pendingSort == null) this else copy(pendingSort = o)
    fun apply() = copy(sort = pendingSort ?: sort, pendingSort = null)
    fun cancel() = copy(pendingSort = null)
    val sheetOpen: Boolean get() = pendingSort != null

    fun visible(entries: List<MyClaims.Entry>, now: Timestamp): List<MyClaims.Entry> =
        entries.filter { filter.matches(CardStates.myClaims(it.item, it.claim, now)) }
            .let { l -> if (sort == ClaimSort.OLDEST_REQUESTED_FIRST) l.sortedBy { it.claim.createdAt.epochMillis } else l.sortedByDescending { it.claim.createdAt.epochMillis } }
}

object CancelCopy {
    const val QUESTION = "Are you sure you want to cancel your request?"
    const val YES = "Yes"
    const val CLOSE = "Close"
}

/** Favorites (heart). Signed-in only; independent of requests (R-1.1-31). */
object Favorites {
    fun toggle(current: Set<String>, itemId: String): Set<String> =
        if (itemId in current) current - itemId else current + itemId
}
