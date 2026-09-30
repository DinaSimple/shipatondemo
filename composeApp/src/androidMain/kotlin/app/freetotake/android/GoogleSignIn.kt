// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
// Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.android

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import app.freetotake.domain.auth.GoogleResult
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.security.MessageDigest
import java.util.UUID

/**
 * v1.15: native Google account picker (Credential Manager) → Google ID token for Supabase.
 * A random nonce is hashed into the Google request; Supabase checks the raw one (no token replay).
 * [webClientId] = OAuth "Web application" client ID from Google Cloud (same one configured in Supabase → Auth → Google).
 */
suspend fun googleIdToken(activity: Activity, webClientId: String): GoogleResult {
    if (webClientId.isBlank()) return GoogleResult.NotConfigured
    val rawNonce = UUID.randomUUID().toString()
    val hashed = MessageDigest.getInstance("SHA-256").digest(rawNonce.toByteArray()).joinToString("") { "%02x".format(it) }
    val option = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(false)
        .setServerClientId(webClientId)
        .setNonce(hashed)
        .setAutoSelectEnabled(false)
        .build()
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
    return try {
        val credential = CredentialManager.create(activity).getCredential(activity, request).credential
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            GoogleResult.Token(GoogleIdTokenCredential.createFrom(credential.data).idToken, rawNonce)
        } else GoogleResult.Failed("unexpected credential")
    } catch (e: GetCredentialCancellationException) {
        GoogleResult.Cancelled
    } catch (e: NoCredentialException) {
        GoogleResult.NoAccount
    } catch (e: Exception) {
        GoogleResult.Failed(e.message)
    }
}
