package com.zerogram.crypto

import android.util.Base64
import com.zerogram.core.logging.SecureLogger
import com.zerogram.domain.model.AppError
import com.zerogram.domain.model.AppResult
import com.google.crypto.tink.CleartextKeysetHandle
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.streamingaead.AesGcmHkdfStreamingKeyManager
import com.google.crypto.tink.BinaryKeysetReader
import com.google.crypto.tink.BinaryKeysetWriter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CryptoManager @Inject constructor() {

    private val associatedData = ByteArray(0)

    fun generateVaultKey(): ByteArray {
        val keysetHandle = KeysetHandle.generateNew(AesGcmHkdfStreamingKeyManager.aes256GcmHkdf1MBTemplate())
        val outStream = ByteArrayOutputStream()
        CleartextKeysetHandle.write(keysetHandle, BinaryKeysetWriter.withOutputStream(outStream))
        return outStream.toByteArray()
    }

    fun encodeKeyBase64(key: ByteArray): String {
        return Base64.encodeToString(key, Base64.NO_WRAP)
    }

    fun decodeKeyBase64(keyBase64: String): ByteArray? {
        return try {
            val sanitizedKey = keyBase64.replace(" ", "+").replace("\n", "").replace("\r", "").trim()
            Base64.decode(sanitizedKey, Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    fun isValidKey(key: ByteArray): Boolean {
        return try {
            CleartextKeysetHandle.read(BinaryKeysetReader.withBytes(key))
            true
        } catch (e: Exception) {
            false
        }
    }

    fun encryptStream(inputStream: InputStream, outputFile: File, key: ByteArray): AppResult<Long> {
        return try {
            val keysetHandle = CleartextKeysetHandle.read(BinaryKeysetReader.withBytes(key))
            val streamingAead = keysetHandle.getPrimitive(StreamingAead::class.java)
            var totalBytesRead = 0L

            FileOutputStream(outputFile).use { fos ->
                streamingAead.newEncryptingStream(fos, associatedData).use { cipherStream ->
                    inputStream.use { fis ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (fis.read(buffer).also { bytesRead = it } != -1) {
                            cipherStream.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead
                        }
                    }
                }
            }
            AppResult.Success(totalBytesRead)
        } catch (e: Exception) {
            SecureLogger.e("CryptoManager", "Encryption failed", e)
            AppResult.Failure(AppError.UnknownError("Encryption failed: ${e.message}"))
        }
    }

    fun encryptFile(inputFile: File, outputFile: File, key: ByteArray): AppResult<Unit> {
        return try {
            FileInputStream(inputFile).use { fis ->
                when (val result = encryptStream(fis, outputFile, key)) {
                    is AppResult.Success -> AppResult.Success(Unit)
                    is AppResult.Failure -> result
                }
            }
        } catch (e: Exception) {
            com.zerogram.core.logging.SecureLogger.e("CryptoManager", "Encryption failed", e)
            AppResult.Failure(AppError.UnknownError("Encryption failed: ${e.message}"))
        }
    }

    fun decryptStream(inputFile: File, outputStream: java.io.OutputStream, key: ByteArray): AppResult<Unit> {
        // IMPORTANT: We never silently fall back to a "cleartext copy" here.
        // A GeneralSecurityException / malformed-ciphertext IOException from Tink means either
        // the wrong vault key was supplied, or the downloaded file is corrupt/truncated.
        // Treating that as a "successful" decrypt would let garbage bytes get written into
        // zerogram_vault.db, which Room would then either crash on or (with destructive
        // migration) silently wipe to an empty database. Both must be surfaced as real errors
        // so the user gets an "Incorrect key" / "Download failed, please retry" message instead
        // of an endless unlock loop.
        return try {
            val keysetHandle = CleartextKeysetHandle.read(BinaryKeysetReader.withBytes(key))
            val streamingAead = keysetHandle.getPrimitive(StreamingAead::class.java)

            FileInputStream(inputFile).use { fis ->
                streamingAead.newDecryptingStream(fis, associatedData).use { cipherStream ->
                    cipherStream.copyTo(outputStream)
                }
            }

            outputStream.close()
            AppResult.Success(Unit)
        } catch (e: java.security.GeneralSecurityException) {
            try { outputStream.close() } catch (ignored: Exception) {}
            com.zerogram.core.logging.SecureLogger.e("CryptoManager", "Decryption failed: wrong key or corrupted data", e)
            AppResult.Failure(AppError.UnknownError("Incorrect vault key, or the downloaded backup is corrupted."))
        } catch (e: java.io.FileNotFoundException) {
            try { outputStream.close() } catch (ignored: Exception) {}
            com.zerogram.core.logging.SecureLogger.e("CryptoManager", "Decryption failed: File not found", e)
            AppResult.Failure(AppError.UnknownError("File not found or permission denied: ${e.message}"))
        } catch (e: java.io.IOException) {
            try { outputStream.close() } catch (ignored: Exception) {}
            com.zerogram.core.logging.SecureLogger.e("CryptoManager", "Decryption failed: IO or stream error (likely wrong key)", e)
            AppResult.Failure(AppError.UnknownError("Incorrect vault key, or the downloaded backup is corrupted."))
        } catch (e: Exception) {
            try { outputStream.close() } catch (ignored: Exception) {}
            com.zerogram.core.logging.SecureLogger.e("CryptoManager", "Decryption failed", e)
            AppResult.Failure(AppError.UnknownError("Decryption failed: ${e.message}"))
        }
    }

    fun decryptFile(inputFile: File, outputFile: File, key: ByteArray): AppResult<Unit> {
        return try {
            val fos = FileOutputStream(outputFile)
            decryptStream(inputFile, fos, key)
        } catch (e: Exception) {
            com.zerogram.core.logging.SecureLogger.e("CryptoManager", "Decryption failed", e)
            AppResult.Failure(AppError.UnknownError("Decryption failed: ${e.message}"))
        }
    }
}
