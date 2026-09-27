package com.zerogram.startup

import android.annotation.SuppressLint
import android.content.Context
import androidx.startup.Initializer
import com.zerogram.domain.repository.ITelegramRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface StartupEntryPoint {
    fun telegramRepository(): ITelegramRepository
}

/**
 * Initializes TDLib asynchronously. Depends on CoreInitializer.
 */
@SuppressLint("EnsureInitializerMetadata")
class TDLibInitializer : Initializer<ITelegramRepository> {

    override fun create(context: Context): ITelegramRepository {
        // Resolve Hilt dependencies manually since this is an Initializer
        val entryPoint = EntryPointAccessors.fromApplication(context, StartupEntryPoint::class.java)
        val repository = entryPoint.telegramRepository()
        
        // Use a structured scope tied to the application lifecycle
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        
        // Defer TDLib client creation and warm-up off the main thread
        appScope.launch {
            repository.warmUp()
        }
        
        return repository
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = listOf(CoreInitializer::class.java)
}
