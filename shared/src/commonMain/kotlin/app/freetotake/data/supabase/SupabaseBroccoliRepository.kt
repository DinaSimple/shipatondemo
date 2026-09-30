// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.data.supabase

import app.freetotake.domain.rewards.PendingHandover
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** v1.17 broccoli balance + handover confirmation. All changes happen on the server (triggers / RPCs). */
interface BroccoliRepository {
    suspend fun balance(): Int
    suspend fun pendingHandovers(): List<PendingHandover>
    /** Returns the new balance. */
    suspend fun confirmHandover(itemId: String, collected: Boolean): Int
}

class SupabaseBroccoliRepository(private val client: SupabaseClient) : BroccoliRepository {
    override suspend fun balance(): Int =
        client.postgrest.rpc("my_broccoli", buildJsonObject { }).data.trim().toInt()

    override suspend fun pendingHandovers(): List<PendingHandover> =
        client.postgrest.rpc("pending_handovers", buildJsonObject { }).decodeList<Row>().map { PendingHandover(it.itemId, it.title, it.collectorNickname) }

    override suspend fun confirmHandover(itemId: String, collected: Boolean): Int =
        client.postgrest.rpc("confirm_handover", buildJsonObject { put("p_item_id", itemId); put("p_collected", collected) })
            .data.trim().toInt()

    @Serializable
    private data class Row(
        @SerialName("item_id") val itemId: String,
        val title: String,
        @SerialName("collector_nickname") val collectorNickname: String? = null,
    )
}
