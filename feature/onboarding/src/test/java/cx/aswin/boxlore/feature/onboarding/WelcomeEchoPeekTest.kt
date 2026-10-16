package cx.aswin.boxlore.feature.onboarding

import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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

    @Test
    fun `Echo sizes itself to the room above the button and hides only when even the smallest Echo can't fit`() {
        assertEquals(WelcomeEchoMaxCell, welcomeEchoCell(200.dp))
        assertEquals(100.dp.value, welcomeEchoCell(51.dp)!!.value, 0.01f)
        assertEquals(WelcomeEchoMinCell.value, welcomeEchoCell(WelcomeEchoMinCell * WelcomeEchoNeckFraction)!!.value, 0.01f)
        assertNull(welcomeEchoCell(40.dp))
    }
}
