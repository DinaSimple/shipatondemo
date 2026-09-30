// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.domain.catalog

import app.freetotake.domain.location.AreaLabel
import app.freetotake.domain.location.GeoPoint
import app.freetotake.domain.model.Item
import app.freetotake.domain.model.ItemId
import app.freetotake.domain.model.ItemStatus
import app.freetotake.domain.model.Timestamp
import app.freetotake.domain.model.UserId
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Fixed category list (spec 0.7). The creator picks exactly one when publishing. */
enum class Category(val key: String, val label: String) {
    KIDS_TOYS("kids_toys", "Kids Toys"),
    FURNITURE("furniture", "Furniture"),
    FOOD("food", "Food"),
    BOOKS("books", "Books"),
    CLOTHES("clothes", "Clothes"),
    RANDOM("random", "Random");

    companion object {
        /** Accepts the stored key or the label, case-insensitive. */
        fun from(value: String?): Category? = value?.trim()?.let { v ->
            entries.firstOrNull { it.key.equals(v, true) || it.label.equals(v, true) }
        }
    }
}

/** Chip row: "All" (default, every category) + the fixed categories. */
sealed interface CategoryChip {
    val label: String
    data object All : CategoryChip { override val label = "All" }
    data class Of(val category: Category) : CategoryChip { override val label = category.label }

    companion object {
        val DEFAULT: CategoryChip = All
        val ORDER: List<CategoryChip> = listOf(All) + Category.entries.map { Of(it) }
    }
}

enum class SortOption(val label: String) {
    /** Default catalog ordering (not shown in the sheet). */
    RECENT_FIRST("Recent First"),
    CLOSEST_NEARBY_FIRST("Closest nearby first"),
    RECENTLY_PUBLISHED_AVAILABLE_FIRST("Recently published available first");

    companion object {
        val DEFAULT = RECENT_FIRST
        /** Options offered in the sorting bottom sheet (MVP: sorting only, no filters). */
        val SHEET_OPTIONS = listOf(CLOSEST_NEARBY_FIRST, RECENTLY_PUBLISHED_AVAILABLE_FIRST)
    }
}

/**
 * Catalog screen-session state: category + applied sort + sheet (pending) selection.
 * Lives as long as the catalog screen; nothing persisted beyond it (not specified).
 */
data class CatalogSession(
    val chip: CategoryChip = CategoryChip.DEFAULT,
    val sort: SortOption = SortOption.DEFAULT,
    /** Non-null while the sorting sheet is open. */
    val pendingSort: SortOption? = null,
) {
    fun selectChip(c: CategoryChip) = copy(chip = c)
    fun openSheet() = copy(pendingSort = sort)
    fun pick(option: SortOption) = if (pendingSort == null) this else copy(pendingSort = option)
    fun apply() = copy(sort = pendingSort ?: sort, pendingSort = null)
    /**
     * Cancel (1.5.1, answered by product): discard only the pending choice; settings applied earlier
     * in this screen session stay. With nothing applied the catalog stays on the default Recent First
     * (service-wide without a location, the user's city when a location is known — see FeedScope).
     */
    fun cancel() = copy(pendingSort = null)
    val sheetOpen: Boolean get() = pendingSort != null
}

object CatalogSorter {

    /**
     * @param reference user's pickup point or device location, for "closest".
     * Closest without any reference point (answered): recently published first from any location in the service.
     * Items without a public point go last.
     */
    fun sort(items: List<Item>, option: SortOption, reference: GeoPoint?): List<Item> = when (option) {
        SortOption.RECENT_FIRST -> items.sortedByDescending { it.createdAt.epochMillis }
        SortOption.RECENTLY_PUBLISHED_AVAILABLE_FIRST -> items.sortedWith(
            compareBy<Item> { if (it.status == ItemStatus.AVAILABLE) 0 else 1 }.thenByDescending { it.createdAt.epochMillis }
        )
        SortOption.CLOSEST_NEARBY_FIRST ->
            if (reference == null) sort(items, SortOption.RECENT_FIRST, null)
            else items.sortedWith(
                compareBy<Item> { it.approxPoint?.let { p -> distanceKm(reference, p) } ?: Double.MAX_VALUE }
                    .thenByDescending { it.createdAt.epochMillis }
            )
    }

    fun distanceKm(a: GeoPoint, b: GeoPoint): Double {
        val r = 6371.0
        fun rad(d: Double) = d * kotlin.math.PI / 180
        val dLat = rad(b.lat - a.lat); val dLng = rad(b.lng - a.lng)
        val h = sin(dLat / 2).pow(2) + cos(rad(a.lat)) * cos(rad(b.lat)) * sin(dLng / 2).pow(2)
        return 2 * r * asin(sqrt(h))
    }

    fun filter(items: List<Item>, chip: CategoryChip): List<Item> = when (chip) {
        CategoryChip.All -> items
        is CategoryChip.Of -> items.filter { Category.from(it.category) == chip.category }
    }
}

/**
 * One educational "Free to Take" example card shown while the service has no real
 * publications; it disappears as soon as the first real publication exists.
 */
object ExampleListing {
    const val ID = "example-free-to-take"
    val item = Item(
        id = ItemId(ID), giverId = UserId("free-to-take"),
        title = TITLE,
        description = DESCRIPTION,
        category = Category.FOOD.key,       // 1.8.4: food example with the design photo
        createdAt = Timestamp(0),
        area = AreaLabel(null, null),
    )

    const val TITLE = "Free food"   // v1.13: the "Example" badge is the only marker
    const val BANNER = "This is only an example"
    const val DESCRIPTION = "This is only an example of a giveaway. Here you can share your surplus food with your neighbours. " +
        "Make sure the food you give away is safe to eat and has not passed its expiry date."

    fun isExample(item: Item) = item.id.value == ID

    /**
     * @param serviceHasRealPublications whether ANY real active publication exists service-wide
     *        (not just in the user's city).
     * Example only while the service is empty; removed for good once real publications exist.
     */
    fun apply(scopedRealFeed: List<Item>, serviceHasRealPublications: Boolean): List<Item> =
        if (serviceHasRealPublications) scopedRealFeed.filterNot(::isExample) else listOf(item)
}
