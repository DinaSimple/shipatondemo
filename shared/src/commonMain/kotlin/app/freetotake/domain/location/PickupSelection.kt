// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.domain.location

/** Where the candidate pickup point came from. */
enum class PickupSource {
    /** Picked from the place directory / search results (café, bar, street…). */
    DIRECTORY,
    /** Map centre under the pin, labelled by reverse geocoding. */
    MAP_PIN,
}

sealed interface PickupValidation {
    data object Valid : PickupValidation
    data object NoSelection : PickupValidation
    /** Reverse geocoding still running. */
    data object Pending : PickupValidation
    /** Coordinates without any resolvable place/name — cannot be confirmed. */
    data object Unresolved : PickupValidation
    data object OutOfRange : PickupValidation
}

/**
 * Spec 0.5: "Continue" only for a valid selection — a directory place or a point the
 * provider resolved to a name/address. Exact rules depend on the MVP provider (Photon/OSM).
 */
object PickupSelectionValidator {

    fun validate(candidate: PickupPoint?, source: PickupSource?, resolving: Boolean): PickupValidation {
        if (resolving) return PickupValidation.Pending
        if (candidate == null || source == null) return PickupValidation.NoSelection
        val p = candidate.point
        if (p.lat !in -90.0..90.0 || p.lng !in -180.0..180.0) return PickupValidation.OutOfRange
        val hasLabel = !candidate.placeName.isNullOrBlank() || !candidate.address.isNullOrBlank()
        return when (source) {
            PickupSource.DIRECTORY -> if (!candidate.placeName.isNullOrBlank()) PickupValidation.Valid else PickupValidation.Unresolved
            PickupSource.MAP_PIN -> if (hasLabel) PickupValidation.Valid else PickupValidation.Unresolved
        }
    }

    fun canContinue(v: PickupValidation): Boolean = v == PickupValidation.Valid
}

/** Confirmation screen after "Continue" (spec 0.5). */
object PickupConfirmation {
    const val MESSAGE = "Ready to pick up point for your free giveaway successfully added."
    const val AUTO_RETURN_MS = 3_000L   // unused since v1.14 (Continue button)
    const val CONTINUE = "Continue"
}
