package cx.aswin.boxlore.ui.announcement

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
import androidx.compose.ui.graphics.Color
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository.Announcement
import cx.aswin.boxlore.updates.releaseNotesBlocks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1300dp-h1300dp-mdpi", application = Application::class)
class AnnouncementRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val window = mutableStateOf(BoxloreTestWindows.TABLET)
    private val events = mutableListOf<Pair<String, Map<String, Any>>>()
    private var dismissed = 0

    private fun alert() = Announcement("News", "Exact **copy**", null, null, null, false, 1, "ANNOUNCEMENT")

    private fun longAlert() = alert().copy(
        title = "Neu in boxlore: Wiedergabelisten, Schlaf-Timer und eine überarbeitete Bibliothek",
        body = (1..60).joinToString("\n\n") { "Absatz $it: Die Bibliothek merkt sich jetzt deinen Platz auf jedem Gerät." },
    )

    private var restoreAnalytics: () -> Unit = {}

    @Before fun recordAnalytics() {
        restoreAnalytics = AnalyticsHelper.installRecordingSink(events)
    }

    @After fun restoreAnalytics() = restoreAnalytics.invoke()

    private fun render(announcement: Announcement) {
        compose.setContent {
            BoxLoreTheme(darkTheme = false, dynamicColor = false) {
                CompositionLocalProvider(LocalBoxloreWindowLayout provides window.value) {
                    InAppAnnouncementDialog(announcement, onDismiss = { dismissed++ }, onAction = {})
                }
            }
        }
        compose.waitForIdle()
    }

    private fun dismissEvents() = events.count { (name, props) -> name == "feature_announcement_action" && "dismissed" in props.values }

    @Test fun `on a tablet the card is one panel over one dim layer, and an outside tap dismisses once`() {
        render(alert())
        val host = compose.onNodeWithTag("boxlore_dialog_panel").getUnclippedBoundsInRoot()
        val card = compose.onNodeWithTag(ANNOUNCEMENT_PANEL_TAG).getUnclippedBoundsInRoot()
        assertEquals("The card fills the dialog panel, with no frame of its own", host, card)
        assertTrue("Card width ${card.width}", card.width <= 560.dp)

        compose.onNode(isDialog()).performTouchInput { click(Offset(16f, 16f)) }
        compose.waitForIdle()
        assertEquals(1, dismissed)
        assertEquals(1, dismissEvents())
    }

    @Test fun `on a phone the card keeps its own backdrop and side margins`() {
        window.value = BoxloreTestWindows.PHONE
        render(alert())
        compose.onNodeWithTag("boxlore_dialog_panel").assertDoesNotExist()
        compose.onNodeWithTag(ANNOUNCEMENT_PANEL_TAG).assertExists()
    }

    @Test
    @Config(qualifiers = "w600dp-h900dp-mdpi")
    fun `crossing the tablet width with the body scrolled keeps the scroll and the card open`() {
        AnalyticsHelper.installRecordingSink(events)
        window.value = BoxloreTestWindows.W599_H900
        render(longAlert())
        compose.onNodeWithText("Absatz 40:", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Absatz 1:", substring = true).assertIsNotDisplayed()
        listOf(BoxloreTestWindows.W600_H900, BoxloreTestWindows.W599_H900).forEach { next ->
            compose.runOnIdle { window.value = next }
            compose.waitForIdle()
            compose.onNodeWithTag(ANNOUNCEMENT_PANEL_TAG).assertExists()
            // The panel's height differs between the two presentations; the body stays scrolled down.
            compose.onNodeWithText("Absatz 1:", substring = true).assertIsNotDisplayed()
        }
        assertEquals(0, dismissed)
        assertEquals(0, dismissEvents())
    }

    @Test fun `release download stays in app and GitHub action uses a validated page`() {
        val value = alert().copy(releaseAlert = true, route = "https://github.com/boxcreate/boxlore/releases/download/v29/app.apk", releaseUrl = "https://evil.example/update")
        val items = announcementActions(value)
        assertEquals(listOf(AnnouncementActionKind.UPDATE, AnnouncementActionKind.GITHUB), items.map { it.kind })
        assertEquals("boxlore://updates/download", items[0].route)
        assertEquals("https://github.com/boxcreate/boxlore/releases/tag/v29", items[1].route)
    }

    @Test fun `ordinary announcements honour optional action visibility and exact custom labels`() {
        val value = alert().copy(route = "https://example.org", actionLabel = "Read the story")
        assertTrue(announcementActions(value).isEmpty())
        val action = announcementActions(value.copy(showActionInApp = true)).single()
        assertEquals(AnnouncementActionKind.CUSTOM, action.kind)
        assertEquals(value.route, action.route)
        assertEquals(value.actionLabel, action.label)
        assertTrue(announcementActions(value.copy(showActionInApp = true, route = " ")).isEmpty())
    }

    @Test fun `tone changes action and tag colours without replacing surfaces or text roles`() {
        val base = lightColorScheme(secondary = Color.Blue, secondaryContainer = Color.Cyan)
        val toned = announcementColors(base, "secondary")
        assertEquals(base.secondary, toned.primary)
        assertEquals(base.secondaryContainer, toned.primaryContainer)
        assertEquals(base.surfaceContainer, toned.surfaceContainer)
        assertEquals(base.onSurface, toned.onSurface)
        assertEquals(base, announcementColors(base, "unknown"))
    }

    @Test fun `announcement images retain the HTTPS only policy`() {
        assertEquals("https://example.org/cover.png", announcementImageUrl("https://example.org/cover.png"))
        listOf(null, "file:///storage/cover.png", "content://cover", "http://example.org/cover.png", "https://user:password@example.org/cover.png", "not a url").forEach { assertNull(announcementImageUrl(it)) }
    }

    @Test fun `native announcement Markdown preserves its links and styled callouts`() {
        val blocks = releaseNotesBlocks("## News\n\n> Important **information**\n\n[Discussion](https://github.com/boxcreate/boxlore/pull/1)", stripPrLinks = false)
        assertEquals(2, blocks[0].heading)
        assertTrue(blocks[1].callout)
        assertEquals("Important **information**", blocks[1].text)
        assertTrue(blocks.last().text.contains("/pull/1"))
    }
}
