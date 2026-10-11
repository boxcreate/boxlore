package cx.aswin.boxlore.feature.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WidgetStartupTest {
    @org.junit.Before
    fun prepareArtworkDispatcher() {
        kotlinx.coroutines.Dispatchers.setMain(kotlinx.coroutines.Dispatchers.Unconfined)
    }

    @org.junit.After
    fun restoreArtworkDispatcher() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun fallbackArtworkKeepsItsOwnCacheIdentityAndPrimaryCanRecover() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val primary = "https://widget-fallback.invalid/episode.jpg"
        val fallback = "https://widget-fallback.invalid/show.jpg"
        val requests = mutableListOf<String>()
        var recovered = false
        val coil = artworkLoader(context) { data ->
            requests.add(data)
            if (data.contains("episode.jpg") && !recovered) throw java.io.IOException("Artwork temporarily unavailable")
            android.graphics.drawable.ColorDrawable(android.graphics.Color.GREEN)
        }
        try {
            val loader = WidgetArtworkLoader(context, coil)
            val fallbackPath = loader.load(primary, listOf(fallback))
            assertTrue(requests.any { it.contains("episode.jpg") })
            assertTrue(requests.last().contains("show.jpg"))
            assertEquals(loader.resolveCachedPath(fallback), fallbackPath)
            assertEquals(null, loader.resolveCachedPath(primary))
            recovered = true
            cx.aswin.boxlore.core.designsystem.components.ArtworkLoadPolicy.Shared.networkRestored()
            requests.clear()
            val primaryPath = loader.load(primary, listOf(fallback))
            assertEquals(loader.resolveCachedPath(primary), primaryPath)
            assertTrue(requests.first().contains("episode.jpg"))
            assertTrue(primaryPath != fallbackPath)
        } finally {
            coil.shutdown()
            java.io.File(context.cacheDir, WidgetArtworkLoader.CACHE_DIR_NAME).deleteRecursively()
        }
    }

    @Test
    fun shortAttemptDeadlineLeavesTimeForFallbackArtwork() = runTest {
        val context: Context = ApplicationProvider.getApplicationContext()
        val requests = mutableListOf<String>()
        val coil = artworkLoader(context, kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)) { data ->
            requests.add(data)
            if (data.contains("slow.jpg")) kotlinx.coroutines.awaitCancellation()
            android.graphics.drawable.ColorDrawable(android.graphics.Color.GREEN)
        }
        var source: String? = null
        try {
            val bitmap = kotlinx.coroutines.withTimeout(2_000) {
                cx.aswin.boxlore.core.designsystem.components.loadArtworkBitmap(
                    context,
                    listOf("https://timeout-art.invalid/slow.jpg", "https://timeout-art.invalid/show.jpg"),
                    loader = coil,
                    perAttemptTimeoutMillis = 50,
                    onSourceLoaded = { source = it },
                )
            }
            assertTrue(bitmap != null)
            assertEquals("https://timeout-art.invalid/show.jpg", source)
            assertTrue(requests.last().contains("show.jpg"))
        } finally {
            coil.shutdown()
        }
    }

    private fun artworkLoader(
        context: Context,
        dispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO,
        fetch: suspend (String) -> android.graphics.drawable.Drawable,
    ): coil.ImageLoader =
        coil.ImageLoader.Builder(context).dispatcher(dispatcher).components {
            add(
                coil.fetch.Fetcher.Factory<android.net.Uri> { data, _, _ ->
                object : coil.fetch.Fetcher {
                    override suspend fun fetch(): coil.fetch.DrawableResult =
                        coil.fetch.DrawableResult(fetch(data.toString()), false, coil.decode.DataSource.NETWORK)
                }
            }
            )
        }.build()

    @Test
    fun transportNormalizationPreservesExistingArtworkCacheIdentity() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val url = "http://art.example/widget-cache-identity.jpg"
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
        val file = java.io.File(context.cacheDir, "${WidgetArtworkLoader.CACHE_DIR_NAME}/$digest.jpg")
        file.parentFile?.mkdirs()
        file.writeBytes(byteArrayOf(1, 2, 3))
        try {
            val loader = WidgetArtworkLoader(context)
            assertEquals(file.absolutePath, loader.load(" $url ", listOf("https://art.example/show.jpg")))
            assertEquals(file.absolutePath, loader.resolveCachedPath(url))
        } finally {
            file.delete()
        }
    }

    @Test
    fun unusedPlaybackWidgetsInstallPortsWithoutRestoringPlayback() = runTest {
        val context: Context = ApplicationProvider.getApplicationContext()
        val deps = object : NowPlayingWidgetDependencies {
            override val context = context
            override val scope: CoroutineScope = backgroundScope
            override val playback: WidgetPlaybackSource
                get() = error("Unused widgets must not construct or restore playback")
        }
        configureNowPlayingWidget(deps)
        runCurrent()
        assertSame(deps, NowPlayingWidgetDependenciesHolder.require())
    }

    @Test
    fun unusedLibraryWidgetsInstallPortsWithoutRankingLibrary() = runTest {
        val context: Context = ApplicationProvider.getApplicationContext()
        val deps = object : LibraryWidgetDependencies {
            override val context = context
            override val scope: CoroutineScope = backgroundScope
            override val library: WidgetLibrarySource
                get() = error("Unused widgets must not construct the library projection")
        }
        configureLibraryWidgets(deps)
        runCurrent()
        assertSame(deps, LibraryWidgetDependenciesHolder.require())
    }

    @Test
    fun installedWidgetsOnlyActivateTheirOwnFamily() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val manager = context.getSystemService(AppWidgetManager::class.java)
        Shadows.shadowOf(manager).bindAppWidgetId(42, ComponentName(context, PlaybackNextControlsWidgetReceiver::class.java))
        assertTrue(hasInstalledWidgets(context, WidgetProviders.all.map { it.receiverClass }))
        assertFalse(hasInstalledWidgets(context, LibraryWidgetProviders.all.map { it.receiverClass }))
        Shadows.shadowOf(manager).bindAppWidgetId(43, ComponentName(context, NewEpisodesWidgetReceiver::class.java))
        assertTrue(hasInstalledWidgets(context, LibraryWidgetProviders.all.map { it.receiverClass }))
    }
}
