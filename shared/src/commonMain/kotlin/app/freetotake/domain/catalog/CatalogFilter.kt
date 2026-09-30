// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
// Copyright (C) 2026 Dina Elokhova (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.domain.catalog

import app.freetotake.domain.model.Claim
import app.freetotake.domain.model.Item
import app.freetotake.domain.model.ItemId
import app.freetotake.domain.model.UserId
import app.freetotake.domain.rules.UserAction

enum class CatalogChip(val requiredAction: UserAction) {
    ALL(UserAction.BROWSE_FEED),
    /** Listings the current user has sent a request for. */
    MY_CLAIMS(UserAction.VIEW_MY_CLAIMS),
    FAVORITES(UserAction.FAVORITES),
}

/**
 * Pure catalog filtering. Claims and favorites are independent sets:
 * sending a request never adds a listing to Favorites.
 */
object CatalogFilter {

    fun myClaimedItemIds(userId: UserId, claims: List<Claim>): Set<ItemId> =
        claims.asSequence().filter { it.takerId == userId }.map { it.itemId }.toSet()

    fun apply(
        items: List<Item>,
        chip: CatalogChip,
        myClaimedItemIds: Set<ItemId>,
        favoriteItemIds: Set<ItemId>,
    ): List<Item> = when (chip) {
        CatalogChip.ALL -> items
        CatalogChip.MY_CLAIMS -> items.filter { it.id in myClaimedItemIds }
        CatalogChip.FAVORITES -> items.filter { it.id in favoriteItemIds }
    }
}
