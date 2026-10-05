package com.zerogram

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.zerogram.domain.repository.ITelegramRepository

import androidx.startup.AppInitializer
import com.zerogram.startup.TDLibInitializer

@HiltAndroidApp
class ZerogramApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Use App Startup to sequence initializers explicitly.
        // We initialize it manually here because Hilt components are not
        // available in the manifest-declared InitializationProvider.
        AppInitializer.getInstance(this)
            .initializeComponent(TDLibInitializer::class.java)
    }
}
