package cx.aswin.boxlore.core.designsystem.theme

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TabletSecondaryClickGestureTest {
    @Test
    fun `only an unconsumed secondary press starts a menu gesture`() {
        val gesture = TabletSecondaryClickGesture()
        assertEquals(SecondaryClickHandling.Ignore, gesture.update(isPress = false, secondaryPressed = true, consumed = false))
        assertEquals(SecondaryClickHandling.Ignore, gesture.update(isPress = true, secondaryPressed = false, consumed = false))
        assertEquals(SecondaryClickHandling.Ignore, gesture.update(isPress = true, secondaryPressed = true, consumed = true))
        assertEquals(SecondaryClickHandling.Ignore, gesture.update(isPress = false, secondaryPressed = true, consumed = false))
        assertEquals(SecondaryClickHandling.Ignore, gesture.update(isPress = false, secondaryPressed = false, consumed = false))
        assertEquals(SecondaryClickHandling.InvokeAndConsume, gesture.update(isPress = true, secondaryPressed = true, consumed = false))
    }

    @Test
    fun `an owned gesture invokes once and consumes through its release`() {
        val gesture = TabletSecondaryClickGesture()
        assertEquals(SecondaryClickHandling.InvokeAndConsume, gesture.update(isPress = true, secondaryPressed = true, consumed = false))
        assertEquals(SecondaryClickHandling.Consume, gesture.update(isPress = true, secondaryPressed = true, consumed = false))
        assertEquals(SecondaryClickHandling.Consume, gesture.update(isPress = false, secondaryPressed = true, consumed = true))
        assertEquals(SecondaryClickHandling.Consume, gesture.update(isPress = false, secondaryPressed = false, consumed = false))
        assertEquals(SecondaryClickHandling.Ignore, gesture.update(isPress = false, secondaryPressed = false, consumed = false))
        assertEquals(SecondaryClickHandling.InvokeAndConsume, gesture.update(isPress = true, secondaryPressed = true, consumed = false))
    }

    @Test
    fun `a parent cannot steal the secondary press or release owned by a child`() {
        val parent = TabletSecondaryClickGesture()
        val child = TabletSecondaryClickGesture()
        assertEquals(SecondaryClickHandling.InvokeAndConsume, child.update(isPress = true, secondaryPressed = true, consumed = false))
        assertEquals(SecondaryClickHandling.Ignore, parent.update(isPress = true, secondaryPressed = true, consumed = true))
        assertEquals(SecondaryClickHandling.Consume, child.update(isPress = false, secondaryPressed = false, consumed = false))
        assertEquals(SecondaryClickHandling.Ignore, parent.update(isPress = false, secondaryPressed = false, consumed = true))
    }
}
