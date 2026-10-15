package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardSpec
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1000dp-h1000dp-mdpi")
class AdaptiveCardBoundsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `tight fill-width slots cap and center every tablet surface while phones retain their constraints`() {
        val family = mutableStateOf(BoxloreCardFamily.Hero)
        val window = mutableStateOf(BoxloreWindowLayout(1_000f, 1_000f))
        val tight = mutableStateOf(true)
        compose.setContent {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides window.value) {
                Box(Modifier.width(900.dp), propagateMinConstraints = tight.value) {
                    Box(Modifier.fillMaxWidth().boxloreCardWidth(family.value)) {
                        Box(Modifier.fillMaxWidth().height(48.dp).testTag("painted_surface"))
                    }
                }
            }
        }
        listOf(true, false).forEach { tightSlot ->
            compose.runOnIdle { tight.value = tightSlot }
            BoxloreCardFamily.entries.forEach { value ->
                compose.runOnIdle { family.value = value }
                val surface = compose.onNodeWithTag("painted_surface").fetchSemanticsNode().boundsInRoot
                assertEquals(boxloreCardSpec(window.value, value).maximumWidthDp, surface.width, 0.01f)
                assertEquals(450f, surface.center.x, 0.01f)
            }
        }
        compose.runOnIdle { window.value = BoxloreWindowLayout(800f, 360f) }
        val phone = compose.onNodeWithTag("painted_surface").fetchSemanticsNode().boundsInRoot
        assertEquals(900f, phone.width, 0.01f)
    }

    @Test
    fun `narrow reading caps still win inside tight tablet slots and leave phones unchanged`() {
        val window = mutableStateOf(BoxloreWindowLayout(1_000f, 1_000f))
        compose.setContent {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides window.value) {
                Box(Modifier.width(900.dp), propagateMinConstraints = true) {
                    Box(Modifier.fillMaxWidth().boxloreCardWidth(BoxloreCardFamily.Reading, maximumWidthDp = 520f)) {
                        Box(Modifier.fillMaxWidth().height(48.dp).testTag("reading_surface"))
                    }
                }
            }
        }
        val tablet = compose.onNodeWithTag("reading_surface").fetchSemanticsNode().boundsInRoot
        assertEquals(520f, tablet.width, 0.01f)
        assertEquals(450f, tablet.center.x, 0.01f)
        compose.runOnIdle { window.value = BoxloreWindowLayout(800f, 360f) }
        assertEquals(900f, compose.onNodeWithTag("reading_surface").fetchSemanticsNode().boundsInRoot.width, 0.01f)
    }
}
