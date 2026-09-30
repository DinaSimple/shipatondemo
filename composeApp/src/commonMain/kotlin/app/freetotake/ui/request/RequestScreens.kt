// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.ui.request

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.freetotake.domain.model.ClaimStatus
import app.freetotake.domain.model.Item
import app.freetotake.domain.request.DateKey
import app.freetotake.domain.request.LocalNow
import app.freetotake.domain.request.PickupSlot
import app.freetotake.domain.request.PickupStatusPolicy
import app.freetotake.domain.request.RequestDraft
import app.freetotake.domain.request.RequestRules
import app.freetotake.domain.request.SlotPicker
import app.freetotake.resources.Res
import app.freetotake.resources.ic_edit
import app.freetotake.resources.empty_mascot
import app.freetotake.resources.ic_result_place
import app.freetotake.resources.ic_share
import app.freetotake.resources.success_mascot
import app.freetotake.ui.home.demoImageFor
import app.freetotake.ui.theme.BackChevron
import app.freetotake.ui.theme.TrashButton
import app.freetotake.ui.platform.RemotePhoto
import app.freetotake.ui.home.BadgeChip
import app.freetotake.domain.catalog.ExampleListing
import app.freetotake.domain.catalog.CardState
import app.freetotake.ui.theme.FttColors
import app.freetotake.ui.theme.FttType
import org.jetbrains.compose.resources.painterResource

/** Design tokens for this flow (Figma "Request submission"). */
private object Req {
    val Side = 16.dp                          // side margin, same on every device width
    val Selected = FttColors.StartLime        // v1.16.4: chosen date/time = same solid lime as the "Available" tag
    val Unavailable = Color(0xFFE5E5EA)       // publisher did not offer this day/time
    val UnavailableText = Color(0xFFAEAEB2)
    val Error = Color(0xFFD70015)
}

/** A request the user already sent for this item (read-only details, "My claims"). */
data class SubmittedRequest(val slot: PickupSlot?, val note: String, val state: CardState, val submittedLabel: String?)

/**
 * Giveaway details + request form (spec 0.8). Layout is fluid: 16dp side margins, the week strip is 7 equal
 * cells and times wrap in rows of 4 equal cells — everything fits the device width (no clipped chips).
 * [slots] null = still loading.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun GiveawayDetailsScreen(
    item: Item,
    /** v1.16.5: opened from My claims → trash in the header; from Available Giveaways → heart on the photo (no trash). */
    fromMyClaims: Boolean = false,
    favorite: Boolean = false,
    onHeart: () -> Unit = {},
    /** v1.16.5: giveaway owner row with Chat (mocked for the Free food example in My claims). */
    owner: OwnerRow? = null,
    meetupText: String,
    slots: List<PickupSlot>?,
    now: LocalNow,
    draft: RequestDraft,
    submitted: SubmittedRequest?,
    sending: Boolean,
    error: String?,
    onDraft: (RequestDraft) -> Unit,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onMap: () -> Unit,
    onSend: () -> Unit,
    /** Sent request: trash replaces share in the header (Figma "Card where I submitted my request"). */
    onRemove: () -> Unit = {},
    /** v1.14: approved / finished request → chat with the publisher. */
    onChat: (() -> Unit)? = null,
) {
    val readOnly = submitted != null
    val shownDate = submitted?.slot?.date ?: draft.date
    val shownTime = submitted?.slot?.time ?: draft.time
    // v1.14: picking a date scrolls "Choose time" into view (it sat below the fold behind the Send button).
    val timeSection = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    LaunchedEffect(draft.date) { if (draft.date != null && !readOnly) runCatching { timeSection.bringIntoView() } }
    Column(Modifier.fillMaxSize().background(FttColors.BackgroundPrimary).statusBarsPadding().imePadding()) {
        // Header: back · centered title · share
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clickable(role = Role.Button, onClickLabel = "Back", onClick = onBack), contentAlignment = Alignment.Center) { BackChevron() }
            Text(
                item.title, style = FttType.bodyBold(), maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
            )
            if (readOnly && fromMyClaims) { if (submitted?.state?.showTrash == true) TrashButton(onRemove, Modifier.size(44.dp)) else Box(Modifier.size(44.dp)) }
            else Box(Modifier.size(44.dp).clickable(role = Role.Button, onClickLabel = "Share", onClick = onShare), contentAlignment = Alignment.Center) {
                Image(painterResource(Res.drawable.ic_share), "Share", Modifier.size(24.dp))
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Req.Side),
        ) {
            submitted?.submittedLabel?.let {
                Text(it, style = FttType.subheadline().copy(fontSize = 13.sp), color = FttColors.LabelSecondary, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
            }
            if (ExampleListing.isExample(item)) Text(
                ExampleListing.BANNER, style = FttType.subheadlineBold(),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    .background(FttColors.StartLime, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
                color = FttColors.OnLime,
            )
            Box {
                Photo(item)
                // Available Giveaways: like only (you can't remove someone else's giveaway).
                if (!fromMyClaims) app.freetotake.ui.theme.HeartButton(favorite, onHeart, Modifier.align(Alignment.TopEnd).padding(6.dp))
            }
            submitted?.let { s -> Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { s.state.badges.forEach { BadgeChip(it) } } }
            if (owner != null && readOnly) {
                Label(app.freetotake.domain.chat.ExampleOwner.LABEL)
                OwnerChatRow(owner)
            }

            Label("Description")
            Description(item.description.ifBlank { "No description." })

            Label("Meetup point")
            MeetupPoint(meetupText, onMap)

            Label("Choose pickup date")
            // Read-only (already sent): show the week of the chosen slot even if it is in the past.
            val pickSlots = slots?.let { it + listOfNotNull(submitted?.slot) }
            val pickNow = if (readOnly) LocalNow(DateKey(1970, 1, 1), now.time) else now
            when {
                pickSlots == null -> Text("Loading availability…", style = FttType.subheadline(), color = FttColors.LabelSecondary)
                SlotPicker.upcoming(pickSlots, pickNow).isEmpty() ->
                    Text("No pickup times left for this giveaway.", style = FttType.subheadline(), color = FttColors.LabelSecondary)
                else -> {
                    // Read-only: only the chosen date/time stay lime, everything else grey.
                    DateCarousel(SlotPicker.dates(pickSlots, pickNow).map { it.date to (it.enabled && !readOnly) }, shownDate) { d ->
                        if (readOnly) return@DateCarousel
                        val keepTime = draft.time?.takeIf { t -> SlotPicker.times(pickSlots, d, pickNow).any { it.time == t && it.enabled } }
                        onDraft(SlotPicker.preselect(pickSlots, pickNow, draft.copy(date = d, time = keepTime)))
                    }
                    Column(Modifier.bringIntoViewRequester(timeSection)) {
                    Label("Choose time")
                    val times = SlotPicker.times(pickSlots, shownDate, pickNow)
                    TimeGrid(times.map { it.time.label() to (it.enabled && !readOnly) }, shownTime?.label()) { label ->
                        if (readOnly) return@TimeGrid
                        times.firstOrNull { it.time.label() == label }?.let { onDraft(draft.copy(time = it.time)) }
                    }
                    Spacer(Modifier.height(8.dp))
                    }
                }
            }

            Label("Why do you need this?")
            NoteField(submitted?.note ?: draft.note, readOnly) { onDraft(draft.copy(note = it.take(RequestRules.NOTE_MAX))) }

            error?.let { Text(it, style = FttType.subheadline(), color = Req.Error, modifier = Modifier.padding(top = 12.dp)) }
            Spacer(Modifier.height(24.dp))
        }
        if (!readOnly) {
            val enabled = RequestRules.canSend(draft) && !sending
            Button(
                onClick = onSend, enabled = enabled,
                modifier = Modifier.padding(horizontal = Req.Side).padding(bottom = 12.dp).navigationBarsPadding().fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, FttColors.OnLime),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FttColors.StartLime, contentColor = FttColors.OnLime,
                    disabledContainerColor = FttColors.StartLime, disabledContentColor = Color(0x80000000),
                ),
            ) { Text(if (sending) "Sending…" else "Send request", style = FttType.bodyBold()) }
        } else if (onChat != null) {
            Button(
                onClick = onChat,
                modifier = Modifier.padding(horizontal = Req.Side).padding(bottom = 12.dp).navigationBarsPadding().fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, FttColors.OnLime),
                colors = ButtonDefaults.buttonColors(containerColor = FttColors.StartLime, contentColor = FttColors.OnLime),
            ) { Text(app.freetotake.domain.chat.ChatCopy.CHAT, style = FttType.bodyBold()) }
        } else Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun Label(text: String) =
    Text(text, style = FttType.subheadlineBold().copy(fontSize = 13.sp), modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))

@Composable
private fun Photo(item: Item) {
    Box(
        Modifier.fillMaxWidth().aspectRatio(380f / 275f).clip(RoundedCornerShape(12.dp)).background(FttColors.Surface),
        contentAlignment = Alignment.Center,
    ) {
        val demo = demoImageFor(item)
        val photo = item.photoUrls.firstOrNull()
        val nothing: @Composable () -> Unit = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Image(painterResource(Res.drawable.empty_mascot), null, Modifier.size(width = 101.dp, height = 90.dp))
                Text(app.freetotake.domain.catalog.HomeCopy.EMPTY, style = FttType.subheadline(), color = FttColors.LabelSecondary)
            }
        }
        if (demo != null) Image(painterResource(demo), item.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else if (photo != null) RemotePhoto(photo, Modifier.fillMaxSize(), nothing)
        else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Image(painterResource(Res.drawable.empty_mascot), null, Modifier.size(width = 101.dp, height = 90.dp))
            Text(app.freetotake.domain.catalog.HomeCopy.EMPTY, style = FttType.subheadline(), color = FttColors.LabelSecondary)
        }
    }
}

/** Collapsed to 3 lines with an inline "more" control when the text overflows. */
@Composable
private fun Description(text: String) {
    var expanded by remember { mutableStateOf(false) }
    var overflows by remember { mutableStateOf(false) }
    Column {
        Text(
            text, style = FttType.body(), color = FttColors.TextSecondary,
            maxLines = if (expanded) Int.MAX_VALUE else RequestRules.DESCRIPTION_COLLAPSED_LINES,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
        )
        if (overflows || expanded) Text(
            if (expanded) "Show less" else "more", style = FttType.subheadlineBold(),
            modifier = Modifier.padding(top = 2.dp).clickable(role = Role.Button) { expanded = !expanded },
        )
    }
}

@Composable
private fun MeetupPoint(text: String, onMap: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(FttColors.Surface, RoundedCornerShape(10.dp)).padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).background(FttColors.StartLime, CircleShape).border(1.dp, FttColors.OnLime, CircleShape))
        Text(text, style = FttType.subheadline().copy(fontSize = 13.sp), modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
        Box(Modifier.size(36.dp).clickable(role = Role.Button, onClickLabel = "Open in maps", onClick = onMap), contentAlignment = Alignment.Center) {
            Image(painterResource(Res.drawable.ic_result_place), "Open in maps", Modifier.size(20.dp))
        }
    }
}

/**
 * v1.16.2: one row, horizontal carousel. Cell width = 1/7 of the content width, so a full week fits and the rest scrolls.
 * Available = white; not offered / past = grey fill; selected = lime.
 */
@Composable
private fun DateCarousel(days: List<Pair<DateKey, Boolean>>, selected: DateKey?, onPick: (DateKey) -> Unit) {
    val gap = 6.dp
    val list = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(selected, days.size) {
        val i = days.indexOfFirst { it.first == selected }.takeIf { it >= 0 } ?: days.indexOfFirst { it.second }
        if (i > 0) list.scrollToItem((i - 3).coerceAtLeast(0))
    }
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cell = (maxWidth - gap * 6) / 7
        androidx.compose.foundation.lazy.LazyRow(state = list, horizontalArrangement = Arrangement.spacedBy(gap)) {
            items(days.size, key = { days[it].first.toString() }) { i ->
                val (d, enabled) = days[i]
                Cell(Modifier.width(cell).height(60.dp), enabled, d == selected, { onPick(d) }) { color, weight ->
                    Text(DateKey.WEEKDAY_SHORT[d.dayOfWeek], style = FttType.subheadline().copy(fontSize = 13.sp, fontWeight = weight), color = color, maxLines = 1)
                    Text("${d.day}", style = FttType.bodyBold().copy(fontWeight = weight), color = color)
                }
            }
        }
    }
}

/** Times in rows of 4 equal cells. */
@Composable
private fun TimeGrid(times: List<Pair<String, Boolean>>, selected: String?, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        times.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { (label, enabled) ->
                    Cell(Modifier.weight(1f).height(40.dp), enabled, label == selected, { onPick(label) }) { color, weight ->
                        Text(label, style = FttType.subheadline().copy(fontWeight = weight), color = color, maxLines = 1)
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Available = white + bold; unavailable = grey, not tappable; selected = light lime + black border. */
@Composable
private fun Cell(
    modifier: Modifier, enabled: Boolean, selected: Boolean, onClick: () -> Unit,
    content: @Composable (Color, FontWeight) -> Unit,
) {
    val bg = when { selected -> Req.Selected; enabled -> FttColors.Surface; else -> Req.Unavailable }
    val base = modifier.clip(RoundedCornerShape(10.dp)).background(bg)
    Column(
        (if (selected) base.border(1.5.dp, FttColors.OnLime, RoundedCornerShape(10.dp)) else base)
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        content(if (enabled || selected) FttColors.Ink else Req.UnavailableText, if (enabled || selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun NoteField(value: String, readOnly: Boolean, onChange: (String) -> Unit) {
    Box(Modifier.fillMaxWidth().heightIn(min = 56.dp).background(FttColors.Surface, RoundedCornerShape(10.dp)).padding(horizontal = 16.dp, vertical = 12.dp)) {
        if (value.isEmpty() && !readOnly) Text(RequestRules.NOTE_PLACEHOLDER, style = FttType.subheadline().copy(fontSize = 13.sp), color = Req.UnavailableText)
        BasicTextField(
            value = value, onValueChange = onChange, readOnly = readOnly,
            textStyle = FttType.subheadline().copy(fontSize = 13.sp, color = FttColors.Ink),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(FttColors.Ink),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (!readOnly && value.length > RequestRules.NOTE_MAX - 100) {
        Text("${value.length}/${RequestRules.NOTE_MAX}", style = FttType.caption(), color = FttColors.LabelSecondary, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun StatusBadge(status: ClaimStatus, modifier: Modifier = Modifier) {
    val approved = PickupStatusPolicy.canCollect(status)
    Text(
        PickupStatusPolicy.label(status), style = FttType.caption().copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
        color = FttColors.OnLime,
        modifier = modifier.background(if (approved) FttColors.StartLime else FttColors.Yellow, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Guest pressed Send request (Figma "Login modal not authenticated user"). */
@Composable
fun LoginSheet(onContinue: () -> Unit, onNextTime: () -> Unit, title: String = RequestRules.LOGIN_SHEET_TITLE) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).clickable(onClick = onNextTime)) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(FttColors.BackgroundPrimary, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .clickable(enabled = false) {}.navigationBarsPadding().padding(horizontal = Req.Side).padding(top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(width = 36.dp, height = 5.dp).background(Color(0x4D3C3C43), RoundedCornerShape(3.dp)))
            Text(title, style = FttType.title1Bold().copy(fontSize = 20.sp), modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            Button(
                onClick = onContinue, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, FttColors.OnLime),
                colors = ButtonDefaults.buttonColors(containerColor = FttColors.StartLime, contentColor = FttColors.OnLime),
            ) { Text("Continue", style = FttType.body().copy(fontWeight = FontWeight.Medium)) }
            Button(
                onClick = onNextTime, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, FttColors.Ink),
                colors = ButtonDefaults.buttonColors(containerColor = FttColors.BackgroundPrimary, contentColor = Color(0xFF3C3C43)),
            ) { Text("Next time", style = FttType.body().copy(fontWeight = FontWeight.Medium)) }
        }
    }
}

/** Figma "Success screen after request button clicked". */
@Composable
fun RequestSentScreen(onGoHome: () -> Unit) =
    SuccessScreen(RequestRules.SENT_TITLE, RequestRules.SENT_BODY, RequestRules.GO_HOME, onGoHome)

/** Mascot card + title + body + one lime button (request sent, publication live). */
@Composable
fun SuccessScreen(title: String, body: String, button: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(FttColors.BackgroundPrimary).statusBarsPadding().navigationBarsPadding().padding(horizontal = Req.Side),
    ) {
        Column(
            Modifier.weight(1f).padding(top = 40.dp, bottom = 24.dp).fillMaxWidth().background(FttColors.Surface, RoundedCornerShape(16.dp)).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(painterResource(Res.drawable.success_mascot), null, Modifier.fillMaxWidth(0.85f).aspectRatio(1312f / 1199f).weight(1f, fill = false))
            Spacer(Modifier.height(24.dp))
            Text(title, style = FttType.title1Bold(), textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(body, style = FttType.subheadline(), color = FttColors.TextSecondary, textAlign = TextAlign.Center)
        }
        Button(
            onClick = onClick, modifier = Modifier.padding(bottom = 12.dp).fillMaxWidth().height(50.dp), shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, FttColors.OnLime),
            colors = ButtonDefaults.buttonColors(containerColor = FttColors.StartLime, contentColor = FttColors.OnLime),
        ) { Text(button, style = FttType.bodyBold()) }
    }
}

/** v1.16.5: requester-side mirror of "Pending collection from" (Figma "My card where recipient is assigned"). */
data class OwnerRow(val nickname: String, val exampleAvatar: Int?, val onChat: () -> Unit)

@Composable
private fun OwnerChatRow(owner: OwnerRow) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).background(FttColors.Surface, RoundedCornerShape(10.dp)).padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            app.freetotake.ui.publish.Avatar(null, owner.exampleAvatar, 32.dp)
            Text("@${owner.nickname}", style = FttType.subheadline(), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 10.dp))
        }
        Spacer(Modifier.width(12.dp))
        Button(
            onClick = owner.onChat, modifier = Modifier.height(42.dp), shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, FttColors.OnLime),
            colors = ButtonDefaults.buttonColors(containerColor = FttColors.StartLime, contentColor = FttColors.OnLime),
        ) {
            Image(painterResource(Res.drawable.ic_edit), null, Modifier.size(16.dp), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(FttColors.OnLime))
            Spacer(Modifier.width(6.dp))
            Text(app.freetotake.domain.chat.ChatCopy.CHAT, style = FttType.subheadline())
        }
    }
}
