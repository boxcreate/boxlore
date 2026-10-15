package cx.aswin.boxlore.feature.onboarding

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WelcomeEchoPeekTest {
    @Test
    fun `Echo starts hidden behind the button, pops slightly past its spot and settles`() {
        assertEquals(0f, welcomeEchoRise(0f), 0.0001f)
        assertEquals(1f, welcomeEchoRise(1f), 0.0001f)
        val samples = (0..100).map { welcomeEchoRise(it / 100f) }
        assertTrue(samples.max() > 1f && samples.max() < 1.1f)
        assertEquals(0f, welcomeEchoRise(-1f), 0.0001f)
        assertEquals(1f, welcomeEchoRise(2f), 0.0001f)
    }
}
