package cx.aswin.boxlore.feature.home.logic

import cx.aswin.boxlore.core.testing.TestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeShowsOrderLogicTest {
    @Test
    fun `first pass sorts by score then title`() {
        val subs =
            listOf(
                TestFixtures.podcast(id = "b", title = "B"),
                TestFixtures.podcast(id = "a", title = "A"),
                TestFixtures.podcast(id = "c", title = "C"),
            )
        val order =
            HomeShowsOrderLogic.computeStableShowsOrder(
                previousOrder = null,
                subs = subs,
                scores = mapOf("a" to 1.0, "b" to 5.0, "c" to 5.0),
            )

        assertEquals(listOf("b", "c", "a"), order)
    }

    @Test
    fun `later pass keeps order drops removed and prepends new`() {
        val subs =
            listOf(
                TestFixtures.podcast(id = "new", title = "New"),
                TestFixtures.podcast(id = "keep", title = "Keep"),
            )
        val order =
            HomeShowsOrderLogic.computeStableShowsOrder(
                previousOrder = listOf("gone", "keep"),
                subs = subs,
                scores = emptyMap(),
            )

        assertEquals(listOf("new", "keep"), order)
    }

    @Test
    fun `ordered subs maps ids to podcasts`() {
        val subs =
            listOf(
                TestFixtures.podcast(id = "a"),
                TestFixtures.podcast(id = "b"),
            )
        assertEquals(listOf("b", "a"), HomeShowsOrderLogic.orderedSubs(listOf("b", "a", "missing"), subs).map { it.id })
    }

    @Test
    fun `pins lead unpinned remainder and skip unsubscribed`() {
        val subs =
            listOf(
                TestFixtures.podcast(id = "new", title = "New"),
                TestFixtures.podcast(id = "keep", title = "Keep"),
                TestFixtures.podcast(id = "pin2", title = "Pin Two"),
                TestFixtures.podcast(id = "pin1", title = "Pin One"),
            )
        val order =
            HomeShowsOrderLogic.computeStableShowsOrder(
                previousOrder = listOf("gone", "keep"),
                subs = subs,
                scores = emptyMap(),
                pinnedIds = listOf("pin1", "unsubscribed", "pin2", "pin1", "extra1", "extra2", "extra3"),
            )

        assertEquals(listOf("pin1", "pin2", "new", "keep"), order)
    }

    @Test
    fun `pins cap at five subscribed ids`() {
        val subs = (1..7).map { TestFixtures.podcast(id = "p$it", title = "P$it") }
        val order =
            HomeShowsOrderLogic.computeStableShowsOrder(
                previousOrder = null,
                subs = subs,
                scores = (1..7).associate { "p$it" to (8 - it).toDouble() },
                pinnedIds = listOf("p6", "p5", "p4", "p3", "p2", "p1"),
            )
        assertEquals(listOf("p6", "p5", "p4", "p3", "p2", "p1", "p7"), order)
    }

    @Test
    fun `new unpinned subs prepend after pins not ahead of them`() {
        val subs =
            listOf(
                TestFixtures.podcast(id = "fresh", title = "Fresh"),
                TestFixtures.podcast(id = "pin", title = "Pinned"),
                TestFixtures.podcast(id = "old", title = "Old"),
            )
        val order =
            HomeShowsOrderLogic.computeStableShowsOrder(
                previousOrder = listOf("old"),
                subs = subs,
                scores = mapOf("fresh" to 99.0, "old" to 1.0),
                pinnedIds = listOf("pin"),
            )

        assertEquals(listOf("pin", "fresh", "old"), order)
    }

    @Test
    fun `mixtape is not a podcast id in the order`() {
        val subs = listOf(TestFixtures.podcast(id = "show", title = "Show"))
        val order =
            HomeShowsOrderLogic.computeStableShowsOrder(
                previousOrder = null,
                subs = subs,
                scores = emptyMap(),
                pinnedIds = listOf("show"),
            )
        assertEquals(listOf("show"), order)
        assertFalse(order.contains("mixtape"))
    }

    @Test
    fun `first pass follows scores not subscribe time`() {
        val nowMs = 1_700_000_000_000L
        val subs =
            listOf(
                TestFixtures.podcast(
                    id = "fresh",
                    title = "Fresh",
                    subscribedAt = nowMs - 2L * 3_600_000L,
                ),
                TestFixtures.podcast(
                    id = "old",
                    title = "Old",
                    subscribedAt = nowMs - 200L * 24 * 3_600_000L,
                ),
            )
        val order =
            HomeShowsOrderLogic.computeStableShowsOrder(
                previousOrder = null,
                subs = subs,
                scores = mapOf("fresh" to 0.72, "old" to 0.95),
            )
        assertEquals(listOf("old", "fresh"), order)
    }

    @Test
    fun `foreground refresh moves only meaningfully stronger shows`() {
        val subs =
            listOf(
                TestFixtures.podcast(id = "a", title = "A"),
                TestFixtures.podcast(id = "b", title = "B"),
                TestFixtures.podcast(id = "c", title = "C"),
            )

        val order =
            HomeShowsOrderLogic.computeStableShowsOrder(
                previousOrder = listOf("a", "b", "c"),
                subs = subs,
                scores = mapOf("a" to 0.40, "b" to 0.43, "c" to 0.90),
                refreshFromScores = true,
            )

        assertEquals(listOf("c", "a", "b"), order)
    }

    @Test
    fun `foreground refresh preserves tiny score differences`() {
        val subs =
            listOf(
                TestFixtures.podcast(id = "a", title = "A"),
                TestFixtures.podcast(id = "b", title = "B"),
            )

        val order =
            HomeShowsOrderLogic.computeStableShowsOrder(
                previousOrder = listOf("a", "b"),
                subs = subs,
                scores =
                    mapOf(
                        "a" to 0.50,
                        "b" to 0.50 + HomeShowsOrderLogic.SCORE_MOVE_THRESHOLD,
                    ),
                refreshFromScores = true,
            )

        assertEquals(listOf("a", "b"), order)
    }

    @Test
    fun `Home requests at most one refresh after snapshot grace`() {
        val createdAt = 1_000L

        assertFalse(
            HomeShowsRefreshPolicy.shouldRequestRefresh(
                hasStableOrder = true,
                hasPendingRequest = false,
                snapshotCreatedAtMs = createdAt,
                nowMs = createdAt + HomeShowsRefreshPolicy.MIN_SNAPSHOT_AGE_MS - 1L,
            ),
        )
        assertTrue(
            HomeShowsRefreshPolicy.shouldRequestRefresh(
                hasStableOrder = true,
                hasPendingRequest = false,
                snapshotCreatedAtMs = createdAt,
                nowMs = createdAt + HomeShowsRefreshPolicy.MIN_SNAPSHOT_AGE_MS,
            ),
        )
        assertFalse(
            HomeShowsRefreshPolicy.shouldRequestRefresh(
                hasStableOrder = true,
                hasPendingRequest = true,
                snapshotCreatedAtMs = createdAt,
                nowMs = createdAt + HomeShowsRefreshPolicy.MIN_SNAPSHOT_AGE_MS,
            ),
        )
    }

    @Test
    fun `mixtape cache invalidates only when signature changes after first build`() {
        assertFalse(HomeShowsOrderLogic.shouldInvalidateMixtapeCache(null, setOf("a")))
        assertFalse(HomeShowsOrderLogic.shouldInvalidateMixtapeCache(setOf("a"), setOf("a")))
        assertTrue(HomeShowsOrderLogic.shouldInvalidateMixtapeCache(setOf("a"), setOf("a", "b")))
    }
}
