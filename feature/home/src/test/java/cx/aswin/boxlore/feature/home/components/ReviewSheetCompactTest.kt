package cx.aswin.boxlore.feature.home.components

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import cx.aswin.boxlore.feature.home.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The review prompt's column scrolls inside the shared engagement sheet on a sideways phone: it
 * composes without an unbounded-height crash, and its last button is reachable and acts once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReviewSheetCompactTest {
    @get:Rule val fixture = PhoneGoldenRule(PhoneGoldenConfiguration(915, 412, dark = false))

    @Test
    fun `the review prompt reaches its last button on a sideways phone`() {
        var dismissals = 0
        fixture.setContent {
            BoxLoreTheme(darkTheme = false, dynamicColor = false) {
                CompositionLocalProvider(LocalBoxloreWindowLayout provides BoxloreTestWindows.PHONE_LAND) {
                    ReviewPromptSheet(
                        completedCount = 30,
                        onDismissRequest = { dismissals++ },
                        onNavigateToReview = {},
                        onNavigateToFeedback = {},
                    )
                }
            }
        }
        fixture.compose.waitForIdle()
        val notNow = fixture.compose.activity.getString(R.string.home_review_not_right_now)
        fixture.compose.onNodeWithText(notNow).performScrollTo().performClick()
        fixture.compose.waitForIdle()
        assertEquals(1, dismissals)
    }
}
