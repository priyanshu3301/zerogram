package com.zerogram.domain.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.zerogram.core.logging.SecureLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CredentialsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val sharedPreferences: SharedPreferences by lazy {
        initSharedPreferences()
    }

    private fun initSharedPreferences(): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                PREF_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            SecureLogger.e("CredentialsManager", "Failed to init EncryptedSharedPreferences, resetting...", e)
            try {
                context.deleteSharedPreferences(PREF_NAME)
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

                EncryptedSharedPreferences.create(
                    context,
                    PREF_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e2: Exception) {
                SecureLogger.e("CredentialsManager", "Fallback to standard SharedPreferences", e2)
                context.getSharedPreferences("${PREF_NAME}_fallback", Context.MODE_PRIVATE)
            }
        }
    }

    fun saveCredentials(apiId: Int, apiHash: String, phoneNumber: String) {
        sharedPreferences.edit()
            .putInt(KEY_API_ID, apiId)
            .putString(KEY_API_HASH, apiHash)
            .putString(KEY_PHONE_NUMBER, phoneNumber)
            .apply()
    }

    fun getApiId(): Int? {
        return try {
            val id = sharedPreferences.getInt(KEY_API_ID, -1)
            if (id == -1) null else id
        } catch (e: Exception) {
            null
        }
    }

    fun getApiHash(): String? {
        return try {
            sharedPreferences.getString(KEY_API_HASH, null)
        } catch (e: Exception) {
            null
        }
    }

    fun getPhoneNumber(): String? {
        return try {
            sharedPreferences.getString(KEY_PHONE_NUMBER, null)
        } catch (e: Exception) {
            null
        }
    }

    fun clearCredentials() {
        try {
            sharedPreferences.edit()
                .remove(KEY_API_ID)
                .remove(KEY_API_HASH)
                .remove(KEY_PHONE_NUMBER)
                .apply()
        } catch (e: Exception) {
            SecureLogger.e("CredentialsManager", "Failed to clear credentials", e)
        }
    }

    fun saveVaultKey(keyBase64: String) {
        try {
            sharedPreferences.edit().putString(KEY_VAULT_KEY, keyBase64).commit()
        } catch (e: Exception) {
            SecureLogger.e("CredentialsManager", "Failed to save vault key", e)
        }
    }

    fun getVaultKey(): String? {
        return try {
            sharedPreferences.getString(KEY_VAULT_KEY, null)
        } catch (e: Exception) {
            null
        }
    }

    fun clearVaultKey() {
        try {
            sharedPreferences.edit().remove(KEY_VAULT_KEY).apply()
        } catch (e: Exception) {
            SecureLogger.e("CredentialsManager", "Failed to clear vault key", e)
        }
    }

    companion object {
        private const val PREF_NAME = "secure_credentials"
        private const val KEY_API_ID = "key_api_id"
        private const val KEY_API_HASH = "key_api_hash"
        private const val KEY_PHONE_NUMBER = "key_phone_number"
        private const val KEY_VAULT_KEY = "key_vault_key"
    }
}
