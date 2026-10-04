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
import coil.request.ImageRequest
import coil.request.SuccessResult
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.home.StableEpisodeList
import cx.aswin.boxlore.feature.home.StablePlaybackStateMap
import cx.aswin.boxlore.feature.home.StablePodcastList
import cx.aswin.boxlore.feature.home.logic.HomeMixMode
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.After
import org.junit.Assert.assertEquals
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
        assertEquals(Color.Blue.toArgb(), backingPixel(composeRule.onNodeWithContentDescription("Second")))
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

    private fun settleComposition() {
        repeat(2) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
        }
    }

    private fun backingPixel(node: SemanticsNodeInteraction): Int {
        val bounds = node.fetchSemanticsNode().boundsInRoot
        return composeRule.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            bitmap.getPixel(
                (bounds.left + bounds.width * 0.1f).toInt(),
                (bounds.top + bounds.height * 0.1f).toInt(),
            ).also { bitmap.recycle() }
        }
    }
}
