// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.freetotake.domain.catalog.CatalogSession
import app.freetotake.domain.catalog.CatalogSorter
import app.freetotake.domain.catalog.Category
import app.freetotake.domain.catalog.CategoryChip
import app.freetotake.domain.catalog.HomeCopy
import app.freetotake.domain.catalog.CardStates
import app.freetotake.domain.catalog.MyClaims
import app.freetotake.domain.model.Claim
import app.freetotake.domain.model.Timestamp
import app.freetotake.domain.catalog.SortOption
import app.freetotake.domain.location.GeoPoint
import app.freetotake.domain.location.HomeLocation
import app.freetotake.domain.model.Item
import app.freetotake.resources.Res
import app.freetotake.resources.ic_cat_books_fill
import app.freetotake.resources.ic_cat_books_stroke
import app.freetotake.resources.ic_cat_clothes_fill
import app.freetotake.resources.ic_cat_clothes_stroke
import app.freetotake.resources.ic_cat_random_fill
import app.freetotake.resources.ic_cat_random_stroke
import app.freetotake.resources.ic_cat_food_fill
import app.freetotake.resources.ic_cat_food_stroke
import app.freetotake.resources.ic_cat_furniture_fill
import app.freetotake.resources.ic_cat_furniture_stroke
import app.freetotake.resources.ic_cat_kids_toys_fill
import app.freetotake.resources.ic_cat_kids_toys_stroke
import androidx.compose.ui.graphics.ColorFilter
import app.freetotake.resources.ic_sort
import app.freetotake.ui.platform.PlatformBackHandler
import app.freetotake.ui.theme.BackChevron
import app.freetotake.ui.theme.FttColors
import app.freetotake.ui.theme.FttType
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * Full catalog (spec 0.7, Figma "Giveaways catalogue" + "Filter"): location row, sort button,
 * fixed category chips, 2-column grid, sorting bottom sheet. [items] are already city-scoped.
 * Sort + chip live only for this screen session.
 */
@Composable
fun CatalogScreen(
    items: List<Item>,
    reference: GeoPoint?,
    location: HomeLocation,
    onEditLocation: () -> Unit,
    onBack: () -> Unit,
    onOpen: (Item) -> Unit,
    onTab: (HomeTab) -> Unit,
    favorites: Set<String> = emptySet(),
    claimFor: (Item) -> Claim? = { null },
    now: Timestamp = Timestamp(0),
    onHeart: (Item) -> Unit = {},
    onRemove: (MyClaims.Entry) -> Unit = {},
) {
    var session by remember { mutableStateOf(CatalogSession()) }
    val visible = CatalogSorter.sort(CatalogSorter.filter(items, session.chip), session.sort, reference)

    PlatformBackHandler(enabled = session.sheetOpen) { session = session.cancel() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(FttColors.BackgroundPrimary)) {
            Column(Modifier.weight(1f).statusBarsPadding()) {
                Row(Modifier.padding(start = 7.dp, end = 16.dp, top = 8.dp)) {
                    Box(Modifier.padding(top = 11.dp).clickable(onClick = onBack)) { BackChevron() }
                    LocationField(location, onEditLocation, Modifier.padding(start = 4.dp))
                }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    item {
                        Box(
                            Modifier.size(36.dp).background(FttColors.BackgroundSecondary, RoundedCornerShape(8.dp))
                                .clickable(role = Role.Button, onClickLabel = "Sorting") { session = session.openSheet() },
                            contentAlignment = Alignment.Center,
                        ) { Image(painterResource(Res.drawable.ic_sort), "Sorting", Modifier.size(18.dp)) }
                    }
                    items(CategoryChip.ORDER) { chip -> Chip(chip, chip == session.chip) { session = session.selectChip(chip) } }
                }
                if (visible.isEmpty()) {
                    app.freetotake.ui.theme.EmptyState()
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(visible) { item ->
                            // Public catalog (0.10): "Available" + heart; own request → its My Claims state.
                            val mine = claimFor(item)
                            ListingCard(
                                item, Modifier.fillMaxWidth(), state = CardStates.available(item),
                                favorite = item.id.value in favorites,
                                onHeart = { onHeart(item) },
                                onTrash = { mine?.let { onRemove(MyClaims.Entry(it, item)) } },
                            ) { onOpen(item) }
                        }
                    }
                }
            }
            // v1.13: no tab bar in catalogs — back returns Home.
            Spacer(Modifier.navigationBarsPadding())
        }
        if (session.sheetOpen) SortSheet(
            pending = session.pendingSort,
            onPick = { session = session.pick(it) },
            onCancel = { session = session.cancel() },
            onApply = { session = session.apply() },
        )
    }
}

/** Two-layer chip icons (from .figma-assets/Icons filter chips): black stroke + tintable fill. */
private fun iconFor(chip: CategoryChip): Pair<DrawableResource, DrawableResource>? = when ((chip as? CategoryChip.Of)?.category) {
    Category.KIDS_TOYS -> Res.drawable.ic_cat_kids_toys_stroke to Res.drawable.ic_cat_kids_toys_fill
    Category.FURNITURE -> Res.drawable.ic_cat_furniture_stroke to Res.drawable.ic_cat_furniture_fill
    Category.FOOD -> Res.drawable.ic_cat_food_stroke to Res.drawable.ic_cat_food_fill
    Category.BOOKS -> Res.drawable.ic_cat_books_stroke to Res.drawable.ic_cat_books_fill
    Category.CLOTHES -> Res.drawable.ic_cat_clothes_stroke to Res.drawable.ic_cat_clothes_fill
    Category.RANDOM -> Res.drawable.ic_cat_random_stroke to Res.drawable.ic_cat_random_fill
    null -> null // "All" has no icon
}

/**
 * Chip states (product rule): selected = lime chip, icon black stroke + WHITE fill;
 * not selected = white chip, icon black stroke + LIME fill.
 */
@Composable
fun Chip(chip: CategoryChip, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.height(36.dp)
            .background(if (selected) FttColors.StartLime else FttColors.BackgroundSecondary, RoundedCornerShape(8.dp))
            .clickable(role = Role.Tab, onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        iconFor(chip)?.let { (stroke, fill) ->
            Box(Modifier.size(20.dp)) {
                Image(
                    painterResource(fill), null, Modifier.fillMaxSize(),
                    colorFilter = ColorFilter.tint(if (selected) Color.White else FttColors.StartLime),
                )
                Image(painterResource(stroke), null, Modifier.fillMaxSize())
            }
        }
        Text(chip.label, style = FttType.subheadline())
    }
}

/** Figma "Filter" sheet — MVP shows the Sorting part only (no filters). */
@Composable
private fun SortSheet(pending: SortOption?, onPick: (SortOption) -> Unit, onCancel: () -> Unit, onApply: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)).clickable(onClick = onCancel)) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(FttColors.BackgroundPrimary, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .clickable(enabled = false) {}.navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(width = 36.dp, height = 5.dp).background(Color(0x4D3C3C43), RoundedCornerShape(3.dp)))
            Text("Sorting", style = FttType.title1Bold(), modifier = Modifier.align(Alignment.CenterHorizontally))
            SortOption.SHEET_OPTIONS.forEach { o ->
                Row(
                    Modifier.fillMaxWidth().height(48.dp).background(FttColors.BackgroundSecondary, RoundedCornerShape(10.dp))
                        .clickable(role = Role.RadioButton) { onPick(o) }.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(o.label, style = FttType.body(), modifier = Modifier.weight(1f))
                    Box(
                        Modifier.size(20.dp).border(1.dp, Color(0x4D3C3C43), CircleShape)
                            .background(if (pending == o) FttColors.StartLime else Color.Transparent, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { if (pending == o) Box(Modifier.size(8.dp).background(FttColors.Ink, CircleShape)) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = onCancel, modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, FttColors.Ink),
                    colors = ButtonDefaults.buttonColors(containerColor = FttColors.BackgroundPrimary, contentColor = FttColors.Ink),
                ) { Text("Cancel", style = FttType.body()) }
                Button(
                    onClick = onApply, modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, FttColors.OnLime),
                    colors = ButtonDefaults.buttonColors(containerColor = FttColors.StartLime, contentColor = FttColors.OnLime),
                ) { Text("Apply", style = FttType.body()) }
            }
        }
    }
}
