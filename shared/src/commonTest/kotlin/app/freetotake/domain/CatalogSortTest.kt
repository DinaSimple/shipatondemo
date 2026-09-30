// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.domain

import app.freetotake.domain.F.NOW
import app.freetotake.domain.catalog.CarouselCard
import app.freetotake.domain.catalog.CatalogSession
import app.freetotake.domain.catalog.CatalogSorter
import app.freetotake.domain.catalog.Category
import app.freetotake.domain.catalog.CategoryChip
import app.freetotake.domain.catalog.ExampleListing
import app.freetotake.domain.catalog.HomeCarousel
import app.freetotake.domain.catalog.SortOption
import app.freetotake.domain.location.GeoPoint
import app.freetotake.domain.model.ItemId
import app.freetotake.domain.model.ItemStatus
import app.freetotake.domain.model.Timestamp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CatalogSortTest {

    private fun item(id: String, created: Long, status: ItemStatus = ItemStatus.AVAILABLE, cat: Category? = null, at: GeoPoint? = null) =
        F.item(status).copy(id = ItemId(id), createdAt = Timestamp(NOW.epochMillis - created), category = cat?.key, approxPoint = at)

    @Test fun r_1_5_01_fixed_category_chips_all_default() {
        assertEquals(listOf("All", "Kids Toys", "Furniture", "Food", "Books", "Clothes", "Random"), CategoryChip.ORDER.map { it.label })
        assertEquals(CategoryChip.All, CategoryChip.DEFAULT)
        assertEquals(CategoryChip.All, CatalogSession().chip)
    }

    @Test fun r_1_5_02_all_includes_every_category_chip_filters_one() {
        val items = listOf(item("t", 1, cat = Category.KIDS_TOYS), item("f", 2, cat = Category.FURNITURE), item("n", 3))
        assertEquals(3, CatalogSorter.filter(items, CategoryChip.All).size)
        assertEquals(listOf("f"), CatalogSorter.filter(items, CategoryChip.Of(Category.FURNITURE)).map { it.id.value })
        assertEquals(Category.KIDS_TOYS, Category.from("Kids Toys"))
        assertEquals(Category.KIDS_TOYS, Category.from("kids_toys"))
    }

    @Test fun r_1_5_03_default_ordering_recent_first() {
        assertEquals(SortOption.RECENT_FIRST, SortOption.DEFAULT)
        assertEquals(SortOption.RECENT_FIRST, CatalogSession().sort)
        val items = listOf(item("old", 50), item("res", 1, ItemStatus.RESERVED), item("new", 5))
        assertEquals(listOf("res", "new", "old"), CatalogSorter.sort(items, SortOption.RECENT_FIRST, null).map { it.id.value })
    }

    @Test fun r_1_5_04_sheet_offers_exactly_two_sort_options_no_filters() {
        assertEquals(listOf(SortOption.CLOSEST_NEARBY_FIRST, SortOption.RECENTLY_PUBLISHED_AVAILABLE_FIRST), SortOption.SHEET_OPTIONS)
    }

    @Test fun r_1_5_05_recently_published_available_first() {
        val items = listOf(item("old", 50), item("res", 1, ItemStatus.RESERVED), item("new", 5))
        assertEquals(listOf("new", "old", "res"), CatalogSorter.sort(items, SortOption.RECENTLY_PUBLISHED_AVAILABLE_FIRST, null).map { it.id.value })
    }

    @Test fun r_1_5_06_closest_nearby_first_by_distance() {
        val me = GeoPoint(41.40, 2.20)                    // Poblenou
        val items = listOf(
            item("far", 1, at = GeoPoint(41.39, 2.11)),   // ~7 km
            item("near", 9, at = GeoPoint(41.40, 2.19)),  // ~1 km
            item("unknown", 0),
        )
        assertEquals(listOf("near", "far", "unknown"), CatalogSorter.sort(items, SortOption.CLOSEST_NEARBY_FIRST, me).map { it.id.value })
        assertTrue(CatalogSorter.distanceKm(me, GeoPoint(41.39, 2.11)) in 6.0..9.0)
    }

    @Test fun r_1_5_07_closest_without_location_is_recent_first_service_wide() {
        val items = listOf(item("old", 50, at = GeoPoint(0.0, 0.0)), item("new", 1))
        assertEquals(listOf("new", "old"), CatalogSorter.sort(items, SortOption.CLOSEST_NEARBY_FIRST, null).map { it.id.value })
    }

    @Test fun r_1_5_08_apply_commits_cancel_discards_pending_only() {
        var s = CatalogSession().selectChip(CategoryChip.Of(Category.FOOD)).openSheet()
        assertTrue(s.sheetOpen)
        s = s.pick(SortOption.CLOSEST_NEARBY_FIRST)
        assertEquals(SortOption.RECENT_FIRST, s.sort, "not applied until Apply")
        s = s.apply()
        assertEquals(SortOption.CLOSEST_NEARBY_FIRST, s.sort); assertFalse(s.sheetOpen)
        assertEquals(CategoryChip.Of(Category.FOOD), s.chip, "switching between chips and sorting keeps both")
        s = s.openSheet().pick(SortOption.RECENTLY_PUBLISHED_AVAILABLE_FIRST).cancel()
        // 1.5.1: Cancel keeps what was applied earlier in the session (supersedes 1.5 "reset all").
        assertEquals(SortOption.CLOSEST_NEARBY_FIRST, s.sort)
        assertEquals(CategoryChip.Of(Category.FOOD), s.chip)
        assertFalse(s.sheetOpen)
        assertEquals(CatalogSession(), CatalogSession().openSheet().pick(SortOption.CLOSEST_NEARBY_FIRST).cancel(), "nothing applied → default Recent First")
    }

    @Test fun r_1_5_09_sort_retained_while_switching_chips() {
        val s = CatalogSession().openSheet().pick(SortOption.RECENTLY_PUBLISHED_AVAILABLE_FIRST).apply()
            .selectChip(CategoryChip.Of(Category.BOOKS)).selectChip(CategoryChip.All)
        assertEquals(SortOption.RECENTLY_PUBLISHED_AVAILABLE_FIRST, s.sort)
    }

    @Test fun r_1_5_10_example_card_only_until_first_real_publication() {
        val shown = ExampleListing.apply(emptyList(), serviceHasRealPublications = false)
        assertEquals(listOf(ExampleListing.item), shown)
        assertEquals("Free food", shown.single().title)   // 1.8.4 food example
        assertEquals(listOf(CarouselCard.Listing(ExampleListing.item), CarouselCard.ViewMore), HomeCarousel.build(shown))
        val real = listOf(item("r", 1))
        assertEquals(real, ExampleListing.apply(real + ExampleListing.item, serviceHasRealPublications = true), "dummy removed")
        assertEquals(emptyList(), ExampleListing.apply(emptyList(), serviceHasRealPublications = true), "real elsewhere → empty state, no dummy")
    }

    @Test fun r_1_8_4_01_example_is_food_labelled_as_example_with_safety_note() {
        val e = app.freetotake.domain.catalog.ExampleListing.item
        assertEquals("food", e.category)
        assertEquals("Free food", e.title)   // v1.13: badge only
        assertTrue(e.description.startsWith("This is only an example"))
        assertTrue(e.description.contains("safe to eat") && e.description.contains("expiry date"))
        val st = app.freetotake.domain.catalog.CardStates.public(e, null, app.freetotake.domain.model.Timestamp(0))
        assertEquals(listOf(app.freetotake.domain.catalog.CardBadge.EXAMPLE), st.badges)
    }
}
