// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Dina Elokhova (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.ui.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.freetotake.domain.chat.ChatCopy
import app.freetotake.domain.chat.ChatItem
import app.freetotake.domain.chat.ChatRules
import app.freetotake.ui.publish.Avatar
import app.freetotake.ui.theme.BackChevron
import app.freetotake.ui.theme.FttColors
import app.freetotake.ui.theme.FttType

/**
 * Figma "Chat and chat history": empty, active conversation and closed (read-only) states.
 * The attachment button is shown as in the design but photos are out of scope (tapping explains it).
 */
@Composable
fun ChatScreen(
    title: String,
    otherName: String?,
    timeline: List<ChatItem>,
    canSend: Boolean,
    loading: Boolean,
    error: String?,
    otherAvatar: @Composable () -> Unit,
    myAvatar: @Composable () -> Unit,
    onSend: (String) -> Unit,
    onBack: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var note by remember { mutableStateOf<String?>(null) }
    val list = rememberLazyListState()
    LaunchedEffect(timeline.size) { if (timeline.isNotEmpty()) list.animateScrollToItem(timeline.lastIndex) }
    LaunchedEffect(note) { if (note != null) { kotlinx.coroutines.delay(2_500); note = null } }

    Column(Modifier.fillMaxSize().background(FttColors.Surface).statusBarsPadding().imePadding()) {
        // Header: back · "Chat about {ad}" · "Chat with @name"
        Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp)) {
            Box(Modifier.align(Alignment.TopStart).padding(start = 8.dp).size(40.dp).clickable(role = Role.Button, onClickLabel = "Back", onClick = onBack), contentAlignment = Alignment.Center) { BackChevron() }
            Column(Modifier.align(Alignment.TopCenter).padding(horizontal = 56.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(ChatCopy.title(title), style = FttType.subheadlineBold(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (otherName != null) Text(ChatCopy.subtitle(otherName), style = FttType.caption().copy(fontSize = 12.sp), color = FttColors.LabelSecondary, modifier = Modifier.padding(top = 6.dp))
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (timeline.isEmpty() && !loading) EmptyChat(Modifier.align(Alignment.Center))
            else LazyColumn(
                state = list, modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Bottom),
            ) {
                items(timeline, key = { when (it) { is ChatItem.Day -> "d-" + it.label; is ChatItem.Message -> it.message.id } }) { item ->
                    when (item) {
                        is ChatItem.Day -> Text(item.label, style = FttType.caption().copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
                        is ChatItem.Message -> Bubble(item.message.body, item.message.mine, if (item.message.mine) myAvatar else otherAvatar)
                    }
                }
            }
        }
        (error ?: note)?.let { Text(it, style = FttType.caption().copy(fontSize = 12.sp), color = Color(0xFFFF3B30), modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
        if (canSend) Composer(
            text = text, onText = { if (it.length <= ChatRules.MAX_LENGTH) text = it },
            onAttach = { note = ChatCopy.ATTACH_LATER },
            onSend = { ChatRules.normalized(text)?.let { onSend(it); text = "" } },
        ) else Text(
            ChatCopy.CLOSED, style = FttType.subheadline().copy(fontSize = 13.sp), color = FttColors.LabelSecondary, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)
                .background(FttColors.BackgroundPrimary, RoundedCornerShape(12.dp)).padding(12.dp),
        )
    }
}

@Composable
private fun EmptyChat(modifier: Modifier) {
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(40.dp).background(FttColors.StartLime.copy(alpha = 0.5f), CircleShape), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(18.dp)) {
                val w = size.width; val h = size.height
                drawRoundRect(FttColors.OnLime, size = androidx.compose.ui.geometry.Size(w, h * 0.78f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()), style = Stroke(1.4.dp.toPx()))
                drawLine(FttColors.OnLime, Offset(w * 0.3f, h * 0.78f), Offset(w * 0.22f, h), 1.4.dp.toPx(), cap = StrokeCap.Round)
            }
        }
        Text(ChatCopy.EMPTY_TITLE, style = FttType.subheadlineBold(), modifier = Modifier.padding(top = 12.dp))
        Text(ChatCopy.EMPTY_TEXT, style = FttType.caption().copy(fontSize = 12.sp), color = FttColors.LabelSecondary, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun Bubble(body: String, mine: Boolean, avatar: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!mine) { avatar(); Spacer(Modifier.size(8.dp)) }
        Text(
            body, style = FttType.subheadline().copy(fontSize = 13.sp, lineHeight = 17.sp),
            modifier = Modifier.widthIn(max = 240.dp)
                .background(FttColors.BackgroundPrimary, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
        )
        if (mine) { Spacer(Modifier.size(8.dp)); avatar() }
    }
}

@Composable
private fun Composer(text: String, onText: (String) -> Unit, onAttach: () -> Unit, onSend: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Attachment (design only — photos are out of scope).
        Box(Modifier.size(36.dp).background(FttColors.StartLime, CircleShape).clickable(role = Role.Button, onClickLabel = "Attach", onClick = onAttach), contentAlignment = Alignment.Center) {
            Text("+", style = FttType.title1Bold().copy(fontSize = 22.sp, lineHeight = 22.sp), color = FttColors.OnLime)
        }
        Spacer(Modifier.size(8.dp))
        Row(
            Modifier.weight(1f).heightIn(min = 40.dp).background(FttColors.BackgroundPrimary, RoundedCornerShape(20.dp)).padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f).padding(vertical = 8.dp)) {
                if (text.isEmpty()) Text(ChatCopy.PLACEHOLDER, style = FttType.subheadline().copy(fontSize = 13.sp), color = FttColors.LabelSecondary)
                BasicTextField(
                    text, onText, maxLines = 5,
                    textStyle = FttType.subheadline().copy(fontSize = 13.sp, color = FttColors.Ink),
                    cursorBrush = SolidColor(FttColors.Ink),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            val canSend = ChatRules.normalized(text) != null
            Box(
                Modifier.size(32.dp).then(if (canSend) Modifier.background(FttColors.Ink, CircleShape).clickable(role = Role.Button, onClickLabel = "Send", onClick = onSend) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                if (canSend) Canvas(Modifier.size(14.dp)) {
                    val w = size.width; val h = size.height; val c = FttColors.Surface
                    drawLine(c, Offset(w / 2, h), Offset(w / 2, 0f), 2.dp.toPx(), cap = StrokeCap.Round)
                    drawPath(Path().apply { moveTo(w * 0.1f, h * 0.45f); lineTo(w / 2, 0f); lineTo(w * 0.9f, h * 0.45f) }, c, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                } else Canvas(Modifier.size(16.dp)) {   // mic glyph (design)
                    val w = size.width; val h = size.height; val c = FttColors.LabelSecondary
                    drawRoundRect(c, topLeft = Offset(w * 0.32f, 0f), size = androidx.compose.ui.geometry.Size(w * 0.36f, h * 0.62f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.18f), style = Stroke(1.4.dp.toPx()))
                    drawArc(c, 0f, 180f, false, topLeft = Offset(w * 0.15f, h * 0.3f), size = androidx.compose.ui.geometry.Size(w * 0.7f, h * 0.5f), style = Stroke(1.4.dp.toPx()))
                    drawLine(c, Offset(w / 2, h * 0.8f), Offset(w / 2, h), 1.4.dp.toPx())
                }
            }
        }
    }
}

/** Small avatar used next to bubbles (design: 24dp). */
@Composable
fun ChatAvatar(url: String?, example: Int? = null) = Avatar(url, example, 24.dp)
