package cx.aswin.boxlore.feature.info.branded

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.feature.info.EpisodeSort
import cx.aswin.boxlore.feature.info.branded.vergecast.VergecastPage
import cx.aswin.boxlore.feature.info.branded.waveform.WaveformPage
import cx.aswin.boxlore.feature.info.components.EpisodeToolbar
import cx.aswin.boxlore.feature.info.components.SUBSCRIBED_CHECK_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A show's subscribe moment holds alerts and auto-download back, then settles on Subscribed. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class BrandedSubscribeMomentTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `alerts wait for the moment, then the button settles on the usual Subscribed state`() {
        var subscribed by mutableStateOf(false)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val style = VergecastPage.style(darkColorScheme(), dark = true)
            CompositionLocalProvider(LocalBrandedPage provides ActiveBrandedPage(VergecastPage, style)) {
                // A regular phone's width, where Subscribed has room for its check.
                Box(Modifier.requiredWidth(412.dp)) {
                EpisodeToolbar(
                    searchQuery = "",
                    onSearchChange = {},
                    isSearching = false,
                    currentSort = EpisodeSort.NEWEST,
                    onSortToggle = {},
                    isSubscribed = subscribed,
                    onSubscribeClick = {},
                    accentColor = style.colors.primary,
                    notificationsEnabled = true,
                    autoDownloadEnabled = true,
                )
                }
            }
        }
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithContentDescription("Subscribe").assertExists()
        rule.onNodeWithTag(SUBSCRIBED_CHECK_TAG, useUnmergedTree = true).assertDoesNotExist()

        subscribed = true
        Snapshot.sendApplyNotifications()
        rule.mainClock.advanceTimeBy(600)
        // Mid-moment: the sticker is on the button; alerts and auto-download have not appeared yet.
        rule.onNodeWithContentDescription("Episode notifications").assertDoesNotExist()
        rule.onNodeWithContentDescription("Unsubscribe").assertExists()

        rule.mainClock.advanceTimeBy(1_400)
        rule.onNodeWithContentDescription("Episode notifications").assertExists()
        rule.onNodeWithContentDescription("Auto-download").assertExists()
        // The moment ends on Subscribed with its check, the button's own settled label.
        rule.onNodeWithTag(SUBSCRIBED_CHECK_TAG, useUnmergedTree = true).assertExists()

        // Unsubscribing never replays it.
        subscribed = false
        Snapshot.sendApplyNotifications()
        rule.mainClock.advanceTimeBy(600)
        rule.onNodeWithContentDescription("Episode notifications").assertDoesNotExist()
        rule.onNodeWithContentDescription("Subscribe").assertExists()
    }

    @Test
    fun `the header follows the moment's clock while it plays, then lets go`() {
        var subscribed by mutableStateOf(false)
        val motion = BrandedHeaderMotion()
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val style = WaveformPage.style(darkColorScheme(), dark = true)
            CompositionLocalProvider(
                LocalBrandedPage provides ActiveBrandedPage(WaveformPage, style),
                LocalBrandedHeaderMotion provides motion,
            ) {
                EpisodeToolbar(
                    searchQuery = "",
                    onSearchChange = {},
                    isSearching = false,
                    currentSort = EpisodeSort.NEWEST,
                    onSortToggle = {},
                    isSubscribed = subscribed,
                    onSubscribeClick = {},
                    accentColor = style.colors.primary,
                    notificationsEnabled = true,
                    autoDownloadEnabled = true,
                )
            }
        }
        rule.mainClock.advanceTimeBy(100)
        assertNull(motion.subscribeMomentMillis)

        subscribed = true
        Snapshot.sendApplyNotifications()
        rule.mainClock.advanceTimeBy(600)
        val clock = motion.subscribeMomentMillis
        assertNotNull(clock)
        assertEquals(560f, clock!!(), 60f)

        rule.mainClock.advanceTimeBy(1_400)
        assertNull(motion.subscribeMomentMillis)
        rule.onNodeWithContentDescription("Episode notifications").assertExists()
    }
}
