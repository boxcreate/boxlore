package cx.aswin.boxlore.feature.home.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MixTimeTest {
    @Test
    fun `remaining minutes include complete hours`() {
        assertEquals(90, remainingMixMinutes(10800, 0.5f))
        assertEquals(60, remainingMixMinutes(7200, 0.5f))
        assertEquals(180, remainingMixMinutes(14400, 0.25f))
    }

    @Test
    fun `sub minute remainders display one minute rather than zero`() {
        assertEquals(1, remainingMixMinutes(120, 0.75f))
        assertEquals(1, remainingMixMinutes(60, 0.99f))
        assertEquals(1, remainingMixMinutes(120, 0.5f))
    }

    @Test
    fun `ordinary remainders retain whole minute formatting`() {
        assertEquals(30, remainingMixMinutes(3600, 0.5f))
        assertEquals(9, remainingMixMinutes(1200, 0.51f))
    }
}
