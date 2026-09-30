// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Dina Elokhova (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.data.supabase

import app.freetotake.domain.auth.AuthError
import app.freetotake.domain.auth.AuthLink
import app.freetotake.domain.auth.CaptchaChallenge
import app.freetotake.domain.auth.LinkPurpose
import app.freetotake.domain.auth.LinkRequestResult
import app.freetotake.domain.auth.RedeemResult
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.Facebook
import io.github.jan.supabase.exceptions.RestException
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** Email + password accounts (spec "Login and auth"). */
interface AccountAuth {
    /** null = signed in (session persisted by supabase-kt). */
    suspend fun signIn(email: String, password: String): AuthError?
    /** v1.15: Google ID token → Supabase session (creates the account on first use). */
    suspend fun signInWithGoogle(idToken: String, rawNonce: String): AuthError?
    /** v1.16.1: providers switched on in Supabase (public /auth/v1/settings); null = unknown (offline). */
    suspend fun enabledProviders(): Set<String>?
    /** v1.15.1: opens Facebook login in the browser; the session arrives via the freetotake://login-callback deep link. */
    suspend fun signInWithFacebook(): AuthError?
    /** New 3×3 picture challenge (null = offline). */
    suspend fun newCaptcha(): CaptchaChallenge?
    /** Token proving the check was passed (null = wrong answer / expired → show a new challenge). */
    suspend fun verifyCaptcha(challengeId: String, selected: Set<Int>): String?
    suspend fun requestLink(purpose: LinkPurpose, email: String, captchaToken: String): LinkRequestResult
    /** Consumes the emailed link (server: valid, unexpired, single-use) and opens a short session to set the password. */
    suspend fun redeem(link: AuthLink): RedeemResult
    /** Sets the password, then closes the session: the user logs in with the new credentials (Figma). */
    suspend fun setPassword(password: String): AuthError?
}

/**
 * Calls the `auth-email` Edge Function over HTTPS with the public (anon) key only — the service-role key stays on the server.
 * Passwords go only to Supabase Auth over TLS; nothing is stored in plaintext on the device.
 */
class SupabaseAccountAuth(
    private val client: SupabaseClient,
    supabaseUrl: String,
    private val anonKey: String,
    private val http: HttpClient = HttpClient(),
) : AccountAuth {
    private val fnUrl = supabaseUrl.trimEnd('/') + "/functions/v1/auth-email"
    private val settingsUrl = supabaseUrl.trimEnd('/') + "/auth/v1/settings"
    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun call(body: JsonObject): JsonObject? = runCatching {
        val text = http.post(fnUrl) {
            header("apikey", anonKey)
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }.bodyAsText()
        json.parseToJsonElement(text).jsonObject
    }.getOrNull()

    private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.ok(): Boolean = (this["ok"] as? JsonPrimitive)?.booleanOrNull == true

    override suspend fun signIn(email: String, password: String): AuthError? = try {
        client.auth.signInWith(Email) { this.email = email.trim().lowercase(); this.password = password }
        null
    } catch (e: RestException) {
        AuthError.WRONG_CREDENTIALS
    } catch (e: Exception) {
        AuthError.NETWORK
    }

    override suspend fun signInWithGoogle(idToken: String, rawNonce: String): AuthError? = try {
        client.auth.signInWith(IDToken) { this.idToken = idToken; provider = Google; nonce = rawNonce }
        null
    } catch (e: RestException) {
        AuthError.UNKNOWN
    } catch (e: Exception) {
        AuthError.NETWORK
    }

    override suspend fun enabledProviders(): Set<String>? = runCatching {
        val text = http.get(settingsUrl) { header("apikey", anonKey) }.bodyAsText()
        val ext = json.parseToJsonElement(text).jsonObject["external"]?.jsonObject ?: return@runCatching null
        ext.filterValues { (it as? JsonPrimitive)?.booleanOrNull == true }.keys
    }.getOrNull()

    override suspend fun signInWithFacebook(): AuthError? = try {
        client.auth.signInWith(Facebook)
        null
    } catch (e: Exception) {
        AuthError.NETWORK
    }

    override suspend fun newCaptcha(): CaptchaChallenge? {
        val r = call(buildJsonObject { put("action", "captcha_new") })?.takeIf { it.ok() } ?: return null
        val tiles = (r["tiles"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: return null
        return runCatching { CaptchaChallenge(r.str("challengeId") ?: return null, r.str("label").orEmpty(), tiles) }.getOrNull()
    }

    override suspend fun verifyCaptcha(challengeId: String, selected: Set<Int>): String? {
        val r = call(buildJsonObject {
            put("action", "captcha_verify"); put("challengeId", challengeId)
            put("selected", JsonArray(selected.sorted().map { JsonPrimitive(it) }))
        }) ?: return null
        return if (r.ok()) r.str("captchaToken") else null
    }

    override suspend fun requestLink(purpose: LinkPurpose, email: String, captchaToken: String): LinkRequestResult {
        val r = call(buildJsonObject {
            put("action", purpose.wire); put("email", email.trim().lowercase()); put("captchaToken", captchaToken)
        }) ?: return LinkRequestResult.Failed(AuthError.NETWORK)
        return if (r.ok()) LinkRequestResult.Sent else LinkRequestResult.Failed(AuthError.of(r.str("error")))
    }

    override suspend fun redeem(link: AuthLink): RedeemResult {
        val r = call(buildJsonObject { put("action", "redeem"); put("token", link.token) })
            ?: return RedeemResult.Failed(AuthError.NETWORK, null, link.purpose)
        val purpose = LinkPurpose.of(r.str("purpose")) ?: link.purpose
        if (!r.ok()) return RedeemResult.Failed(AuthError.of(r.str("error")), r.str("email"), purpose)
        val email = r.str("email").orEmpty()
        val tokenHash = r.str("tokenHash") ?: return RedeemResult.Failed(AuthError.UNKNOWN, email, purpose)
        return try {
            client.auth.verifyEmailOtp(type = OtpType.Email.MAGIC_LINK, tokenHash = tokenHash)
            RedeemResult.Ok(purpose, email)
        } catch (e: Exception) {
            RedeemResult.Failed(AuthError.LINK_INVALID, email, purpose)
        }
    }

    override suspend fun setPassword(password: String): AuthError? = try {
        client.auth.updateUser { this.password = password }
        runCatching { client.auth.signOut() }
        null
    } catch (e: RestException) {
        AuthError.UNKNOWN
    } catch (e: Exception) {
        AuthError.NETWORK
    }
}
