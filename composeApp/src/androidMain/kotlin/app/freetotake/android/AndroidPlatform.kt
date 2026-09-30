// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
// Copyright (C) 2026 Dina Elokhova (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.android

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.location.Geocoder
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import app.freetotake.domain.location.AreaLabel
import app.freetotake.domain.location.DeviceLocationProvider
import app.freetotake.domain.location.GeoPoint
import app.freetotake.domain.location.PlaceSuggestion
import app.freetotake.domain.location.ReverseGeocoder
import app.freetotake.domain.media.CompressedImage
import app.freetotake.domain.media.ImageCompressor
import app.freetotake.domain.media.ImagePolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.util.Locale
import kotlin.coroutines.resume

/** Approximate device location via the platform LocationManager (no Play Services dependency). */
class AndroidDeviceLocation(private val context: Context) : DeviceLocationProvider {
    @SuppressLint("MissingPermission") // caller checks LocationAccess first
    override suspend fun approximateLocation(): GeoPoint? = runCatching {
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { lm.isProviderEnabled(it) } ?: return null
        val fresh = if (Build.VERSION.SDK_INT >= 30) withTimeoutOrNull(8_000) {
            suspendCancellableCoroutine<android.location.Location?> { cont ->
                lm.getCurrentLocation(provider, null, context.mainExecutor) { cont.resume(it) }
            }
        } else null
        val loc = fresh ?: lm.getLastKnownLocation(provider) ?: return null
        GeoPoint(loc.latitude, loc.longitude)
    }.getOrNull()
}

/** Free on-device reverse geocoding (Android Geocoder); falls back to [fallback] (Photon). */
class AndroidReverseGeocoder(
    private val context: Context,
    private val fallback: ReverseGeocoder?,
) : ReverseGeocoder {

    override suspend fun areaAt(point: GeoPoint): AreaLabel? {
        val a = platformAddress(point)
        val label = a?.let { AreaLabel(it.locality ?: it.subAdminArea, it.postalCode) }
        return label?.takeIf { it.format() != null } ?: runCatching { fallback?.areaAt(point) }.getOrNull()
    }

    override suspend fun placeAt(point: GeoPoint): PlaceSuggestion? =
        runCatching { fallback?.placeAt(point) }.getOrNull() ?: platformAddress(point)?.let { a ->
            val street = listOfNotNull(a.thoroughfare, a.subThoroughfare).joinToString(" ").ifEmpty { null }
            PlaceSuggestion(
                name = a.featureName?.takeIf { it != a.subThoroughfare } ?: street ?: a.locality ?: "Selected point",
                subtitle = listOfNotNull(street, a.locality).distinct().joinToString(", ").ifEmpty { null },
                point = point,
                area = AreaLabel(a.locality, a.postalCode),
            )
        }

    @Suppress("DEPRECATION")
    private suspend fun platformAddress(p: GeoPoint): android.location.Address? {
        if (!Geocoder.isPresent()) return null
        val g = Geocoder(context, Locale.getDefault())
        return runCatching {
            if (Build.VERSION.SDK_INT >= 33) withTimeoutOrNull(6_000) {
                suspendCancellableCoroutine { cont ->
                    g.getFromLocation(p.lat, p.lng, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<android.location.Address>) { cont.resume(addresses.firstOrNull()) }
                        override fun onError(errorMessage: String?) { cont.resume(null) }
                    })
                }
            } else withContext(Dispatchers.IO) { g.getFromLocation(p.lat, p.lng, 1)?.firstOrNull() }
        }.getOrNull()
    }
}

/**
 * On-device compression before upload. HEIC/HEIF decoded by ImageDecoder (Android 9+;
 * Android 10+ for most HEIC photos from iPhone). Output JPEG, long edge ≤ 1600 px.
 */
class AndroidImageCompressor(private val context: Context) : ImageCompressor {
    override suspend fun compress(source: String): CompressedImage = withContext(Dispatchers.Default) {
        val uri = Uri.parse(source)
        val bitmap: Bitmap = if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val (w, h) = ImagePolicy.targetSize(info.size.width, info.size.height)
                decoder.setTargetSize(w, h)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val mime = context.contentResolver.getType(uri)
            require(!ImagePolicy.isHeif(mime, uri.lastPathSegment)) { "HEIC photos need Android 9 or newer" }
            val raw = context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
                ?: error("Unsupported image")
            val (w, h) = ImagePolicy.targetSize(raw.width, raw.height)
            if (w == raw.width) raw else Bitmap.createScaledBitmap(raw, w, h, true)
        }
        var quality = ImagePolicy.JPEG_QUALITY
        var bytes: ByteArray
        do {
            bytes = ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out); out.toByteArray()
            }
            quality -= 10
        } while (bytes.size > ImagePolicy.MAX_UPLOAD_BYTES && quality >= 40)
        CompressedImage(bytes, bitmap.width, bitmap.height)
    }
}
