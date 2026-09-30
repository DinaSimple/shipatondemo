// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.domain.location

data class GeoPoint(val lat: Double, val lng: Double)

/** Result of the location permission request after onboarding. */
enum class LocationAccess {
    NOT_ASKED,
    APPROXIMATE,
    PRECISE,
    DENIED;

    val hasDeviceLocation: Boolean get() = this == APPROXIMATE || this == PRECISE
}

enum class CatalogLocationMode {
    /** Sort/filter by distance from the device's approximate location. */
    NEARBY,
    /** No device location — user picks an area manually; catalog still fully browsable. */
    MANUAL_AREA,
}

object LocationPolicy {
    /** Catalog browsing is never blocked by missing location permission. */
    fun canBrowseCatalog(@Suppress("UNUSED_PARAMETER") access: LocationAccess): Boolean = true

    fun catalogMode(access: LocationAccess): CatalogLocationMode =
        if (access.hasDeviceLocation) CatalogLocationMode.NEARBY else CatalogLocationMode.MANUAL_AREA

    /**
     * Meetup point is ALWAYS an explicit user choice — the app never publishes
     * a location the user did not pick.
     */
    fun canPublishMeetupPoint(userSelected: GeoPoint?): Boolean = userSelected != null
}

/** Device location — behind an interface so the platform/provider stays replaceable. */
interface DeviceLocationProvider {
    suspend fun approximateLocation(): GeoPoint?
}

object LocationDefaults {
    /** Initial map centre when neither a chosen point nor device location exists (MVP market: Barcelona). */
    val FALLBACK_CENTER = GeoPoint(41.3874, 2.1686)
}
