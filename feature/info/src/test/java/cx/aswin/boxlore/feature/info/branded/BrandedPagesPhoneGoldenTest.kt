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
import coil.request.SuccessResult
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.info.PodcastInfoScreen
import cx.aswin.boxlore.feature.info.PodcastInfoUiState
import cx.aswin.boxlore.feature.info.PodcastInfoViewModel
import cx.aswin.boxlore.feature.info.logic.PodcastInfoPageShow
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
        val model = setUp(subscribed = true, window = "w840dp-h1200dp")
        capture("$show-tablet-${theme()}", model.first)
    }

    /** The subscribe moment from the tap, every frame, so a one-frame flash cannot hide. */
    @Test fun subscribeMoment() {
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
                add(coil.intercept.Interceptor { chain -> SuccessResult(BitmapDrawable(activity.resources, artwork), chain.request, DataSource.MEMORY_CACHE) })
            }.build(),
        )
        val (podcast, episodes) = when (show) {
            "conspiracy" -> conspiracy()
            "waveform" -> waveform()
            else -> vergecast()
        }
        podcastId = podcast.id
        val model = mock(PodcastInfoViewModel::class.java)
        val state = MutableStateFlow<PodcastInfoUiState>(PodcastInfoUiState.Success(podcast, episodes, subscribed))
        `when`(model.uiState).thenReturn(state)
        `when`(model.queuedEpisodeIds).thenReturn(MutableStateFlow(emptySet()))
        `when`(model.downloadedEpisodeIds).thenReturn(MutableStateFlow(emptySet()))
        `when`(model.downloadingEpisodeIds).thenReturn(MutableStateFlow(emptySet()))
        `when`(model.episodePendingDownloadRemoval).thenReturn(MutableStateFlow(null))
        `when`(model.hideCompletedInShowDetails).thenReturn(MutableStateFlow(false))
        `when`(model.isPinnedToHome).thenReturn(MutableStateFlow(false))
        `when`(model.pageShow).thenReturn(MutableStateFlow(PodcastInfoPageShow()))
        `when`(model.globalSkipBeginningMs).thenReturn(MutableStateFlow(0L))
        `when`(model.globalSkipEndingMs).thenReturn(MutableStateFlow(0L))
        `when`(model.autoDownloadBackgroundChecksEnabled).thenReturn(MutableStateFlow(false))
        `when`(model.completedEpisodesState).thenReturn(MutableStateFlow(emptySet()))
        `when`(model.likedEpisodesState).thenReturn(MutableStateFlow(emptySet()))
        `when`(model.episodePlaybackState).thenReturn(MutableStateFlow(emptyMap()))
        return model to state
    }

    private fun vergecast(): Pair<Podcast, List<Episode>> {
        val now = System.currentTimeMillis() / 1000
        val podcast = TestFixtures.podcast(id = "415434", title = "The Vergecast", artist = "The Verge", imageUrl = ART, genre = "Technology")
            .copy(
                updateFrequency = "daily",
                notificationsEnabled = true,
                autoDownloadEnabled = true,
                description = "The Vergecast is the flagship daily podcast from The Verge about small gadgets, Big Tech, and everything in " +
                    "between. Every Friday, Nilay Patel and David Pierce hang out and make sense of the week’s most important technology news.",
            )
        return podcast to listOf(
            episode(podcast, "vergecast-latest", "The delightfully messy state of Apple gadgets", 93 * 60, now - 55 * 60, "Nilay is going to buy the new touchscreen MacBook. But not for the reason you think."),
            episode(podcast, "vergecast-previous", "The touchscreen MacBook Apple said it wouldn't make", 40 * 60, now - 20 * 3600, "David and Nilay on Apple's surprise, and what it means for every other laptop."),
        )
    }

    /** Weekly on Wednesdays, with a trailer, like the board. */
    private fun conspiracy(): Pair<Podcast, List<Episode>> {
        val podcast = TestFixtures.podcast(id = "9926", title = "Conspiracy Theories", artist = "Spotify Studios", imageUrl = ART, genre = "True Crime")
            .copy(
                notificationsEnabled = true,
                autoDownloadEnabled = true,
                description = "The world’s most controversial events, and the complex beliefs behind them. From Bigfoot sightings to bitcoin " +
                    "takeovers, alien landings to assassinations, who’s shaping the narrative — and why?",
            )
        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 9)
            while (get(java.util.Calendar.DAY_OF_WEEK) != java.util.Calendar.WEDNESDAY) add(java.util.Calendar.DAY_OF_MONTH, -1)
        }
        val latestWednesday = calendar.timeInMillis / 1000
        val titles = listOf(
            "Was Jack the Ripper a Freemason?" to "In 1888, Jack the Ripper stalked the streets of Whitechapel, England. Officially, he killed five women.",
            "Winchester Mystery House: Ghosts, Legends, and Hoaxes" to "Sarah Winchester built a house with stairs to nowhere. Was she hiding from ghosts?",
            "The Dyatlov Pass Incident" to "Nine hikers died in the Ural Mountains in 1959. What happened that night?",
            "Did the CIA Make Up the Moon Landing?" to "Some say the footage was shot in a studio. Here is what the evidence says.",
            "The Mothman of Point Pleasant" to "A winged creature, a collapsed bridge and a town that never forgot.",
        )
        val episodes = titles.mapIndexed { i, (title, notes) ->
            episode(podcast, "conspiracy-$i", title, (55 - i * 3) * 60, latestWednesday - i * 7L * 86_400, notes)
        } + episode(podcast, "conspiracy-trailer", "Introducing Conspiracy Theories", 2 * 60, latestWednesday - 400L * 86_400, "Welcome to the case file.")
            .copy(episodeType = "trailer")
        return podcast to episodes
    }

    /** Weekly on Fridays, like the board: the latest from this morning, then last week's. */
    private fun waveform(): Pair<Podcast, List<Episode>> {
        val podcast = TestFixtures.podcast(id = "947138", title = "Waveform: The MKBHD Podcast", artist = "MKBHD", imageUrl = ART, genre = "Technology")
            .copy(
                notificationsEnabled = true,
                autoDownloadEnabled = true,
                description = "A tech podcast for the gadget lovers and tech heads among us from the mind of Marques Brownlee, better known " +
                    "as MKBHD. MKBHD has made a name for himself on YouTube reviewing everything from the newest smartphones to cameras " +
                    "to electric cars.",
            )
        val calendar = java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.HOUR_OF_DAY, -9)
            while (get(java.util.Calendar.DAY_OF_WEEK) != java.util.Calendar.FRIDAY) add(java.util.Calendar.DAY_OF_MONTH, -1)
        }
        val latestFriday = calendar.timeInMillis / 1000
        val titles = listOf(
            "Is The \"PROLED\" Macbook Any Good?" to "David and Marques try the new MacBook and its display.",
            "Introducing Tesla Roadster... Again" to "The Roadster is back, again. Plus a phone that folds three ways.",
            "The Best Phones of the Year" to "The team picks their favourite phones so far.",
            "We Tried Every Smart Ring" to "Rings that track your sleep, steps and heart.",
        )
        val episodes = titles.mapIndexed { i, (title, notes) ->
            episode(podcast, "waveform-$i", title, (122 - i) * 60, latestFriday - i * 7L * 86_400, notes)
        }
        return podcast to episodes
    }

    private fun episode(podcast: Podcast, id: String, title: String, seconds: Int, published: Long, notes: String) = TestFixtures.episode(
        id = id,
        title = title,
        podcastId = podcast.id,
        podcastTitle = podcast.title,
        duration = seconds,
        publishedDate = published,
        imageUrl = ART,
        description = notes,
    )

    private fun render(model: PodcastInfoViewModel) {
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
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
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
        private const val ART = "https://images.example.invalid/art.jpg"

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-dark={1}")
        fun pages(): List<Array<Any>> = listOf("vergecast", "conspiracy", "waveform").flatMap { show -> listOf(arrayOf<Any>(show, true), arrayOf<Any>(show, false)) }
    }
}
