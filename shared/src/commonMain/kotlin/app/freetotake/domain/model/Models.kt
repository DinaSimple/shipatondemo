// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.domain.model

data class User(
    val id: UserId,
    /** Service-generated, always present, unique (e.g. "SunnyOtter4821"). */
    val nickname: String,
    /** Optional preferred public name set by the user. */
    val publicName: String? = null,
    val avatarUrl: String? = null,
) {
    /** What other users see: preferred public name when set, otherwise the generated nickname. */
    val displayName: String
        get() = publicName?.trim()?.takeIf { it.isNotEmpty() } ?: nickname
}

/** Lifecycle of a giveaway (GIVE role). */
enum class ItemStatus {
    /** Visible to everyone, accepts claims. */
    AVAILABLE,
    /** A claim is approved; pickup is scheduled. Other claims wait in the queue. */
    RESERVED,
    /**
     * Taker cancelled an approved claim ≤ 8h before pickup. Slot is NOT auto-released
     * to the queue; the giver decides what happens next. (Final behaviour: see SPEC open question Q1.)
     */
    AWAITING_GIVER_DECISION,
    /** Handed over. Terminal. */
    GIVEN,
    /** Giver removed the post. Terminal. */
    WITHDRAWN;

    val isTerminal: Boolean get() = this == GIVEN || this == WITHDRAWN
    /** 0.10: once a recipient is selected (RESERVED) the listing takes no new requests. */
    val acceptsClaims: Boolean get() = this == AVAILABLE
}

data class Item(
    val id: ItemId,
    val giverId: UserId,
    val title: String,
    val description: String,
    val photoUrls: List<String> = emptyList(),
    /** Storage paths matching [photoUrls] (needed to keep/remove photos when editing). */
    val photoPaths: List<String> = emptyList(),
    val category: String? = null,
    val status: ItemStatus = ItemStatus.AVAILABLE,
    val createdAt: Timestamp,
    /** Public approximate area (city, postcode); exact point is private (see PickupPrivacy). */
    val area: app.freetotake.domain.location.AreaLabel? = null,
    /** Service expiration/timeout; null = no expiry set. */
    val expiresAt: Timestamp? = null,
    /** Public ~1 km point (items.approx_lat/lng) — used for "Closest nearby first". */
    val approxPoint: app.freetotake.domain.location.GeoPoint? = null,
)

/** Lifecycle of a request (TAKE role). */
enum class ClaimStatus {
    PENDING_APPROVAL,
    APPROVED,
    REJECTED,
    /** Cancelled by taker while pending, or approved but > 8h before pickup. */
    CANCELLED_BY_TAKER,
    /** Cancelled by taker after approval, ≤ 8h before pickup. */
    CANCELLED_LATE,
    /** Auto-closed because the item was given to someone else or withdrawn. */
    CLOSED,
    COMPLETED;

    val isActive: Boolean get() = this == PENDING_APPROVAL || this == APPROVED

    companion object {
        /** DB enum value (`pending_approval` …) → status. */
        fun fromDb(v: String): ClaimStatus = valueOf(v.uppercase())
    }
}

data class Claim(
    val id: ClaimId,
    val itemId: ItemId,
    val takerId: UserId,
    val status: ClaimStatus,
    val createdAt: Timestamp,
    val message: String? = null,
    val pickupAt: Timestamp? = null,
    /** Pickup option the requester chose from the publisher's availability (spec 0.8). */
    val requested: app.freetotake.domain.request.PickupSlot? = null,
    /** Taker removed it from My Claims (trash); kept server-side for the publisher's history. */
    val hiddenByTaker: Boolean = false,
)

sealed interface Session {
    /** Persisted session is still being restored on app start — UI waits, gate treats as not signed in. */
    data object Restoring : Session
    data object Guest : Session
    /** [expiresAt] = access-token expiry when known; null = managed/refreshed by the auth provider. */
    data class Authenticated(val user: User, val expiresAt: Timestamp? = null) : Session

    fun isValidAt(now: Timestamp?): Boolean = when (this) {
        is Authenticated -> expiresAt == null || now == null || now < expiresAt
        else -> false
    }
}
