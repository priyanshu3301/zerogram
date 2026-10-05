package com.zerogram.startup

import android.annotation.SuppressLint
import android.content.Context
import androidx.startup.Initializer
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.streamingaead.StreamingAeadConfig

/**
 * Initializes core synchronous dependencies (like Tink crypto config) early in startup.
 */
@SuppressLint("EnsureInitializerMetadata")
class CoreInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        StreamingAeadConfig.register()
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
