package cx.aswin.boxlore.core.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SupportWorldVariantTest {
    private val known = setOf("control", "neon", "orbit", "alchemy", "abyss", "sky")

    /** Records each flag read as true when it would send PostHog's exposure. */
    private class FakeFlag(private val value: Any?) {
        val reads = mutableListOf<Boolean>()
        fun read(sendExposure: Boolean): Any? {
            reads += sendExposure
            return value
        }
    }

    @Test fun `a known variant is returned and records exactly one exposure`() {
        val flag = FakeFlag("orbit")
        assertEquals("orbit", SupportAnalyticsTracks.resolveSupportWorldVariant(known, flag::read))
        assertEquals(listOf(false, true), flag.reads)
    }

    @Test fun `a variant is matched after trimming and lowercasing`() {
        val flag = FakeFlag("  SKY ")
        assertEquals("sky", SupportAnalyticsTracks.resolveSupportWorldVariant(known, flag::read))
        assertEquals(listOf(false, true), flag.reads)
    }

    @Test fun `flags that have not loaded never record an exposure`() {
        val flag = FakeFlag(null)
        assertNull(SupportAnalyticsTracks.resolveSupportWorldVariant(known, flag::read))
        assertEquals(listOf(false), flag.reads)
    }

    @Test fun `an unknown or non-string variant never records an exposure`() {
        listOf<Any>("galaxy", "", true, 3).forEach { value ->
            val flag = FakeFlag(value)
            assertNull(SupportAnalyticsTracks.resolveSupportWorldVariant(known, flag::read))
            assertEquals("exposure for $value", listOf(false), flag.reads)
        }
    }
}
