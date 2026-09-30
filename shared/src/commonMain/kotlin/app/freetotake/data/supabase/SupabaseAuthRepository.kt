// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
// Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.data.supabase

import app.freetotake.domain.model.Session
import app.freetotake.domain.model.User
import app.freetotake.domain.model.UserId
import app.freetotake.domain.repository.AuthRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Session persistence: supabase-kt stores the session on-device and refreshes the
 * token automatically, so a valid session survives app restarts. We only map its
 * status to the domain [Session]; an expired session that cannot refresh = Guest (gate applies).
 */
class SupabaseAuthRepository(private val client: SupabaseClient) : AuthRepository {

    override val session: Flow<Session> = client.auth.sessionStatus.map { status ->
        when (status) {
            is SessionStatus.Authenticated -> {
                val id = status.session.user?.id
                if (id == null) Session.Guest else Session.Authenticated(loadUser(id))
            }
            is SessionStatus.NotAuthenticated -> Session.Guest
            is SessionStatus.RefreshFailure -> Session.Guest
            else -> Session.Restoring // Initializing
        }
    }

    /**
     * PLACEHOLDER (AuthIntegrationPoint): real sign-in is deferred. Debug "Continue as test user" uses a Supabase
     * anonymous session (needs "Allow anonymous sign-ins" in the dashboard) so server-side flows can be tested.
     */
    suspend fun signInAnonymously(): Boolean = runCatching { client.auth.signInAnonymously() }.isSuccess

    override suspend fun signOut() {
        client.auth.signOut()
    }

    private suspend fun loadUser(id: String): User = runCatching {
        client.from("profiles")
            .select { filter { eq("id", id) } }
            .decodeSingle<ProfileRow>()
            .let { User(UserId(it.id), it.nickname, it.publicName, it.avatarUrl) }
    }.getOrElse { User(UserId(id), nickname = "") }

    @Serializable
    private data class ProfileRow(
        val id: String,
        val nickname: String,
        @SerialName("public_name") val publicName: String? = null,
        @SerialName("avatar_url") val avatarUrl: String? = null,
    )
}
