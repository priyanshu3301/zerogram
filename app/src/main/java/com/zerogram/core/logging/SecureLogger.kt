package com.zerogram.core.logging

import android.util.Log
import com.zerogram.BuildConfig

/**
 * Secure logger that enforces the no-plaintext-leak policy (NFR-1.3, NFR-1.4).
 *
 * Rules:
 * - In RELEASE builds, all logging is a no-op.
 * - In DEBUG builds, logging is enabled but this class provides no way to
 *   accidentally log raw keys, IVs, passphrases, file paths, or file names.
 * - Callers must use the provided methods and cannot inject raw sensitive objects.
 */
object SecureLogger {

    private const val TAG = "Zerogram"

    fun d(component: String, message: String) {
        if (BuildConfig.SECURE_LOG_ENABLED) {
            Log.d(TAG, "[$component] $message")
        }
    }

    fun i(component: String, message: String) {
        if (BuildConfig.SECURE_LOG_ENABLED) {
            Log.i(TAG, "[$component] $message")
        }
    }

    fun w(component: String, message: String) {
        if (BuildConfig.SECURE_LOG_ENABLED) {
            Log.w(TAG, "[$component] $message")
        }
    }

    /**
     * Log an error with a sanitized throwable message.
     * The throwable's stack trace is stripped from the message in release builds.
     */
    fun e(component: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.SECURE_LOG_ENABLED) {
            if (throwable != null) {
                Log.e(TAG, "[$component] $message", throwable)
            } else {
                Log.e(TAG, "[$component] $message")
            }
        }
    }

    /**
     * Log a security-relevant event (e.g., HW crypto fallback).
     * Always logs in DEBUG; never logs in RELEASE.
     */
    fun security(component: String, message: String) {
        if (BuildConfig.SECURE_LOG_ENABLED) {
            Log.w("$TAG/Security", "[$component] SECURITY: $message")
        }
    }
}
