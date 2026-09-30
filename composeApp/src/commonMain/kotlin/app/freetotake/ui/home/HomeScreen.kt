// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
// Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import app.freetotake.domain.catalog.CarouselCard
import app.freetotake.domain.catalog.HomeCarousel
import app.freetotake.domain.catalog.HomeCopy
import app.freetotake.domain.catalog.FullCatalog
import app.freetotake.domain.location.HomeLocation
import app.freetotake.domain.model.Item
import app.freetotake.resources.logo
import app.freetotake.domain.catalog.ExampleListing
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import app.freetotake.resources.Res
import app.freetotake.resources.empty_mascot
import app.freetotake.resources.example_food
import app.freetotake.resources.example_clothes
import app.freetotake.resources.ic_edit
import app.freetotake.resources.ic_tab_profile
import app.freetotake.resources.ic_tab_publications
import app.freetotake.ui.theme.FttColors
import app.freetotake.ui.theme.FttType
import org.jetbrains.compose.resources.DrawableResource
import androidx.compose.ui.draw.alpha
import app.freetotake.domain.catalog.MyClaims
import app.freetotake.domain.catalog.CardBadge
import app.freetotake.domain.catalog.CardState
import app.freetotake.domain.catalog.CardStates
import app.freetotake.domain.model.Claim
import app.freetotake.domain.model.Timestamp
import app.freetotake.ui.theme.HeartButton
import app.freetotake.ui.platform.RemotePhoto
import app.freetotake.ui.theme.TrashButton
import app.freetotake.domain.request.MyClaimsCopy
import org.jetbrains.compose.resources.painterResource

enum class HomeTab { HOME, MY_PUBLICATIONS, PROFILE }

/** Figma card size; every Home section (cards or placeholder) has this same height. */
val CARD_WIDTH = 170.dp
val CARD_HEIGHT = 238.dp
private val SIDE = 16.dp

/** Home (spec 0.4 + Figma "Plaeholder page…"): location row, My giveaways, Available Giveaways, My claims, tab bar. */
@Composable
fun HomeScreen(
    location: HomeLocation,
    message: String?,
    onEditLocation: () -> Unit,
    onStartGiveaway: () -> Unit,
    myGiveaways: List<Item>,
    carousel: List<CarouselCard>,
    myClaims: List<MyClaims.Entry>,
    onOpenAvailable: () -> Unit,
    onOpenListing: (Item) -> Unit,
    onOpenMyClaims: () -> Unit,
    onOpenPickup: (MyClaims.Entry) -> Unit,
    /** Favorites (item ids) and the user's own request per listing, for card states (0.9/0.10). */
    favorites: Set<String> = emptySet(),
    claimFor: (Item) -> Claim? = { null },
    now: Timestamp = Timestamp(0),
    onHeart: (Item) -> Unit = {},
    onRemove: (MyClaims.Entry) -> Unit = {},
    onTab: (HomeTab) -> Unit,
    onOpenExampleGiveaway: () -> Unit = {},
    /** v1.15.4: trash on My giveaways cards (null item = the example). */
    onDeleteGiveaway: (Item?) -> Unit = {},
    showExampleGiveaway: Boolean = true,
    /** v1.17 broccoli balance (top-right pill + panel under My giveaways). */
    broccoli: Int = app.freetotake.domain.rewards.BroccoliRules.INITIAL,
    onBroccoli: () -> Unit = {},
) {
    Column(Modifier.fillMaxSize().background(FttColors.BackgroundPrimary)) {
        Column(
            Modifier.weight(1f).statusBarsPadding().verticalScroll(rememberScrollState()),
        ) {
            Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                LocationField(location, onEditLocation, Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                app.freetotake.ui.rewards.BroccoliChip(broccoli, onBroccoli)   // v1.17: top-right balance
            }
            // No chevron: nothing to navigate into while the user has no publications (spec 0.5/0.6).
            SectionHeader("My giveaways", onClick = null)
            // v1.17 (Figma "homescreen remade"): broccoli panel + Start under My giveaways.
            app.freetotake.ui.rewards.BroccoliPanel(broccoli, onStart = onStartGiveaway, onRules = onBroccoli,
                modifier = Modifier.padding(start = SIDE, end = SIDE, bottom = SIDE))
            if (myGiveaways.isNotEmpty()) LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 16.dp),
            ) {
                items(myGiveaways) { g ->
                    ListingCard(g, state = CardStates.myGiveaway(example = false), onTrash = { onDeleteGiveaway(g) }) { onOpenListing(g) }
                }
            }
            else LazyRow(
                // 1.10: no publications yet → an example publication with 3 requests + the Start card.
                contentPadding = PaddingValues(horizontal = SIDE),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = SIDE),
            ) {
                if (showExampleGiveaway) item {
                    ListingCard(
                        app.freetotake.domain.publish.ExamplePublication.item,
                        state = CardStates.myGiveaway(example = true),
                        onTrash = { onDeleteGiveaway(null) },
                        onClick = onOpenExampleGiveaway,
                    )
                }
            }
            // No chevron to the catalog while the service has no real publications (only the example card).
            val hasRealListings = carousel.any { it is CarouselCard.Listing && !ExampleListing.isExample(it.item) }
            SectionHeader("Available Giveaways", onClick = if (hasRealListings) onOpenAvailable else null)
            if (carousel.isEmpty()) EmptyCard(HomeCopy.AVAILABLE_EMPTY)
            else LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 16.dp),
            ) {
                items(carousel) { card ->
                    when (card) {
                        is CarouselCard.Listing -> {
                            val mine = claimFor(card.item)
                            ListingCard(
                                card.item, state = CardStates.available(card.item),
                                favorite = card.item.id.value in favorites,
                                onHeart = { onHeart(card.item) },
                            ) { onOpenListing(card.item) }
                        }
                        CarouselCard.ViewMore -> ViewMoreCard(onOpenAvailable)
                    }
                }
            }
            SectionHeader(MyClaimsCopy.TITLE, onClick = onOpenMyClaims)
            if (myClaims.isEmpty()) EmptyCard(HomeCopy.MY_CLAIMS_EMPTY)
            else LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 16.dp),
            ) {
                items(myClaims) { entry ->
                    ListingCard(
                        entry.item, state = CardStates.myClaimCard(entry.item, entry.claim, now),
                        onTrash = { onRemove(entry) },
                    ) { onOpenPickup(entry) }
                }
            }
            message?.let {
                Text(it, style = FttType.subheadlineBold(), modifier = Modifier.padding(16.dp))
            }
        }
        TabBar(selected = HomeTab.HOME, onTab = onTab)
    }
}


/**
 * Listing card (Figma "Available Giveaways" / "My claims card with statuses"): photo with status badges,
 * heart and/or trash (per [state], spec 0.9/0.10), title, description, area. Greyed = rejected request.
 */
@Composable
fun ListingCard(
    item: Item,
    modifier: Modifier = Modifier.width(CARD_WIDTH),
    state: CardState? = null,
    favorite: Boolean = false,
    onHeart: () -> Unit = {},
    onTrash: () -> Unit = {},
    onClick: () -> Unit,
) {
    Column(
        modifier.height(CARD_HEIGHT).alpha(if (state?.greyed == true) 0.45f else 1f)
            .background(FttColors.BackgroundSecondary, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
    ) {
        val demo = demoImageFor(item)
        Box(Modifier.fillMaxWidth().height(150.dp).background(FttColors.SectionCard), contentAlignment = Alignment.Center) {
            val photo = item.photoUrls.firstOrNull()
            if (demo != null) Image(painterResource(demo), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else if (photo != null) RemotePhoto(photo, Modifier.fillMaxSize()) {
                Image(painterResource(Res.drawable.empty_mascot), null, Modifier.size(width = 60.dp, height = 54.dp))
            }
            else Image(painterResource(Res.drawable.empty_mascot), null, Modifier.size(width = 60.dp, height = 54.dp))
            if (state != null) {
                Row(Modifier.align(Alignment.TopEnd).padding(2.dp)) {
                    if (state.showHeart) HeartButton(favorite, onHeart)
                    if (state.showTrash) TrashButton(onTrash)
                }
                Column(
                    Modifier.align(Alignment.BottomEnd).padding(8.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) { state.badges.forEach { BadgeChip(it) } }
            }
        }
        Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.title, style = FttType.subheadlineBold(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item.description.isNotBlank()) Text(item.description, style = FttType.caption().copy(fontSize = 13.sp, lineHeight = 16.sp), color = FttColors.LabelSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            item.area?.format()?.let { Text(it, style = FttType.caption().copy(fontSize = 12.sp, lineHeight = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

/** Status chip on the photo (Figma colours: pending grey, approved lime, rejected orange). */
@Composable
fun BadgeChip(badge: CardBadge) {
    val (bg, fg) = when (badge) {
        CardBadge.AVAILABLE -> FttColors.StartLime to Color.Black   // v1.16.1: lime
        CardBadge.EXAMPLE -> FttColors.StartLime to Color.Black   // v1.14.3: example labels lime
        CardBadge.PENDING -> Color(0xFFE5E5EA) to Color.Black
        CardBadge.APPROVED -> FttColors.StartLime to Color.Black
        CardBadge.REJECTED -> Color(0xFFFF9500) to Color.Black
        CardBadge.FINISHED -> Color(0xFFE5E5EA) to Color.Black
        CardBadge.PENDING_MY_APPROVAL -> FttColors.StartLime to Color.Black
    }
    Text(
        "• " + badge.label, style = FttType.caption().copy(fontSize = 11.sp, lineHeight = 14.sp), color = fg, maxLines = 1,
        modifier = Modifier.background(bg, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** Final carousel card — opens the full catalog. */
@Composable
private fun ViewMoreCard(onClick: () -> Unit) {
    Box(
        // Same size and white background as a listing card (design).
        Modifier.width(CARD_WIDTH).height(CARD_HEIGHT).background(FttColors.BackgroundSecondary, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(HomeCarousel.VIEW_MORE_LABEL, style = FttType.subheadlineBold())
            Chevron()
        }
    }
}

/** Local photo for the educational example card (food photo from the design, 1.8.4). */
fun demoImageFor(item: Item): DrawableResource? = when {
    ExampleListing.isExample(item) -> Res.drawable.example_food
    app.freetotake.domain.publish.ExamplePublication.isExample(item.id.value) -> Res.drawable.example_clothes
    else -> null
}

/** "UPROCK/Text field" — also used at the top of the catalog.: caption "Your giveaways location" + value + pencil (opens map picker). */
@Composable
fun LocationField(location: HomeLocation, onEdit: () -> Unit, modifier: Modifier) {
    val (value, isPlaceholder) = when (location) {
        is HomeLocation.Chosen -> location.pickup.displayText() to false
        is HomeLocation.DeviceArea -> location.label to false
        HomeLocation.Detecting -> "Detecting your area…" to true
        HomeLocation.NotSet -> "Choose your pickup point" to true
    }
    Row(
        modifier.fillMaxWidth().height(50.dp).background(FttColors.BackgroundSecondary, RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClickLabel = "Change pickup location", onClick = onEdit)
            .padding(start = 16.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text("My giveaways location", style = FttType.caption(), color = FttColors.LabelSecondary)
            Text(
                value, style = FttType.subheadline(), maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (isPlaceholder) FttColors.LabelSecondary.copy(alpha = 0.35f) else FttColors.TextPrimary,
            )
        }
        Image(painterResource(Res.drawable.ic_edit), contentDescription = "Edit location", modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun SectionHeader(title: String, onClick: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().height(44.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = FttType.sectionTitle(), modifier = Modifier.weight(1f))
        if (onClick != null) Chevron()
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun EmptyCard(text: String) {
    Column(
        Modifier.padding(horizontal = SIDE).padding(bottom = SIDE).fillMaxWidth().height(CARD_HEIGHT)
            .background(FttColors.SectionCard, RoundedCornerShape(16.dp)).padding(SIDE),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(text, style = FttType.subheadline(), color = FttColors.LabelSecondary, textAlign = TextAlign.Center)
        Image(painterResource(Res.drawable.empty_mascot), contentDescription = null, modifier = Modifier.size(width = 101.dp, height = 90.dp))
    }
}

@Composable
private fun Chevron() {
    Canvas(Modifier.size(width = 8.dp, height = 13.dp)) {
        val p = Path().apply { moveTo(0f, 0f); lineTo(size.width, size.height / 2); lineTo(0f, size.height) }
        drawPath(p, Color(0x4D3C3C43), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** "UPROCK / TabBar": Home / My publications / Profile. */
@Composable
fun TabBar(selected: HomeTab, onTab: (HomeTab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(FttColors.BackgroundSecondary).navigationBarsPadding().height(58.dp),
        verticalAlignment = Alignment.Top,
    ) {
        TabItem("Home", selected == HomeTab.HOME, TabIcon.HOME, Modifier.weight(1f)) { onTab(HomeTab.HOME) }
        TabItem("My publications", selected == HomeTab.MY_PUBLICATIONS, TabIcon.PUBLICATIONS, Modifier.weight(1f)) { onTab(HomeTab.MY_PUBLICATIONS) }
        TabItem("Profile", selected == HomeTab.PROFILE, TabIcon.PROFILE, Modifier.weight(1f)) { onTab(HomeTab.PROFILE) }
    }
}

@Composable
private fun TabItem(label: String, selected: Boolean, icon: TabIcon, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.fillMaxHeight().clickable(role = Role.Tab, onClick = onClick), contentAlignment = Alignment.TopCenter) {
        if (selected) {
            // Figma "Navigation items active": lime bar on top + lime spotlight fading down.
            Canvas(Modifier.size(width = 43.dp, height = 49.dp)) {
                val u = size.width / 43f
                val beam = Path().apply {
                    moveTo(33.73f * u, 3f * u); lineTo(10.17f * u, 3f * u); lineTo(3f * u, 49f * u); lineTo(40.9f * u, 49f * u); close()
                }
                drawPath(beam, Brush.verticalGradient(listOf(FttColors.StartLime, Color.White.copy(alpha = 0f)), startY = 3f * u, endY = 49f * u))
                drawRoundRect(FttColors.StartLime, size = androidx.compose.ui.geometry.Size(size.width, 3f * u), cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5f * u))
            }
        }
        Column(
            Modifier.padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            TabGlyph(icon, selected)
            Text(label, style = FttType.caption().copy(fontSize = 11.sp, lineHeight = 13.sp),
                color = if (selected) FttColors.Ink else FttColors.TabInactive)
        }
    }
}

enum class TabIcon { HOME, PUBLICATIONS, PROFILE }

/** Icons from Figma "UPROCK" nav sheet (paths in its own 24dp box); active = black (home filled lime), inactive = #8E8E93. */
@Composable
private fun TabGlyph(icon: TabIcon, selected: Boolean) {
    val ink = if (selected) FttColors.Ink else FttColors.TabInactive
    Canvas(Modifier.size(24.dp)) {
        val k = size.width / 24f
        fun path(d: String, dx: Float, dy: Float): Path {
            val p = PathParser().parsePathString(d).toPath()
            p.translate(Offset(dx, dy))
            val m = Matrix(); m.scale(k, k); p.transform(m)
            return p
        }
        when (icon) {
            TabIcon.HOME -> {
                val house = path(HOME_D, -46f, -29f)
                if (selected) drawPath(house, FttColors.StartLime)
                drawPath(house, ink, style = Stroke(width = 1f * k * 1.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawLine(if (selected) FttColors.StartLime else ink, Offset(12f * k, 15f * k), Offset(12f * k, 18f * k), strokeWidth = 1.5f * k, cap = StrokeCap.Round)
            }
            TabIcon.PUBLICATIONS -> {
                drawPath(path(DOC_D1, -47f, -169f), ink)
                drawPath(path(DOC_D2, -47f, -169f), ink)
            }
            TabIcon.PROFILE -> {
                drawPath(path(USER_D1, -46f, -309f), ink)
                drawPath(path(USER_D2, -46f, -309f), ink)
            }
        }
    }
}

private const val HOME_D = "M56.0693 31.8198L49.1393 37.3698C48.3593 37.9898 47.8593 39.2998 48.0293 40.2798L49.3593 48.2398C49.5993 49.6598 50.9593 50.8098 52.3993 50.8098H63.5993C65.0293 50.8098 66.3993 49.6498 66.6393 48.2398L67.9693 40.2798C68.1293 39.2998 67.6293 37.9898 66.8593 37.3698L59.9293 31.8298C58.8593 30.9698 57.1293 30.9698 56.0693 31.8198Z"
private const val DOC_D1 = "M66.275 175.975L61.025 170.725C60.875 170.575 60.725 170.5 60.5 170.5H53C52.175 170.5 51.5 171.175 51.5 172V190C51.5 190.825 52.175 191.5 53 191.5H65C65.825 191.5 66.5 190.825 66.5 190V176.5C66.5 176.275 66.425 176.125 66.275 175.975ZM60.5 172.3L64.7 176.5H60.5V172.3ZM65 190H53V172H59V176.5C59 177.325 59.675 178 60.5 178H65V190Z"
private const val DOC_D2 = "M54.5 185.5H63.5V187H54.5V185.5ZM54.5 181H63.5V182.5H54.5V181Z"
private const val USER_D1 = "M58.0009 322.705C56.8928 322.705 55.8097 322.376 54.8883 321.761C53.967 321.145 53.2489 320.27 52.8249 319.246C52.4009 318.223 52.2899 317.096 52.5061 316.009C52.7223 314.923 53.2558 313.924 54.0394 313.141C54.8229 312.357 55.8211 311.824 56.9079 311.608C57.9947 311.391 59.1212 311.502 60.1449 311.926C61.1686 312.351 62.0436 313.069 62.6592 313.99C63.2748 314.911 63.6034 315.994 63.6034 317.102C63.6008 318.588 63.0097 320.011 61.9596 321.061C60.9095 322.111 59.486 322.702 58.0009 322.705ZM58.0009 312.962C57.1819 312.962 56.3813 313.204 55.7003 313.659C55.0193 314.114 54.4886 314.761 54.1752 315.518C53.8617 316.274 53.7797 317.107 53.9395 317.91C54.0993 318.714 54.4937 319.451 55.0728 320.031C55.6519 320.61 56.3898 321.004 57.193 321.164C57.9963 321.324 58.8289 321.242 59.5856 320.928C60.3422 320.615 60.989 320.084 61.444 319.403C61.899 318.722 62.1419 317.921 62.1419 317.102C62.1393 316.005 61.7022 314.953 60.9262 314.177C60.1501 313.401 59.0984 312.964 58.0009 312.962Z"
private const val USER_D2 = "M66.7689 330.5C66.5759 330.498 66.3915 330.42 66.255 330.284C66.1185 330.147 66.0407 329.963 66.0381 329.77C66.0356 327.897 65.2905 326.102 63.9663 324.777C62.6421 323.453 60.8469 322.708 58.9742 322.706H57.0255C55.1528 322.708 53.3575 323.453 52.0333 324.777C50.7092 326.102 49.9641 327.897 49.9615 329.77C49.9615 329.963 49.8845 330.149 49.7475 330.286C49.6104 330.423 49.4246 330.5 49.2308 330.5C49.0369 330.5 48.8511 330.423 48.714 330.286C48.577 330.149 48.5 329.963 48.5 329.77C48.5026 327.509 49.4016 325.342 50.9999 323.744C52.5982 322.146 54.7652 321.247 57.0255 321.244H58.9742C61.2345 321.247 63.4015 322.146 64.9997 323.744C66.598 325.342 67.4971 327.509 67.4996 329.77C67.4971 329.963 67.4193 330.147 67.2828 330.284C67.1463 330.42 66.9619 330.498 66.7689 330.5Z"
