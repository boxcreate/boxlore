package cx.aswin.boxlore.feature.explore

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The composition-level wiring the For you refresh policy relies on: one report per real change. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ExploreVisibilityEffectTest {
    @get:Rule val composeRule = createComposeRule()

    private val reports = mutableListOf<Boolean>()
    private val shown = mutableStateOf(true)
    private val generation = mutableIntStateOf(0)
    private var lastGeneration = -1

    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    private fun host(owner: Owner) {
        composeRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                if (shown.value) {
                    // Reading the generation recomposes here and builds a new callback instance each
                    // time, as a bound method reference does.
                    val seen = generation.intValue
                    ExploreVisibilityEffect { visible ->
                        lastGeneration = seen
                        reports += visible
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test fun `started reports visible once and stopping reports hidden`() {
        val owner = Owner()
        composeRule.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        host(owner)
        composeRule.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
        composeRule.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        composeRule.waitForIdle()
        assertEquals(listOf(true, false, true), reports)
    }

    @Test fun `a created but not started screen reports nothing until it starts`() {
        val owner = Owner()
        composeRule.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
        host(owner)
        assertEquals(emptyList<Boolean>(), reports)
        composeRule.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        composeRule.waitForIdle()
        assertEquals(listOf(true), reports)
    }

    @Test fun `recompositions with a new callback do not report again`() {
        val owner = Owner()
        composeRule.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        host(owner)
        repeat(5) {
            composeRule.runOnIdle { generation.intValue += 1 }
            composeRule.waitForIdle()
        }
        assertEquals(listOf(true), reports)
    }

    @Test fun `leaving and re-entering the composition, as a configuration change does, reports once each`() {
        val owner = Owner()
        composeRule.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        host(owner)
        composeRule.runOnIdle { shown.value = false }
        composeRule.waitForIdle()
        composeRule.runOnIdle { shown.value = true }
        composeRule.waitForIdle()
        assertEquals(listOf(true, false, true), reports)
    }

    @Test fun `the latest callback receives the stop`() {
        val owner = Owner()
        val late = mutableListOf<Boolean>()
        val useLate = mutableStateOf(false)
        composeRule.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        composeRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                val target = if (useLate.value) late else reports
                ExploreVisibilityEffect { visible -> target += visible }
            }
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { useLate.value = true }
        composeRule.waitForIdle()
        composeRule.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
        composeRule.waitForIdle()
        assertEquals(listOf(true), reports)
        assertEquals(listOf(false), late)
    }
}
