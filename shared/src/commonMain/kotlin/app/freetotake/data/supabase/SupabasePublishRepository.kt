// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
// Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.data.supabase

import app.freetotake.domain.model.ItemId
import app.freetotake.domain.publish.PublicationDraft
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Create publication (spec 0.11): upload compressed photos to the user's folder, then one atomic RPC. */
interface PublishRepository {
    /** Backend user id of the current session (null = not signed in on the server). */
    fun currentUserId(): String?
    /** Uploads a compressed JPEG; returns its storage path `<uid>/<draft>/<n>.jpg`. */
    suspend fun uploadPhoto(draftId: String, index: Int, jpeg: ByteArray): String
    suspend fun publish(draft: PublicationDraft, photoPaths: List<String>, timeZone: String): ItemId
    /** Edit (spec 0.12) — same payload + item id; only before a collector is assigned. */
    suspend fun update(itemId: String, draft: PublicationDraft, photoPaths: List<String>, timeZone: String)
    /** Removes photos the publisher dropped while editing (own folder only, storage RLS). */
    suspend fun deletePhotos(paths: List<String>)
}

class SupabasePublishRepository(private val client: SupabaseClient) : PublishRepository {

    override fun currentUserId(): String? = client.auth.currentUserOrNull()?.id

    override suspend fun uploadPhoto(draftId: String, index: Int, jpeg: ByteArray): String {
        val uid = currentUserId() ?: error("NOT_AUTHENTICATED")
        val path = "$uid/$draftId/$index.jpg"
        client.storage.from("item-photos").upload(path, jpeg) { upsert = true }
        return path
    }

    override suspend fun publish(draft: PublicationDraft, photoPaths: List<String>, timeZone: String): ItemId =
        ItemId(client.postgrest.rpc("publish_item", params(draft, photoPaths, timeZone)).decodeAs<Row>().id)

    override suspend fun update(itemId: String, draft: PublicationDraft, photoPaths: List<String>, timeZone: String) {
        val base = params(draft, photoPaths, timeZone)
        client.postgrest.rpc("update_item", buildJsonObject {
            put("p_item_id", itemId)
            base.forEach { (k, v) -> put(k, v) }
        })
    }

    override suspend fun deletePhotos(paths: List<String>) {
        if (paths.isNotEmpty()) client.storage.from("item-photos").delete(paths)
    }

    private fun params(draft: PublicationDraft, photoPaths: List<String>, timeZone: String): kotlinx.serialization.json.JsonObject {
        val p = draft.pickup ?: error("LOCATION_REQUIRED")
        fun opt(s: String) = if (s.isBlank()) JsonNull else JsonPrimitive(s.trim())
        val params = buildJsonObject {
            put("p_title", draft.title.trim())
            put("p_description", draft.description)
            put("p_category", draft.category?.key)
            put("p_lat", p.point.lat)
            put("p_lng", p.point.lng)
            put("p_place_name", p.placeName)
            put("p_address", p.address ?: p.displayText())
            put("p_area_city", p.area.city)
            put("p_area_postal", p.area.postalCode)
            put("p_date", draft.date.toString())
            put("p_from", draft.from?.label())
            put("p_to", draft.to?.label())
            put("p_photo_paths", buildJsonArray { photoPaths.forEach { add(JsonPrimitive(it)) } })
            put("p_notes", opt(draft.notes))
            put("p_time_zone", timeZone)
        }
        return params
    }

    @Serializable
    private data class Row(val id: String)
}
