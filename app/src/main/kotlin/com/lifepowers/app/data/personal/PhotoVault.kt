package com.lifepowers.app.data.personal

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Journal photos, encrypted at rest under filesDir/photos with a keystore backed AES256 GCM key.
 * Large images are scaled so the long side is at most [MAX_SIDE] pixels before they are stored.
 */
@Singleton
class PhotoVault @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    data class Stored(val fileName: String, val mime: String)

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    }

    private fun dir(): File = File(context.filesDir, DIR).apply { mkdirs() }

    /** Only the base name is honored, so a stored name can never point outside the photo folder. */
    private fun fileFor(fileName: String): File = File(dir(), File(fileName).name)

    private fun encrypted(file: File): EncryptedFile =
        EncryptedFile.Builder(context, file, masterKey, EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB).build()

    suspend fun store(bytes: ByteArray, mime: String): Stored = withContext(Dispatchers.IO) {
        val (data, finalMime) = shrink(bytes, mime)
        val name = "${UUID.randomUUID()}.bin"
        val file = fileFor(name)
        try {
            encrypted(file).openFileOutput().use { it.write(data) }
        } catch (e: Exception) {
            file.delete()
            throw e
        }
        Stored(name, finalMime)
    }

    /** Decrypted bytes, or null when the file is gone or cannot be read. */
    suspend fun load(fileName: String): ByteArray? = withContext(Dispatchers.IO) {
        val file = fileFor(fileName)
        if (!file.exists()) return@withContext null
        try {
            encrypted(file).openFileInput().use { it.readBytes() }
        } catch (e: IOException) {
            null
        } catch (e: GeneralSecurityException) {
            null
        }
    }

    suspend fun delete(fileName: String) {
        withContext(Dispatchers.IO) { fileFor(fileName).delete() }
    }

    /** Returns the bytes unchanged unless they decode as an image with a long side over [MAX_SIDE]. */
    private fun shrink(bytes: ByteArray, mime: String): Pair<ByteArray, String> {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0 || max(w, h) <= MAX_SIDE) return bytes to mime

        var sample = 1
        while (max(w, h) / (sample * 2) >= MAX_SIDE) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return bytes to mime

        val scale = MAX_SIDE.toFloat() / max(decoded.width, decoded.height)
        val matrix = Matrix()
        if (scale < 1f) matrix.postScale(scale, scale)
        rotationOf(bytes).takeIf { it != 0 }?.let { matrix.postRotate(it.toFloat()) }
        val out = if (matrix.isIdentity) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)

        val buffer = ByteArrayOutputStream()
        out.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, buffer)
        if (out !== decoded) out.recycle()
        decoded.recycle()
        return buffer.toByteArray() to "image/jpeg"
    }

    /** Re-encoding drops EXIF, so the camera's rotation is applied to the pixels instead. */
    private fun rotationOf(bytes: ByteArray): Int = try {
        when (ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    } catch (e: IOException) {
        0
    }

    companion object {
        const val DIR = "photos"
        const val MAX_SIDE = 2048
        const val JPEG_QUALITY = 85
    }
}
