package cx.aswin.boxlore.tv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import cx.aswin.boxlore.tv.cloud.TvAccount
import cx.aswin.boxlore.tv.data.TvLibrary
import cx.aswin.boxlore.tv.data.TvPlayback

/** The TV app's whole object graph. Built once by [TvApplication]; nothing else creates one. */
class TvContainer(
    val library: TvLibrary,
    val playback: TvPlayback,
    val account: TvAccount,
)

class TvApplication :
    Application(),
    ImageLoaderFactory {
    lateinit var container: TvContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = TvContainerFactory.create(this)
    }

    /**
     * Many TVs have little memory, so artwork keeps a small in-memory cache and a bounded disk
     * cache; Coil already decodes each picture at the size it is drawn, never the feed's full size.
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(MEMORY_CACHE_SHARE).build() }
            .diskCache { DiskCache.Builder().directory(cacheDir.resolve("artwork")).maxSizeBytes(DISK_CACHE_BYTES).build() }
            .crossfade(false)
            .build()

    private companion object {
        const val MEMORY_CACHE_SHARE = 0.12
        const val DISK_CACHE_BYTES = 64L * 1024 * 1024
    }
}
