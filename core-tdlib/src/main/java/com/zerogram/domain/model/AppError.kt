package com.zerogram.domain.model

/**
 * Sealed class representing all possible application errors.
 * Every error in Zerogram flows through this type, ensuring consistent
 * error handling across all layers (SRS Section 9).
 *
 * SECURITY: No error message shall ever include plaintext file content,
 * encryption keys, or raw Telegram credentials (NFR-1.3).
 */
sealed class AppError(open val message: String) {

    /** Network connectivity or timeout errors */
    data class NetworkError(
        override val message: String,
        val isRetryable: Boolean = true
    ) : AppError(message)

    /** Telegram API rate limit (FLOOD_WAIT) — carries retry delay */
    data class RateLimitError(
        override val message: String,
        val retryAfterSeconds: Int
    ) : AppError(message)

    /** Telegram authorization or session errors */
    data class AuthError(
        override val message: String,
        val cause: AuthCause = AuthCause.UNKNOWN
    ) : AppError(message) {
        enum class AuthCause {
            INVALID_PHONE,
            INVALID_OTP,
            INVALID_2FA_PASSWORD,
            SESSION_EXPIRED,
            UNKNOWN
        }
    }

    /**
     * Encryption or decryption failure.
     * The message must NEVER contain the key, IV, or plaintext.
     */
    data class CryptoError(
        override val message: String,
        val cause: CryptoCause = CryptoCause.UNKNOWN
    ) : AppError(message) {
        enum class CryptoCause {
            INTEGRITY_TAG_MISMATCH,    // GCM auth tag verification failed
            INVALID_HEADER,            // Encrypted file header is corrupt or missing
            INVALID_IV,                // IV is missing or malformed
            KEY_UNAVAILABLE,           // Android Keystore couldn't provide the key
            HW_ACCELERATION_UNAVAILABLE, // No hardware AES-GCM support; fallback used
            UNKNOWN
        }
    }

    /** Local Room database errors */
    data class DatabaseError(
        override val message: String,
        val isCorrupt: Boolean = false
    ) : AppError(message)

    /** File size exceeds the plan limit (FR-6.2, FR-6.3, FR-6.4) */
    data class StorageLimitError(
        override val message: String,
        val fileSizeBytes: Long,
        val limitBytes: Long,
        val isPremiumLimit: Boolean
    ) : AppError(message)

    /** File not found locally or the Telegram message is missing (FR-7.9) */
    data class FileNotFoundError(
        override val message: String,
        val isMissingRemotely: Boolean = false
    ) : AppError(message)

    /** Storage channel is unavailable or inaccessible (FR-2.6) */
    data class ChannelError(
        override val message: String,
        val chatId: Long? = null
    ) : AppError(message)

    /** Insufficient local device storage */
    data class InsufficientStorageError(
        override val message: String,
        val requiredBytes: Long
    ) : AppError(message)

    /** Media format not supported by ExoPlayer (FR-8.9) */
    data class UnsupportedMediaError(
        override val message: String,
        val mimeType: String?
    ) : AppError(message)

    /** Permission denied by the Android system */
    data class PermissionError(
        override val message: String,
        val permission: String
    ) : AppError(message)

    /** Catch-all for unexpected failures */
    data class UnknownError(
        override val message: String,
        val throwable: Throwable? = null
    ) : AppError(message)

    /** VFS operations failures */
    data class VfsError(
        override val message: String,
        val cause: VfsCause = VfsCause.UNKNOWN
    ) : AppError(message) {
        enum class VfsCause {
            NAME_COLLISION,
            INVALID_NAME,
            CYCLIC_MOVE,
            UNKNOWN
        }
    }
}
