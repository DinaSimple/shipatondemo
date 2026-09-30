// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Dina Elokhova (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.domain.auth

import app.freetotake.domain.profile.PasswordPolicy
import app.freetotake.domain.profile.PasswordRule
import app.freetotake.domain.profile.ProfileRules

/** Copy from Figma "Log in", "Registration", "Pw setup" (spec "Login and auth"). */
object AuthCopy {
    const val SKIP = "Skip"
    const val LOGIN_TITLE = "Log in"
    const val LOGIN_SUBTITLE = "Insert your email and password"
    const val EMAIL = "Email"
    const val PASSWORD = "Password"
    const val FORGOT = "Forgot password?"
    const val LOGIN_BUTTON = "Log in"
    const val WRONG_VALUE = "Wrong or missing value"
    const val TERMS_PREFIX = "When you click “Log in”, you automatically accept"
    const val TERMS_LINK = "Conditions and terms of use"
    const val TERMS_SUFFIX = " for this service"
    const val NO_ACCOUNT = "Not account yet? "
    const val REGISTER = "Register"

    const val SIGNUP_TITLE = "Sign up"
    const val SIGNUP_SUBTITLE = "Insert your email"
    const val CONTINUE = "Continue"
    const val HAVE_ACCOUNT = "Already have an account? "
    const val SIGN_IN = "Sign in"

    const val CHECK_EMAIL_TITLE = "Check your email"
    const val CHECK_EMAIL_TEXT = "Check your email to see the instruction"
    const val CHECK_EMAIL_APP = "Check my email app"
    const val CLOSE = "Close"

    const val CREATE_PASSWORD_TITLE = "Create a password"
    const val CREATE_PASSWORD_SUBTITLE = "Create a password"
    const val NEW_PASSWORD_TITLE = "Create a new password"
    const val NEW_PASSWORD_SUBTITLE = "Create a  new password"
    const val INSERT_PASSWORD = "Insert your password"
    const val CONFIRM_PASSWORD = "Confirm your password"
    const val PASSWORDS_DIFFER = "Passwords don’t match"

    const val REGISTERED_TITLE = "You’re registered!"
    const val REGISTERED_TEXT = "Your account has been successfully created."
    const val SUCCESS_CARD = "Log in to start searching or posting your giveaways!"
    const val READY_TITLE = "Ready!"
    const val READY_TEXT = "Your password has been successfully changed."

    const val RECOVERY_TITLE = "Password reovery"   // sic — Figma copy
    const val RECOVERY_SUBTITLE = "Insert your email used for registration"
    const val REMEMBERED = "Remembered your password?"
    const val LOG_IN_LINK = "Log in"

    const val TERMS_TITLE = "Terms and conditions"

    // v1.15 Google sign-in
    const val GOOGLE = "Continue with Google"
    const val FACEBOOK = "Continue with Facebook"
    const val SOCIAL_NOT_READY = "Sign-in is being set up. Please try again later."
    const val SOCIAL_SUBTITLE = "Sign in to give away, request items and chat with neighbours"
    const val SOCIAL_SIGNUP_SUBTITLE = "Create your account with Google or Facebook"
    const val SOCIAL_LOGIN_SUBTITLE = "Log in with your Google or Facebook account"
    const val OR = "or"

    // Captcha sheet (no Figma frame; adapted to the product style)
    const val CAPTCHA_TITLE = "Quick check"
    const val CAPTCHA_VERIFY = "Verify"
    const val CAPTCHA_WRONG = "Not quite — try this one."
    fun captchaPrompt(label: String) = "Select all images with $label"

    // Link result (no Figma frame)
    const val REDIRECTING = "Redirecting…"
    const val LINK_EXPIRED_TITLE = "This link has expired"
    const val LINK_EXPIRED_TEXT = "Links are valid for 5 minutes and work once. We can send you a new one."
    const val LINK_USED_TITLE = "This link was already used"
    const val LINK_INVALID_TITLE = "This link is not valid"
    const val LINK_OTHER_TEXT = "Request a new link, or log in if you already set your password."
    const val SEND_NEW_LINK = "Send a new link"
}

/**
 * v1.15.2: the email + password flow (sign up by email link, email log in, password recovery) stays in the code
 * but is hidden from users until a better email architecture is chosen. Flip to true to bring it back.
 */
object AuthFeatures {
    const val EMAIL_FLOW = false

    /** Screens of the email flow that are replaced by the social sign-in page while it is hidden. */
    /**
     * v1.16.5: the social-only page follows Figma "Registration Default" / "Log in Default":
     * Sign up (from Profile → Sign up) vs Log in, each with its bottom switch link.
     */
    data class SocialPage(val title: String, val subtitle: String, val bottomPrefix: String, val bottomLink: String, val switchTo: AuthStep, val skip: Boolean)

    fun socialPage(step: AuthStep): SocialPage =
        if (step == AuthStep.SignUpEmail) SocialPage(AuthCopy.SIGNUP_TITLE, AuthCopy.SOCIAL_SIGNUP_SUBTITLE, AuthCopy.HAVE_ACCOUNT, AuthCopy.SIGN_IN, AuthStep.Login, skip = false)
        else SocialPage(AuthCopy.LOGIN_TITLE, AuthCopy.SOCIAL_LOGIN_SUBTITLE, AuthCopy.NO_ACCOUNT, AuthCopy.REGISTER, AuthStep.SignUpEmail, skip = true)

    fun showsSocialOnly(step: AuthStep, emailFlow: Boolean = EMAIL_FLOW): Boolean =
        !emailFlow && (step == AuthStep.Login || step == AuthStep.SignUpEmail || step == AuthStep.Recovery)

    /**
     * Show a provider's button only when Supabase confirms it is switched on.
     * v1.16.3: fail-safe — still loading / offline / unknown → hidden (never open the "provider is not enabled" page).
     */
    fun showProvider(provider: String, enabled: Set<String>?): Boolean = enabled != null && provider in enabled
}

/** Why the auth link was sent. */
enum class LinkPurpose(val wire: String) {
    SIGN_UP("signup"), RECOVER("recover");
    companion object { fun of(wire: String?): LinkPurpose? = entries.firstOrNull { it.wire == wire } }
}

/** A verification link that came back into the app (Android App Link or freetotake:// fallback). */
data class AuthLink(val token: String, val purpose: LinkPurpose) {
    companion object {
        private val TOKEN = Regex("^[A-Za-z0-9_-]{20,128}$")

        /** Accepts https://<host>/auth/confirm[/]?t=..&p=.. and freetotake://auth/confirm?t=..&p=.. ; anything else → null. */
        fun parse(uri: String, trustedHost: String?): AuthLink? {
            val u = uri.trim()
            val schemeEnd = u.indexOf("://").takeIf { it > 0 } ?: return null
            val scheme = u.substring(0, schemeEnd).lowercase()
            val rest = u.substring(schemeEnd + 3)
            val hostEnd = rest.indexOfAny(charArrayOf('/', '?', '#')).let { if (it < 0) rest.length else it }
            val host = rest.substring(0, hostEnd).lowercase()
            val pathAndQuery = rest.substring(hostEnd)
            val path = pathAndQuery.substringBefore('?').substringBefore('#').trimEnd('/')
            val ok = when (scheme) {
                "https" -> trustedHost != null && host == trustedHost.lowercase() && path == "/auth/confirm"
                "freetotake" -> host == "auth" && path == "/confirm"
                else -> false
            }
            if (!ok) return null
            val query = pathAndQuery.substringAfter('?', "").substringBefore('#')
            val params = query.split('&').filter { '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=') }
            val token = params["t"]?.takeIf { TOKEN.matches(it) } ?: return null
            val purpose = LinkPurpose.of(params["p"]) ?: return null
            return AuthLink(token, purpose)
        }
    }
}

/** Server error codes of the `auth-email` function → UI copy. */
enum class AuthError(val code: String, val message: String) {
    EMAIL_TAKEN("EMAIL_TAKEN", "Sorry, try a different email or use forgot a password option"),
    NOT_REGISTERED("NOT_REGISTERED", "This email is not registered in the service"),
    INVALID_EMAIL("INVALID_EMAIL", AuthCopy.WRONG_VALUE),
    CAPTCHA_FAILED("CAPTCHA_FAILED", "The check didn’t pass. Please try again."),
    CAPTCHA_REQUIRED("CAPTCHA_REQUIRED", "Please complete the quick check again."),
    RATE_LIMITED("RATE_LIMITED", "We just sent you a link. Please wait a minute before asking again."),
    LINK_EXPIRED("LINK_EXPIRED", AuthCopy.LINK_EXPIRED_TEXT),
    LINK_USED("LINK_USED", AuthCopy.LINK_OTHER_TEXT),
    LINK_INVALID("LINK_INVALID", AuthCopy.LINK_OTHER_TEXT),
    EMAIL_NOT_CONFIGURED("EMAIL_NOT_CONFIGURED", "Email sending isn’t set up yet. Please try again later."),
    WRONG_CREDENTIALS("WRONG_CREDENTIALS", AuthCopy.WRONG_VALUE),
    NETWORK("NETWORK", "No connection. Please try again."),
    UNKNOWN("UNKNOWN", "Something went wrong. Please try again.");

    companion object { fun of(code: String?): AuthError = entries.firstOrNull { it.code == code } ?: UNKNOWN }
}

/** Log in form (Figma "Sign In Screen_Error": both fields red with "Wrong or missing value"). */
data class LoginErrors(val email: Boolean = false, val password: Boolean = false) {
    val isValid get() = !email && !password
}

object AuthRules {
    fun isValidEmail(e: String) = ProfileRules.isValidEmail(e) && e.trim().length <= 254

    fun normalizeEmail(e: String) = e.trim().lowercase()

    /** Local check before calling the server; a server rejection marks both fields (no hint which one was wrong). */
    fun loginErrors(email: String, password: String) = LoginErrors(email = !isValidEmail(email), password = password.isEmpty())

    val WRONG_CREDENTIALS = LoginErrors(email = true, password = true)

    /** Email-only screens (Sign up, Password recovery): Continue is always tappable; empty/invalid → inline error. */
    fun emailError(email: String): String? = if (isValidEmail(email)) null else AuthCopy.WRONG_VALUE
}

/** "Create a password" / "Create a new password": entered twice; ≥6, upper, lower, digit, special symbol. */
data class NewPasswordErrors(val password: List<PasswordRule> = emptyList(), val confirm: String? = null) {
    val isValid get() = password.isEmpty() && confirm == null
}

object NewPasswordForm {
    fun canContinue(password: String, confirm: String) = password.isNotEmpty() && confirm.isNotEmpty()
    fun validate(password: String, confirm: String) = NewPasswordErrors(
        password = PasswordPolicy.unmet(password),
        confirm = if (confirm != password) AuthCopy.PASSWORDS_DIFFER else null,
    )
}

/** Screens of the auth flow. */
sealed interface AuthStep {
    data object Login : AuthStep
    data object SignUpEmail : AuthStep
    data object Recovery : AuthStep
    /** Link opened; redeeming it with the server. */
    data class Redirecting(val link: AuthLink) : AuthStep
    data class LinkProblem(val error: AuthError, val email: String?, val purpose: LinkPurpose?) : AuthStep
    data class CreatePassword(val purpose: LinkPurpose, val email: String) : AuthStep
    data class Success(val purpose: LinkPurpose, val email: String) : AuthStep
}

object AuthFlow {
    /** Link redeemed → a short-lived session exists → set the password. */
    fun afterRedeem(purpose: LinkPurpose, email: String): AuthStep = AuthStep.CreatePassword(purpose, email)

    /** Password saved → the one-off session is closed; success screen (Figma: "You're registered!" / "Ready!"). */
    fun afterPasswordSet(purpose: LinkPurpose, email: String): AuthStep = AuthStep.Success(purpose, email)

    /** Spec: after sign-up the user is asked to enter credentials → Log in (email prefilled). */
    fun afterSuccess(): AuthStep = AuthStep.Login

    fun afterRedeemFailed(error: AuthError, email: String?, purpose: LinkPurpose?): AuthStep = AuthStep.LinkProblem(error, email, purpose)

    /** Expired links offer a resend (captcha again) when the email is known; other problems → the matching email screen. */
    fun canResend(p: AuthStep.LinkProblem) = p.error == AuthError.LINK_EXPIRED && p.email != null && p.purpose != null

    fun backFrom(step: AuthStep): AuthStep? = when (step) {
        AuthStep.Login -> null                      // leaves auth (Skip / back)
        AuthStep.SignUpEmail, AuthStep.Recovery -> AuthStep.Login
        is AuthStep.CreatePassword, is AuthStep.LinkProblem, is AuthStep.Redirecting -> AuthStep.Login
        is AuthStep.Success -> AuthStep.Login
    }
}

/** 3×3 picture challenge; images are bundled in the app as files/cap/<tile>.jpg, the answer stays on the server. */
data class CaptchaChallenge(val id: String, val label: String, val tiles: List<String>) {
    init { require(tiles.size == CaptchaRules.GRID) }
}

object CaptchaRules {
    const val GRID = 9
    /** A solved check is valid on the server for 15 min; reuse it (no second puzzle) for 14 min or until an email was sent. */
    const val REUSE_MILLIS = 14 * 60_000L
    fun tilePath(tile: String) = "files/cap/$tile.jpg"
    fun canVerify(selected: Set<Int>) = selected.isNotEmpty()
    fun toggle(selected: Set<Int>, index: Int): Set<Int> = if (index in selected) selected - index else selected + index
    fun reusable(solvedAtMillis: Long?, nowMillis: Long) = solvedAtMillis != null && nowMillis - solvedAtMillis < REUSE_MILLIS
}

/** Google account picker on the device (Android Credential Manager). */
sealed interface GoogleResult {
    /** ID token for Supabase + the raw nonce whose SHA-256 was sent to Google (replay protection). */
    data class Token(val idToken: String, val rawNonce: String) : GoogleResult
    data object Cancelled : GoogleResult
    data object NoAccount : GoogleResult
    data object NotConfigured : GoogleResult
    data class Failed(val reason: String?) : GoogleResult
}

object GoogleCopy {
    const val NO_ACCOUNT = "Add a Google account on this phone, or use your email instead."
    const val NOT_CONFIGURED = "Google sign-in isn't set up yet."
    const val FAILED = "Google sign-in didn't work. Please try again."
    fun message(r: GoogleResult): String? = when (r) {
        is GoogleResult.Token, GoogleResult.Cancelled -> null
        GoogleResult.NoAccount -> NO_ACCOUNT
        GoogleResult.NotConfigured -> NOT_CONFIGURED
        is GoogleResult.Failed -> FAILED
    }
}

/** Result of asking the server to email a link. */
sealed interface LinkRequestResult {
    data object Sent : LinkRequestResult
    data class Failed(val error: AuthError) : LinkRequestResult
}

/** Result of redeeming a link. */
sealed interface RedeemResult {
    data class Ok(val purpose: LinkPurpose, val email: String) : RedeemResult
    data class Failed(val error: AuthError, val email: String?, val purpose: LinkPurpose?) : RedeemResult
}
