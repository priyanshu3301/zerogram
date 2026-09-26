package com.zerogram

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.zerogram.domain.repository.ITelegramRepository

import com.google.crypto.tink.streamingaead.StreamingAeadConfig

@HiltAndroidApp
class ZerogramApplication : Application() {
    
    @Inject lateinit var telegramRepository: ITelegramRepository
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    override fun onCreate() {
        super.onCreate()
        StreamingAeadConfig.register()
        appScope.launch {
            telegramRepository.warmUp()
        }
    }
}
