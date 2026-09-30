// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
// Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.data.supabase

import app.freetotake.domain.profile.MyProfile
import app.freetotake.domain.profile.NotificationPrefs
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A server event to show as a device notification. */
data class PendingNotification(val id: String, val kind: String, val itemTitle: String?, val body: String?)

/** My Profile (spec 0.14). */
interface ProfileRepository {
    suspend fun me(): MyProfile?
    suspend fun setPublicName(name: String?)
    /** Supabase sends a confirmation email to the new address. */
    suspend fun setEmail(email: String)
    /** Compressed JPEG → avatars/<uid>/avatar_<ts>.jpg; returns the public URL. */
    suspend fun uploadAvatar(jpeg: ByteArray): String
    /** Verifies the old password, then sets the new one; the session stays signed in. */
    suspend fun changePassword(oldPassword: String, newPassword: String)
    suspend fun sendPasswordReset(email: String)
    suspend fun setPrefs(prefs: NotificationPrefs)
    /** Coarse area (city only) for "new giveaways nearby". */
    suspend fun setHomeCity(city: String?)
    suspend fun unreadNotifications(): List<PendingNotification>
    suspend fun markRead(ids: List<String>)
}

class SupabaseProfileRepository(private val client: SupabaseClient) : ProfileRepository {

    private fun uid(): String = client.auth.currentUserOrNull()?.id ?: error("NOT_AUTHENTICATED")

    override suspend fun me(): MyProfile? {
        val user = client.auth.currentUserOrNull() ?: return null
        val r = client.from("profiles")
            .select(Columns.raw("nickname,public_name,avatar_url,notify_claim_approved,notify_new_request,notify_nearby")) { filter { eq("id", user.id) } }
            .decodeList<Row>().firstOrNull() ?: return null
        val email = user.email?.takeIf { it.isNotBlank() }
        return MyProfile(
            id = user.id, nickname = r.nickname, publicName = r.publicName, avatarUrl = r.avatarUrl, email = email,
            isAnonymous = email == null,
            prefs = NotificationPrefs(r.notifyClaimApproved, r.notifyNewRequest, r.notifyNearby),
            passwordLogin = user.appMetadata?.get("providers")?.toString()?.contains("email") ?: true,
        )
    }

    override suspend fun setPublicName(name: String?) {
        client.from("profiles").update(buildJsonObject { put("public_name", name) }) { filter { eq("id", uid()) } }
    }

    override suspend fun setEmail(email: String) {
        client.auth.updateUser { this.email = email.trim() }
    }

    override suspend fun uploadAvatar(jpeg: ByteArray): String {
        val id = uid()
        val path = "$id/avatar_${kotlin.random.Random.nextLong(Long.MAX_VALUE)}.jpg"   // new name → no stale cache
        client.storage.from("avatars").upload(path, jpeg) { upsert = true }
        val url = client.storage.from("avatars").publicUrl(path)
        client.from("profiles").update(buildJsonObject { put("avatar_url", url) }) { filter { eq("id", id) } }
        return url
    }

    override suspend fun changePassword(oldPassword: String, newPassword: String) {
        val email = client.auth.currentUserOrNull()?.email ?: error("NO_EMAIL_ACCOUNT")
        // Re-check the old password (Supabase itself doesn't ask for it), then update; no sign-out.
        runCatching { client.auth.signInWith(Email) { this.email = email; password = oldPassword } }
            .onFailure { error("WRONG_OLD_PASSWORD") }
        client.auth.updateUser { password = newPassword }
    }

    /** Not used by the app since v1.12 (reset goes through the captcha-gated `auth-email` function). */
    override suspend fun sendPasswordReset(email: String) {
        error("USE_RECOVERY_FLOW")
    }

    override suspend fun setPrefs(prefs: NotificationPrefs) {
        client.from("profiles").update(buildJsonObject {
            put("notify_claim_approved", prefs.claimApproved)
            put("notify_new_request", prefs.newRequest)
            put("notify_nearby", prefs.nearby)
        }) { filter { eq("id", uid()) } }
    }

    override suspend fun setHomeCity(city: String?) {
        client.from("profiles").update(buildJsonObject { put("home_city", city?.trim()?.take(80)) }) { filter { eq("id", uid()) } }
    }

    override suspend fun unreadNotifications(): List<PendingNotification> =
        client.from("my_notifications").select(Columns.raw("id,kind,item_title,body,read_at")) {
            order("created_at", Order.DESCENDING)
            limit(50)
        }.decodeList<NoteRow>().filter { it.readAt == null }.reversed()
            .map { PendingNotification(it.id, it.kind, it.itemTitle, it.body) }

    override suspend fun markRead(ids: List<String>) {
        if (ids.isEmpty()) return
        @OptIn(kotlin.time.ExperimentalTime::class)
        val now = kotlin.time.Clock.System.now().toString()
        client.from("notifications").update(buildJsonObject { put("read_at", now) }) { filter { isIn("id", ids) } }
    }

    @Serializable
    private data class Row(
        val nickname: String,
        @SerialName("public_name") val publicName: String? = null,
        @SerialName("avatar_url") val avatarUrl: String? = null,
        @SerialName("notify_claim_approved") val notifyClaimApproved: Boolean = true,
        @SerialName("notify_new_request") val notifyNewRequest: Boolean = true,
        @SerialName("notify_nearby") val notifyNearby: Boolean = true,
    )

    @Serializable
    private data class NoteRow(
        val id: String,
        val kind: String,
        @SerialName("item_title") val itemTitle: String? = null,
        val body: String? = null,
        @SerialName("read_at") val readAt: String? = null,
    )
}
