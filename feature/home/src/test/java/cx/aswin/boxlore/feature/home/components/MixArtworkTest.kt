package cx.aswin.boxlore.feature.home.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MixArtworkTest {
    @Test fun `missing episode art uses the episode show artwork like details`() {
        assertEquals("show", resolveMixArtwork(null, "show", "parent", "fallback"))
        assertEquals("show", resolveMixArtwork("  ", " show ", "parent", "fallback"))
    }

    @Test fun `episode artwork wins and empty candidates fall through`() {
        assertEquals("episode", resolveMixArtwork("episode", "show", "parent", "fallback"))
        assertEquals("parent", resolveMixArtwork(null, "", "parent", "fallback"))
        assertEquals("fallback", resolveMixArtwork(null, "", " ", "fallback"))
        assertNull(resolveMixArtwork(null, "", " ", null))
    }
}
