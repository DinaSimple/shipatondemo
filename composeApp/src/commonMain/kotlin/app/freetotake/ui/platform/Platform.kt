// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Dina Elokhova (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.freetotake.domain.location.GeoPoint

/**
 * Map view (Android: MapLibre + OpenFreeMap tiles, no key/fees). The map is only a picker:
 * the selected point is the map centre under the fixed pin, reported when the camera stops.
 * [moveTo] animates the camera whenever it changes (search result, "my location").
 */
@Composable
expect fun PickupMap(
    modifier: Modifier,
    initialCenter: GeoPoint,
    moveTo: GeoPoint?,
    onCameraIdle: (GeoPoint) -> Unit,
)

/** System back handling. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)

/** Photo picked on this device (content URI), center-cropped. */
@Composable
expect fun LocalPhoto(uri: String, modifier: Modifier)

/** Uploaded listing photo (public storage URL), center-cropped; [placeholder] while loading / on failure. */
@Composable
expect fun RemotePhoto(url: String, modifier: Modifier, placeholder: @Composable () -> Unit)
