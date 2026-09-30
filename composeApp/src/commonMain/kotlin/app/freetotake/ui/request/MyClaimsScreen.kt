// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
// Copyright (C) 2026 Dina Elokhova (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.ui.request

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.freetotake.domain.catalog.CancelCopy
import app.freetotake.domain.catalog.CardStates
import app.freetotake.domain.catalog.HomeCopy
import app.freetotake.domain.catalog.MyClaims
import app.freetotake.domain.catalog.ClaimFilter
import app.freetotake.domain.catalog.ClaimSort
import app.freetotake.domain.catalog.MyClaimsSession
import app.freetotake.domain.model.Timestamp
import app.freetotake.resources.Res
import app.freetotake.resources.ic_sort
import app.freetotake.ui.platform.PlatformBackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import org.jetbrains.compose.resources.painterResource
import app.freetotake.ui.home.ListingCard
import app.freetotake.ui.theme.BackChevron
import app.freetotake.ui.theme.FttColors
import app.freetotake.ui.theme.FttType

/** Figma "My claims screen": All / Pending / Approved / Rejected chips + 2-column grid of My Claims cards. */
@Composable
fun MyClaimsScreen(
    entries: List<MyClaims.Entry>,
    favorites: Set<String>,
    now: Timestamp,
    onBack: () -> Unit,
    onOpen: (MyClaims.Entry) -> Unit,
    onHeart: (MyClaims.Entry) -> Unit,
    onRemove: (MyClaims.Entry) -> Unit,
) {
    var session by remember { mutableStateOf(MyClaimsSession()) }
    val visible = session.visible(entries, now)
    PlatformBackHandler(enabled = session.sheetOpen) { session = session.cancel() }
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().background(FttColors.BackgroundPrimary).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clickable(role = Role.Button, onClickLabel = "Back", onClick = onBack), contentAlignment = Alignment.Center) { BackChevron() }
            Text("My claims", style = FttType.bodyBold(), textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Box(Modifier.size(44.dp))
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Box(
                    Modifier.size(36.dp).background(FttColors.Surface, RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button, onClickLabel = "Sorting") { session = session.openSheet() },
                    contentAlignment = Alignment.Center,
                ) { Image(painterResource(Res.drawable.ic_sort), "Sorting", Modifier.size(18.dp)) }
            }
            items(ClaimFilter.entries.toList()) { f ->
                Text(
                    f.label, style = FttType.subheadline(),
                    modifier = Modifier.height(36.dp)
                        .background(if (f == session.filter) FttColors.StartLime else FttColors.Surface, RoundedCornerShape(8.dp))
                        .clickable(role = Role.Tab) { session = session.select(f) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
        if (visible.isEmpty()) {
            app.freetotake.ui.theme.EmptyState()
        } else LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(visible) { e ->
                ListingCard(
                    e.item, Modifier.fillMaxWidth(), state = CardStates.myClaimCard(e.item, e.claim, now),
                    favorite = e.item.id.value in favorites,
                    onHeart = { onHeart(e) }, onTrash = { onRemove(e) },
                ) { onOpen(e) }
            }
        }
    }
        if (session.sheetOpen) ClaimSortSheet(
            pending = session.pendingSort,
            onPick = { session = session.pick(it) },
            onCancel = { session = session.cancel() },
            onApply = { session = session.apply() },
        )
    }
}

/** Figma "Sorting for my claims": two options, Cancel / Apply. */
@Composable
private fun ClaimSortSheet(pending: ClaimSort?, onPick: (ClaimSort) -> Unit, onCancel: () -> Unit, onApply: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)).clickable(onClick = onCancel)) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(FttColors.BackgroundPrimary, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .clickable(enabled = false) {}.navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(width = 36.dp, height = 5.dp).background(Color(0x4D3C3C43), RoundedCornerShape(3.dp)))
            Text("Sorting", style = FttType.title1Bold().copy(fontSize = 20.sp), modifier = Modifier.align(Alignment.CenterHorizontally))
            ClaimSort.entries.forEach { o ->
                Row(
                    Modifier.fillMaxWidth().height(48.dp).background(FttColors.Surface, RoundedCornerShape(10.dp))
                        .clickable(role = Role.RadioButton) { onPick(o) }.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(o.label, style = FttType.subheadline(), modifier = Modifier.weight(1f))
                    Box(
                        Modifier.size(20.dp).border(1.dp, Color(0x4D3C3C43), CircleShape)
                            .background(if (pending == o) FttColors.StartLime else Color.Transparent, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { if (pending == o) Box(Modifier.size(8.dp).background(FttColors.Ink, CircleShape)) }
                }
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
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

/** Figma "My request cancellation": question + red Yes, separate Close. */
@Composable
fun CancelRequestSheet(onYes: () -> Unit, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)).clickable(onClick = onClose)) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.fillMaxWidth().background(FttColors.Surface, RoundedCornerShape(14.dp)).clickable(enabled = false) {}) {
                Text(
                    CancelCopy.QUESTION, style = FttType.subheadline().copy(fontSize = 13.sp), color = FttColors.LabelSecondary,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 16.dp),
                )
                HorizontalDivider(color = FttColors.Separator)
                Text(
                    CancelCopy.YES, style = FttType.body().copy(fontSize = 20.sp), color = Color(0xFFFF3B30), textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onYes).padding(vertical = 16.dp),
                )
            }
            Text(
                CancelCopy.CLOSE, style = FttType.body().copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold), color = Color(0xFF007AFF),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().background(FttColors.Surface, RoundedCornerShape(14.dp))
                    .clickable(role = Role.Button, onClick = onClose).padding(vertical = 16.dp),
            )
        }
    }
}
