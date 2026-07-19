package cx.aswin.boxlore.feature.onboarding

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class AiOnboardingOptionIconsTest {
    @Test
    fun storyAndUnknown_resolveDistinctIconPairs() {
        val story = getOptionIcons("Storyseeker | Serialized narratives")
        val unknown = getOptionIcons("Something totally unknown xyz")
        assertNotEquals(story, unknown)
    }

    @Test
    fun sameOption_isDeterministic() {
        val a = getOptionIcons("Build my feed now")
        val b = getOptionIcons("Build my feed now")
        assertEquals(a, b)
    }

    @Test
    fun tastesOption_differsFromDefaultMicFallback() {
        val tastes = getOptionIcons("Tell me more about my tastes")
        val unknown = getOptionIcons("zzzz-not-a-real-category")
        assertNotEquals(tastes, unknown)
    }
}
