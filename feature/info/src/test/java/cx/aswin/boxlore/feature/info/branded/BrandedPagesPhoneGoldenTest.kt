package cx.aswin.boxlore.feature.info.branded

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import coil.Coil
import coil.ImageLoader
import coil.decode.DataSource
import coil.request.ErrorResult
import coil.request.SuccessResult
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.text.UiText
import cx.aswin.boxlore.feature.info.PodcastInfoScreen
import cx.aswin.boxlore.feature.info.PodcastInfoUiState
import cx.aswin.boxlore.feature.info.PodcastInfoViewModel
import cx.aswin.boxlore.feature.info.logic.PodcastInfoPageShow
import cx.aswin.boxlore.feature.info.stubArchive
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Local design check for the branded show pages: renders each at the design boards' size (369 by
 * 821dp, 2x) with the boards' sample episodes, in both themes, and its subscribe moment every 16ms,
 * for side-by-side comparison. Like the phone goldens it runs only on request:
 * `-PtabletCaptures=after --tests '*BrandedPagesPhoneGoldenTest'`. Copy each show's cover to
 * `build/branded-fixtures/<show>-art.jpg` first.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BrandedPagesPhoneGoldenTest(private val show: String, private val dark: Boolean) {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun page() {
        val model = setUp(subscribed = true)
        capture("$show-${theme()}", model.first)
    }

    /** A tablet-width window, to check the header and spotlight adapt. */
    @Test fun tablet() {
        assumeTrue("Dark is enough at tablet width", dark)
        assumeTrue("The card variants share the main page's layout", show in Moments)
        val model = setUp(subscribed = true, window = "w840dp-h1200dp")
        capture("$show-tablet-${theme()}", model.first)
    }

    /** A header's opening, from the page's first frame, every 50ms: lights coming on, art arriving. */
    @Test fun headerOpening() {
        assumeTrue("Only headers with an opening", show in Openings)
        val (model, _) = setUp(subscribed = true)
        render(model, settle = 0)
        var elapsed = 0L
        for (frame in 0L..3_000L step 50L) {
            compose.mainClock.advanceTimeBy(frame - elapsed)
            elapsed = frame
            save("$show-opening-${theme()}-$frame")
        }
    }

    /** The subscribe moment from the tap, every frame, so a one-frame flash cannot hide. */
    @Test fun subscribeMoment() {
        assumeTrue("The card variants share the main page's moment", show in Moments)
        val (model, state) = setUp(subscribed = false)
        render(model)
        save("$show-subscribe-${theme()}-before")
        state.value = (state.value as PodcastInfoUiState.Success).copy(isSubscribed = true)
        var elapsed = 0L
        for (frame in 0L..1_408L step 16L) {
            compose.mainClock.advanceTimeBy(frame - elapsed)
            elapsed = frame
            save("$show-subscribe-${theme()}-$frame")
        }
    }

    private fun theme() = if (dark) "dark" else "light"

    private fun fixture(): Pair<Podcast, List<Episode>> = BrandedPageFixtures.forShow(show)

    private lateinit var root: String
    private lateinit var podcastId: String

    private fun setUp(subscribed: Boolean, window: String = "w369dp-h821dp"): Pair<PodcastInfoViewModel, MutableStateFlow<PodcastInfoUiState>> {
        root = System.getProperty("boxlore.projectRoot") ?: ""
        assumeTrue("Local design capture only", root.isNotEmpty())
        RuntimeEnvironment.setQualifiers("en-rUS-$window-port-${if (dark) "night" else "notnight"}-xhdpi")
        val activity = compose.activity
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        shadowOf(activity.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(true)
        shadowOf(activity.application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val art = File(root, "build/branded-fixtures/$show-art.jpg")
        assumeTrue("Copy the cover to $art first", art.isFile)
        val artwork = BitmapFactory.decodeFile(art.absolutePath)
        Coil.setImageLoader(
            ImageLoader.Builder(activity).components {
                add(
                    coil.intercept.Interceptor { chain ->
                        // "jre-fail": the latest episode's picture never arrives.
                        if (show == "jre-fail" && chain.request.data.toString().contains("jre-0")) {
                            ErrorResult(null, chain.request, java.io.IOException("Picture unavailable"))
                        } else {
                            // An episode's own picture when one is copied in as build/branded-fixtures/<episode id>.jpg.
                            val address = java.net.URLDecoder.decode(chain.request.data.toString(), "UTF-8")
                            val name = Regex("""[A-Za-z0-9-]+\.jpg""").findAll(address).lastOrNull()?.value.orEmpty()
                            val own = File(root, "build/branded-fixtures/$name")
                            val picture = if (name.isNotEmpty() && own.isFile && own.name != art.name) BitmapFactory.decodeFile(own.absolutePath) else artwork
                            SuccessResult(BitmapDrawable(activity.resources, picture), chain.request, DataSource.MEMORY_CACHE)
                        }
                    },
                )
            }.build(),
        )
        val (podcast, episodes) = fixture()
        podcastId = podcast.id
        val model = mock(PodcastInfoViewModel::class.java)
        val state = MutableStateFlow<PodcastInfoUiState>(PodcastInfoUiState.Success(podcast, episodes, subscribed))
        `when`(model.uiState).thenReturn(state)
        `when`(model.queuedEpisodeIds).thenReturn(MutableStateFlow(emptySet()))
        `when`(model.downloadedEpisodeIds).thenReturn(MutableStateFlow(emptySet()))
        `when`(model.downloadingEpisodeIds).thenReturn(MutableStateFlow(emptySet()))
        `when`(model.episodePendingDownloadRemoval).thenReturn(MutableStateFlow(null))
        `when`(model.hideCompletedInShowDetails).thenReturn(MutableStateFlow(false))
        `when`(model.episodeTitlesOnly).thenReturn(MutableStateFlow(false))
        `when`(model.isPinnedToHome).thenReturn(MutableStateFlow(false))
        `when`(model.isHidden).thenReturn(MutableStateFlow(false))
        `when`(model.pageShow).thenReturn(MutableStateFlow(PodcastInfoPageShow()))
        `when`(model.globalSkipBeginningMs).thenReturn(MutableStateFlow(0L))
        `when`(model.globalSkipEndingMs).thenReturn(MutableStateFlow(0L))
        `when`(model.globalPlaybackSpeed).thenReturn(MutableStateFlow(1f))
        `when`(model.globalTrimSilence).thenReturn(MutableStateFlow(false))
        `when`(model.autoDownloadBackgroundChecksEnabled).thenReturn(MutableStateFlow(false))
        `when`(model.completedEpisodesState).thenReturn(MutableStateFlow(emptySet()))
        stubArchive(model)
        `when`(model.likedEpisodesState).thenReturn(MutableStateFlow(emptySet()))
        // "-partway": the latest episode is 38% heard.
        val partway = if (show.endsWith("-partway")) {
            mapOf(episodes.first().id to PodcastInfoViewModel.EpisodePlaybackState(isResume = true, progress = 0.38f, timeLeft = UiText.raw("28m left")))
        } else {
            emptyMap()
        }
        `when`(model.episodePlaybackState).thenReturn(MutableStateFlow(partway))
        return model to state
    }

    private fun render(model: PodcastInfoViewModel, settle: Long = 3_500) {
        val activity = compose.activity
        compose.mainClock.autoAdvance = false
        compose.setContent {
            BoxLoreTheme(darkTheme = dark, dynamicColor = false) {
                Surface(Modifier.fillMaxSize()) { PodcastInfoScreen(podcastId, model, {}, { _, _, _ -> }, {}, {}) }
            }
        }
        // The phone's status bar: 140px at 3.25x is 43dp.
        val insets = WindowInsetsCompat.Builder().setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, 86, 0, 0)).build()
        compose.runOnUiThread { ViewCompat.dispatchApplyWindowInsets(activity.window.decorView, insets) }
        // Long enough for a header's lights or intro, which wait for the page to settle, to finish.
        if (settle > 0) {
            compose.mainClock.advanceTimeBy(settle)
            compose.waitForIdle()
        }
    }

    private fun capture(name: String, model: PodcastInfoViewModel) {
        render(model)
        save(name)
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun save(name: String) {
        val out = File(root, "build/reports/branded-captures/$name.png")
        requireNotNull(out.parentFile).mkdirs()
        compose.onRoot().captureRoboImage(out.absolutePath, roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
    }

    companion object {
        private val Openings = setOf("history", "darknet", "conspiracy", "serial", "sherlock", "desicrime")

        private val Moments = setOf("vergecast", "conspiracy", "history", "darknet", "serial", "waveform", "crimejunkie", "jre", "jre-nopic", "rottenmango", "rottenmango-long", "daily", "sherlock", "sherlock-long", "desicrime")

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-dark={1}")
        fun pages(): List<Array<Any>> = (Moments + listOf("crimejunkie-partway", "jre-partway", "jre-fail", "jre-twoguests", "rottenmango-partway", "daily-nophoto", "daily-partway")).flatMap { show -> listOf(arrayOf<Any>(show, true), arrayOf<Any>(show, false)) }
    }
}
