// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
// Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.domain.catalog

import app.freetotake.domain.model.Claim
import app.freetotake.domain.model.ClaimStatus
import app.freetotake.domain.model.Item
import app.freetotake.domain.model.ItemStatus
import app.freetotake.domain.model.Timestamp
import app.freetotake.domain.model.UserId

/** Empty-state copy (spec 0.6; Available Giveaways wording taken from the approved design). */
object HomeCopy {
    /** v1.13: one short empty-state line everywhere (with the hero mascot). */
    const val EMPTY = "Nothing here yet."
    const val MY_GIVEAWAYS_EMPTY = ""   // v1.13: Start card is mascot + Start only
    const val MY_CLAIMS_EMPTY = "Nothing here yet."
    /** From Figma "Plaeholder page if nothing found around you…" — not invented. */
    const val AVAILABLE_EMPTY = EMPTY
    const val RESERVED_BADGE = "Reserved"
}

/** "My Giveaways": the user's own publications that are still live. */
object MyGiveaways {
    fun of(user: UserId?, items: List<Item>): List<Item> =
        if (user == null) emptyList()
        else items.filter { it.giverId == user && !it.status.isTerminal }
}

/**
 * "My Claims": the user's requests for items published by OTHER users.
 * Shows open requests (pending / approved) and rejected ones (greyed, removable) until the user removes them (0.9).
 */
object MyClaims {
    data class Entry(val claim: Claim, val item: Item)

    fun of(user: UserId?, claims: List<Claim>, items: List<Item>): List<Entry> {
        if (user == null) return emptyList()
        val byId = items.associateBy { it.id }
        return claims.asSequence()
            .filter { it.takerId == user && !it.hiddenByTaker && (it.status.isActive || it.status == ClaimStatus.REJECTED || it.status == ClaimStatus.COMPLETED) }
            .mapNotNull { c -> byId[c.itemId]?.takeIf { it.giverId != user }?.let { Entry(c, it) } }
            .sortedByDescending { it.claim.createdAt.epochMillis }
            .toList()
    }

    /** v1.14: while the user has no claims, the food example shows as a pending request they can cancel (teaches the flow). */
    fun withExample(entries: List<Entry>, dismissed: Boolean, example: Claim): List<Entry> =
        if (entries.isEmpty() && !dismissed) listOf(Entry(example, ExampleListing.item)) else entries
}

/** Full catalog (opened from chevron / View More): same scope rules as the carousel, no 5-card cap. */
object FullCatalog {
    const val ALL_CATEGORIES = "All"

    fun items(feed: List<Item>, scope: FeedScope, now: Timestamp, category: String? = null): List<Item> =
        FeedPolicy.apply(feed, scope, now)
            .filter { category == null || category == ALL_CATEGORIES || it.category.equals(category, ignoreCase = true) }

    /** Category chips = "All" + categories present in the current (scoped) feed; nothing invented. */
    fun categories(feed: List<Item>, scope: FeedScope, now: Timestamp): List<String> =
        listOf(ALL_CATEGORIES) + FeedPolicy.apply(feed, scope, now).mapNotNull { it.category?.trim()?.takeIf(String::isNotEmpty) }.distinctBy { it.lowercase() }

    /** Reserved = recipient selected. Superseded by 0.10: such listings are no longer in the public feed. */
    fun isReserved(item: Item): Boolean = item.status == ItemStatus.RESERVED
}
