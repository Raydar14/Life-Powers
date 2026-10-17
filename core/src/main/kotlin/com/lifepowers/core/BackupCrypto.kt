package com.lifepowers.core

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password based encryption for backup files, so a backup can be restored on a new phone where the
 * keystore key does not exist. Format: "LPB1", iteration count (4 bytes), 16 byte salt, 12 byte IV,
 * then AES-256-GCM ciphertext with its tag. The key comes from PBKDF2 with HMAC-SHA256.
 */
object BackupCrypto {
    private val MAGIC = "LPB1".toByteArray(Charsets.US_ASCII)
    const val ITERATIONS = 210_000
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    class WrongPasswordException : Exception("The password does not open this backup.")
    class NotABackupException : Exception("This file is not a Life Powers backup.")

    private fun key(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    fun encrypt(plain: ByteArray, password: CharArray, iterations: Int = ITERATIONS): ByteArray {
        val rnd = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also { rnd.nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { rnd.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(MAGIC)
        val body = cipher.doFinal(plain)
        return ByteBuffer.allocate(MAGIC.size + 4 + SALT_BYTES + IV_BYTES + body.size)
            .put(MAGIC).putInt(iterations).put(salt).put(iv).put(body).array()
    }

    fun decrypt(file: ByteArray, password: CharArray): ByteArray {
        val header = MAGIC.size + 4 + SALT_BYTES + IV_BYTES
        if (file.size <= header || !file.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) throw NotABackupException()
        val buf = ByteBuffer.wrap(file)
        buf.position(MAGIC.size)
        val iterations = buf.int
        if (iterations !in 10_000..10_000_000) throw NotABackupException()
        val salt = ByteArray(SALT_BYTES).also { buf.get(it) }
        val iv = ByteArray(IV_BYTES).also { buf.get(it) }
        val body = ByteArray(buf.remaining()).also { buf.get(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(password, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(MAGIC)
        return try {
            cipher.doFinal(body)
        } catch (e: javax.crypto.AEADBadTagException) {
            throw WrongPasswordException()
        }
    }

    /** At least 10 characters, so a lost phone does not hand over the backup to a quick guess. */
    fun passwordProblem(password: String): String? = when {
        password.length < 10 -> "Use at least 10 characters."
        password.isBlank() -> "Use letters, numbers, or words, not only spaces."
        else -> null
    }
}
