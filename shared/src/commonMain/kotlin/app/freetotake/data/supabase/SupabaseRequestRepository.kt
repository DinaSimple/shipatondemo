// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
// Copyright (C) 2026 Dina Elokhova (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.data.supabase

import app.freetotake.domain.model.Claim
import app.freetotake.domain.model.ClaimId
import app.freetotake.domain.model.ClaimStatus
import app.freetotake.domain.model.ItemId
import app.freetotake.domain.model.Timestamp
import app.freetotake.domain.model.UserId
import app.freetotake.domain.request.DateKey
import app.freetotake.domain.request.PickupSlot
import app.freetotake.domain.request.TimeOfDay
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Request submission (spec 0.8): publisher availability, send request, the requester's own requests. */
interface RequestRepository {
    suspend fun availability(item: ItemId): List<PickupSlot>
    /** RPC `submit_claim` — server re-validates slot, note length, own item, duplicates. */
    suspend fun submit(item: ItemId, slot: PickupSlot, note: String): Claim
    /** Requests of the signed-in user (RLS: takers see only their own). */
    suspend fun myRequests(user: UserId): List<Claim>
    /** RPC `remove_my_request` — trash on a My Claims card (cancels if open, then hides). */
    suspend fun remove(claim: ClaimId)
}

/** Heart (spec 0.9/0.10). Own rows only (RLS). */
interface FavoritesRepository {
    suspend fun list(): Set<ItemId>
    suspend fun add(item: ItemId)
    suspend fun remove(item: ItemId)
}

class SupabaseFavoritesRepository(private val client: SupabaseClient) : FavoritesRepository {
    override suspend fun list(): Set<ItemId> =
        client.from("favorites").select(io.github.jan.supabase.postgrest.query.Columns.raw("item_id")).decodeList<FavRow>().map { ItemId(it.itemId) }.toSet()
    override suspend fun add(item: ItemId) {
        client.from("favorites").insert(buildJsonObject { put("item_id", item.value) })
    }
    override suspend fun remove(item: ItemId) {
        client.from("favorites").delete { filter { eq("item_id", item.value) } }
    }
    @Serializable
    private data class FavRow(@SerialName("item_id") val itemId: String)
}

class SupabaseRequestRepository(private val client: SupabaseClient) : RequestRepository {

    override suspend fun availability(item: ItemId): List<PickupSlot> =
        client.from("item_availability").select {
            filter { eq("item_id", item.value) }
        }.decodeList<SlotRow>().map { PickupSlot(DateKey.parse(it.date), TimeOfDay.parse(it.time)) }

    override suspend fun submit(item: ItemId, slot: PickupSlot, note: String): Claim =
        client.postgrest.rpc(
            "submit_claim",
            buildJsonObject {
                put("p_item_id", item.value)
                put("p_message", note)
                put("p_slot_date", slot.date.toString())
                put("p_slot_time", slot.time.label())
            },
        ).decodeAs<ClaimRow>().toClaim()

    override suspend fun myRequests(user: UserId): List<Claim> =
        client.from("claims").select {
            filter { eq("taker_id", user.value); eq("hidden_by_taker", false) }
            order("created_at", Order.DESCENDING)
        }.decodeList<ClaimRow>().map { it.toClaim() }

    override suspend fun remove(claim: ClaimId) {
        client.postgrest.rpc("remove_my_request", buildJsonObject { put("p_claim_id", claim.value) })
    }

    @Serializable
    private data class SlotRow(
        @SerialName("slot_date") val date: String,
        @SerialName("slot_time") val time: String,
    )

    @Serializable
    private data class ClaimRow(
        val id: String,
        @SerialName("item_id") val itemId: String,
        @SerialName("taker_id") val takerId: String,
        val status: String,
        @SerialName("created_at") val createdAt: String,
        val message: String? = null,
        @SerialName("pickup_at") val pickupAt: String? = null,
        @SerialName("requested_date") val requestedDate: String? = null,
        @SerialName("requested_time") val requestedTime: String? = null,
        @SerialName("hidden_by_taker") val hiddenByTaker: Boolean = false,
    ) {
        @OptIn(ExperimentalTime::class)
        fun toClaim() = Claim(
            id = ClaimId(id),
            itemId = ItemId(itemId),
            takerId = UserId(takerId),
            status = ClaimStatus.fromDb(status),
            createdAt = Timestamp(Instant.parse(createdAt).toEpochMilliseconds()),
            message = message,
            pickupAt = pickupAt?.let { Timestamp(Instant.parse(it).toEpochMilliseconds()) },
            requested = if (requestedDate != null && requestedTime != null)
                PickupSlot(DateKey.parse(requestedDate), TimeOfDay.parse(requestedTime)) else null,
            hiddenByTaker = hiddenByTaker,
        )
    }
}
