package com.zerogram.di

import android.content.Context
import coil3.ImageLoader
import coil3.memory.MemoryCache
import coil3.SingletonImageLoader
import coil3.request.allowHardware
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ImageModule {

    @Provides
    @Singleton
    fun provideImageLoader(@ApplicationContext context: Context): ImageLoader =
        ImageLoader.Builder(context)
            .memoryCache {
                // 20% of RAM for the in-process LRU bitmap cache
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.20)
                    .build()
            }
            // HARDWARE config: decoded directly into GPU texture memory.
            // Eliminates the CPU-side Bitmap copy — single biggest flag for 120Hz pacing.
            .allowHardware(true)
            // Decode coroutine dispatcher (Coil 3 default is IO, explicit for clarity)
            .coroutineContext(Dispatchers.IO)
            .build()
            .apply { SingletonImageLoader.setSafe { this } }
}
