// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Dina Elokhova (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.domain.catalog

import app.freetotake.domain.location.AreaLabel
import app.freetotake.domain.location.LocationAccess
import app.freetotake.domain.location.PickupPoint
import app.freetotake.domain.location.PlaceSearchPolicy
import app.freetotake.domain.model.Item
import app.freetotake.domain.model.ItemStatus
import app.freetotake.domain.model.Timestamp

/** Which listings the Home carousel / catalog shows (spec 0.5). */
sealed interface FeedScope {
    /** No location: all active listings. */
    data object All : FeedScope
    /** Location selected: listings in that city (boundary/distance rules TBD — Q8). */
    data class City(val city: String) : FeedScope
}

object FeedPolicy {

    /** Chosen pickup point's city wins, then device area city; otherwise everything. */
    fun scope(chosen: PickupPoint?, access: LocationAccess, deviceArea: AreaLabel?): FeedScope {
        val city = chosen?.area?.city?.takeIf { it.isNotBlank() }
            ?: deviceArea?.city?.takeIf { access.hasDeviceLocation && it.isNotBlank() }
        return if (city != null) FeedScope.City(city) else FeedScope.All
    }

    /**
     * Public = available (0.10: listings with a selected recipient are hidden), not withdrawn/given,
     * not expired (service timeout rule).
     */
    fun isActive(item: Item, now: Timestamp): Boolean =
        item.status == ItemStatus.AVAILABLE &&
            (item.expiresAt == null || now < item.expiresAt)

    fun apply(items: List<Item>, scope: FeedScope, now: Timestamp): List<Item> =
        items.filter { isActive(it, now) }
            .filter { scope !is FeedScope.City || sameCity(it.area?.city, scope.city) }
            .sortedWith(compareBy<Item> { if (it.status == ItemStatus.AVAILABLE) 0 else 1 }.thenByDescending { it.createdAt.epochMillis })

    private fun sameCity(a: String?, b: String): Boolean =
        a != null && PlaceSearchPolicy.fold(a.trim()) == PlaceSearchPolicy.fold(b.trim())
}

sealed interface CarouselCard {
    data class Listing(val item: Item) : CarouselCard
    /** Final card, opens the full catalog. */
    data object ViewMore : CarouselCard
}

/** "Available Giveaways": horizontal carousel, up to 5 cards in total, the last one "View More". */
object HomeCarousel {
    const val MAX_CARDS = 5
    const val VIEW_MORE_LABEL = "View More"

    fun build(feed: List<Item>): List<CarouselCard> =
        if (feed.isEmpty()) emptyList()
        else feed.take(MAX_CARDS - 1).map { CarouselCard.Listing(it) } + CarouselCard.ViewMore
}
