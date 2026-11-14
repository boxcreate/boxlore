package cx.aswin.boxlore.wearlink

import cx.aswin.boxlore.core.model.Episode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneListRulesTest {
    private fun episode(id: String, published: Long) =
        Episode(id = id, title = id, description = "", audioUrl = "https://example.com/$id.mp3", publishedDate = published)

    @Test
    fun `new episodes are each show's newest, without the finished ones, newest first`() {
        val latest = listOf(episode("old", 100L), episode("done", 300L), episode("new", 200L))

        assertEquals(listOf("new", "old"), PhoneListRules.newEpisodes(latest, completedIds = setOf("done")).map { it.id })
    }

    @Test
    fun `a long library sends only the newest few`() {
        val latest = (1..50).map { episode("e$it", it.toLong()) }

        val sent = PhoneListRules.newEpisodes(latest, completedIds = emptySet())

        assertEquals(PhoneListRules.MAX_NEW, sent.size)
        assertEquals("e50", sent.first().id)
    }

    @Test
    fun `the Daily Mix is rebuilt only once it is half an hour old`() {
        assertFalse(PhoneListRules.mixIsFresh(builtAt = 0L, now = 1_000L))
        assertTrue(PhoneListRules.mixIsFresh(builtAt = 1_000L, now = 1_000L + 29 * 60_000L))
        assertFalse(PhoneListRules.mixIsFresh(builtAt = 1_000L, now = 1_000L + 31 * 60_000L))
    }
}
