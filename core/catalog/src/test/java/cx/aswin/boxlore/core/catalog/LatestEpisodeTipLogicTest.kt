package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.testing.TestFixtures
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LatestEpisodeTipLogicTest {
    @Test
    fun `null existing always replaces`() {
        val incoming = TestFixtures.episode(id = "1", publishedDate = 10L)
        assertTrue(LatestEpisodeTipLogic.shouldReplace(existing = null, incoming = incoming))
    }

    @Test
    fun `newer published date replaces`() {
        val existing = TestFixtures.episode(id = "1", publishedDate = 10L)
        val incoming = TestFixtures.episode(id = "2", publishedDate = 20L)
        assertTrue(LatestEpisodeTipLogic.shouldReplace(existing, incoming))
    }

    @Test
    fun `older published date does not replace`() {
        val existing = TestFixtures.episode(id = "-9", publishedDate = 200L)
        val incoming = TestFixtures.episode(id = "1", publishedDate = 100L)
        assertFalse(LatestEpisodeTipLogic.shouldReplace(existing, incoming))
    }

    @Test
    fun `same date different id does not replace`() {
        val existing = TestFixtures.episode(id = "-9", publishedDate = 100L)
        val incoming = TestFixtures.episode(id = "55", publishedDate = 100L)
        assertFalse(LatestEpisodeTipLogic.shouldReplace(existing, incoming))
    }

    @Test
    fun `same date and id is a no-op`() {
        val existing = TestFixtures.episode(id = "1", publishedDate = 100L)
        val incoming = TestFixtures.episode(id = "1", publishedDate = 100L)
        assertFalse(LatestEpisodeTipLogic.shouldReplace(existing, incoming))
    }
}
