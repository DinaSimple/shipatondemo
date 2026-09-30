// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
// Copyright (C) 2026 Dina Elokhova (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.ui.profile

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.freetotake.domain.catalog.CardState
import app.freetotake.domain.model.Item
import app.freetotake.domain.profile.MyProfile
import app.freetotake.domain.profile.NotificationKind
import app.freetotake.domain.profile.NotificationPrefs
import app.freetotake.domain.profile.PasswordFormErrors
import app.freetotake.domain.profile.ProfileCopy as C
import app.freetotake.domain.profile.ProfileRules
import app.freetotake.resources.Res
import app.freetotake.resources.empty_mascot
import app.freetotake.resources.ic_edit
import app.freetotake.ui.home.HomeTab
import app.freetotake.ui.home.ListingCard
import app.freetotake.ui.home.TabBar
import app.freetotake.ui.publish.Avatar
import app.freetotake.ui.theme.BackChevron
import app.freetotake.ui.theme.FttColors
import app.freetotake.ui.theme.FttType
import org.jetbrains.compose.resources.painterResource

private val Red = Color(0xFFFF3B30)
private val Placeholder = Color(0xFFAEAEB2)

/** Uploaded avatar, else a placeholder generated in the app (stable per user; mascots on lime). */
@Composable
fun ProfileAvatar(p: MyProfile, size: androidx.compose.ui.unit.Dp) {
    val idx = ProfileRules.placeholderAvatar(p.id)
    Avatar(p.avatarUrl, if (p.avatarUrl == null && idx < 3) idx else null, size)
}

@Composable
private fun Header(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clickable(role = Role.Button, onClickLabel = "Back", onClick = onBack), contentAlignment = Alignment.Center) { BackChevron() }
        Text(title, style = FttType.bodyBold(), textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        Box(Modifier.size(44.dp))
    }
}

@Composable
private fun Toast(message: String?, modifier: Modifier) {
    message?.let {
        Text(it, style = FttType.subheadline(), color = Color.White, modifier = modifier.padding(16.dp)
            .background(Color(0xE6333333), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 8.dp))
    }
}

/** Figma "Profile". */
@Composable
fun ProfileScreen(
    profile: MyProfile,
    message: String?,
    onEdit: () -> Unit,
    onFavourites: () -> Unit,
    onNotifications: () -> Unit,
    onLogOut: () -> Unit,
    onNavTab: (HomeTab) -> Unit,
) {
    Column(Modifier.fillMaxSize().background(FttColors.BackgroundPrimary)) {
        Box(Modifier.weight(1f).statusBarsPadding()) {
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                Text(C.TITLE, style = FttType.largeTitleBold(), modifier = Modifier.padding(top = 16.dp, bottom = 16.dp))
                Row(
                    Modifier.fillMaxWidth().background(FttColors.Surface, RoundedCornerShape(12.dp)).clickable(onClick = onEdit).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProfileAvatar(profile, 60.dp)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(profile.displayName, style = FttType.body().copy(fontSize = 18.sp), color = FttColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(profile.email ?: "Test account (no email yet)", style = FttType.subheadline().copy(fontSize = 13.sp), color = FttColors.LabelSecondary, maxLines = 1)
                    }
                    Image(painterResource(Res.drawable.ic_edit), "Edit my profile", Modifier.size(22.dp))
                }
                Spacer(Modifier.height(16.dp))
                NavRow(C.FAVOURITES, { HeartIcon() }, onFavourites)
                Spacer(Modifier.height(8.dp))
                NavRow(C.NOTIFICATIONS, { BellIcon() }, onNotifications)
                Spacer(Modifier.weight(1f))
                Text(C.LOG_OUT, style = FttType.body(), color = Red, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onLogOut).padding(vertical = 16.dp))
            }
            Toast(message, Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp))
        }
        TabBar(selected = HomeTab.PROFILE, onTab = onNavTab)
    }
}

@Composable
private fun NavRow(label: String, icon: @Composable () -> Unit, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(44.dp).background(FttColors.Surface, RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Text(label, style = FttType.body().copy(fontSize = 15.sp), modifier = Modifier.weight(1f).padding(start = 12.dp))
        Text("›", style = FttType.title1Bold().copy(fontSize = 20.sp), color = Color(0x4D3C3C43))
    }
}

@Composable
private fun HeartIcon() {
    Canvas(Modifier.size(18.dp)) {
        val w = size.width; val h = size.height
        val p = Path().apply {
            moveTo(w * 0.5f, h * 0.88f)
            cubicTo(w * 0.1f, h * 0.6f, w * 0.02f, h * 0.38f, w * 0.1f, h * 0.22f)
            cubicTo(w * 0.2f, h * 0.04f, w * 0.44f, h * 0.06f, w * 0.5f, h * 0.28f)
            cubicTo(w * 0.56f, h * 0.06f, w * 0.8f, h * 0.04f, w * 0.9f, h * 0.22f)
            cubicTo(w * 0.98f, h * 0.38f, w * 0.9f, h * 0.6f, w * 0.5f, h * 0.88f)
            close()
        }
        drawPath(p, FttColors.Ink, style = Stroke(1.5.dp.toPx()))
    }
}

@Composable
private fun BellIcon() {
    Canvas(Modifier.size(18.dp)) {
        val w = size.width; val h = size.height; val s = 1.5.dp.toPx()
        val p = Path().apply {
            moveTo(w * 0.2f, h * 0.72f)
            cubicTo(w * 0.2f, h * 0.2f, w * 0.8f, h * 0.2f, w * 0.8f, h * 0.72f)
            close()
        }
        drawPath(p, FttColors.Ink, style = Stroke(s))
        drawLine(FttColors.Ink, Offset(w * 0.1f, h * 0.72f), Offset(w * 0.9f, h * 0.72f), s, cap = StrokeCap.Round)
        drawLine(FttColors.Ink, Offset(w * 0.42f, h * 0.88f), Offset(w * 0.58f, h * 0.88f), s, cap = StrokeCap.Round)
    }
}

/** Figma "Edit profile" / "Edit profile state". The generated nickname is shown but not editable. */
@Composable
fun EditProfileScreen(
    profile: MyProfile,
    name: String,
    email: String,
    saving: Boolean,
    error: String?,
    onName: (String) -> Unit,
    onEmail: (String) -> Unit,
    onAvatar: () -> Unit,
    onChangePassword: () -> Unit,
    onDeleteAccount: () -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    val changed = (ProfileRules.normalizedPublicName(name) != profile.publicName?.trim()?.takeIf { it.isNotEmpty() }) || (email.trim() != (profile.email ?: ""))
    Column(Modifier.fillMaxSize().background(FttColors.BackgroundPrimary).statusBarsPadding().imePadding()) {
        Header(C.EDIT_TITLE, onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Box(Modifier.align(Alignment.CenterHorizontally).padding(vertical = 16.dp).clickable(role = Role.Button, onClickLabel = "Change avatar", onClick = onAvatar)) {
                ProfileAvatar(profile, 100.dp)
                UploadBadge(Modifier.align(Alignment.Center))
            }
            Field(C.PUBLIC_NAME, name, C.PUBLIC_NAME_PLACEHOLDER, onChange = onName, clearable = true)
            Spacer(Modifier.height(8.dp))
            ReadOnlyField(C.NICKNAME, "@${profile.nickname}")
            Spacer(Modifier.height(8.dp))
            if (profile.passwordLogin) Field(C.EMAIL, email, "Add your email", onChange = onEmail, keyboard = KeyboardType.Email)
            else ReadOnlyField(C.EMAIL, profile.email.orEmpty())   // Google account: email comes from Google
            Spacer(Modifier.height(16.dp))
            if (profile.passwordLogin) Row(
                Modifier.fillMaxWidth().height(44.dp).background(FttColors.Surface, RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClick = onChangePassword).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LockIcon()
                Text(C.CHANGE_PASSWORD, style = FttType.body().copy(fontSize = 15.sp), modifier = Modifier.weight(1f).padding(start = 12.dp))
                Text("›", style = FttType.title1Bold().copy(fontSize = 20.sp), color = Color(0x4D3C3C43))
            }
            error?.let { Text(it, style = FttType.subheadline(), color = Red, modifier = Modifier.padding(top = 12.dp)) }
            Spacer(Modifier.height(48.dp))
            Text(C.DELETE_ACCOUNT, style = FttType.body(), color = Red, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onDeleteAccount).padding(vertical = 12.dp))
        }
        SaveButton(enabled = changed && !saving, text = if (saving) "Saving…" else C.SAVE, onClick = onSave)
    }
}

@Composable
private fun UploadBadge(modifier: Modifier) {
    Canvas(modifier.size(28.dp)) {
        val w = size.width; val h = size.height; val s = 2.dp.toPx()
        drawCircle(Color.White.copy(alpha = 0.7f))
        drawLine(FttColors.Ink, Offset(w * 0.5f, h * 0.25f), Offset(w * 0.5f, h * 0.62f), s, cap = StrokeCap.Round)
        drawLine(FttColors.Ink, Offset(w * 0.34f, h * 0.4f), Offset(w * 0.5f, h * 0.25f), s, cap = StrokeCap.Round)
        drawLine(FttColors.Ink, Offset(w * 0.66f, h * 0.4f), Offset(w * 0.5f, h * 0.25f), s, cap = StrokeCap.Round)
        drawLine(FttColors.Ink, Offset(w * 0.28f, h * 0.74f), Offset(w * 0.72f, h * 0.74f), s, cap = StrokeCap.Round)
    }
}

@Composable
private fun LockIcon() {
    Canvas(Modifier.size(18.dp)) {
        val w = size.width; val h = size.height; val s = 1.5.dp.toPx()
        drawRoundRect(FttColors.Ink, Offset(w * 0.18f, h * 0.45f), androidx.compose.ui.geometry.Size(w * 0.64f, h * 0.48f), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()), style = Stroke(s))
        drawArc(FttColors.Ink, 180f, 180f, false, Offset(w * 0.3f, h * 0.1f), androidx.compose.ui.geometry.Size(w * 0.4f, h * 0.7f), style = Stroke(s))
    }
}

@Composable
private fun Field(
    label: String, value: String, placeholder: String, onChange: (String) -> Unit,
    clearable: Boolean = false, keyboard: KeyboardType = KeyboardType.Text,
    password: Boolean = false, errors: List<String> = emptyList(),
) {
    var visible by remember { mutableStateOf(false) }
    Column {
        Row(
            Modifier.fillMaxWidth().height(50.dp).background(FttColors.Surface, RoundedCornerShape(10.dp)).padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) Text(placeholder, style = FttType.body().copy(fontSize = 15.sp), color = Placeholder)
                else Text(label, style = FttType.caption(), color = FttColors.LabelSecondary, modifier = Modifier.padding(bottom = 26.dp))
                BasicTextField(
                    value, onChange, singleLine = true,
                    textStyle = TextStyle(fontSize = 15.sp, color = FttColors.Ink),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(FttColors.Ink),
                    keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard),
                    visualTransformation = if (password && !visible) PasswordVisualTransformation() else VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth().padding(top = if (value.isEmpty()) 0.dp else 12.dp),
                )
            }
            if (clearable && value.isNotEmpty()) Text("✕", style = FttType.body(), color = FttColors.LabelSecondary,
                modifier = Modifier.clickable(role = Role.Button, onClickLabel = "Clear") { onChange("") }.padding(8.dp))
            if (password) Text(if (visible) "Hide" else "Show", style = FttType.caption().copy(fontSize = 12.sp), color = FttColors.LabelSecondary,
                modifier = Modifier.clickable(role = Role.Button) { visible = !visible }.padding(8.dp))
        }
        errors.forEach { Text(it, style = FttType.caption().copy(fontSize = 12.sp), color = Red, modifier = Modifier.padding(start = 16.dp, top = 4.dp)) }
    }
}

@Composable
private fun ReadOnlyField(label: String, value: String) {
    Column(Modifier.fillMaxWidth().height(50.dp).background(FttColors.Surface, RoundedCornerShape(10.dp)).padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(label, style = FttType.caption(), color = FttColors.LabelSecondary)
        Text(value, style = FttType.body().copy(fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SaveButton(enabled: Boolean, text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled,
        modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp).navigationBarsPadding().fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(10.dp),
        border = if (enabled) BorderStroke(1.dp, FttColors.OnLime) else null,
        colors = ButtonDefaults.buttonColors(
            containerColor = FttColors.StartLime, contentColor = FttColors.OnLime,
            disabledContainerColor = Color(0xFFE5E5EA), disabledContentColor = Placeholder,
        ),
    ) { Text(text, style = FttType.bodyBold()) }
}

/** Figma "Change password" (default / in process / step 3). Rules shown inline under the fields. */
@Composable
fun ChangePasswordScreen(
    old: String, new: String, confirm: String,
    errors: PasswordFormErrors?,
    serverError: String?,
    saving: Boolean,
    onOld: (String) -> Unit, onNew: (String) -> Unit, onConfirm: (String) -> Unit,
    onForgot: () -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(FttColors.BackgroundPrimary).statusBarsPadding().imePadding()) {
        Header(C.CHANGE_PASSWORD, onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Spacer(Modifier.height(8.dp))
            Field(C.OLD_PASSWORD, old, C.OLD_PLACEHOLDER, onOld, password = true, errors = listOfNotNull(errors?.old, serverError))
            Field(C.NEW_PASSWORD, new, C.NEW_PLACEHOLDER, onNew, password = true, errors = errors?.new?.map { it.message }.orEmpty())
            Field(C.CONFIRM_PASSWORD, confirm, C.CONFIRM_PLACEHOLDER, onConfirm, password = true, errors = listOfNotNull(errors?.confirm))
            if (app.freetotake.domain.auth.AuthFeatures.EMAIL_FLOW) Text(C.FORGOT, style = FttType.subheadline().copy(fontSize = 13.sp), color = FttColors.LabelSecondary,
                modifier = Modifier.padding(top = 4.dp).clickable(role = Role.Button, onClick = onForgot))
        }
        SaveButton(enabled = old.isNotEmpty() && new.isNotEmpty() && confirm.isNotEmpty() && !saving, text = if (saving) "Saving…" else C.SAVE, onClick = onSave)
    }
}

/** Figma "Favorites default placeholder" / "Favorites filled in". Unavailable listings are greyed. */
@Composable
fun FavouritesScreen(items: List<Item>?, onBack: () -> Unit, onOpen: (Item) -> Unit, onUnfavourite: (Item) -> Unit) {
    Column(Modifier.fillMaxSize().background(FttColors.BackgroundPrimary).statusBarsPadding().navigationBarsPadding()) {
        Header(C.FAVOURITES_TITLE, onBack)
        when {
            items == null -> Unit
            items.isEmpty() -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(C.EMPTY, style = FttType.body(), color = FttColors.LabelSecondary)
                Image(painterResource(Res.drawable.empty_mascot), null, Modifier.padding(top = 12.dp).size(width = 101.dp, height = 90.dp))
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2), contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(items) { item ->
                    val available = item.status.acceptsClaims
                    ListingCard(
                        item, Modifier.fillMaxWidth(),
                        state = CardState(emptyList(), showHeart = true, showTrash = false, greyed = !available),
                        favorite = true,
                        onHeart = { onUnfavourite(item) },
                    ) { onOpen(item) }
                }
            }
        }
    }
}

/** Figma "Notifications" bottom sheet: three topics with lime switches. */
@Composable
fun NotificationsSheet(prefs: NotificationPrefs, onChange: (NotificationPrefs) -> Unit, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).clickable(onClick = onClose)) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(FttColors.BackgroundPrimary, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .clickable(enabled = false) {}.navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(width = 36.dp, height = 5.dp).background(Color(0x4D3C3C43), RoundedCornerShape(3.dp)))
            Text(C.NOTIFICATIONS, style = FttType.title1Bold(), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
            NotificationKind.entries.forEach { k ->
                val on = when (k) { NotificationKind.CLAIM_APPROVED -> prefs.claimApproved; NotificationKind.NEW_REQUEST -> prefs.newRequest; NotificationKind.NEARBY -> prefs.nearby }
                Row(Modifier.fillMaxWidth().height(48.dp).background(FttColors.Surface, RoundedCornerShape(10.dp)).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(k.label, style = FttType.body().copy(fontSize = 15.sp), modifier = Modifier.weight(1f))
                    LimeSwitch(
                        checked = on,
                        label = k.label,
                        onCheckedChange = { v ->
                            onChange(when (k) {
                                NotificationKind.CLAIM_APPROVED -> prefs.copy(claimApproved = v)
                                NotificationKind.NEW_REQUEST -> prefs.copy(newRequest = v)
                                NotificationKind.NEARBY -> prefs.copy(nearby = v)
                            })
                        },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** iOS-style single-button alert used in the designs ("Password changed", "Removed from favourites"). */
@Composable
fun OkAlert(title: String, onOk: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable(onClick = onOk), contentAlignment = Alignment.Center) {
        Column(Modifier.width(272.dp).background(FttColors.Surface, RoundedCornerShape(14.dp)).clickable(enabled = false) {}) {
            Text(title, style = FttType.bodyBold(), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(16.dp))
            HorizontalDivider(color = FttColors.Separator)
            Text(C.OK, style = FttType.body(), color = Color(0xFF007AFF), textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOk).padding(vertical = 12.dp))
        }
    }
}

/** Figma "Profile placeholder": guests see an invitation to sign up (no forced jump to Log in). */
@Composable
fun GuestProfileScreen(onRegister: () -> Unit, onLogIn: () -> Unit, onNavTab: (HomeTab) -> Unit) {
    Column(Modifier.fillMaxSize().background(FttColors.BackgroundPrimary)) {
        Column(Modifier.weight(1f).statusBarsPadding().padding(horizontal = 16.dp)) {
            Text(C.TITLE, style = FttType.largeTitleBold(), modifier = Modifier.padding(top = 16.dp))
            Column(
                Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(C.GUEST_LINE1, style = FttType.subheadline(), color = FttColors.TextSecondary, textAlign = TextAlign.Center)
                Text(
                    C.GUEST_SIGN_UP, style = FttType.subheadlineBold(), color = FttColors.Ink,
                    modifier = Modifier.clickable(role = Role.Button, onClick = onRegister).padding(horizontal = 12.dp, vertical = 2.dp),
                )
                Text(C.GUEST_LINE2, style = FttType.subheadline(), color = FttColors.TextSecondary, textAlign = TextAlign.Center)
                Image(painterResource(Res.drawable.empty_mascot), null, Modifier.padding(top = 20.dp).size(width = 101.dp, height = 90.dp))
            }
        }
        TabBar(selected = HomeTab.PROFILE, onTab = onNavTab)
    }
}

/**
 * v1.16.9: lime toggle whose white knob has a dark outline, so it stays readable on the lime track
 * (the Material switch drew a white knob with no edge).
 */
@Composable
private fun LimeSwitch(checked: Boolean, label: String, onCheckedChange: (Boolean) -> Unit) {
    val x by androidx.compose.animation.core.animateDpAsState(if (checked) 22.dp else 2.dp, label = "knob")
    Box(
        Modifier.size(width = 52.dp, height = 32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (checked) FttColors.StartLime else Color(0xFFE5E5EA))
            .border(1.5.dp, if (checked) FttColors.OnLime else Color(0xFFAEAEB2), RoundedCornerShape(16.dp))
            .toggleable(value = checked, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = onCheckedChange)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier.padding(start = x).size(28.dp)
                .background(Color.White, CircleShape)
                .border(1.5.dp, if (checked) FttColors.OnLime else Color(0xFFAEAEB2), CircleShape),
        )
    }
}
