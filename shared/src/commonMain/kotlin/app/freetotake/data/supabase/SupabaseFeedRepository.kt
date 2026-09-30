// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.data.supabase

import app.freetotake.domain.catalog.FeedScope
import app.freetotake.domain.location.AreaLabel
import app.freetotake.domain.location.GeoPoint
import app.freetotake.domain.model.Item
import app.freetotake.domain.model.ItemId
import app.freetotake.domain.model.ItemStatus
import app.freetotake.domain.model.Timestamp
import app.freetotake.domain.model.UserId
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Public feed of active listings (view `active_items`: not withdrawn/given, not expired). */
interface FeedRepository {
    suspend fun activeFeed(scope: FeedScope, limit: Int): List<Item>
    /** Any real active publication service-wide (decides the educational example card). */
    suspend fun hasAnyActive(): Boolean
    /** Listings behind the user's requests — incl. reserved ones hidden from the public feed (0.10). */
    suspend fun itemsByIds(ids: List<ItemId>): List<Item>
    /** The user's own publications, any status (RLS: owners see all their items). */
    suspend fun myItems(user: UserId): List<Item>
}

class SupabaseFeedRepository(private val client: SupabaseClient) : FeedRepository {

    override suspend fun activeFeed(scope: FeedScope, limit: Int): List<Item> =
        client.from("active_items").select {
            if (scope is FeedScope.City) filter { ilike("area_city", scope.city.trim()) }
            order("created_at", Order.DESCENDING)
            limit(limit.toLong())
        }.decodeList<Row>().map { it.toItem() }

    override suspend fun hasAnyActive(): Boolean =
        client.from("active_items").select { limit(1) }.decodeList<Row>().isNotEmpty()

    override suspend fun itemsByIds(ids: List<ItemId>): List<Item> =
        if (ids.isEmpty()) emptyList()
        else client.from("items").select(Columns.raw(COLUMNS.joinToString(","))) {
            filter { isIn("id", ids.map { it.value }) }
        }.decodeList<Row>().map { it.toItem() }

    override suspend fun myItems(user: UserId): List<Item> =
        client.from("items").select(Columns.raw(COLUMNS.joinToString(","))) {
            filter { eq("giver_id", user.value) }
            order("created_at", Order.DESCENDING)
        }.decodeList<Row>().map { it.toItem() }

    @OptIn(ExperimentalTime::class)
    private fun Row.toItem() = Item(
        id = ItemId(id),
        giverId = UserId(giverId),
        title = title,
        description = description,
        photoUrls = photoPaths.map { client.storage.from("item-photos").publicUrl(it) },
        photoPaths = photoPaths,
        category = category,
        status = ItemStatus.entries.firstOrNull { it.name.equals(status, ignoreCase = true) } ?: ItemStatus.AVAILABLE,
        createdAt = Timestamp(Instant.parse(createdAt).toEpochMilliseconds()),
        area = AreaLabel(areaCity, areaPostal),
        expiresAt = expiresAt?.let { Timestamp(Instant.parse(it).toEpochMilliseconds()) },
        approxPoint = if (approxLat != null && approxLng != null) GeoPoint(approxLat, approxLng) else null,
    )

    private companion object {
        /** Public columns only (exact pickup point lives in a private table). */
        val COLUMNS = listOf("id", "giver_id", "title", "description", "photo_paths", "category", "area_city", "area_postal",
            "status", "created_at", "expires_at", "approx_lat", "approx_lng")
    }

    @Serializable
    private data class Row(
        val id: String,
        @SerialName("giver_id") val giverId: String,
        val title: String,
        val description: String = "",
        @SerialName("photo_paths") val photoPaths: List<String> = emptyList(),
        val category: String? = null,
        @SerialName("area_city") val areaCity: String? = null,
        @SerialName("area_postal") val areaPostal: String? = null,
        val status: String,
        @SerialName("created_at") val createdAt: String,
        @SerialName("expires_at") val expiresAt: String? = null,
        @SerialName("approx_lat") val approxLat: Double? = null,
        @SerialName("approx_lng") val approxLng: Double? = null,
    )
}
