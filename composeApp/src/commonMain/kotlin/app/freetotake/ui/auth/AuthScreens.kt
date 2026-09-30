// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Dina Elokhova (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.ExperimentalResourceApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.freetotake.data.supabase.AccountAuth
import app.freetotake.domain.auth.AuthCopy as A
import app.freetotake.domain.auth.AuthError
import app.freetotake.domain.auth.AuthFlow
import app.freetotake.domain.auth.AuthFeatures
import app.freetotake.domain.auth.AuthLink
import app.freetotake.domain.auth.AuthRules
import app.freetotake.domain.auth.AuthStep
import app.freetotake.domain.auth.CaptchaChallenge
import app.freetotake.domain.auth.CaptchaRules
import app.freetotake.domain.auth.GoogleCopy
import app.freetotake.domain.auth.GoogleResult
import app.freetotake.domain.auth.LinkPurpose
import app.freetotake.domain.auth.LinkRequestResult
import app.freetotake.domain.auth.LoginErrors
import app.freetotake.domain.auth.NewPasswordErrors
import app.freetotake.domain.auth.NewPasswordForm
import app.freetotake.domain.auth.RedeemResult
import app.freetotake.domain.legal.Terms
import app.freetotake.resources.Res
import app.freetotake.resources.auth_mascot
import app.freetotake.ui.theme.BackChevron
import app.freetotake.ui.theme.FttColors
import app.freetotake.ui.theme.FttType
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

/** A passed check is reused until an email was sent (server consumes it) or it is about to expire — one puzzle per flow. */
private object CaptchaPass {
    var token: String? = null
    var solvedAt: kotlin.time.TimeSource.Monotonic.ValueTimeMark? = null
    fun valid(): String? = token?.takeIf { CaptchaRules.reusable(0L, solvedAt?.elapsedNow()?.inWholeMilliseconds ?: Long.MAX_VALUE) }
    fun clear() { token = null; solvedAt = null }
}

private val Red = Color(0xFFFF3B30)
private val Placeholder = Color(0xFFAEAEB2)
private val Side = 16.dp

/**
 * Log in, Sign up, Password recovery, Create a password, success screens, Terms (spec "Login and auth").
 * [step] is hoisted so an incoming email link can jump straight to [AuthStep.Redirecting].
 */
@Composable
fun AuthHost(
    step: AuthStep,
    onStep: (AuthStep) -> Unit,
    auth: AccountAuth?,
    openEmailApp: () -> Unit,
    initialEmail: String?,
    onEmailUsed: (String) -> Unit,
    /** true while a one-off link session exists (password not yet set). */
    onLinkSession: (Boolean) -> Unit,
    onLeave: () -> Unit,
    isDebug: Boolean,
    onDebugSignIn: () -> Unit,
    /** v1.15: device Google account picker (null = not available on this platform). */
    googleSignIn: (suspend () -> GoogleResult)? = null,
) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf(initialEmail.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var loginErrors by remember { mutableStateOf(LoginErrors()) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var pwErrors by remember { mutableStateOf<NewPasswordErrors?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var checkEmail by remember { mutableStateOf(false) }
    var showTerms by remember { mutableStateOf(false) }
    var captchaFor by remember { mutableStateOf<Pair<LinkPurpose, String>?>(null) }
    var providers by remember { mutableStateOf<Set<String>?>(null) }
    // null = still loading; a failed check → empty (buttons hidden, "not ready" note shown).
    LaunchedEffect(auth) { providers = auth?.enabledProviders() ?: emptySet() }

    fun go(next: AuthStep) {
        password = ""; confirm = ""; loginErrors = LoginErrors(); emailError = null; pwErrors = null; notice = null
        onStep(next)
    }

    /** Sends the link; asks for the picture check only if no passed check is at hand. */
    fun sendLink(purpose: LinkPurpose, address: String, token: String) {
        val a = auth ?: return
        busy = true; notice = null; emailError = null
        scope.launch {
            val r = a.requestLink(purpose, address, token)
            busy = false
            when (r) {
                LinkRequestResult.Sent -> { CaptchaPass.clear(); onEmailUsed(address); checkEmail = true }
                is LinkRequestResult.Failed -> when (r.error) {
                    AuthError.CAPTCHA_REQUIRED -> { CaptchaPass.clear(); captchaFor = purpose to address }
                    AuthError.EMAIL_TAKEN, AuthError.NOT_REGISTERED, AuthError.INVALID_EMAIL -> emailError = r.error.message
                    else -> notice = r.error.message
                }
            }
        }
    }

    /** Google account → Supabase session; the session flow then leaves the auth screens. */
    fun google() {
        val a = auth ?: run { notice = AuthError.NETWORK.message; return }
        val pick = googleSignIn ?: run { notice = GoogleCopy.NOT_CONFIGURED; return }
        notice = null
        scope.launch {
            when (val r = pick()) {
                is GoogleResult.Token -> {
                    busy = true
                    val err = a.signInWithGoogle(r.idToken, r.rawNonce)
                    busy = false
                    if (err != null) notice = err.message
                }
                else -> notice = GoogleCopy.message(r)
            }
        }
    }

    /** Facebook opens in the browser; when it returns, the session flow leaves the auth screens. */
    fun facebook() {
        val a = auth ?: run { notice = AuthError.NETWORK.message; return }
        notice = null
        scope.launch { a.signInWithFacebook()?.let { notice = it.message } }
    }

    fun requestLink(purpose: LinkPurpose, address: String) {
        if (auth == null) { notice = AuthError.NETWORK.message; return }
        val pass = CaptchaPass.valid()
        if (pass != null) sendLink(purpose, address, pass) else captchaFor = purpose to address
    }

    fun onCaptchaPassed(token: String) {
        val (purpose, address) = captchaFor ?: return
        captchaFor = null
        CaptchaPass.token = token; CaptchaPass.solvedAt = kotlin.time.TimeSource.Monotonic.markNow()
        sendLink(purpose, address, token)
    }

    // Link opened → redeem (server validates: exists, unexpired, unused) → session → Create password.
    LaunchedEffect(step) {
        val s = step as? AuthStep.Redirecting ?: return@LaunchedEffect
        val a = auth ?: run { onStep(AuthFlow.afterRedeemFailed(AuthError.NETWORK, null, s.link.purpose)); return@LaunchedEffect }
        onLinkSession(true)
        when (val r = a.redeem(s.link)) {
            is RedeemResult.Ok -> { email = r.email; go(AuthFlow.afterRedeem(r.purpose, r.email)) }
            is RedeemResult.Failed -> { onLinkSession(false); go(AuthFlow.afterRedeemFailed(r.error, r.email, r.purpose)) }
        }
    }

    Box(Modifier.fillMaxSize()) {
        when (val s = step) {
            // v1.15.2: email flow hidden (AuthFeatures.EMAIL_FLOW) → Google / Facebook only.
            AuthStep.Login, AuthStep.SignUpEmail, AuthStep.Recovery -> if (AuthFeatures.showsSocialOnly(s)) {
                // v1.16.5: Figma "Registration Default" / "Log in Default" layout — Sign up vs Log in title, bottom switch link.
                val page = AuthFeatures.socialPage(s)
                AuthPage(
                    onBack = if (page.skip) null else onLeave, onSkip = if (page.skip) onLeave else null,
                    title = page.title, subtitle = page.subtitle,
                    bottom = { BottomLink(page.bottomPrefix, page.bottomLink) { go(page.switchTo) } },
                ) {
                    val g = AuthFeatures.showProvider("google", providers)
                    val f = AuthFeatures.showProvider("facebook", providers)
                    if (g) GoogleButton(::google)
                    if (f) FacebookButton(::facebook)
                    if (!g && !f) {
                        // Same shapes as the design (white 50dp field + lime button) while no provider is switched on yet.
                        Box(Modifier.fillMaxWidth().height(50.dp).background(FttColors.Surface, RoundedCornerShape(10.dp)).padding(horizontal = Side), contentAlignment = Alignment.CenterStart) {
                            Text(if (providers == null) "Loading…" else A.SOCIAL_NOT_READY, style = small(), color = Placeholder, maxLines = 2)
                        }
                        Box(Modifier.alpha(0.5f)) { LimeButton(A.CONTINUE) {} }
                    }
                    TermsLine { showTerms = true }
                    notice?.let { Notice(it) }
                    if (isDebug) Text("Continue as test user (debug)", style = small(), color = FttColors.LabelSecondary,
                        modifier = Modifier.align(Alignment.CenterHorizontally).clickable(onClick = onDebugSignIn).padding(8.dp))
                }
            } else when (s) {
            AuthStep.Login -> AuthPage(
                onBack = null, onSkip = onLeave, title = A.LOGIN_TITLE, subtitle = A.LOGIN_SUBTITLE,
                bottom = { BottomLink(A.NO_ACCOUNT, A.REGISTER) { email = email.trim(); go(AuthStep.SignUpEmail) } },
            ) {
                AuthField(A.EMAIL, email, A.EMAIL, { email = it; loginErrors = loginErrors.copy(email = false) }, KeyboardType.Email,
                    error = if (loginErrors.email) A.WRONG_VALUE else null)
                AuthField(A.PASSWORD, password, A.PASSWORD, { password = it; loginErrors = loginErrors.copy(password = false) }, password = true,
                    error = if (loginErrors.password) A.WRONG_VALUE else null)
                Text(A.FORGOT, style = small(), color = FttColors.LabelSecondary,
                    modifier = Modifier.align(Alignment.End).clickable(role = Role.Button) { go(AuthStep.Recovery) }.padding(vertical = 4.dp))
                LimeButton(A.LOGIN_BUTTON, Modifier.padding(top = 8.dp)) {
                    val e = AuthRules.loginErrors(email, password)
                    loginErrors = e
                    if (!e.isValid) return@LimeButton
                    val a = auth ?: run { notice = AuthError.NETWORK.message; return@LimeButton }
                    busy = true; notice = null
                    scope.launch {
                        val err = a.signIn(email, password)
                        busy = false
                        when (err) {
                            null -> { onEmailUsed(AuthRules.normalizeEmail(email)); password = "" }
                            AuthError.WRONG_CREDENTIALS -> loginErrors = AuthRules.WRONG_CREDENTIALS
                            else -> notice = err.message
                        }
                    }
                }
                TermsLine { showTerms = true }
                OrDivider()
                GoogleButton(::google)
                FacebookButton(::facebook)
                notice?.let { Notice(it) }
                if (isDebug) Text("Continue as test user (debug)", style = small(), color = FttColors.LabelSecondary,
                    modifier = Modifier.align(Alignment.CenterHorizontally).clickable(onClick = onDebugSignIn).padding(8.dp))
            }

            AuthStep.SignUpEmail -> AuthPage(
                onBack = { go(AuthStep.Login) }, onSkip = null, title = A.SIGNUP_TITLE, subtitle = A.SIGNUP_SUBTITLE,
                bottom = { BottomLink(A.HAVE_ACCOUNT, A.SIGN_IN) { go(AuthStep.Login) } },
            ) {
                AuthField(A.EMAIL, email, A.EMAIL, { email = it; emailError = null }, KeyboardType.Email, error = emailError, clearable = emailError != null)
                LimeButton(A.CONTINUE, Modifier.padding(top = 4.dp)) {
                    emailError = AuthRules.emailError(email)
                    if (emailError == null) requestLink(LinkPurpose.SIGN_UP, AuthRules.normalizeEmail(email))
                }
                TermsLine { showTerms = true }
                OrDivider()
                GoogleButton(::google)
                FacebookButton(::facebook)
                notice?.let { Notice(it) }
            }

            AuthStep.Recovery -> AuthPage(
                onBack = { go(AuthStep.Login) }, onSkip = null, title = A.RECOVERY_TITLE, subtitle = A.RECOVERY_SUBTITLE,
            ) {
                AuthField(A.EMAIL, email, A.EMAIL, { email = it; emailError = null }, KeyboardType.Email, error = emailError, clearable = emailError != null)
                LimeButton(A.CONTINUE, Modifier.padding(top = 4.dp)) {
                    emailError = AuthRules.emailError(email)
                    if (emailError == null) requestLink(LinkPurpose.RECOVER, AuthRules.normalizeEmail(email))
                }
                Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { go(AuthStep.Login) }.padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(A.REMEMBERED, style = small(), color = FttColors.LabelSecondary)
                    Text(A.LOG_IN_LINK, style = small(), color = FttColors.TextPrimary)
                }
                notice?.let { Notice(it) }
            }

            else -> Unit
            }

            is AuthStep.Redirecting -> Box(Modifier.fillMaxSize().background(FttColors.BackgroundPrimary)) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = FttColors.LabelSecondary, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(16.dp))
                    Text(A.REDIRECTING, style = FttType.subheadline(), color = FttColors.LabelSecondary)
                }
            }

            is AuthStep.LinkProblem -> AuthPage(
                onBack = { go(AuthStep.Login) }, onSkip = null,
                title = when (s.error) { AuthError.LINK_EXPIRED -> A.LINK_EXPIRED_TITLE; AuthError.LINK_USED -> A.LINK_USED_TITLE; else -> A.LINK_INVALID_TITLE },
                subtitle = s.error.message,
            ) {
                if (AuthFlow.canResend(s)) LimeButton(A.SEND_NEW_LINK) { val e = s.email!!; email = e; requestLink(s.purpose!!, e) }
                else LimeButton(A.CONTINUE) { go(if (s.purpose == LinkPurpose.RECOVER) AuthStep.Recovery else AuthStep.SignUpEmail) }
                OutlineButton(A.LOGIN_BUTTON) { go(AuthStep.Login) }
                notice?.let { Notice(it) }
            }

            is AuthStep.CreatePassword -> {
                val signup = s.purpose == LinkPurpose.SIGN_UP
                val signInLink: (@Composable () -> Unit)? = if (signup) { { BottomLink(A.HAVE_ACCOUNT, A.SIGN_IN) { go(AuthStep.Login) } } } else null
                AuthPage(
                    onBack = { go(AuthStep.Login) }, onSkip = null,
                    title = if (signup) A.CREATE_PASSWORD_TITLE else A.NEW_PASSWORD_TITLE,
                    subtitle = if (signup) A.CREATE_PASSWORD_SUBTITLE else A.NEW_PASSWORD_SUBTITLE,
                    bottom = signInLink,
                ) {
                    AuthField(A.PASSWORD, password, A.INSERT_PASSWORD, { password = it; pwErrors = null }, password = true, plainByDefault = true,
                        errors = pwErrors?.password?.map { it.message }.orEmpty())
                    AuthField(A.PASSWORD, confirm, A.CONFIRM_PASSWORD, { confirm = it; pwErrors = null }, password = true,
                        error = pwErrors?.confirm)
                    LimeButton(A.CONTINUE, Modifier.padding(top = 4.dp)) {
                        if (!NewPasswordForm.canContinue(password, confirm)) { pwErrors = NewPasswordForm.validate(password, confirm); return@LimeButton }
                        val v = NewPasswordForm.validate(password, confirm)
                        pwErrors = v
                        if (!v.isValid) return@LimeButton
                        val a = auth ?: return@LimeButton
                        busy = true; notice = null
                        scope.launch {
                            val err = a.setPassword(password)
                            busy = false
                            if (err == null) { onLinkSession(false); onEmailUsed(s.email); go(AuthFlow.afterPasswordSet(s.purpose, s.email)) }
                            else notice = err.message
                        }
                    }
                    TermsLine { showTerms = true }
                    notice?.let { Notice(it) }
                }
            }

            is AuthStep.Success -> AuthSuccess(
                title = if (s.purpose == LinkPurpose.SIGN_UP) A.REGISTERED_TITLE else A.READY_TITLE,
                text = if (s.purpose == LinkPurpose.SIGN_UP) A.REGISTERED_TEXT else A.READY_TEXT,
                onContinue = { email = s.email; go(AuthFlow.afterSuccess()) },
            )
        }

        if (busy) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).clickable(enabled = true) {}) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.align(Alignment.Center).size(36.dp))
        }
        if (captchaFor != null && auth != null) CaptchaSheet(auth, onPassed = ::onCaptchaPassed,
            onOffline = { captchaFor = null; notice = AuthError.NETWORK.message },
            onClose = { captchaFor = null })
        if (checkEmail) CheckEmailSheet(onOpen = { checkEmail = false; openEmailApp() }, onClose = { checkEmail = false })
        if (showTerms) TermsScreen(onBack = { showTerms = false })
    }
}

@Composable private fun small() = FttType.subheadline().copy(fontSize = 13.sp, lineHeight = 18.sp)

@Composable
private fun AuthPage(
    onBack: (() -> Unit)?,
    onSkip: (() -> Unit)?,
    title: String,
    subtitle: String,
    bottom: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().background(FttColors.BackgroundPrimary).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Box(Modifier.fillMaxWidth().height(42.dp)) {
            if (onBack != null) Box(Modifier.align(Alignment.CenterStart).padding(start = 8.dp).clickable(role = Role.Button, onClick = onBack)) { BackChevron() }
            if (onSkip != null) Text(A.SKIP, style = FttType.body(), color = FttColors.TextSecondary,
                modifier = Modifier.align(Alignment.CenterEnd).clickable(role = Role.Button, onClick = onSkip).padding(horizontal = Side, vertical = 8.dp))
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Side),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(44.dp))
            Column {
                Text(title, style = FttType.largeTitleBold())
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = FttType.subheadline(), color = FttColors.TextPrimary)
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
        if (bottom != null) Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 40.dp), contentAlignment = Alignment.Center) { bottom() }
    }
}

@Composable
private fun AuthField(
    label: String, value: String, placeholder: String, onChange: (String) -> Unit,
    keyboard: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    /** Figma "Create a password inserted": the first password field shows the text, the eye toggles it. */
    plainByDefault: Boolean = false,
    error: String? = null,
    errors: List<String> = emptyList(),
    clearable: Boolean = false,
) {
    var visible by remember { mutableStateOf(plainByDefault) }
    val all = listOfNotNull(error) + errors
    Column {
        Row(
            Modifier.fillMaxWidth().height(50.dp)
                .background(FttColors.Surface, RoundedCornerShape(10.dp))
                .then(if (all.isNotEmpty()) Modifier.border(1.dp, Red, RoundedCornerShape(10.dp)) else Modifier)
                .padding(start = Side, end = 8.dp),
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
            if (password && value.isNotEmpty()) Box(Modifier.clickable(role = Role.Button, onClickLabel = if (visible) "Hide password" else "Show password") { visible = !visible }.padding(6.dp)) {
                EyeIcon(open = visible)
            }
        }
        all.forEach { Text(it, style = FttType.caption().copy(fontSize = 12.sp), color = Red, modifier = Modifier.padding(start = Side, top = 4.dp)) }
    }
}

/** Figma eye (shown) / closed eye with lashes (hidden). */
@Composable
private fun EyeIcon(open: Boolean) {
    Canvas(Modifier.size(width = 22.dp, height = 18.dp)) {
        val c = FttColors.LabelSecondary
        val w = size.width; val h = size.height
        val stroke = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round)
        if (open) {
            drawOval(c, topLeft = Offset(1f, h * 0.2f), size = Size(w - 2f, h * 0.6f), style = stroke)
            drawCircle(c, radius = h * 0.18f, center = Offset(w / 2, h / 2), style = stroke)
        } else {
            drawArc(c, 0f, 180f, false, topLeft = Offset(1f, -h * 0.1f), size = Size(w - 2f, h * 0.7f), style = stroke)
            for (i in 0..3) {
                val x = w * (0.2f + i * 0.2f)
                drawLine(c, Offset(x, h * 0.6f), Offset(x + (i - 1.5f) * 1.5f, h * 0.85f), strokeWidth = 1.2.dp.toPx(), cap = StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun LimeButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick, modifier = modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, FttColors.OnLime),
        colors = ButtonDefaults.buttonColors(containerColor = FttColors.Lime, contentColor = FttColors.OnLime),
    ) { Text(text, style = FttType.bodyBold()) }
}

@Composable
private fun OutlineButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, FttColors.ButtonBorder),
        colors = ButtonDefaults.buttonColors(containerColor = FttColors.BackgroundPrimary, contentColor = FttColors.TextSecondary),
    ) { Text(text, style = FttType.body().copy(fontWeight = FontWeight.Medium)) }
}

@Composable
private fun OrDivider() {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(FttColors.Separator))
        Text(A.OR, style = small(), color = FttColors.TextSecondary, modifier = Modifier.padding(horizontal = 12.dp))
        Box(Modifier.weight(1f).height(1.dp).background(FttColors.Separator))
    }
}

/** Google sign-in button (Google branding: white, "G" mark, "Continue with Google"). */
@Composable
private fun GoogleButton(onClick: () -> Unit) {
    Button(
        onClick = onClick, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, FttColors.ButtonBorder),
        colors = ButtonDefaults.buttonColors(containerColor = FttColors.Surface, contentColor = FttColors.Ink),
    ) {
        GoogleG()
        Spacer(Modifier.size(10.dp))
        Text(A.GOOGLE, style = FttType.body().copy(fontWeight = FontWeight.Medium))
    }
}

/** Facebook branding: blue #1877F2, white "f", "Continue with Facebook". */
@Composable
private fun FacebookButton(onClick: () -> Unit) {
    Button(
        onClick = onClick, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1877F2), contentColor = Color.White),
    ) {
        Box(Modifier.size(20.dp).background(Color.White, CircleShape), contentAlignment = Alignment.BottomCenter) {
            Text("f", style = FttType.bodyBold().copy(fontSize = 18.sp, lineHeight = 18.sp, fontWeight = FontWeight.ExtraBold), color = Color(0xFF1877F2))
        }
        Spacer(Modifier.size(10.dp))
        Text(A.FACEBOOK, style = FttType.body().copy(fontWeight = FontWeight.Medium))
    }
}

@Composable
private fun GoogleG() {
    Canvas(Modifier.size(20.dp)) {
        val sw = size.width * 0.2f
        val inset = sw / 2
        val tl = Offset(inset, inset); val sz = Size(size.width - sw, size.height - sw)
        val st = Stroke(width = sw)
        drawArc(Color(0xFFEA4335), 200f, 115f, false, tl, sz, style = st)   // red (top)
        drawArc(Color(0xFFFBBC05), 135f, 65f, false, tl, sz, style = st)    // yellow (left-bottom)
        drawArc(Color(0xFF34A853), 45f, 90f, false, tl, sz, style = st)     // green (bottom)
        drawArc(Color(0xFF4285F4), -15f, 60f, false, tl, sz, style = st)    // blue (right)
        drawLine(Color(0xFF4285F4), Offset(size.width / 2, size.height / 2), Offset(size.width - inset, size.height / 2), strokeWidth = sw)
    }
}

@Composable
private fun TermsLine(onTerms: () -> Unit) {
    Text(
        buildAnnotatedString {
            append(A.TERMS_PREFIX); append("\n")
            withStyle(SpanStyle(color = FttColors.Ink, fontWeight = FontWeight.SemiBold)) { append(A.TERMS_LINK) }
            append(A.TERMS_SUFFIX)
        },
        style = small().copy(fontSize = 12.sp, lineHeight = 17.sp), color = FttColors.TextSecondary, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = A.TERMS_LINK, onClick = onTerms).padding(vertical = 4.dp),
    )
}

@Composable
private fun BottomLink(prefix: String, link: String, onClick: () -> Unit) {
    Text(
        buildAnnotatedString {
            append(prefix)
            withStyle(SpanStyle(color = FttColors.Ink, fontWeight = FontWeight.SemiBold)) { append(link) }
        },
        style = FttType.subheadline(), color = FttColors.TextSecondary,
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick).padding(8.dp),
    )
}

@Composable
private fun Notice(text: String) {
    Text(text, style = small(), color = Red, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun Sheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).clickable(onClick = onDismiss)) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(FttColors.BackgroundPrimary, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .clickable(enabled = false) {}.navigationBarsPadding().padding(horizontal = Side).padding(top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(width = 36.dp, height = 5.dp).background(Color(0x4D3C3C43), RoundedCornerShape(3.dp)))
            content()
        }
    }
}

/** Figma "Registration check your email" / "Pw recovery requested". */
@Composable
private fun CheckEmailSheet(onOpen: () -> Unit, onClose: () -> Unit) = Sheet(onClose) {
    Text(A.CHECK_EMAIL_TITLE, style = FttType.title1Bold().copy(fontSize = 20.sp), modifier = Modifier.padding(top = 16.dp))
    Text(A.CHECK_EMAIL_TEXT, style = FttType.subheadline(), modifier = Modifier.padding(bottom = 8.dp))
    LimeButton(A.CHECK_EMAIL_APP, onClick = onOpen)
    OutlineButton(A.CLOSE, onClose)
}

/**
 * No Figma frame: our own 3×3 picture check ("Select all images with cars") in the app's sheet style.
 * Tiles are bundled images; which ones match is known only to the server. Wrong → a new puzzle.
 */
@Composable
private fun CaptchaSheet(auth: AccountAuth, onPassed: (String) -> Unit, onOffline: () -> Unit, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var challenge by remember { mutableStateOf<CaptchaChallenge?>(null) }
    var selected by remember { mutableStateOf(setOf<Int>()) }
    var wrong by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var round by remember { mutableStateOf(0) }
    LaunchedEffect(round) {
        loading = true; selected = emptySet()
        val c = auth.newCaptcha()
        if (c == null) onOffline() else { challenge = c; loading = false }
    }
    Sheet(onClose) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(A.CAPTCHA_TITLE, style = FttType.title1Bold().copy(fontSize = 20.sp))
                val c = challenge
                if (c != null) Text(
                    buildAnnotatedString {
                        append(A.captchaPrompt("").trimEnd())
                        append(" ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(c.label) }
                    },
                    style = FttType.subheadline(), color = FttColors.TextPrimary,
                )
            }
            Box(Modifier.size(40.dp).clickable(role = Role.Button, onClickLabel = "New puzzle") { wrong = false; round++ }, contentAlignment = Alignment.Center) {
                RefreshIcon()
            }
        }
        if (wrong) Text(A.CAPTCHA_WRONG, style = small(), color = Red)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cell = (maxWidth - 8.dp) / 3
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (row in 0 until 3) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (col in 0 until 3) {
                        val i = row * 3 + col
                        val tile = challenge?.tiles?.getOrNull(i)
                        CaptchaTile(tile, i in selected, loading, Modifier.size(cell)) { if (!loading) selected = CaptchaRules.toggle(selected, i) }
                    }
                }
            }
        }
        LimeButton(A.CAPTCHA_VERIFY, Modifier.padding(top = 2.dp)) {
            val c = challenge ?: return@LimeButton
            if (!CaptchaRules.canVerify(selected) || loading) return@LimeButton
            loading = true
            scope.launch {
                val token = auth.verifyCaptcha(c.id, selected)
                if (token != null) onPassed(token) else { wrong = true; round++ }
            }
        }
        OutlineButton(A.CLOSE, onClose)
    }
}

@OptIn(ExperimentalResourceApi::class)
@Composable
private fun CaptchaTile(tile: String?, selected: Boolean, loading: Boolean, modifier: Modifier, onClick: () -> Unit) {
    var bmp by remember(tile) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(tile) {
        bmp = tile?.let { runCatching { Res.readBytes(CaptchaRules.tilePath(it)).decodeToImageBitmap() }.getOrNull() }
    }
    Box(
        modifier.clip(RoundedCornerShape(8.dp)).background(FttColors.SectionCard)
            .then(if (selected) Modifier.border(3.dp, FttColors.StartLime, RoundedCornerShape(8.dp)) else Modifier)
            .clickable(role = Role.Checkbox, onClick = onClick),
    ) {
        val b = bmp
        if (b != null && !loading) Image(b, null, Modifier.fillMaxSize().padding(if (selected) 6.dp else 0.dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop)
        if (selected) Box(
            Modifier.align(Alignment.TopStart).padding(4.dp).size(22.dp).background(FttColors.StartLime, CircleShape).border(1.dp, FttColors.OnLime, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text("✓", style = FttType.caption().copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = FttColors.OnLime) }
    }
}

@Composable
private fun RefreshIcon() {
    Canvas(Modifier.size(22.dp)) {
        val st = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        drawArc(FttColors.TextPrimary, 30f, 300f, false, topLeft = Offset(size.width * 0.15f, size.height * 0.15f), size = Size(size.width * 0.7f, size.height * 0.7f), style = st)
        val tip = Offset(size.width * 0.80f, size.height * 0.30f)
        drawLine(FttColors.TextPrimary, tip, Offset(tip.x, tip.y - size.height * 0.2f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        drawLine(FttColors.TextPrimary, tip, Offset(tip.x - size.width * 0.2f, tip.y), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
    }
}

/** Figma "You're registered" / "Success screen" (password changed). */
@Composable
private fun AuthSuccess(title: String, text: String, onContinue: () -> Unit) {
    Column(Modifier.fillMaxSize().background(FttColors.BackgroundPrimary).statusBarsPadding().navigationBarsPadding().padding(horizontal = Side)) {
        Spacer(Modifier.height(86.dp))
        Text(title, style = FttType.largeTitleBold())
        Spacer(Modifier.height(4.dp))
        Text(text, style = FttType.subheadline())
        Spacer(Modifier.height(24.dp))
        Column(
            Modifier.weight(1f, fill = false).fillMaxWidth().background(FttColors.Surface, RoundedCornerShape(12.dp)).padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(painterResource(Res.drawable.auth_mascot), null, Modifier.fillMaxWidth().aspectRatio(1312f / 1199f).weight(1f, fill = false))
            Spacer(Modifier.height(24.dp))
            Text(A.SUCCESS_CARD, style = FttType.subheadlineBold(), textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(16.dp))
        LimeButton(A.CONTINUE, Modifier.padding(bottom = 12.dp), onContinue)
    }
}

/** Figma "Log in/Privacy Policy" frame: "Terms and conditions" text screen. */
@Composable
fun TermsScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(FttColors.BackgroundPrimary).statusBarsPadding().navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(44.dp)) {
            Box(Modifier.align(Alignment.CenterStart).padding(start = 8.dp).clickable(role = Role.Button, onClick = onBack)) { BackChevron() }
            Text(A.TERMS_TITLE, style = FttType.bodyBold(), modifier = Modifier.align(Alignment.Center))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Side, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Terms.blocks.forEach { b ->
                Text(b.text, style = FttType.subheadline().copy(fontSize = 16.sp, lineHeight = 22.sp,
                    fontWeight = if (b.heading) FontWeight.SemiBold else FontWeight.Normal))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Parses an incoming URI into an [AuthLink] (only the configured App Link host or the app scheme). */
fun parseAuthLink(uri: String, host: String?): AuthLink? = AuthLink.parse(uri, host)
