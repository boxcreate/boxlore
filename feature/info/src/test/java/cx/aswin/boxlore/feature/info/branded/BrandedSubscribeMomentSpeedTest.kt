package cx.aswin.boxlore.feature.info.branded

import android.app.Application
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import cx.aswin.boxlore.feature.info.EpisodeSort
import cx.aswin.boxlore.feature.info.branded.vergecast.VergecastPage
import cx.aswin.boxlore.feature.info.components.EpisodeToolbar
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * With the system's animations at twice their length, a show's subscribe moment plays at that speed
 * to the end before alerts and auto-download appear. It used to stop at its normal length, cut short.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class BrandedSubscribeMomentSpeedTest {
    @get:Rule val rule = createComposeRule(effectContext = TwiceAsLong)

    @Test
    fun `the moment and its reveal follow the system animation speed`() {
        var subscribed by mutableStateOf(false)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val style = VergecastPage.style(darkColorScheme(), dark = true)
            CompositionLocalProvider(LocalBrandedPage provides ActiveBrandedPage(VergecastPage, style)) {
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
        subscribed = true
        Snapshot.sendApplyNotifications()

        // Past the moment's normal length, but halfway through at this speed: still playing.
        val moment = VergecastPage.subscribeMoment
        rule.mainClock.advanceTimeBy(moment.durationMillis + 150L)
        rule.onNodeWithContentDescription("Episode notifications").assertDoesNotExist()

        rule.mainClock.advanceTimeBy(moment.durationMillis + 1_000L)
        rule.onNodeWithContentDescription("Episode notifications").assertExists()
        rule.onNodeWithContentDescription("Auto-download").assertExists()
    }

    private object TwiceAsLong : MotionDurationScale {
        override val scaleFactor = 2f
    }
}
