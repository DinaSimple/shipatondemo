// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
// Copyright (C) 2026 Dina Elokhova (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.ui.location

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.freetotake.domain.location.GeoPoint
import app.freetotake.domain.location.PickupPoint
import app.freetotake.domain.location.PlaceSearchPolicy
import app.freetotake.domain.location.PlaceSuggestion
import app.freetotake.resources.Res
import app.freetotake.resources.empty_mascot
import app.freetotake.resources.ic_locate
import app.freetotake.resources.ic_result_place
import app.freetotake.resources.ic_result_venue
import app.freetotake.resources.map_pin_face
import app.freetotake.ui.platform.PickupMap
import app.freetotake.ui.theme.BackChevron
import app.freetotake.ui.theme.FttColors
import app.freetotake.ui.theme.FttType
import org.jetbrains.compose.resources.painterResource

private const val SHEET_TITLE = "What is your location?"

/**
 * Map-based pickup selection (Figma "Light / Location"). Map is optional: the user can
 * also just tap the field and search. Nothing is saved until "Continue" (only enabled for a valid selection).
 */
@Composable
fun LocationPickerScreen(
    initialCenter: GeoPoint,
    moveTo: GeoPoint?,
    candidate: PickupPoint?,
    resolving: Boolean,
    canLocateMe: Boolean,
    canContinue: Boolean,
    onCameraIdle: (GeoPoint) -> Unit,
    onOpenSearch: () -> Unit,
    onLocateMe: () -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(FttColors.BackgroundPrimary)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            PickupMap(Modifier.fillMaxSize(), initialCenter, moveTo, onCameraIdle)
            MapPin(Modifier.align(Alignment.Center).offset(y = (-38).dp))
            Box(
                Modifier.statusBarsPadding().padding(start = 7.dp, top = 8.dp).background(Color.White.copy(alpha = 0.8f), CircleShape)
                    .clickable(role = Role.Button, onClickLabel = "Back", onClick = onBack),
            ) { BackChevron() }
            if (canLocateMe) {
                Image(
                    painterResource(Res.drawable.ic_locate), contentDescription = "My location",
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).size(44.dp).clickable(onClick = onLocateMe),
                )
            }
        }
        // Bottom sheet
        Column(
            Modifier.fillMaxWidth().background(FttColors.BackgroundPrimary).navigationBarsPadding()
                .padding(horizontal = 16.dp).padding(top = 6.dp, bottom = 12.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(width = 36.dp, height = 5.dp).background(Color(0x4D3C3C43), RoundedCornerShape(3.dp)))
            Spacer(Modifier.height(20.dp))
            Text(SHEET_TITLE, style = FttType.title1Bold())
            Spacer(Modifier.height(16.dp))
            Column(
                Modifier.fillMaxWidth().height(50.dp).background(FttColors.BackgroundSecondary, RoundedCornerShape(10.dp))
                    .clickable(role = Role.Button, onClick = onOpenSearch).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(PlaceSearchPolicy.PROMPT, style = FttType.caption(), color = FttColors.LabelSecondary)
                Text(
                    when {
                        candidate != null -> candidate.displayText()
                        resolving -> "Finding address…"
                        else -> "Search or move the map"
                    },
                    style = FttType.subheadline(), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (candidate != null) FttColors.TextPrimary else FttColors.LabelSecondary,
                )
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onContinue, enabled = canContinue,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, FttColors.OnLime),
                colors = ButtonDefaults.buttonColors(containerColor = FttColors.StartLime, contentColor = FttColors.OnLime),
            ) { Text("Continue", style = FttType.bodyBold()) }
        }
    }
}

/** Fixed centre pin: mascot in a circle with a pointer; the tip marks the map centre. */
@Composable
private fun MapPin(modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(64.dp).background(FttColors.BackgroundPrimary, CircleShape)
                .border(1.5.dp, Color.Black, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Image(painterResource(Res.drawable.map_pin_face), contentDescription = "Pickup point", modifier = Modifier.width(36.dp)) }
        Canvas(Modifier.size(width = 12.dp, height = 8.dp)) {
            drawPath(Path().apply { moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width / 2, size.height); close() }, Color.Black)
        }
    }
}

/** Search sheet (Figma "Location_Address Delivery Form"): search starts as the user types. */
@Composable
fun PlaceSearchScreen(
    query: String,
    results: List<PlaceSuggestion>,
    loading: Boolean,
    error: String?,
    onQueryChange: (String) -> Unit,
    onPick: (PlaceSuggestion) -> Unit,
    onClose: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column(
        Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().padding(top = 8.dp)
            .background(FttColors.BackgroundPrimary, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .imePadding().padding(horizontal = 16.dp),
    ) {
        Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp).size(width = 36.dp, height = 5.dp).background(Color(0x4D3C3C43), RoundedCornerShape(3.dp)))
        Spacer(Modifier.height(20.dp))
        Text(SHEET_TITLE, style = FttType.title1Bold())
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth().height(50.dp).background(FttColors.BackgroundSecondary, RoundedCornerShape(10.dp)).padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(PlaceSearchPolicy.PROMPT, style = FttType.caption(), color = FttColors.LabelSecondary)
                BasicTextField(
                    value = query, onValueChange = onQueryChange, singleLine = true,
                    textStyle = FttType.subheadline(),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(FttColors.Ink),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            Canvas(
                Modifier.size(28.dp).clickable(onClickLabel = "Clear") { if (query.isEmpty()) onClose() else onQueryChange("") }.padding(8.dp),
            ) {
                val s = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
                drawLine(FttColors.LabelSecondary, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(size.width, size.height), s.width, s.cap)
                drawLine(FttColors.LabelSecondary, androidx.compose.ui.geometry.Offset(size.width, 0f), androidx.compose.ui.geometry.Offset(0f, size.height), s.width, s.cap)
            }
        }
        Spacer(Modifier.height(12.dp))
        when {
            error != null -> Text(error, style = FttType.subheadline(), color = FttColors.LabelSecondary, modifier = Modifier.padding(8.dp))
            loading && results.isEmpty() -> Text("Searching…", style = FttType.subheadline(), color = FttColors.LabelSecondary, modifier = Modifier.padding(8.dp))
            query.isNotBlank() && !PlaceSearchPolicy.shouldSearch(query) -> Unit
            query.isNotBlank() && results.isEmpty() && !loading ->
                Text("Nothing found. Try another name or pick on the map.", style = FttType.subheadline(), color = FttColors.LabelSecondary, modifier = Modifier.padding(8.dp))
        }
        LazyColumn(Modifier.fillMaxWidth()) {
            items(results) { r -> ResultRow(r, query) { onPick(r) } }
        }
    }
}

@Composable
private fun ResultRow(r: PlaceSuggestion, query: String, onClick: () -> Unit) {
    val venue = r.category?.let { c -> listOf("cafe", "bar", "pub", "restaurant", "fast_food").any { c.endsWith(it) } } == true
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(
            painterResource(if (venue) Res.drawable.ic_result_venue else Res.drawable.ic_result_place),
            contentDescription = null, modifier = Modifier.size(width = 14.dp, height = 18.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            val hl = PlaceSearchPolicy.highlight(r.name, query)
            Text(
                buildAnnotatedString {
                    if (hl == null) withStyle(SpanStyle(color = FttColors.LabelSecondary)) { append(r.name) }
                    else {
                        withStyle(SpanStyle(color = FttColors.LabelSecondary)) { append(r.name.substring(0, hl.first)) }
                        withStyle(SpanStyle(color = FttColors.Ink, fontWeight = FontWeight.SemiBold)) { append(r.name.substring(hl.first, hl.last + 1)) }
                        withStyle(SpanStyle(color = FttColors.LabelSecondary)) { append(r.name.substring(hl.last + 1)) }
                    }
                },
                style = FttType.subheadline(), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            r.subtitle?.let { Text(it, style = FttType.caption().copy(fontSize = FttType.caption().fontSize * 1.1f), color = FttColors.LabelSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
    HorizontalDivider(color = FttColors.Separator, thickness = 0.5.dp, modifier = Modifier.padding(start = 28.dp))
}

/** Spec 0.5 confirmation; returns Home automatically after [app.freetotake.domain.location.PickupConfirmation.AUTO_RETURN_MS]. */
@Composable
fun PickupConfirmationScreen(onDone: () -> Unit) {
    // v1.14: explicit Continue (no silent auto-return — the screen looked locked).
    Column(
        Modifier.fillMaxSize().background(FttColors.BackgroundPrimary).statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.weight(1f).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(painterResource(Res.drawable.empty_mascot), contentDescription = null, modifier = Modifier.size(width = 150.dp, height = 134.dp))
            Text(
                app.freetotake.domain.location.PickupConfirmation.MESSAGE,
                style = FttType.title1Bold(), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        app.freetotake.ui.theme.PrimaryButton(app.freetotake.domain.location.PickupConfirmation.CONTINUE, onClick = onDone, modifier = Modifier.padding(bottom = 12.dp))
    }
}
