// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
// Copyright (C) 2026 Dina Elokhova (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.domain.location

import app.freetotake.domain.model.Claim
import app.freetotake.domain.model.ClaimStatus
import app.freetotake.domain.model.Item
import app.freetotake.domain.model.UserId
import kotlin.math.round

/** Approximate area shown publicly and on Home, e.g. "Barcelona, 08019". */
data class AreaLabel(val city: String?, val postalCode: String?) {
    /** null when neither part is known. */
    fun format(): String? {
        val parts = listOfNotNull(city?.trim()?.takeIf { it.isNotEmpty() }, postalCode?.trim()?.takeIf { it.isNotEmpty() })
        return parts.takeIf { it.isNotEmpty() }?.joinToString(", ")
    }
}

/** An exact pickup point chosen by the user (map or search). Private until approval. */
data class PickupPoint(
    val point: GeoPoint,
    val placeName: String? = null,
    val address: String? = null,
    val area: AreaLabel = AreaLabel(null, null),
) {
    /** Text for the Home location field. */
    fun displayText(): String =
        listOfNotNull(placeName?.takeIf { it.isNotBlank() }, address?.takeIf { it.isNotBlank() })
            .distinct().joinToString(", ")
            .ifEmpty { area.format() ?: "${point.lat}, ${point.lng}" }
}

/** What the Home "My giveaways location" row shows (spec 0.4). */
sealed interface HomeLocation {
    /** Waiting for device location / reverse geocoding. */
    data object Detecting : HomeLocation
    /** Device approximate area — "Barcelona, 08019". */
    data class DeviceArea(val label: String) : HomeLocation
    /** Pickup point the user chose explicitly. */
    data class Chosen(val pickup: PickupPoint) : HomeLocation
    /** No permission / no location: user must choose manually ("Choose your pickup point"). */
    data object NotSet : HomeLocation
}

object HomeLocationResolver {
    /**
     * Precedence: explicit user choice > device approximate area > nothing.
     * A device area is used only when permission is granted and a label could be resolved.
     */
    fun resolve(
        chosen: PickupPoint?,
        access: LocationAccess,
        deviceArea: AreaLabel?,
        lookupInProgress: Boolean,
    ): HomeLocation {
        if (chosen != null) return HomeLocation.Chosen(chosen)
        if (!access.hasDeviceLocation) return HomeLocation.NotSet
        deviceArea?.format()?.let { return HomeLocation.DeviceArea(it) }
        return if (lookupInProgress) HomeLocation.Detecting else HomeLocation.NotSet
    }
}

/**
 * "Keep exact pickup information private until a giver approves a request."
 * Everyone else sees the area label and a coarse point (≈1 km grid).
 */
object PickupPrivacy {

    fun canSeeExact(viewer: UserId?, item: Item, claimsForItem: List<Claim>): Boolean {
        if (viewer == null) return false
        if (viewer == item.giverId) return true
        return claimsForItem.any {
            it.itemId == item.id && it.takerId == viewer &&
                (it.status == ClaimStatus.APPROVED || it.status == ClaimStatus.COMPLETED)
        }
    }

    /** Rounds to 2 decimals (~1.1 km) — enough for "near you", useless for finding a door. */
    fun approximate(p: GeoPoint): GeoPoint = GeoPoint(round2(p.lat), round2(p.lng))

    private fun round2(v: Double) = round(v * 100.0) / 100.0
}
