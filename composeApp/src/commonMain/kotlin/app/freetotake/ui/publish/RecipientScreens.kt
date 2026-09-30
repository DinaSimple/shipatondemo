// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.ui.publish

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.freetotake.domain.publish.Candidate
import app.freetotake.domain.publish.ReviewCopy
import app.freetotake.domain.publish.ReviewSession
import app.freetotake.domain.publish.SwipeRules
import app.freetotake.resources.Res
import app.freetotake.resources.avatar_croc_0
import app.freetotake.resources.avatar_giraffe
import app.freetotake.resources.avatar_croc_1
import app.freetotake.resources.avatar_croc_2
import app.freetotake.resources.empty_mascot
import app.freetotake.resources.ic_edit
import app.freetotake.resources.success_mascot
import app.freetotake.ui.platform.RemotePhoto
import app.freetotake.ui.theme.BackChevron
import app.freetotake.ui.theme.FttColors
import app.freetotake.ui.theme.FttType
import app.freetotake.ui.theme.TrashButton
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt

private val Rejected = Color(0xFFFF3B30)

/** Avatar: uploaded photo, example crocodile, or mascot placeholder on lime (#C8FF00) when there is none. */
@Composable
fun Avatar(url: String?, example: Int?, size: androidx.compose.ui.unit.Dp) {
    val crocs = listOf(Res.drawable.avatar_croc_0, Res.drawable.avatar_croc_1, Res.drawable.avatar_croc_2)
    Box(Modifier.size(size).clip(CircleShape).background(FttColors.StartLime), contentAlignment = Alignment.Center) {
        val placeholder: @Composable () -> Unit = {
            Image(painterResource(Res.drawable.empty_mascot), null, Modifier.fillMaxSize().padding(size / 6), contentScale = ContentScale.Fit)
        }
        when {
            example == app.freetotake.domain.chat.ExampleOwner.AVATAR -> Image(painterResource(Res.drawable.avatar_giraffe), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            example != null -> Image(painterResource(crocs[example % crocs.size]), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            url != null -> RemotePhoto(url, Modifier.fillMaxSize(), placeholder)
            else -> placeholder()
        }
    }
}

/**
 * Publisher's "My Publication Details" (Figma "Card with received approvals" / "My card where recipient is assigned").
 * [photo] draws the image area; [collector] non-null → Pending collection header with Chat.
 */
@Composable
fun PublisherDetailsScreen(
    title: String,
    photo: @Composable (Modifier) -> Unit,
    scheduleDate: String?,
    scheduleTime: String?,
    meetup: String,
    description: String,
    pendingRequests: Int,
    collector: Candidate?,
    canCancel: Boolean,
    canEdit: Boolean,
    onBack: () -> Unit,
    onCancel: () -> Unit,
    onReview: () -> Unit,
    onChat: () -> Unit,
    onEdit: () -> Unit,
    message: String? = null,
) {
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().background(FttColors.BackgroundPrimary).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clickable(role = Role.Button, onClickLabel = "Back", onClick = onBack), contentAlignment = Alignment.Center) { BackChevron() }
            Text(title, style = FttType.bodyBold(), textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (canCancel) TrashButton(onCancel, Modifier.size(44.dp)) else Box(Modifier.size(44.dp))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            if (collector != null) {
                Label(ReviewCopy.PENDING_COLLECTION_FROM)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier.weight(1f).background(FttColors.Surface, RoundedCornerShape(10.dp)).padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(collector.avatarUrl, collector.exampleAvatar, 32.dp)
                        Text("@${collector.nickname}", style = FttType.subheadline(), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 10.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Button(
                        onClick = onChat, modifier = Modifier.height(42.dp), shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, FttColors.OnLime),
                        colors = ButtonDefaults.buttonColors(containerColor = FttColors.StartLime, contentColor = FttColors.OnLime),
                    ) {
                        // v1.16.4: black icon (was grey, poor contrast on lime).
                        Image(painterResource(Res.drawable.ic_edit), null, Modifier.size(16.dp), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(FttColors.OnLime))
                        Spacer(Modifier.width(6.dp))
                        Text(ReviewCopy.CHAT, style = FttType.subheadline())
                    }
                }
            } else {
                Label(ReviewCopy.CHOOSE_RECIPIENT)
                val active = pendingRequests > 0
                Row(
                    Modifier.fillMaxWidth().height(40.dp)
                        .background(if (active) FttColors.StartLime else Color(0xFFE5E5EA), RoundedCornerShape(8.dp))
                        .then(if (active) Modifier.border(1.dp, FttColors.OnLime, RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClick = onReview) else Modifier)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(app.freetotake.domain.publish.MyPublicationsRules.pendingLabel(pendingRequests), style = FttType.subheadline(), modifier = Modifier.weight(1f))
                    if (active) Text("›", style = FttType.title1Bold().copy(fontSize = 20.sp))
                }
            }
            Box(Modifier.padding(top = 12.dp).fillMaxWidth().aspectRatio(320f / 240f).clip(RoundedCornerShape(12.dp)).background(FttColors.Surface)) { photo(Modifier.fillMaxSize()) }
            Label("Pickup date and time")
            Text(listOfNotNull(scheduleDate, scheduleTime).joinToString("  ·  ").ifEmpty { "—" }, style = FttType.subheadline().copy(fontSize = 13.sp))
            Label("Meetup point")
            Row(Modifier.background(FttColors.Surface, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(FttColors.StartLime, CircleShape).border(1.dp, FttColors.OnLime, CircleShape))
                Text(meetup, style = FttType.subheadline().copy(fontSize = 13.sp), modifier = Modifier.padding(start = 8.dp))
            }
            Label("Description")
            Text(description.ifBlank { "—" }, style = FttType.body(), color = FttColors.TextSecondary)
            if (canEdit) Text(
                "Edit publication", style = FttType.subheadlineBold(),
                modifier = Modifier.padding(top = 16.dp).clickable(role = Role.Button, onClick = onEdit),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
    message?.let {
        Text(it, style = FttType.subheadline(), color = Color.White, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp)
            .background(Color(0xE6333333), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 8.dp))
    }
    }
}

@Composable
private fun Label(text: String) = Text(text, style = FttType.subheadlineBold().copy(fontSize = 13.sp), modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))

/** Figma "Start approval process" — shown the first time only. */
@Composable
fun ReviewIntroScreen(onContinue: () -> Unit, onBack: () -> Unit) {
    app.freetotake.ui.platform.PlatformBackHandler(enabled = true, onBack = onBack)
    Column(Modifier.fillMaxSize().background(FttColors.BackgroundPrimary).statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp)) {
        Column(
            Modifier.weight(1f).padding(top = 40.dp, bottom = 16.dp).fillMaxWidth().background(FttColors.Surface, RoundedCornerShape(16.dp)).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Image(painterResource(Res.drawable.success_mascot), null, Modifier.fillMaxWidth(0.85f).aspectRatio(1312f / 1199f).weight(1f, fill = false))
            Spacer(Modifier.height(24.dp))
            Text(ReviewCopy.INTRO_TITLE, style = FttType.title1Bold(), textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(ReviewCopy.INTRO_BODY, style = FttType.subheadline(), color = FttColors.TextSecondary, textAlign = TextAlign.Center)
        }
        LimeBar(Modifier.padding(bottom = 12.dp)) { Text(ReviewCopy.CONTINUE, style = FttType.bodyBold(), modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onContinue).padding(vertical = 14.dp), textAlign = TextAlign.Center) }
    }
}

@Composable
private fun LimeBar(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxWidth().height(50.dp).background(FttColors.StartLime, RoundedCornerShape(10.dp)).border(1.dp, FttColors.OnLime, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { content() }
}

/**
 * Figma "Candidate card" + approve/reject animations: swipe right / heart = approve (tilts right, "Approved"
 * stamp), swipe left / X = reject (tilts left, "Rejected" stamp). Bottom bar "1 out of N" with arrows.
 */
@Composable
fun CandidateReviewScreen(
    session: ReviewSession,
    busy: Boolean,
    onSession: (ReviewSession) -> Unit,
    onApprove: (Candidate) -> Unit,
    onReject: (Candidate) -> Unit,
    onBack: () -> Unit,
) {
    app.freetotake.ui.platform.PlatformBackHandler(enabled = true, onBack = onBack)
    val scope = rememberCoroutineScope()
    val candidate = session.current
    Column(Modifier.fillMaxSize().background(FttColors.BackgroundPrimary).statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clickable(role = Role.Button, onClickLabel = "Back", onClick = onBack), contentAlignment = Alignment.CenterStart) { BackChevron() }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp)) {
            val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
            if (candidate != null) {
                val offsetX = remember(candidate.id) { Animatable(0f) }
                fun fly(decision: SwipeRules.Decision) {
                    scope.launch {
                        offsetX.animateTo(if (decision == SwipeRules.Decision.APPROVE) widthPx * 1.4f else -widthPx * 1.4f, tween(350))
                        if (decision == SwipeRules.Decision.APPROVE) onApprove(candidate) else onReject(candidate)
                    }
                }
                val drag = offsetX.value
                Column(
                    Modifier.fillMaxSize()
                        .offset { IntOffset(drag.roundToInt(), 0) }
                        .rotate(drag / widthPx * 12f)
                        .background(FttColors.Surface, RoundedCornerShape(16.dp))
                        .pointerInput(candidate.id, busy) {
                            if (busy) return@pointerInput
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    when (val d = SwipeRules.decide(offsetX.value, widthPx)) {
                                        SwipeRules.Decision.NONE -> scope.launch { offsetX.animateTo(0f, tween(200)) }
                                        else -> fly(d)
                                    }
                                },
                                onHorizontalDrag = { change, dx -> change.consume(); scope.launch { offsetX.snapTo(offsetX.value + dx) } },
                            )
                        }
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(Modifier.fillMaxWidth().height(48.dp)) {
                        if (drag > 20f) Stamp(ReviewCopy.APPROVED_STAMP, FttColors.StartLime, Color.Black, -12f, Modifier.align(Alignment.CenterEnd).alpha((drag / (widthPx * SwipeRules.THRESHOLD)).coerceIn(0f, 1f)))
                        if (drag < -20f) Stamp(ReviewCopy.REJECTED_STAMP, Color.White, Rejected, -12f, Modifier.align(Alignment.CenterStart).alpha((-drag / (widthPx * SwipeRules.THRESHOLD)).coerceIn(0f, 1f)))
                    }
                    Avatar(candidate.avatarUrl, candidate.exampleAvatar, 100.dp)
                    Text("@${candidate.nickname}", style = FttType.subheadline(), color = FttColors.LabelSecondary, modifier = Modifier.padding(top = 8.dp))
                    Text(ReviewCopy.WHY, style = FttType.subheadlineBold().copy(fontSize = 13.sp), modifier = Modifier.padding(top = 24.dp, bottom = 12.dp))
                    Note(candidate.note.ifBlank { "—" })
                    Row(Modifier.padding(top = 32.dp), horizontalArrangement = Arrangement.spacedBy(72.dp)) {
                        RoundAction("Reject", enabled = !busy, onClick = { fly(SwipeRules.Decision.REJECT) }) { XGlyph() }
                        RoundAction("Approve", enabled = !busy, onClick = { fly(SwipeRules.Decision.APPROVE) }) { HeartGlyph() }
                    }
                }
            }
        }
        LimeBar(Modifier.padding(bottom = 12.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("‹", style = FttType.title1Bold(), color = if (session.canPrev) FttColors.Ink else FttColors.Ink.copy(alpha = 0.33f),
                    modifier = Modifier.clickable(enabled = session.canPrev, role = Role.Button, onClickLabel = "Previous request") { onSession(session.prev()) }.padding(horizontal = 12.dp))
                Text(session.counter, style = FttType.bodyBold(), textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                Text("›", style = FttType.title1Bold(), color = if (session.canNext) FttColors.Ink else FttColors.Ink.copy(alpha = 0.33f),
                    modifier = Modifier.clickable(enabled = session.canNext, role = Role.Button, onClickLabel = "Next request") { onSession(session.next()) }.padding(horizontal = 12.dp))
            }
        }
    }
}

/** "Why do you need this?" answer, collapsed to a few lines with "more". */
@Composable
private fun Note(text: String) {
    var expanded by remember(text) { mutableStateOf(false) }
    var overflows by remember(text) { mutableStateOf(false) }
    Text(
        text, style = FttType.body(), color = FttColors.TextSecondary, textAlign = TextAlign.Center,
        maxLines = if (expanded) Int.MAX_VALUE else ReviewCopy.NOTE_COLLAPSED_LINES, overflow = TextOverflow.Ellipsis,
        onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
    )
    if (overflows || expanded) Text(
        if (expanded) "Show less" else "more", style = FttType.subheadlineBold(),
        modifier = Modifier.padding(top = 4.dp).clickable(role = Role.Button) { expanded = !expanded },
    )
}

@Composable
private fun Stamp(text: String, bg: Color, fg: Color, angle: Float, modifier: Modifier) {
    Text(
        text, style = FttType.title1Bold().copy(fontWeight = FontWeight.ExtraBold), color = if (bg == Color.White) fg else Color.Black,
        modifier = modifier.rotate(angle).background(bg, RoundedCornerShape(8.dp)).border(3.dp, if (bg == Color.White) fg else Color.Black, RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 4.dp),
    )
}

@Composable
private fun RoundAction(label: String, enabled: Boolean, onClick: () -> Unit, glyph: @Composable () -> Unit) {
    Box(
        Modifier.size(60.dp).border(2.dp, FttColors.Ink, CircleShape).clip(CircleShape).background(FttColors.Surface)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { glyph() }
}

@Composable
private fun XGlyph() {
    Canvas(Modifier.size(24.dp)) {
        val s = 4.dp.toPx()
        drawLine(Rejected, Offset(0f, 0f), Offset(size.width, size.height), s, cap = StrokeCap.Round)
        drawLine(Rejected, Offset(size.width, 0f), Offset(0f, size.height), s, cap = StrokeCap.Round)
    }
}

@Composable
private fun HeartGlyph() {
    Canvas(Modifier.size(28.dp)) {
        val w = size.width; val h = size.height
        val p = Path().apply {
            moveTo(w * 0.5f, h * 0.88f)
            cubicTo(w * 0.1f, h * 0.6f, w * 0.02f, h * 0.38f, w * 0.1f, h * 0.22f)
            cubicTo(w * 0.2f, h * 0.04f, w * 0.44f, h * 0.06f, w * 0.5f, h * 0.28f)
            cubicTo(w * 0.56f, h * 0.06f, w * 0.8f, h * 0.04f, w * 0.9f, h * 0.22f)
            cubicTo(w * 0.98f, h * 0.38f, w * 0.9f, h * 0.6f, w * 0.5f, h * 0.88f)
            close()
        }
        drawPath(p, FttColors.StartLime); drawPath(p, FttColors.OnLime, style = Stroke(2.dp.toPx()))
    }
}

/** Local photo for the example publication. */
@Composable
fun ExamplePhoto(res: DrawableResource, modifier: Modifier) = Image(painterResource(res), null, modifier, contentScale = ContentScale.Crop)
