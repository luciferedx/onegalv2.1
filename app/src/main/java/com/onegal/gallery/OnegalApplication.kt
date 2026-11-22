package com.onegal.gallery

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder
import kotlinx.coroutines.Dispatchers

/**
 * Onegal Application configuring Coil 3 for locked 120Hz scrolling on high-refresh-rate AMOLED displays.
 * Zero network, zero cloud sync, zero ML — 100% local hardware-accelerated decoding.
 */
class OnegalApplication : Application(), SingletonImageLoader.Factory {

    override fun newImageLoader(context: Context): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.30)
                    .strongReferencesEnabled(true)
                    .weakReferencesEnabled(true)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("onegal_thumb_cache"))
                    .maxSizeBytes(512L * 1024L * 1024L) // 512 MB fast NVMe thumbnail cache
                    .build()
            }
            .decoderCoroutineContext(Dispatchers.IO.limitedParallelism(8))
            .allowHardware(true)
            .crossfade(120)
            .build()
    }
}
