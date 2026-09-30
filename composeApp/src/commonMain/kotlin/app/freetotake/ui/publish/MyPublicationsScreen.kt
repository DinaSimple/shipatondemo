// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Dina Elokhova (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.ui.publish

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.freetotake.domain.model.Timestamp
import app.freetotake.domain.publish.MyPublicationsRules as R
import app.freetotake.domain.publish.PublicationOverview
import app.freetotake.domain.publish.PublicationSection
import app.freetotake.domain.publish.PublicationsTab
import app.freetotake.domain.request.DateKey
import app.freetotake.domain.request.TimeOfDay
import app.freetotake.resources.Res
import app.freetotake.resources.empty_mascot
import app.freetotake.resources.ic_edit
import app.freetotake.ui.home.HomeTab
import app.freetotake.ui.home.TabBar
import app.freetotake.ui.platform.RemotePhoto
import app.freetotake.ui.theme.BackChevron
import app.freetotake.ui.theme.FttColors
import app.freetotake.ui.theme.FttType
import app.freetotake.ui.theme.TrashButton
import org.jetbrains.compose.resources.painterResource

private val Grey = Color(0xFFE5E5EA)

/** Figma "My active publications" / "My archived publications" / "My piblicationss empty" (spec 0.12). */
@Composable
fun MyPublicationsScreen(
    publications: List<PublicationOverview>,
    tab: PublicationsTab,
    now: Timestamp,
    today: DateKey,
    message: String?,
    onTab: (PublicationsTab) -> Unit,
    onOpen: (PublicationOverview) -> Unit,
    onCancel: (PublicationOverview) -> Unit,
    onChat: (PublicationOverview) -> Unit,
    onCreate: () -> Unit,
    onNavTab: (HomeTab) -> Unit,
) {
    val sections = R.grouped(publications, tab, now)
    Column(Modifier.fillMaxSize().background(FttColors.BackgroundPrimary)) {
        Box(Modifier.weight(1f).statusBarsPadding()) {
            Column(Modifier.fillMaxSize()) {
                Text(R.TITLE, style = FttType.largeTitleBold(), modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 12.dp))
                Segmented(tab, onTab)
                if (sections.isEmpty()) {
                    Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Image(painterResource(Res.drawable.empty_mascot), null, Modifier.size(width = 101.dp, height = 90.dp))
                        Text(R.EMPTY, style = FttType.body(), color = FttColors.LabelSecondary, modifier = Modifier.padding(top = 12.dp))
                    }
                } else Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                    sections.forEach { (section, list) ->
                        Text(section.title, style = FttType.bodyBold(), modifier = Modifier.padding(top = 16.dp))
                        Text(section.subtitle, style = FttType.caption().copy(fontSize = 11.sp), color = FttColors.LabelSecondary, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
                        list.forEach { p -> PublicationCard(p, section, now, today, onOpen, onCancel, onChat); Spacer(Modifier.height(10.dp)) }
                    }
                    Spacer(Modifier.height(88.dp))   // room for the Create button
                }
            }
            message?.let {
                Text(it, style = FttType.subheadline(), color = Color.White, modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
                    .background(Color(0xE6333333), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 8.dp))
            }
            // Floating "Create" (design): starts the Create flow; guests get the login sheet.
            Button(
                onClick = onCreate,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).height(50.dp),
                shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, FttColors.OnLime),
                colors = ButtonDefaults.buttonColors(containerColor = FttColors.StartLime, contentColor = FttColors.OnLime),
            ) {
                Image(painterResource(Res.drawable.ic_edit), null, Modifier.size(18.dp), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(FttColors.OnLime))
                Spacer(Modifier.width(8.dp))
                Text(R.CREATE, style = FttType.body())
            }
        }
        TabBar(selected = HomeTab.MY_PUBLICATIONS, onTab = onNavTab)
    }
}

@Composable
private fun Segmented(tab: PublicationsTab, onTab: (PublicationsTab) -> Unit) {
    Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(40.dp).background(Grey, RoundedCornerShape(9.dp)).padding(2.dp)) {
        PublicationsTab.entries.forEach { t ->
            val sel = t == tab
            Box(
                Modifier.weight(1f).fillMaxSize().then(if (sel) Modifier.background(FttColors.Surface, RoundedCornerShape(7.dp)) else Modifier)
                    .clickable(role = Role.Tab) { onTab(t) },
                contentAlignment = Alignment.Center,
            ) { Text(t.label, style = FttType.subheadline().copy(fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal), color = if (sel) FttColors.Ink else FttColors.LabelSecondary) }
        }
    }
}

@Composable
private fun PublicationCard(
    p: PublicationOverview,
    section: PublicationSection,
    now: Timestamp,
    today: DateKey,
    onOpen: (PublicationOverview) -> Unit,
    onCancel: (PublicationOverview) -> Unit,
    onChat: (PublicationOverview) -> Unit,
) {
    val archived = section == PublicationSection.ARCHIVED
    Box(
        Modifier.fillMaxWidth().alpha(if (archived) 0.5f else 1f).background(FttColors.Surface, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp))
            .then(if (R.isClickable(p, now)) Modifier.clickable { onOpen(p) } else Modifier)
            .padding(8.dp),
    ) {
        Row {
            Box(Modifier.size(90.dp).clip(RoundedCornerShape(8.dp)).background(FttColors.SectionCard), contentAlignment = Alignment.Center) {
                val url = p.item.photoUrls.firstOrNull()
                val demo = app.freetotake.ui.home.demoImageFor(p.item)
                if (url != null) RemotePhoto(url, Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize()) }
                else if (demo != null) Image(painterResource(demo), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Image(painterResource(Res.drawable.empty_mascot), null, Modifier.size(48.dp), contentScale = ContentScale.Fit)
            }
            Column(Modifier.weight(1f).padding(start = 12.dp, top = 2.dp)) {
                Text(p.item.title, style = FttType.bodyBold(), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(end = 36.dp))
                if (app.freetotake.domain.publish.ExamplePublication.isExample(p.item.id.value))
                    Text("• Example", style = FttType.caption().copy(fontSize = 11.sp), color = FttColors.OnLime,
                        modifier = Modifier.padding(top = 4.dp).background(FttColors.StartLime, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 2.dp))
                Text("Pick up schedule", style = FttType.caption(), color = FttColors.LabelSecondary, modifier = Modifier.padding(top = 4.dp))
                val schedule = p.schedule
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (schedule != null) {
                        Dot(square = true); Text(R.dateLabel(schedule.date, today), style = FttType.caption().copy(fontSize = 11.sp), modifier = Modifier.padding(start = 4.dp, end = 10.dp))
                        Dot(square = false); Text(R.timeRangeLabel(schedule), style = FttType.caption().copy(fontSize = 11.sp), modifier = Modifier.padding(start = 4.dp))
                    } else Text("—", style = FttType.caption())
                }
                when (section) {
                    PublicationSection.PENDING_COLLECTION -> p.collector?.let { c ->
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("meet ", style = FttType.caption(), color = FttColors.LabelSecondary)
                            Text("@${c.nickname}", style = FttType.caption().copy(textDecoration = TextDecoration.Underline), modifier = Modifier.weight(1f), maxLines = 1)
                            ChatButton { onChat(p) }
                        }
                    }
                    else -> Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                        val (label, bg) = when (section) {
                            PublicationSection.ARCHIVED -> R.ARCHIVED_BADGE to Grey
                            else -> R.pendingLabel(p.pendingRequests) to (if (p.pendingRequests > 0) FttColors.StartLime else Grey)
                        }
                        Text("• $label", style = FttType.caption().copy(fontSize = 11.sp), modifier = Modifier.background(bg, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                }
            }
        }
        // Cancel/unpublish until 2 h before the pickup start (spec 0.12) — hidden after the cutoff.
        if (R.canCancel(p, now)) TrashButton({ onCancel(p) }, Modifier.align(Alignment.TopEnd))
    }
}

@Composable
private fun Dot(square: Boolean) {
    Canvas(Modifier.size(10.dp)) {
        if (square) {
            drawRoundRect(FttColors.StartLime, cornerRadius = CornerRadius(2.dp.toPx()))
            drawRoundRect(Color(0xFF8E9A5B), cornerRadius = CornerRadius(2.dp.toPx()), style = Stroke(1.dp.toPx()))
        } else {
            drawCircle(FttColors.StartLime); drawCircle(Color(0xFF8E9A5B), style = Stroke(1.dp.toPx()))
        }
    }
}

/** Chat icon (design); chat itself arrives in a later step. */
@Composable
private fun ChatButton(onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).border(1.dp, FttColors.Ink, RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClickLabel = "Chat", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(18.dp)) {
            val w = size.width; val h = size.height
            drawRoundRect(FttColors.StartLime, Offset(0f, 0f), Size(w, h * 0.78f), CornerRadius(3.dp.toPx()))
            drawRoundRect(FttColors.Ink, Offset(0f, 0f), Size(w, h * 0.78f), CornerRadius(3.dp.toPx()), style = Stroke(1.5.dp.toPx()))
            drawLine(FttColors.Ink, Offset(w * 0.25f, h * 0.78f), Offset(w * 0.2f, h), 1.5.dp.toPx())
        }
    }
}

/** Figma "My request cancellation": centered alert with Cancel / Yes, delete. */
@Composable
fun DeletePublicationDialog(onYes: () -> Unit, onNo: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable(onClick = onNo), contentAlignment = Alignment.Center) {
        Column(Modifier.width(272.dp).background(FttColors.Surface, RoundedCornerShape(14.dp)).clickable(enabled = false) {}) {
            Text(R.DELETE_QUESTION, style = FttType.bodyBold(), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(16.dp))
            HorizontalDivider(color = FttColors.Separator)
            Row(Modifier.height(44.dp)) {
                Text(R.DELETE_NO, style = FttType.body(), color = Color(0xFF007AFF), textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).clickable(role = Role.Button, onClick = onNo).padding(vertical = 11.dp))
                Box(Modifier.width(1.dp).fillMaxHeight().background(FttColors.Separator))
                Text(R.DELETE_YES, style = FttType.body(), color = Color(0xFFFF3B30), textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).clickable(role = Role.Button, onClick = onYes).padding(vertical = 11.dp))
            }
        }
    }
}

/** Figma "My request cancellation loader". */
@Composable
fun BlockingLoader() {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Color.White)
    }
}

/** Figma "Publication deleted" sheet. */
@Composable
fun PublicationDeletedSheet(onGoHome: () -> Unit, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).clickable(onClick = onClose)) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(FttColors.BackgroundPrimary, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .clickable(enabled = false) {}.navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(width = 36.dp, height = 5.dp).background(Color(0x4D3C3C43), RoundedCornerShape(3.dp)))
            Text(R.DELETED_TITLE, style = FttType.title1Bold(), modifier = Modifier.padding(top = 8.dp))
            Text(R.DELETED_BODY, style = FttType.subheadline())
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = onGoHome, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, FttColors.OnLime),
                colors = ButtonDefaults.buttonColors(containerColor = FttColors.StartLime, contentColor = FttColors.OnLime),
            ) { Text("Go to homepage", style = FttType.bodyBold()) }
            Button(
                onClick = onClose, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, FttColors.Ink),
                colors = ButtonDefaults.buttonColors(containerColor = FttColors.BackgroundPrimary, contentColor = Color(0xFF3C3C43)),
            ) { Text("Close", style = FttType.body()) }
        }
    }
}
