package cx.aswin.boxlore.feature.home.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.ColorDrawable
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import coil.Coil
import coil.EventListener
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.Fetcher
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.home.StableEpisodeList
import cx.aswin.boxlore.feature.home.StablePlaybackStateMap
import cx.aswin.boxlore.feature.home.StablePodcastList
import cx.aswin.boxlore.feature.home.logic.HomeMixMode
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class HomeArtworkHandoffTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var view: View
    private var originalImageLoader: ImageLoader? = null
    private var testImageLoader: ImageLoader? = null

    @After
    fun restoreImageLoader() {
        originalImageLoader?.let(Coil::setImageLoader)
        testImageLoader?.shutdown()
    }

    @Test
    fun `artwork retains its backing when success painter is still transparent`() {
        val succeeded = AtomicBoolean(false)
        composeRule.mainClock.autoAdvance = false
        installTransparentImageLoader(RuntimeEnvironment.getApplication(), succeeded)
        composeRule.setContent {
            view = LocalView.current
            MaterialTheme(colorScheme = darkColorScheme(primaryContainer = Color.Blue)) {
                Box(Modifier.background(Color.Black)) {
                    OptimizedImage(
                        url = "test-artwork",
                        proxyWidth = 120,
                        contentDescription = "Artwork",
                        modifier = Modifier.size(120.dp).testTag("artwork"),
                    )
                }
            }
        }
        settleComposition()
        backingPixel(composeRule.onNodeWithTag("artwork"))
        composeRule.waitUntil(timeoutMillis = 5_000) {
            settleComposition()
            succeeded.get()
        }
        settleComposition()
        assertEquals(Color.Blue.toArgb(), backingPixel(composeRule.onNodeWithTag("artwork")))
        composeRule.mainClock.advanceTimeBy(200)
        composeRule.waitForIdle()
        assertEquals(Color.Blue.toArgb(), backingPixel(composeRule.onNodeWithTag("artwork")))
    }

    @Test
    fun `missing artwork renders the B and microphone at small and large sizes in both modes`() {
        val size = mutableStateOf(120.dp)
        val light = mutableStateOf(false)
        val url = mutableStateOf<String?>(null)
        composeRule.setContent {
            view = LocalView.current
            val scheme = if (light.value) lightColorScheme() else darkColorScheme()
            MaterialTheme(colorScheme = scheme.copy(primaryContainer = Color.Blue, onPrimaryContainer = Color.White)) {
                OptimizedImage(url.value, 240, "Episode artwork", Modifier.size(size.value).testTag("artwork"))
            }
        }
        for (isLight in listOf(false, true)) {
            for (side in listOf(54.dp, 120.dp)) {
                for (missingUrl in listOf(null, "", "  ")) {
                    composeRule.runOnIdle {
                        light.value = isLight
                        size.value = side
                        url.value = missingUrl
                    }
                    assertBrandedArtwork()
                    composeRule.onNodeWithContentDescription("Episode artwork").assertExists()
                }
            }
        }
    }

    @Test
    fun `failed proxy and original artwork retain the branded fallback`() {
        val failures = AtomicInteger()
        val context = RuntimeEnvironment.getApplication()
        originalImageLoader = Coil.imageLoader(context)
        testImageLoader = ImageLoader.Builder(context)
            .components {
                add(
                    Fetcher.Factory<android.net.Uri> { _, _, _ ->
                    object : Fetcher {
                        override suspend fun fetch(): DrawableResult = throw IOException("Artwork unavailable")
                    }
                }
                )
            }
            .eventListener(object : EventListener {
                override fun onError(request: ImageRequest, result: ErrorResult) {
                    failures.incrementAndGet()
                }
            })
            .build()
        Coil.setImageLoader(requireNotNull(testImageLoader))
        composeRule.setContent {
            view = LocalView.current
            MaterialTheme(colorScheme = darkColorScheme(primaryContainer = Color.Blue, onPrimaryContainer = Color.White)) {
                OptimizedImage(
                    "https://example.invalid/branded-fallback.png",
                    240,
                    null,
                    Modifier.size(120.dp).testTag("artwork"),
                )
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { failures.get() == 2 }
        composeRule.waitForIdle()
        assertBrandedArtwork()
    }

    private fun assertBrandedArtwork() {
        val artwork = composeRule.onNodeWithTag("artwork")
        // Sample the approved vector's B stem and microphone grille, then both counters.
        // Coordinates refer to the original 1254px mark, cropped to its 820 x 640 bounds.
        for ((x, y) in listOf(450f to 760f, 744f to 651f)) {
            val red = android.graphics.Color.red(markPixel(artwork, x, y))
            assertTrue("B and microphone grille remain visible at $x,$y: $red", red > 100)
        }
        for ((x, y) in listOf(650f to 450f, 580f to 760f)) {
            assertTrue("B counter and microphone body retain negative space", android.graphics.Color.red(markPixel(artwork, x, y)) < 30)
        }
    }

    private fun markPixel(node: SemanticsNodeInteraction, x: Float, y: Float): Int = backingPixel(
        node,
        xFraction = 0.18f + (x - 214f) / 820f * 0.64f,
        yFraction = (1f - 640f / 820f * 0.64f) / 2f + (y - 292f) / 820f * 0.64f,
    )

    @Test
    fun `new subscription cover paints immediately during a room list update`() {
        val podcasts = mutableStateOf(
            listOf(TestFixtures.podcast(id = "first", title = "First", imageUrl = "test-first")),
        )
        composeRule.mainClock.autoAdvance = false
        installTransparentImageLoader(RuntimeEnvironment.getApplication(), AtomicBoolean())
        composeRule.setContent {
            view = LocalView.current
            MaterialTheme(colorScheme = darkColorScheme(primaryContainer = Color.Blue)) {
                Box(Modifier.background(Color.Black)) {
                    YourShowsSection(
                        subscribedPodcasts = StablePodcastList(podcasts.value),
                        latestEpisodes = StablePodcastList(emptyList()),
                        selectedPodcastId = null,
                        selectedPodcastEpisodes = StableEpisodeList(emptyList()),
                        isSelectedPodcastLoading = false,
                        isSelectedRssRefreshing = false,
                        episodePlaybackState = StablePlaybackStateMap(emptyMap()),
                        onPodcastSelected = {},
                        onPodcastClick = {},
                        onEpisodeClick = { _, _, _ -> },
                        onPlayMix = {},
                        onMixModeChanged = {},
                        selectedMixMode = HomeMixMode.DAILY,
                        onPlayEpisode = { _, _, _ -> },
                        onViewLibrary = {},
                        onViewDownloads = {},
                    )
                }
            }
        }
        settleComposition()
        composeRule.runOnIdle {
            podcasts.value += TestFixtures.podcast(id = "second", title = "Second", imageUrl = "test-second")
            Snapshot.sendApplyNotifications()
        }
        settleComposition()
        assertEquals(Color.Blue.toArgb(), backingPixel(composeRule.onNodeWithTag("home_show_artwork_second", useUnmergedTree = true)))
    }

    private fun installTransparentImageLoader(context: android.content.Context, succeeded: AtomicBoolean) {
        originalImageLoader = Coil.imageLoader(context)
        testImageLoader =
            ImageLoader.Builder(context)
                .components {
                    add(
                        Fetcher.Factory<android.net.Uri> { _, _, _ ->
                        object : Fetcher {
                            override suspend fun fetch() = DrawableResult(
                                // Models the transparent first frame of a successful crossfade.
                                drawable = ColorDrawable(android.graphics.Color.TRANSPARENT),
                                isSampled = false,
                                dataSource = DataSource.NETWORK,
                            )
                        }
                    }
                    )
                }
                .eventListener(object : EventListener {
                    override fun onSuccess(request: ImageRequest, result: SuccessResult) {
                        succeeded.set(true)
                    }
                })
                .build()
        Coil.setImageLoader(requireNotNull(testImageLoader))
    }

    @Test
    fun `Mix card preserves the shared backdrop while playing retains its tonal highlight`() {
        val playing = mutableStateOf(false)
        val episode = TestFixtures.episode(id = "backdrop-episode", title = "Episode")
        val podcast = TestFixtures.podcast(id = "backdrop-show", title = "Show", imageUrl = "")
        composeRule.setContent {
            view = LocalView.current
            MaterialTheme(colorScheme = darkColorScheme(primaryContainer = Color.Blue)) {
                Box(Modifier.background(Color.Magenta)) {
                    MixtapeEpisodeCard(
                        episode = episode,
                        podcast = podcast,
                        onClick = {},
                        onPlay = {},
                        currentPlayingEpisodeId = episode.id,
                        isPlaying = playing.value,
                    )
                }
            }
        }
        val card = composeRule.onNodeWithTag("home_mix_episode_backdrop-episode", useUnmergedTree = true)
        assertEquals(Color.Magenta.toArgb(), backingPixel(card, xFraction = 0.015f, yFraction = 0.5f))
        composeRule.runOnIdle { playing.value = true }
        assertEquals(Color.Blue.toArgb(), backingPixel(card, xFraction = 0.015f, yFraction = 0.5f))
    }

    private fun settleComposition() {
        repeat(2) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
        }
    }

    private fun backingPixel(node: SemanticsNodeInteraction, xFraction: Float = 0.1f, yFraction: Float = 0.1f): Int {
        val bounds = node.fetchSemanticsNode().boundsInRoot
        return composeRule.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            bitmap.getPixel(
                (bounds.left + bounds.width * xFraction).toInt(),
                (bounds.top + bounds.height * yFraction).toInt(),
            ).also { bitmap.recycle() }
        }
    }
}
