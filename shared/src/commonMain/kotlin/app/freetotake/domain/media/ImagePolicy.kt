// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
// Copyright (C) 2026 Dina Elokhova (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.domain.media

/** Giveaway photo rules: ≤3 photos, compressed on-device before upload, HEIC/HEIF accepted. */
object ImagePolicy {
    const val MAX_PHOTOS = 3
    const val MAX_LONG_EDGE_PX = 1600
    const val JPEG_QUALITY = 80
    /** Hard cap after compression — keeps Supabase free-tier storage/egress low. */
    const val MAX_UPLOAD_BYTES = 600 * 1024
    const val OUTPUT_MIME = "image/jpeg"

    val ACCEPTED_INPUT_MIME = setOf(
        "image/jpeg", "image/png", "image/webp",
        "image/heic", "image/heif", "image/heic-sequence", "image/heif-sequence",
    )
    private val HEIF_EXTENSIONS = setOf("heic", "heif", "hif")

    fun isAccepted(mime: String?, fileName: String? = null): Boolean {
        val m = mime?.lowercase()
        if (m != null && m in ACCEPTED_INPUT_MIME) return true
        // Some pickers report HEIC as octet-stream: fall back to extension.
        val ext = fileName?.substringAfterLast('.', "")?.lowercase()
        return ext != null && (ext in HEIF_EXTENSIONS || ext in setOf("jpg", "jpeg", "png", "webp"))
    }

    fun isHeif(mime: String?, fileName: String? = null): Boolean =
        mime?.lowercase()?.startsWith("image/hei") == true ||
            fileName?.substringAfterLast('.', "")?.lowercase() in HEIF_EXTENSIONS

    /** Scale down (never up) so the long edge ≤ [MAX_LONG_EDGE_PX], keeping aspect ratio. */
    fun targetSize(width: Int, height: Int): Pair<Int, Int> {
        require(width > 0 && height > 0)
        val long = maxOf(width, height)
        if (long <= MAX_LONG_EDGE_PX) return width to height
        val scale = MAX_LONG_EDGE_PX.toDouble() / long
        return maxOf(1, (width * scale).toInt()) to maxOf(1, (height * scale).toInt())
    }

    fun canAddPhoto(currentCount: Int): Boolean = currentCount < MAX_PHOTOS
}

data class CompressedImage(val bytes: ByteArray, val width: Int, val height: Int, val mime: String = ImagePolicy.OUTPUT_MIME)

/** Platform compressor (Android: ImageDecoder → JPEG). */
interface ImageCompressor {
    /** @param source platform reference (content Uri string on Android). */
    suspend fun compress(source: String): CompressedImage
}
