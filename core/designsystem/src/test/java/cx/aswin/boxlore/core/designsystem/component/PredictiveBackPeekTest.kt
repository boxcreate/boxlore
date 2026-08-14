package cx.aswin.boxlore.core.designsystem.component

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PredictiveBackPeekTest {
    @Test
    fun `gesture end restores rest progress so the wrapper is not left scaled down`() {
        assertEquals(0f, PredictiveBackPeek.progressAfterGesture())
        assertEquals(0f, PredictiveBackPeek.REST_PROGRESS)
        assertEquals(1f, PredictiveBackPeek.scaleFor(PredictiveBackPeek.progressAfterGesture()))
    }

    @Test
    fun `peek progress shrinks toward nine tenths and rest is full size`() {
        assertEquals(1f, PredictiveBackPeek.scaleFor(0f))
        assertEquals(0.9f, PredictiveBackPeek.scaleFor(1f), 0.0001f)
    }
}
