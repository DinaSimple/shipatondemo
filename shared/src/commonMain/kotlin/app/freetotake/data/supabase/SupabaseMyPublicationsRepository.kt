// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
// Copyright (C) 2026 Dina Elokhova (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.data.supabase

import app.freetotake.domain.location.AreaLabel
import app.freetotake.domain.location.GeoPoint
import app.freetotake.domain.location.PickupPoint
import app.freetotake.domain.model.ClaimId
import app.freetotake.domain.model.ClaimStatus
import app.freetotake.domain.model.Item
import app.freetotake.domain.model.ItemId
import app.freetotake.domain.model.ItemStatus
import app.freetotake.domain.model.Timestamp
import app.freetotake.domain.model.UserId
import app.freetotake.domain.publish.Collector
import app.freetotake.domain.publish.MeetupDetails
import app.freetotake.domain.publish.PickupSchedule
import app.freetotake.domain.publish.PublicationOverview
import app.freetotake.domain.request.DateKey
import app.freetotake.domain.request.PickupSlot
import app.freetotake.domain.request.TimeOfDay
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A pending request as the publisher reviews it. */
data class ReviewRequest(
    val claimId: ClaimId,
    val takerNickname: String,
    val status: ClaimStatus,
    val createdAt: Timestamp,
    val message: String?,
    val slot: PickupSlot?,
    val avatarUrl: String? = null,
)

/** My Publications (spec 0.12): list, review requests, cancel, meetup details for editing. */
interface MyPublicationsRepository {
    suspend fun list(): List<PublicationOverview>
    suspend fun requests(item: ItemId): List<ReviewRequest>
    suspend fun approve(claim: ClaimId)
    suspend fun reject(claim: ClaimId)
    /** RPC `withdraw_item`: 2 h cutoff, requesters notified, publication archived. */
    suspend fun cancel(item: ItemId)
    suspend fun meetup(item: ItemId): Pair<PickupPoint?, MeetupDetails>
}

class SupabaseMyPublicationsRepository(private val client: SupabaseClient) : MyPublicationsRepository {

    @OptIn(ExperimentalTime::class)
    private fun ts(s: String) = Timestamp(Instant.parse(s).toEpochMilliseconds())

    override suspend fun list(): List<PublicationOverview> =
        client.from("my_publications").select { order("created_at", Order.DESCENDING) }
            .decodeList<PubRow>().map { r ->
                PublicationOverview(
                    item = Item(
                        id = ItemId(r.id), giverId = UserId(r.giverId), title = r.title, description = r.description,
                        photoUrls = r.photoPaths.map { client.storage.from("item-photos").publicUrl(it) },
                        photoPaths = r.photoPaths,
                        category = r.category,
                        status = ItemStatus.entries.firstOrNull { it.name.equals(r.status, true) } ?: ItemStatus.AVAILABLE,
                        createdAt = ts(r.createdAt),
                        area = AreaLabel(r.areaCity, r.areaPostal),
                        expiresAt = r.expiresAt?.let(::ts),
                        approxPoint = if (r.approxLat != null && r.approxLng != null) GeoPoint(r.approxLat, r.approxLng) else null,
                    ),
                    schedule = if (r.scheduleDate != null && r.scheduleFrom != null && r.scheduleTo != null)
                        PickupSchedule(DateKey.parse(r.scheduleDate), TimeOfDay.parse(r.scheduleFrom), TimeOfDay.parse(r.scheduleTo)) else null,
                    scheduleStart = r.scheduleStart?.let(::ts),
                    pendingRequests = r.pendingRequests,
                    collector = if (r.collectorNickname != null && r.collectorPickupAt != null) Collector(r.collectorNickname, ts(r.collectorPickupAt)) else null,
                )
            }

    override suspend fun requests(item: ItemId): List<ReviewRequest> =
        client.from("item_requests").select(Columns.raw("id,status,created_at,message,requested_date,requested_time,taker_nickname,taker_avatar_url")) {
            filter { eq("item_id", item.value); eq("status", "pending_approval") }
            order("created_at", Order.ASCENDING)
        }.decodeList<ReqRow>().map { r ->
            ReviewRequest(
                ClaimId(r.id), r.takerNickname, ClaimStatus.fromDb(r.status), ts(r.createdAt), r.message,
                if (r.requestedDate != null && r.requestedTime != null) PickupSlot(DateKey.parse(r.requestedDate), TimeOfDay.parse(r.requestedTime)) else null,
                r.takerAvatarUrl,
            )
        }

    override suspend fun approve(claim: ClaimId) {
        // pickup time defaults to the slot the requester chose (v1.6 approve_claim)
        client.postgrest.rpc("approve_claim", buildJsonObject { put("p_claim_id", claim.value) })
    }

    override suspend fun reject(claim: ClaimId) {
        client.postgrest.rpc("reject_claim", buildJsonObject { put("p_claim_id", claim.value) })
    }

    override suspend fun cancel(item: ItemId) {
        client.postgrest.rpc("withdraw_item", buildJsonObject { put("p_item_id", item.value) })
    }

    override suspend fun meetup(item: ItemId): Pair<PickupPoint?, MeetupDetails> {
        val r = client.from("item_pickup_points").select(Columns.raw("lat,lng,place_name,address,notes")) { filter { eq("item_id", item.value) } }.decodeList<PointRow>().firstOrNull()
            ?: return null to MeetupDetails()
        val items = client.from("items").select(Columns.raw("area_city,area_postal")) { filter { eq("id", item.value) } }.decodeList<AreaRow>().firstOrNull()
        return PickupPoint(GeoPoint(r.lat, r.lng), r.placeName, r.address, AreaLabel(items?.areaCity, items?.areaPostal)) to
            MeetupDetails(r.notes.orEmpty())
    }

    @Serializable
    private data class PubRow(
        val id: String,
        @SerialName("giver_id") val giverId: String,
        val title: String,
        val description: String = "",
        @SerialName("photo_paths") val photoPaths: List<String> = emptyList(),
        val category: String? = null,
        @SerialName("area_city") val areaCity: String? = null,
        @SerialName("area_postal") val areaPostal: String? = null,
        @SerialName("approx_lat") val approxLat: Double? = null,
        @SerialName("approx_lng") val approxLng: Double? = null,
        val status: String,
        @SerialName("created_at") val createdAt: String,
        @SerialName("expires_at") val expiresAt: String? = null,
        @SerialName("schedule_date") val scheduleDate: String? = null,
        @SerialName("schedule_from") val scheduleFrom: String? = null,
        @SerialName("schedule_to") val scheduleTo: String? = null,
        @SerialName("schedule_start") val scheduleStart: String? = null,
        @SerialName("pending_requests") val pendingRequests: Int = 0,
        @SerialName("collector_nickname") val collectorNickname: String? = null,
        @SerialName("collector_pickup_at") val collectorPickupAt: String? = null,
    )

    @Serializable
    private data class ReqRow(
        val id: String,
        val status: String,
        @SerialName("created_at") val createdAt: String,
        val message: String? = null,
        @SerialName("requested_date") val requestedDate: String? = null,
        @SerialName("requested_time") val requestedTime: String? = null,
        @SerialName("taker_nickname") val takerNickname: String,
        @SerialName("taker_avatar_url") val takerAvatarUrl: String? = null,
    )

    @Serializable
    private data class PointRow(
        val lat: Double,
        val lng: Double,
        @SerialName("place_name") val placeName: String? = null,
        val address: String? = null,
        val notes: String? = null,
    )

    @Serializable
    private data class AreaRow(
        @SerialName("area_city") val areaCity: String? = null,
        @SerialName("area_postal") val areaPostal: String? = null,
    )
}
