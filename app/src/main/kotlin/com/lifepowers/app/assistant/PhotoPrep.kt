package com.lifepowers.app.assistant

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Turns stored photo bytes into the one JPEG that is sent: upright, long side at most 1568 px, under
 * about 1.5 MB. Anything carrying EXIF or XMP is re-encoded, so the location and camera details in the
 * original file never leave the phone.
 */
object PhotoPrep {

    private val QUALITIES = intArrayOf(85, 75, 65, 55)

    /** Returns the prepared JPEG, or null when the bytes are not a readable image. Call off the main thread. */
    fun prepare(bytes: ByteArray): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        // Already prepared (for example by the screen, to show its size in the preview): send it as is.
        val fits = maxOf(bounds.outWidth, bounds.outHeight) <= PhotoSizing.MAX_LONG_SIDE && bytes.size <= PhotoSizing.MAX_BYTES
        if (fits && PhotoSizing.isCleanJpeg(bytes)) return bytes

        val options = BitmapFactory.Options().apply { inSampleSize = PhotoSizing.sampleSize(bounds.outWidth, bounds.outHeight) }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        var bitmap = upright(decoded, bytes)

        var maxLong = PhotoSizing.MAX_LONG_SIDE
        repeat(4) {
            val (w, h) = PhotoSizing.fit(bitmap.width, bitmap.height, maxLong)
            val scaled = if (w != bitmap.width || h != bitmap.height) Bitmap.createScaledBitmap(bitmap, w, h, true) else bitmap
            for (q in QUALITIES) {
                val out = encode(scaled, q)
                if (out.size <= PhotoSizing.MAX_BYTES) return out
            }
            bitmap = scaled
            maxLong = (maxLong * 0.75).toInt()
        }
        return null
    }

    private fun encode(bitmap: Bitmap, quality: Int): ByteArray = ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        out.toByteArray()
    }

    private fun upright(bitmap: Bitmap, original: ByteArray): Bitmap {
        val degrees = runCatching {
            when (ExifInterface(ByteArrayInputStream(original)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
