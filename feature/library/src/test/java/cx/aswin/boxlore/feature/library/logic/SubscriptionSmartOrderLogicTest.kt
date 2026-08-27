package cx.aswin.boxlore.feature.library.logic

import cx.aswin.boxlore.core.testing.TestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SubscriptionSmartOrderLogicTest {
    @Test
    fun `sort follows score then title without a recency band`() {
        val fresh = TestFixtures.podcast(id = "fresh", title = "Fresh")
        val favorite = TestFixtures.podcast(id = "fav", title = "Favorite")
        val ordered =
            SubscriptionSmartOrderLogic.sort(
                podcasts = listOf(favorite, fresh),
                scores = mapOf("fresh" to 0.72, "fav" to 0.95),
            )
        assertEquals(listOf("fav", "fresh"), ordered.map { it.id })
    }

    @Test
    fun `equal scores break ties by title`() {
        val a = TestFixtures.podcast(id = "a", title = "A")
        val b = TestFixtures.podcast(id = "b", title = "B")
        val ordered =
            SubscriptionSmartOrderLogic.sort(
                podcasts = listOf(b, a),
                scores = mapOf("a" to 0.5, "b" to 0.5),
            )
        assertEquals(listOf("a", "b"), ordered.map { it.id })
    }
}
