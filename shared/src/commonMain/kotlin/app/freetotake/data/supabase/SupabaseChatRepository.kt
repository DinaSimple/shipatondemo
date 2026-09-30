// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
// Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
@file:OptIn(kotlin.time.ExperimentalTime::class)

package app.freetotake.data.supabase

import app.freetotake.domain.chat.ChatError
import app.freetotake.domain.chat.ChatInfo
import app.freetotake.domain.chat.ChatMessage
import app.freetotake.domain.chat.ChatSend
import app.freetotake.domain.model.Timestamp
import app.freetotake.domain.request.DateKey
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

/** Chat between a publisher and the approved collector (v1.14). Access and expiry are enforced by the server. */
interface ChatRepository {
    /** null = not a participant (or no approved collector yet). */
    suspend fun info(itemId: String): ChatInfo?
    suspend fun messages(itemId: String): List<ChatMessage>
    suspend fun send(itemId: String, body: String): ChatSend
}

class SupabaseChatRepository(private val client: SupabaseClient) : ChatRepository {

    override suspend fun info(itemId: String): ChatInfo? {
        val r = client.postgrest.rpc("chat_state", buildJsonObject { put("p_item_id", itemId) })
            .decodeList<StateRow>().firstOrNull() ?: return null
        if (!r.participant) return null
        val name = r.otherPublicName?.trim()?.takeIf { it.isNotEmpty() } ?: "@${r.otherNickname.orEmpty()}"
        return ChatInfo(itemId, r.itemTitle.orEmpty(), name, r.otherAvatarUrl, r.meetingAt?.let(::ts), r.open)
    }

    override suspend fun messages(itemId: String): List<ChatMessage> {
        val me = client.auth.currentUserOrNull()?.id
        return client.from("chat_messages").select(Columns.raw("id,sender_id,body,created_at")) {
            filter { eq("item_id", itemId) }
            order("created_at", Order.ASCENDING)
            limit(500)
        }.decodeList<MessageRow>().map { m ->
            val at = Instant.parse(m.createdAt)
            val local = at.toLocalDateTime(TimeZone.currentSystemDefault()).date
            ChatMessage(m.id, m.senderId == me, m.body, Timestamp(at.toEpochMilliseconds()), local.toString().split("-").let { (y, mo, d) -> DateKey(y.toInt(), mo.toInt(), d.toInt()) })   // ISO yyyy-MM-dd; API-version independent
        }
    }

    override suspend fun send(itemId: String, body: String): ChatSend = try {
        client.postgrest.rpc("send_chat_message", buildJsonObject { put("p_item_id", itemId); put("p_body", body) })
        ChatSend.Ok
    } catch (e: Exception) {
        ChatSend.Failed(ChatError.of(e.message))
    }

    private fun ts(s: String) = Timestamp(Instant.parse(s).toEpochMilliseconds())

    @Serializable
    private data class StateRow(
        val participant: Boolean = false,
        val open: Boolean = false,
        @SerialName("meeting_at") val meetingAt: String? = null,
        @SerialName("item_title") val itemTitle: String? = null,
        @SerialName("other_nickname") val otherNickname: String? = null,
        @SerialName("other_public_name") val otherPublicName: String? = null,
        @SerialName("other_avatar_url") val otherAvatarUrl: String? = null,
    )

    @Serializable
    private data class MessageRow(
        val id: String,
        @SerialName("sender_id") val senderId: String,
        val body: String,
        @SerialName("created_at") val createdAt: String,
    )
}
