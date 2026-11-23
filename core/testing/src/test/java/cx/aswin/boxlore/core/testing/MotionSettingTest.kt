package cx.aswin.boxlore.core.testing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MotionSettingTest {
    @Test
    fun `each sequence puts back the motion setting it found, even when it fails`() {
        val applied = mutableListOf<Boolean>()
        val motion = MotionSetting { applied += it }
        val seen = mutableListOf<Boolean>()

        motion.during(true) { seen += motion.reduced }
        motion.during(false) { seen += motion.reduced }
        assertThrows(IllegalStateException::class.java) { motion.during(true) { error("capture failed") } }
        assertEquals(listOf(true, false), seen)
        assertEquals(listOf(true, false, true, false), applied)
        assertFalse(motion.reduced)

        // A test that turned motion off itself keeps it off after a reduced sequence.
        motion.set(true)
        motion.during(true) {}
        assertTrue(motion.reduced)
    }
}
