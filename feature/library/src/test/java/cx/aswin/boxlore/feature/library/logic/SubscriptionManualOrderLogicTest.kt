package cx.aswin.boxlore.feature.library.logic

import cx.aswin.boxlore.core.testing.TestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SubscriptionManualOrderLogicTest {
    @Test
    fun `apply keeps saved order drops unknown and appends new a to z`() {
        val podcasts =
            listOf(
                TestFixtures.podcast(id = "c", title = "Charlie"),
                TestFixtures.podcast(id = "a", title = "Alpha"),
                TestFixtures.podcast(id = "b", title = "Bravo"),
            )
        val ordered =
            SubscriptionManualOrderLogic.apply(
                order = listOf("gone", "b", "a"),
                podcasts = podcasts,
            )
        assertEquals(listOf("b", "a", "c"), ordered.map { it.id })
    }

    @Test
    fun `empty saved order sorts remaining a to z`() {
        val podcasts =
            listOf(
                TestFixtures.podcast(id = "b", title = "B"),
                TestFixtures.podcast(id = "a", title = "A"),
            )
        assertEquals(
            listOf("a", "b"),
            SubscriptionManualOrderLogic.apply(emptyList(), podcasts).map { it.id },
        )
    }

    @Test
    fun `order after drag moves on the visible list`() {
        assertEquals(
            listOf("b", "a", "c"),
            SubscriptionManualOrderLogic.orderAfterDrag(
                visibleIds = listOf("a", "b", "c"),
                fromId = "a",
                toId = "b",
            ),
        )
    }

    @Test
    fun `move ignores unknown ids`() {
        assertEquals(
            listOf("a", "b"),
            SubscriptionManualOrderLogic.move(listOf("a", "b"), "a", "missing"),
        )
    }

    @Test
    fun `moveVisible skips blocked header keys and unchanged lists`() {
        assertEquals(
            null,
            SubscriptionManualOrderLogic.moveVisible(
                ids = listOf("a", "b"),
                fromId = "shows_genre_header",
                toId = "a",
                blockedKeys = setOf("shows_genre_header"),
            ),
        )
        assertEquals(
            null,
            SubscriptionManualOrderLogic.moveVisible(
                ids = listOf("a", "b"),
                fromId = "a",
                toId = "a",
                blockedKeys = emptySet(),
            ),
        )
        assertEquals(
            listOf("b", "a"),
            SubscriptionManualOrderLogic.moveVisible(
                ids = listOf("a", "b"),
                fromId = "a",
                toId = "b",
                blockedKeys = setOf("shows_genre_header"),
            ),
        )
    }

    @Test
    fun `drop removes unsubscribed id`() {
        assertEquals(
            listOf("a", "c"),
            SubscriptionManualOrderLogic.drop(listOf("a", "b", "c"), "b"),
        )
    }
}
