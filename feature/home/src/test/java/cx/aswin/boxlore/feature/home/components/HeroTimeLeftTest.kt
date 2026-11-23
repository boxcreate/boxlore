package cx.aswin.boxlore.feature.home.components

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.text.ListeningTime
import cx.aswin.boxlore.core.model.text.LocaleFormats
import cx.aswin.boxlore.core.playback.PlaybackSession
import cx.aswin.boxlore.feature.home.HeroType
import cx.aswin.boxlore.feature.home.SmartHeroItem
import cx.aswin.boxlore.feature.home.logic.HomeHeroLogic
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The hero said "20m left" while the mini player under it said "21m left" for the same episode, and
 * Block's stacked time could read "1h / 0m". Both now round a partial minute up and share the mini
 * player's short time units in each language.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HeroTimeLeftTest {
    @get:Rule val compose = createComposeRule()

    private val screenLocale = mutableStateOf(Locale.ENGLISH)
    private val item = mutableStateOf(resumeItem(positionMs = 0L, durationMs = 60_000L))
    private val wideContext = mutableStateOf(true)

    @Test fun `the hero says the same time left as the mini player in every language`() {
        render()
        val sessions = listOf(
            21 * 60_000L + 30_000L to 42 * 60_000L,
            59_000L to 61 * 60_000L,
            5 * 60_000L to 70 * 60_000L,
            0L to 30 * 60_000L,
        )
        for (language in listOf(Locale.ENGLISH, Locale.GERMAN, Locale.FRENCH)) {
            screenLocale.value = language
            for ((position, duration) in sessions) {
                item.value = resumeItem(position, duration)
                // What the mini player shows for the same session (MiniPlayerSplit.miniPlayerTimeLeft).
                val miniPlayer = LocaleFormats.duration(ListeningTime.minutesLeft(position, duration)!! * 60L, language, LocaleFormats.DurationWidth.NARROW)
                assertEquals("$language at $position of $duration", miniPlayer, durationText())
            }
        }
    }

    @Test fun `a whole hour reads as one line in Block's stacked time`() {
        wideContext.value = false
        render()
        item.value = resumeItem(positionMs = 0L, durationMs = 60 * 60_000L)
        assertEquals("1h", durationText())
        item.value = resumeItem(positionMs = 0L, durationMs = 65 * 60_000L)
        assertEquals("1h\n5m", durationText())
        screenLocale.value = Locale.GERMAN
        item.value = resumeItem(positionMs = 0L, durationMs = 120 * 60_000L)
        assertFalse(durationText().contains('\n'))
    }

    private fun durationText(): String {
        compose.waitForIdle()
        val node = compose.onNodeWithTag("home_hero_duration", useUnmergedTree = true).fetchSemanticsNode()
        return node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString("") { it.text }
    }

    private fun render() {
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply { setLocale(screenLocale.value) }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                BoxLoreTheme(dynamicColor = false) {
                    HeroCardContext(item.value, Color.Black, wide = wideContext.value, Modifier)
                }
            }
        }
    }

    private fun resumeItem(positionMs: Long, durationMs: Long): SmartHeroItem {
        val session = PlaybackSession(
            podcastId = "show",
            episodeId = "episode",
            positionMs = positionMs,
            durationMs = durationMs,
            timestamp = 0L,
            episodeTitle = "Episode",
            podcastTitle = "Show",
            imageUrl = null,
            podcastImageUrl = null,
            audioUrl = null,
        )
        val podcast: Podcast = HomeHeroLogic.sessionToPodcast(session, emptyList())
        check(podcast.latestEpisode is Episode)
        return SmartHeroItem(HeroType.RESUME, podcast, "RESUME")
    }
}
