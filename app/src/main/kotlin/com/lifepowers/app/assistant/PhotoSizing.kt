package com.lifepowers.app.assistant

import kotlin.math.max
import kotlin.math.roundToInt

/** Pure size math for photos sent to the assistant. */
object PhotoSizing {
    /** The long side the API works with best; larger images are scaled down by the API anyway. */
    const val MAX_LONG_SIDE = 1568
    /** Stay well under the API's per image limit. */
    const val MAX_BYTES = 1_500_000

    /** The largest power of two that still leaves the long side at or above [maxLong] after sampling. */
    fun sampleSize(width: Int, height: Int, maxLong: Int = MAX_LONG_SIDE): Int {
        val long = max(width, height)
        var sample = 1
        while (long / (sample * 2) >= maxLong) sample *= 2
        return sample
    }

    /** Width and height scaled so the long side is at most [maxLong], keeping the aspect ratio. */
    fun fit(width: Int, height: Int, maxLong: Int = MAX_LONG_SIDE): Pair<Int, Int> {
        val long = max(width, height)
        if (long <= maxLong || long <= 0) return width to height
        val scale = maxLong.toDouble() / long
        return max(1, (width * scale).roundToInt()) to max(1, (height * scale).roundToInt())
    }

    /**
     * True for a JPEG that carries no metadata segments beyond JFIF (APP0) and a color profile (APP2):
     * no EXIF or XMP (APP1), where location and camera details live, and no comments.
     */
    fun isCleanJpeg(bytes: ByteArray): Boolean {
        if (bytes.size < 4 || u(bytes[0]) != 0xFF || u(bytes[1]) != 0xD8) return false
        var i = 2
        while (i + 3 < bytes.size) {
            if (u(bytes[i]) != 0xFF) return false
            val marker = u(bytes[i + 1])
            if (marker == 0xFF) { i += 1; continue }
            if (marker == 0xDA) return true // start of scan: the image data follows, no more headers
            if (marker == 0xD8 || marker == 0x01 || marker in 0xD0..0xD7) { i += 2; continue }
            val isMetadata = (marker in 0xE1..0xEF && marker != 0xE2) || marker == 0xFE
            if (isMetadata) return false
            val length = (u(bytes[i + 2]) shl 8) or u(bytes[i + 3])
            if (length < 2) return false
            i += 2 + length
        }
        return false
    }

    private fun u(b: Byte): Int = b.toInt() and 0xFF
}
