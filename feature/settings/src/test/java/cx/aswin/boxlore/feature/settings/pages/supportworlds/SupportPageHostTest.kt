package cx.aswin.boxlore.feature.settings.pages.supportworlds

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.feature.settings.pages.SupportPageEnvironment
import cx.aswin.boxlore.feature.settings.pages.SupportPageHost
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w900dp-h1300dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SupportPageHostTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val sink = RecordingSupportSink()
    private var layout by mutableStateOf(BoxloreWindowLayout(390f, 844f))

    /** The picker's tab for a price; the pay button repeats the price once that tier is chosen. */
    private fun tierTab(price: String) =
        compose.onNode(hasText(price) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))

    @After fun resetDebugOverride() {
        SupportWorldDebugOverride.world = null
    }

    @Composable
    private fun Host(
        variant: String?,
        debuggable: Boolean = false,
        changingConfigurations: Boolean = true,
        onVariantRead: () -> Unit = {},
    ) {
        CompositionLocalProvider(LocalBoxloreWindowLayout provides layout) {
            BoxLoreTheme(dynamicColor = false) {
                SupportPageHost(
                    onBack = {},
                    totalListeningHours = 12L,
                    environment = SupportPageEnvironment(
                        variantProvider = {
                            onVariantRead()
                            variant
                        },
                        analyticsSink = sink,
                        elapsedMs = { 0L },
                        debuggable = debuggable,
                        isChangingConfigurations = { changingConfigurations },
                    ),
                )
            }
        }
    }

    @Test fun `selection and an open payments notice survive every window gate in both directions`() {
        compose.setContent { Host(variant = "orbit") }
        tierTab("$6.99").performClick()
        compose.onNodeWithText("Launch Ring Station").performClick()
        compose.onNodeWithText("Mission control isn't online yet").assertIsDisplayed()

        listOf(
            BoxloreWindowLayout(599f, 844f),
            BoxloreWindowLayout(600f, 844f),
            BoxloreWindowLayout(800f, 479f),
            BoxloreWindowLayout(800f, 480f),
            BoxloreWindowLayout(839f, 600f),
            BoxloreWindowLayout(840f, 600f),
            BoxloreWindowLayout(840f, 1000f),
            BoxloreWindowLayout(599f, 844f),
            BoxloreWindowLayout(390f, 844f),
        ).forEach { next ->
            layout = next
            compose.waitForIdle()
            compose.onNodeWithText("Mission control isn't online yet").assertIsDisplayed()
        }

        compose.onNodeWithText("Stand down").performClick()
        compose.onNodeWithText("Mission control isn't online yet").assertDoesNotExist()
        compose.onNodeWithText("Launch Ring Station").assertIsDisplayed()
        assertEquals(listOf("view:orbit:experiment", "tier:3", "pay:3"), sink.events)
    }

    @Test fun `back and a tap outside close the notice without leaving the page`() {
        compose.setContent { Host(variant = "alchemy") }
        compose.onNodeWithText("Conjure Sun Crystal").performClick()
        compose.onNodeWithText("The cauldron isn't lit yet").assertIsDisplayed()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithText("The cauldron isn't lit yet").assertDoesNotExist()

        compose.onNodeWithText("Conjure Sun Crystal").performClick()
        compose.onRoot().performTouchInput { click(Offset(24f, 24f)) }
        compose.waitForIdle()
        compose.onNodeWithText("The cauldron isn't lit yet").assertDoesNotExist()
        compose.onNodeWithText("Conjure Sun Crystal").assertIsDisplayed()
        assertEquals(0, sink.count("left"))
    }

    @Test fun `a configuration restore keeps selection and notice and never counts a second view`() {
        var variantReads = 0
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Host(variant = "sky", onVariantRead = { variantReads++ }) }
        tierTab("$17.99").performClick()
        compose.onNodeWithText("Raise Beacon Citadel").performClick()

        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithText("The skyport isn't open yet").assertIsDisplayed()
        compose.onNodeWithText("Back to the clouds").performClick()
        compose.onNodeWithText("Raise Beacon Citadel").assertIsDisplayed()
        assertEquals(1, variantReads)
        assertEquals(1, sink.count("view"))
        assertEquals(0, sink.count("left"))
    }

    @Test fun `a missing flag shows today's page as an uncounted fallback with its own notice`() {
        compose.setContent { Host(variant = null) }
        compose.onNodeWithText("Deploy Power Station").performClick()
        compose.onNodeWithText("The power grid isn't connected yet").assertIsDisplayed()
        assertEquals(listOf("view:control:fallback", "pay:2"), sink.events)
    }

    @Test fun `leaving the page reports one exit`() {
        var shown by mutableStateOf(true)
        compose.setContent { if (shown) Host(variant = "neon", changingConfigurations = false) }
        tierTab("$17.99").performClick()
        compose.waitForIdle()
        shown = false
        compose.waitForIdle()
        assertEquals(listOf("view:neon:experiment", "tier:4", "left:neon"), sink.events)
        assertEquals(Triple(0L, 2, false), sink.lastLeft)
    }

    @Test fun `debug builds can preview another world, which ends the current visit`() {
        compose.setContent { Host(variant = "orbit", debuggable = true, changingConfigurations = false) }
        compose.onNodeWithContentDescription("Preview a Support world").performClick()
        compose.onNodeWithText("Abyss").performClick()
        compose.onNodeWithText("Summon Amber Angler").assertIsDisplayed()
        assertEquals(listOf("view:orbit:experiment", "left:orbit", "view:abyss:debug_override"), sink.events)
        assertEquals(SupportWorld.Abyss, SupportWorldDebugOverride.world)
    }
}
