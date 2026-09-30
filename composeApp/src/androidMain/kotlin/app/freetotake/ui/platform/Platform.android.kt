// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Dina Elokhova (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.ui.platform

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.freetotake.domain.location.GeoPoint
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/** OpenFreeMap "positron" — light grey style matching the design. Attribution is shown by MapLibre. */
private const val STYLE_URL = "https://tiles.openfreemap.org/styles/positron"
private const val PICK_ZOOM = 16.0

@Composable
actual fun PickupMap(
    modifier: Modifier,
    initialCenter: GeoPoint,
    moveTo: GeoPoint?,
    onCameraIdle: (GeoPoint) -> Unit,
) {
    val context = LocalContext.current
    val onIdle by rememberUpdatedState(onCameraIdle)
    val holder = remember { arrayOfNulls<MapLibreMap>(1) }
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply {
            onCreate(null)
            getMapAsync { map ->
                holder[0] = map
                map.setStyle(Style.Builder().fromUri(STYLE_URL))
                map.uiSettings.isRotateGesturesEnabled = false
                map.uiSettings.isTiltGesturesEnabled = false
                map.uiSettings.isCompassEnabled = false
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(initialCenter.lat, initialCenter.lng)).zoom(PICK_ZOOM).build()
                map.addOnCameraIdleListener {
                    map.cameraPosition.target?.let { onIdle(GeoPoint(it.latitude, it.longitude)) }
                }
            }
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(obs)
        onDispose {
            lifecycle.removeObserver(obs)
            mapView.onPause(); mapView.onStop(); mapView.onDestroy()
        }
    }
    LaunchedEffect(moveTo) {
        val target = moveTo ?: return@LaunchedEffect
        holder[0]?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(target.lat, target.lng), PICK_ZOOM))
    }
    AndroidView(factory = { mapView }, modifier = modifier)
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = BackHandler(enabled, onBack)

private val photoCache = android.util.LruCache<String, androidx.compose.ui.graphics.ImageBitmap>(24)

/** Decode with subsampling so previews stay light (long edge ~800 px). */
private fun decodeSampled(open: () -> java.io.InputStream?): android.graphics.Bitmap? {
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    open()?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 800) sample *= 2
    val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
    return open()?.use { android.graphics.BitmapFactory.decodeStream(it, null, opts) }
}

@Composable
actual fun LocalPhoto(uri: String, modifier: Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val bitmap by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(photoCache.get(uri), uri) {
        if (value == null) value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                // ImageDecoder handles HEIC on Android 9+; BitmapFactory otherwise.
                val u = android.net.Uri.parse(uri)
                val bmp = if (android.os.Build.VERSION.SDK_INT >= 28) android.graphics.ImageDecoder.decodeBitmap(
                    android.graphics.ImageDecoder.createSource(context.contentResolver, u)
                ) { d, info, _ ->
                    val long = maxOf(info.size.width, info.size.height)
                    if (long > 800) d.setTargetSize(info.size.width * 800 / long, info.size.height * 800 / long)
                } else decodeSampled { context.contentResolver.openInputStream(u) }
                bmp?.asImageBitmap()
            }.getOrNull()
        }?.also { photoCache.put(uri, it) }
    }
    bitmap?.let { androidx.compose.foundation.Image(it, null, modifier, contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
        ?: androidx.compose.foundation.layout.Box(modifier)
}

@Composable
actual fun RemotePhoto(url: String, modifier: Modifier, placeholder: @Composable () -> Unit) {
    val bitmap by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(photoCache.get(url), url) {
        if (value == null) value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { decodeSampled { java.net.URL(url).openStream() }?.asImageBitmap() }.getOrNull()
        }?.also { photoCache.put(url, it) }
    }
    val b = bitmap
    if (b != null) androidx.compose.foundation.Image(b, null, modifier, contentScale = androidx.compose.ui.layout.ContentScale.Crop)
    else placeholder()
}
