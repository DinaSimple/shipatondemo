// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.ui.publish

import app.freetotake.domain.publish.PublishStep
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.freetotake.domain.catalog.Category
import app.freetotake.domain.catalog.CategoryChip
import app.freetotake.domain.media.ImagePolicy
import app.freetotake.domain.publish.Clock12
import app.freetotake.domain.publish.DateSheet
import app.freetotake.domain.publish.MonthGrid
import app.freetotake.domain.publish.PublicationDraft
import app.freetotake.domain.publish.PublicationRules
import app.freetotake.domain.publish.PublishCopy
import app.freetotake.domain.request.DateKey
import app.freetotake.domain.request.LocalNow
import app.freetotake.domain.request.TimeOfDay
import app.freetotake.resources.Res
import app.freetotake.resources.photo_mascot
import app.freetotake.ui.home.Chip
import app.freetotake.ui.platform.LocalPhoto
import app.freetotake.ui.platform.RemotePhoto
import app.freetotake.domain.publish.StoredPhoto
import app.freetotake.ui.theme.BackChevron
import app.freetotake.ui.theme.FttColors
import app.freetotake.ui.theme.FttType
import org.jetbrains.compose.resources.painterResource

private val Side = 16.dp
private val LightLime = Color(0xFFEFFFA8)
private val Placeholder = Color(0xFFAEAEB2)

// v1.16.9: PublishStep moved to the domain (app.freetotake.domain.publish.PublishStep)

/** Figma "Create your publication" (form → photos). Width-adaptive: 16dp margins, fields fill the width. */
@Composable
fun CreatePublicationScreen(
    step: PublishStep,
    draft: PublicationDraft,
    now: LocalNow,
    publishing: Boolean,
    error: String?,
    onDraft: (PublicationDraft) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onOpenDate: () -> Unit,
    onOpenTime: () -> Unit,
    onOpenLocation: () -> Unit,
    onAddPhoto: () -> Unit,
    onRemovePhoto: (String) -> Unit,
    onPublish: () -> Unit,
    /** Editing an existing publication (spec 0.12): stored photos can be removed; button says Save. */
    onRemoveStoredPhoto: (StoredPhoto) -> Unit = {},
) {
    val editing = draft.editingItemId != null
    Column(Modifier.fillMaxSize().background(FttColors.BackgroundPrimary).statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clickable(role = Role.Button, onClickLabel = "Back", onClick = onBack), contentAlignment = Alignment.Center) { BackChevron() }
            Text(if (editing) "Edit your publication" else PublishCopy.SCREEN_TITLE, style = FttType.bodyBold(), textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Box(Modifier.size(44.dp))
        }
        StepDots(if (step == PublishStep.FORM) 0 else 1)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Side)) {
            if (step == PublishStep.FORM) Form(draft, onDraft, onOpenDate, onOpenTime, onOpenLocation)
            else Photos(draft.photos, draft.storedPhotos, onAddPhoto, onRemovePhoto, onRemoveStoredPhoto)
            error?.let { Text(it, style = FttType.subheadline(), color = Color(0xFFD70015), modifier = Modifier.padding(top = 12.dp)) }
            Spacer(Modifier.height(24.dp))
        }
        val enabled = if (step == PublishStep.FORM) PublicationRules.canContinue(draft, now) else !publishing && PublicationRules.canPublish(draft, now)
        val hint = if (step == PublishStep.FORM) PublicationRules.hint(draft, now) else null
        if (hint != null) Text(
            hint,
            style = FttType.caption().copy(fontSize = 12.sp), color = FttColors.LabelSecondary,
            modifier = Modifier.padding(horizontal = Side).padding(bottom = 6.dp),
        )
        LimeButton(
            text = when { step == PublishStep.FORM -> "Next"; publishing -> if (editing) "Saving…" else "Publishing…"; editing -> "Save"; else -> "Publish" },
            enabled = enabled,
            onClick = if (step == PublishStep.FORM) onNext else onPublish,
            modifier = Modifier.padding(horizontal = Side).padding(bottom = 12.dp).navigationBarsPadding(),
        )
    }
}

@Composable
private fun StepDots(active: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center) {
        repeat(3) { i ->
            Box(
                Modifier.padding(horizontal = 2.dp).height(4.dp).width(if (i == active) 32.dp else 6.dp)
                    .background(if (i == active) FttColors.Ink else Color(0xFFD8D8DC), CircleShape),
            )
        }
    }
}

@Composable
private fun Label(text: String) = Text(text, style = FttType.subheadlineBold(), modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))

@Composable
private fun Form(
    d: PublicationDraft,
    onDraft: (PublicationDraft) -> Unit,
    onOpenDate: () -> Unit,
    onOpenTime: () -> Unit,
    onOpenLocation: () -> Unit,
) {
    Label("Title")
    InputBox(d.title, PublishCopy.TITLE_PLACEHOLDER, singleLine = true) { onDraft(d.copy(title = it.take(PublicationRules.TITLE_MAX))) }
    Label("Description")
    // Shown in full while typing — no expand/collapse (spec 0.11).
    InputBox(d.description, PublishCopy.DESCRIPTION_PLACEHOLDER, minHeight = 100.dp) { onDraft(d.copy(description = it.take(PublicationRules.DESCRIPTION_MAX))) }
    if (d.description.length > PublicationRules.DESCRIPTION_MAX - 100) Counter(d.description.length, PublicationRules.DESCRIPTION_MAX)

    Label("Category")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(Category.entries.toList()) { c ->
            val chip = CategoryChip.Of(c)
            Chip(chip, d.category == c) { onDraft(d.copy(category = c)) }
        }
    }

    Label("Meetup details")
    PickerField(PublishCopy.WHEN_LABEL, d.date?.let { PublicationRules.dateLabel(it) }, PublishCopy.WHEN_PLACEHOLDER, FieldIcon.CALENDAR, onOpenDate)
    Spacer(Modifier.height(8.dp))
    PickerField(PublishCopy.TIME_LABEL, PublicationRules.timeLabel(d), PublishCopy.TIME_PLACEHOLDER, FieldIcon.CLOCK, onOpenTime)
    Spacer(Modifier.height(8.dp))
    PickerField(PublishCopy.LOCATION_LABEL, d.pickup?.displayText(), PublishCopy.LOCATION_PLACEHOLDER, FieldIcon.PIN, onOpenLocation)
    Spacer(Modifier.height(8.dp))

    Label("Meetup notes")
    InputBox(d.notes, "How to find you at the meetup point (optional). Please don’t share apartment, floor or entrance.", minHeight = 64.dp) { onDraft(d.copy(notes = it.take(PublicationRules.NOTES_MAX))) }
}

@Composable
private fun Counter(n: Int, max: Int) =
    Text("$n/$max", style = FttType.caption(), color = FttColors.LabelSecondary, modifier = Modifier.padding(top = 4.dp))

@Composable
private fun InputBox(value: String, placeholder: String, singleLine: Boolean = false, minHeight: androidx.compose.ui.unit.Dp = 36.dp, onChange: (String) -> Unit) {
    Box(Modifier.fillMaxWidth().heightIn(min = minHeight).background(FttColors.Surface, RoundedCornerShape(10.dp)).padding(horizontal = 16.dp, vertical = 10.dp)) {
        if (value.isEmpty()) Text(placeholder, style = FttType.subheadline().copy(fontSize = 13.sp), color = Placeholder)
        BasicTextField(
            value, onChange, singleLine = singleLine,
            textStyle = FttType.subheadline().copy(fontSize = 13.sp, color = FttColors.Ink),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(FttColors.Ink),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

enum class FieldIcon { CALENDAR, CLOCK, PIN }

@Composable
private fun PickerField(label: String, value: String?, placeholder: String, icon: FieldIcon, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(50.dp).background(FttColors.Surface, RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick).padding(start = 16.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = FttType.caption(), color = FttColors.LabelSecondary, maxLines = 1)
            Text(value ?: placeholder, style = FttType.body().copy(fontSize = 15.sp), color = if (value == null) Placeholder else FttColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        FieldGlyph(icon)
    }
}

/** Lime glyphs with black outline (design: calendar, clock, pin). */
@Composable
private fun FieldGlyph(icon: FieldIcon) {
    Canvas(Modifier.size(22.dp)) {
        val w = size.width; val h = size.height; val sw = 1.5.dp.toPx()
        when (icon) {
            FieldIcon.CALENDAR -> {
                drawRoundRect(FttColors.StartLime, Offset(w * 0.08f, h * 0.18f), Size(w * 0.84f, h * 0.74f), CornerRadius(3.dp.toPx()))
                drawRoundRect(FttColors.Ink, Offset(w * 0.08f, h * 0.18f), Size(w * 0.84f, h * 0.74f), CornerRadius(3.dp.toPx()), style = Stroke(sw))
                drawLine(FttColors.Ink, Offset(w * 0.08f, h * 0.4f), Offset(w * 0.92f, h * 0.4f), sw)
                drawLine(FttColors.Ink, Offset(w * 0.3f, h * 0.08f), Offset(w * 0.3f, h * 0.26f), sw)
                drawLine(FttColors.Ink, Offset(w * 0.7f, h * 0.08f), Offset(w * 0.7f, h * 0.26f), sw)
            }
            FieldIcon.CLOCK -> {
                drawCircle(FttColors.StartLime, radius = w * 0.44f)
                drawCircle(FttColors.Ink, radius = w * 0.44f, style = Stroke(sw))
                drawLine(FttColors.Ink, center, Offset(w * 0.5f, h * 0.24f), sw)
                drawLine(FttColors.Ink, center, Offset(w * 0.68f, h * 0.6f), sw)
            }
            FieldIcon.PIN -> {
                val p = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.5f, h * 0.95f)
                    cubicTo(w * 0.1f, h * 0.55f, w * 0.1f, h * 0.05f, w * 0.5f, h * 0.05f)
                    cubicTo(w * 0.9f, h * 0.05f, w * 0.9f, h * 0.55f, w * 0.5f, h * 0.95f)
                    close()
                }
                drawPath(p, FttColors.StartLime); drawPath(p, FttColors.OnLime, style = Stroke(sw))
            }
        }
    }
}

/** Photos step: picked photos, then the lime "add" tile, then empty slots (max 3, optional). */
@Composable
private fun Photos(photos: List<String>, stored: List<StoredPhoto>, onAdd: () -> Unit, onRemove: (String) -> Unit, onRemoveStored: (StoredPhoto) -> Unit) {
    Label(PublishCopy.PHOTOS_LABEL)
    Column(Modifier.fillMaxWidth().background(FttColors.Surface, RoundedCornerShape(12.dp)).padding(16.dp)) {
        Text(PublishCopy.PHOTOS_CARD_TITLE, style = FttType.bodyBold(), modifier = Modifier.padding(bottom = 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            stored.forEach { sp ->
                Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(6.dp))) {
                    RemotePhoto(sp.url, Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize().background(LightLime)) }
                    RemoveBadge(Modifier.align(Alignment.TopEnd)) { onRemoveStored(sp) }
                }
            }
            photos.forEach { uri ->
                Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(6.dp))) {
                    LocalPhoto(uri, Modifier.fillMaxSize())
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp).background(Color.White.copy(alpha = 0.85f), CircleShape)
                            .clickable(role = Role.Button, onClickLabel = "Remove photo") { onRemove(uri) },
                        contentAlignment = Alignment.Center,
                    ) { Text("✕", style = FttType.caption().copy(fontSize = 12.sp, fontWeight = FontWeight.Bold)) }
                }
            }
            // Always tappable: at the limit it shows the "maximum photo limit" alert (design).
            Box(
                Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(6.dp)).background(FttColors.StartLime)
                    .clickable(role = Role.Button, onClickLabel = "Add a photo", onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) { Image(painterResource(Res.drawable.photo_mascot), null, Modifier.fillMaxSize().padding(10.dp), contentScale = ContentScale.Fit) }
            repeat((ImagePolicy.MAX_PHOTOS - photos.size - stored.size - 1).coerceAtLeast(0)) {
                Box(
                    // Same colour as the Publish button (#C8FF00, FttColors.StartLime).
                    Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(6.dp)).background(FttColors.StartLime).clickable(onClick = onAdd),
                    contentAlignment = Alignment.Center,
                ) { CameraGlyph() }
            }
        }
    }
    Text(PublishCopy.PHOTOS_HINT, style = FttType.caption().copy(fontSize = 12.sp), color = FttColors.LabelSecondary, modifier = Modifier.padding(top = 8.dp))
    // Answer Q22: small secondary-grey note about automatic photo deletion.
    Text(PublishCopy.PHOTOS_CLEANUP_NOTE, style = FttType.caption().copy(fontSize = 12.sp), color = FttColors.LabelSecondary, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun RemoveBadge(modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.padding(4.dp).size(24.dp).background(Color.White.copy(alpha = 0.85f), CircleShape)
            .clickable(role = Role.Button, onClickLabel = "Remove photo", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text("✕", style = FttType.caption().copy(fontSize = 12.sp, fontWeight = FontWeight.Bold)) }
}

@Composable
private fun CameraGlyph() {
    Canvas(Modifier.size(40.dp)) {
        val w = size.width; val h = size.height
        val c = Color(0xFF8E9A5B)
        drawRoundRect(c, Offset(0f, h * 0.2f), Size(w, h * 0.75f), CornerRadius(6.dp.toPx()))
        drawRoundRect(c, Offset(w * 0.3f, h * 0.08f), Size(w * 0.4f, h * 0.2f), CornerRadius(3.dp.toPx()))
        drawCircle(FttColors.StartLime, radius = w * 0.14f, center = Offset(w * 0.5f, h * 0.48f))
        drawRoundRect(FttColors.StartLime, Offset(w * 0.28f, h * 0.66f), Size(w * 0.44f, h * 0.2f), CornerRadius(6.dp.toPx()))
    }
}

@Composable
private fun LimeButton(text: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick, enabled = enabled,
        modifier = modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, FttColors.OnLime),
        colors = ButtonDefaults.buttonColors(
            containerColor = FttColors.StartLime, contentColor = FttColors.OnLime,
            disabledContainerColor = FttColors.StartLime, disabledContentColor = Color(0x80000000),
        ),
    ) { Text(text, style = FttType.bodyBold()) }
}

@Composable
private fun CloseText(onClick: () -> Unit) = Text(
    "Close", style = FttType.body(), color = FttColors.LabelSecondary, textAlign = TextAlign.Center,
    modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 14.dp),
)

@Composable
private fun Sheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).clickable(onClick = onDismiss)) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(FttColors.BackgroundPrimary, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .clickable(enabled = false) {}.navigationBarsPadding().padding(horizontal = Side).padding(top = 8.dp, bottom = 8.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(width = 36.dp, height = 5.dp).background(Color(0x4D3C3C43), RoundedCornerShape(3.dp)))
            content()
        }
    }
}

/** Figma "Date picker": Sunday-first month, past days disabled; Apply commits, Close keeps the saved date. */
@Composable
fun DatePickerSheet(initial: DateSheet, today: DateKey, onApply: (DateKey?) -> Unit, onClose: () -> Unit) {
    var sheet by remember { mutableStateOf(initial) }
    Sheet(onClose) {
        Text(PublishCopy.DATE_SHEET_TITLE, style = FttType.title1Bold(), modifier = Modifier.padding(top = 16.dp))
        Text(PublishCopy.DATE_SHEET_SUBTITLE, style = FttType.subheadline(), modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
        Column(Modifier.fillMaxWidth().background(FttColors.Surface, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp))) {
            Row(Modifier.fillMaxWidth().background(LightLime).padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("‹", style = FttType.title1Bold(), modifier = Modifier.clickable(onClickLabel = "Previous month") { sheet = sheet.prevMonth() }.padding(horizontal = 12.dp))
                Text("${PublicationRules.MONTHS[sheet.month.month - 1]} ${sheet.month.year}", style = FttType.bodyBold(), textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                Text("›", style = FttType.title1Bold(), modifier = Modifier.clickable(onClickLabel = "Next month") { sheet = sheet.nextMonth() }.padding(horizontal = 12.dp))
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach {
                    Text(it, style = FttType.subheadline(), textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                }
            }
            MonthGrid.of(sheet.month, today).chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { day ->
                        val selected = day.date == sheet.pending
                        Box(
                            Modifier.weight(1f).height(44.dp).clickable(enabled = day.selectable) { sheet = sheet.pick(day.date) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) Box(Modifier.size(36.dp).background(FttColors.StartLime, CircleShape))
                            Text(
                                day.date.day.toString().padStart(2, '0'),
                                style = FttType.body().copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
                                color = if (day.selectable) FttColors.Ink else Color(0xFFD1D1D6),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        LimeButton("Apply", enabled = sheet.pending != null, onClick = { onApply(sheet.apply()) })
        CloseText { onClose() }
    }
}

/** Figma "Time picker": From / To as hour : minutes (00/30) AM/PM. Apply only for a valid range. */
@Composable
fun TimePickerSheet(from: TimeOfDay?, to: TimeOfDay?, onApply: (TimeOfDay, TimeOfDay) -> Unit, onClose: () -> Unit) {
    var a by remember { mutableStateOf(from?.let { Clock12.of(it) } ?: Clock12(10, 0, pm = false)) }
    var b by remember { mutableStateOf(to?.let { Clock12.of(it) } ?: Clock12(11, 0, pm = false)) }
    val fa = a.to24(); val fb = b.to24()
    val valid = fa != null && fb != null && fa < fb
    Sheet(onClose) {
        Text(PublishCopy.TIME_SHEET_TITLE, style = FttType.title1Bold(), modifier = Modifier.padding(top = 16.dp))
        Text(PublishCopy.TIME_SHEET_SUBTITLE, style = FttType.subheadline(), modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
        Row(
            Modifier.fillMaxWidth().background(FttColors.Surface, RoundedCornerShape(12.dp)).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ClockInput("From", a, Modifier.weight(1f)) { a = it }
            ClockInput("To", b, Modifier.weight(1f)) { b = it }
        }
        if (!valid) Text("“To” must be later than “From”.", style = FttType.caption().copy(fontSize = 12.sp), color = Color(0xFFD70015), modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(16.dp))
        LimeButton("Apply", enabled = valid, onClick = { if (fa != null && fb != null) onApply(fa, fb) })
        CloseText { onClose() }
    }
}

@Composable
private fun ClockInput(label: String, v: Clock12, modifier: Modifier, onChange: (Clock12) -> Unit) {
    Column(modifier) {
        Text(label, style = FttType.caption().copy(fontSize = 12.sp), color = FttColors.LabelSecondary)
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            var text by remember(v.hour) { mutableStateOf(v.hour.toString()) }
            BasicTextField(
                text,
                { t -> val digits = t.filter(Char::isDigit).take(2); text = digits; digits.toIntOrNull()?.takeIf { it in 1..12 }?.let { onChange(v.copy(hour = it)) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = TextStyle(fontSize = 15.sp, textAlign = TextAlign.Center, color = FttColors.Ink),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(FttColors.Ink),
                modifier = Modifier.width(36.dp).border(1.dp, Color(0x4D3C3C43), RoundedCornerShape(6.dp)).padding(vertical = 6.dp),
            )
            Text(":", style = FttType.bodyBold())
            Toggle(listOf("00", "30"), if (v.minute >= 30) 1 else 0) { onChange(v.copy(minute = if (it == 1) 30 else 0)) }
            Toggle(listOf("AM", "PM"), if (v.pm) 1 else 0) { onChange(v.copy(pm = it == 1)) }
        }
    }
}

@Composable
private fun Toggle(options: List<String>, selected: Int, onPick: (Int) -> Unit) {
    Row(Modifier.border(1.dp, Color(0x4D3C3C43), RoundedCornerShape(6.dp)).clip(RoundedCornerShape(6.dp))) {
        options.forEachIndexed { i, o ->
            Text(
                o, style = FttType.caption().copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.background(if (i == selected) FttColors.StartLime else Color.Transparent)
                    .clickable(role = Role.RadioButton) { onPick(i) }.padding(horizontal = 6.dp, vertical = 8.dp),
            )
        }
    }
}

/** Figma "Select camera or access to the device storage". */
@Composable
fun PhotoSourceSheet(onPick: () -> Unit, onCamera: () -> Unit, onClose: () -> Unit) {
    Sheet(onClose) {
        Spacer(Modifier.height(16.dp))
        LimeButton(PublishCopy.PICK_EXISTING, enabled = true, onClick = onPick)
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onCamera, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, FttColors.Ink),
            colors = ButtonDefaults.buttonColors(containerColor = FttColors.BackgroundPrimary, contentColor = Color(0xFF3C3C43)),
        ) { Text(PublishCopy.TAKE_PICTURE, style = FttType.body()) }
        Spacer(Modifier.height(8.dp))
    }
}

/** Figma "Maximum limit for photos exceeded". */
@Composable
fun MaxPhotosAlert(onOk: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable(onClick = onOk), contentAlignment = Alignment.Center) {
        Column(Modifier.width(272.dp).background(FttColors.Surface, RoundedCornerShape(14.dp)).clickable(enabled = false) {}) {
            Text(
                PublishCopy.MAX_PHOTOS_ALERT, style = FttType.bodyBold(), textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
            HorizontalDivider(color = FttColors.Separator)
            Text(
                "OK", style = FttType.body(), color = Color(0xFF007AFF), textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOk).padding(vertical = 12.dp),
            )
        }
    }
}
