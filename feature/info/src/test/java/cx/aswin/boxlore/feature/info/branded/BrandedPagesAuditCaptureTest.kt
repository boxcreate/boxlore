package cx.aswin.boxlore.feature.info.branded

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import coil.Coil
import coil.ImageLoader
import coil.decode.DataSource
import coil.request.SuccessResult
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import cx.aswin.boxlore.feature.info.PodcastInfoScreen
import cx.aswin.boxlore.feature.info.PodcastInfoUiState
import cx.aswin.boxlore.feature.info.PodcastInfoViewModel
import cx.aswin.boxlore.feature.info.R
import cx.aswin.boxlore.feature.info.logic.PodcastInfoPageShow
import cx.aswin.boxlore.feature.info.stubArchive
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Audit captures of every Showcase page at the shared review sizes (`-PtabletCaptures=audit`), named
 * `branded-{show}-{size}`: the page with its header and spotlight settled, and for three pages the ©
 * note open. Filter with `--tests '*BrandedPagesAuditCaptureTest*daily*'`. Covers are a generated
 * gradient, so the pictures check placement, not the real art.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BrandedPagesAuditCaptureTest(private val show: String, configuration: PhoneGoldenConfiguration) {
    @get:Rule val golden = PhoneGoldenRule(configuration)

    @Test fun page() {
        render()
        golden.capture("branded-$show")
    }

    @Test fun copyrightOpen() {
        assumeTrue("The © note is checked on three pages", show in CopyrightShows)
        render()
        golden.compose.onAllNodesWithContentDescription(golden.compose.activity.getString(R.string.branded_copyright_button)).onFirst().performClick()
        golden.compose.mainClock.advanceTimeBy(600) // the captures hold the clock
        // An empty scene fails here instead of passing as a picture of the closed page.
        golden.compose.onNode(isPopup()).assertExists()
        golden.capture("branded-$show-copyright-open", allWindows = true)
    }

    private fun render() {
        golden.assumeAuditCapture()
        golden.prepare()
        val activity = golden.compose.activity
        shadowOf(activity.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(true)
        shadowOf(activity.application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val cover = gradientCover()
        Coil.setImageLoader(
            ImageLoader.Builder(activity).components {
                add(coil.intercept.Interceptor { chain -> SuccessResult(BitmapDrawable(activity.resources, cover), chain.request, DataSource.MEMORY_CACHE) })
            }.build(),
        )
        val (podcast, episodes) = BrandedPageFixtures.forShow(show)
        check(BrandedShowPages.all.any { it.ids.matches(podcast) }) { "$show is not a Showcase page" }
        val model = mock(PodcastInfoViewModel::class.java)
        `when`(model.uiState).thenReturn(MutableStateFlow<PodcastInfoUiState>(PodcastInfoUiState.Success(podcast, episodes, true)))
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
        `when`(model.episodePlaybackState).thenReturn(MutableStateFlow(emptyMap()))
        golden.setContent(foldSafeViewport = true) {
            BoxLoreTheme(darkTheme = golden.configuration.dark, dynamicColor = false) {
                Surface(Modifier.fillMaxSize()) { PodcastInfoScreen(podcast.id, model, {}, { _, _, _ -> }, {}, {}) }
            }
        }
        // Long enough for a header's lights or intro, which wait for the page to settle, to finish.
        golden.compose.mainClock.advanceTimeBy(INTRO_SETTLE_MS)
        golden.compose.waitForIdle()
    }

    /** A warm-to-cool diagonal, so art placement and cropping show in the pictures. */
    private fun gradientCover(): Bitmap = Bitmap.createBitmap(COVER_PX, COVER_PX, Bitmap.Config.ARGB_8888).apply {
        val paint = Paint().apply {
            shader = LinearGradient(0f, 0f, COVER_PX.toFloat(), COVER_PX.toFloat(), 0xFFE0A040.toInt(), 0xFF305A90.toInt(), Shader.TileMode.CLAMP)
        }
        Canvas(this).drawRect(0f, 0f, COVER_PX.toFloat(), COVER_PX.toFloat(), paint)
    }

    companion object {
        private const val INTRO_SETTLE_MS = 3_500L
        private const val COVER_PX = 256

        /** One name per page in [BrandedShowPages.all], as [BrandedPageFixtures] knows them. */
        val Shows = listOf("vergecast", "conspiracy", "waveform", "crimejunkie", "jre", "rottenmango", "daily", "history", "darknet", "serial", "sherlock", "desicrime")
        private val CopyrightShows = setOf("conspiracy", "jre", "daily")

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-{1}")
        fun pages(): List<Array<Any>> {
            if (System.getProperty("boxlore.tabletCaptures") != "audit") return listOf(arrayOf("vergecast", PhoneGoldenConfiguration(412, 915, false)))
            return Shows.flatMap { show -> PhoneGoldenConfiguration.auditConfigurations().map { arrayOf<Any>(show, it) } }
        }
    }
}
