package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.model.FeedUrlCorrectionReason
import cx.aswin.boxlore.core.model.FeedUrlCorrectionStep
import cx.aswin.boxlore.core.network.model.V2ShowAddress
import cx.aswin.boxlore.core.network.model.V2ShowFlags
import cx.aswin.boxlore.core.network.model.V2ShowsFeedsResponse
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The daily address check of followed shows (own-episode-IDs P4 PR 14, plan section 11; decision 2):
 * chunked calls, confirmed moves only, one stamp per show per day, and the `/podcast` fallback only
 * for shows the check couldn't reach.
 */
class FeedAddressDailyCheckTest {
    private val now = 10 * DAY
    private val rows = mutableMapOf<String, PodcastEntity>()
    private val calls = mutableListOf<List<Long>>()
    private val changes = mutableListOf<FeedAddressChange>()
    private val events = mutableListOf<String>()
    private val unreadable = mutableSetOf<String>()
    private var enabled = true
    private var answer: (List<Long>) -> V2ShowsFeedsResponse? = { ids -> V2ShowsFeedsResponse(true, ids.map { address(it) }) }

    private val check =
        FeedAddressDailyCheck(
            followedShows = { rows.values.filter { it.isSubscribed } },
            fetchAddresses = { ids ->
                calls += ids
                answer(ids)
            },
            readsAt = { id, url ->
                events += "read $id $url"
                url !in unreadable
            },
            replaceAddress = { change ->
                changes += change
                events += "swap ${change.podcastId}"
                val row = rows.getValue(change.podcastId)
                rows[change.podcastId] =
                    row.copy(feedUrl = change.feedUrl, previousFeedUrl = row.feedUrl, feedUrlSource = change.source, feedUrlCheckedAt = change.checkedAt)
                FeedAddressUpdate.UPDATED
            },
            markChecked = { id, at ->
                events += "stamp $id"
                rows[id] = rows.getValue(id).copy(feedUrlCheckedAt = at)
            },
            enabled = { enabled },
            nowMillis = { now },
        )

    @Test
    fun `501 followed shows make two calls, and a second run the same day makes none`() = runTest {
        (1..501).forEach { add(show(it.toString())) }

        val outcome = check.run()

        assertEquals(listOf(500, 1), calls.map { it.size })
        assertEquals(501, outcome.checked)
        assertTrue(rows.values.all { it.feedUrlCheckedAt == now })
        calls.clear()
        check.run()
        assertTrue(calls.isEmpty(), "never twice a day")
    }

    @Test
    fun `only a confirmed move to a readable registry address is swapped`() = runTest {
        add(show("1"))
        add(show("2"))
        add(show("3"))
        add(show("4"))
        add(show("5"))
        add(show("6"))
        add(show("7").copy(feedUrl = MOVED, previousFeedUrl = NEW, feedUrlSource = FeedUrlSources.FEED_MOVE))
        answer = { ids ->
            V2ShowsFeedsResponse(
                registryAvailable = true,
                results =
                ids.map { id ->
                    when (id) {
                        1L -> address(id)
                        2L -> address(id, repair = false)
                        3L -> address(id, source = "catalog")
                        4L -> address(id, url = "http://new.example/4.xml", via = "proxy")
                        5L -> address(id, url = null)
                        6L -> address(id, url = OLD)
                        else -> address(id)
                    }
                },
            )
        }

        val outcome = check.run()

        assertEquals(listOf("1"), changes.map { it.podcastId }, "no flag, a dump address, http only, no address, the same address, or back to where a move left: kept")
        assertEquals(listOf("read 1 $NEW", "swap 1"), events.filterNot { it.startsWith("stamp") }, "read before it is saved")
        val change = changes.single()
        assertEquals(NEW, change.feedUrl)
        assertEquals(OLD, change.onlyReplacing)
        assertEquals("registry:apple", change.source)
        assertEquals(now, change.checkedAt)
        assertEquals(FeedUrlCorrectionReason.REGISTRY, change.reason)
        assertEquals(FeedUrlCorrectionStep.DAILY_CHECK, change.step)
        assertEquals(1, outcome.swapped)
        assertTrue(rows.values.all { it.feedUrlCheckedAt == now }, "every show the registry answered for is checked")
    }

    @Test
    fun `a registry address the phone can't read is never saved, and the next day's check tries again`() = runTest {
        add(show("1"))
        unreadable += NEW

        val outcome = check.run()

        assertTrue(changes.isEmpty(), "the working address stays")
        assertEquals(0, outcome.swapped)
        assertEquals(OLD, rows.getValue("1").feedUrl)
        assertEquals(now, rows.getValue("1").feedUrlCheckedAt, "checked today, so the show isn't asked about twice a day")
        events.clear()
        check.run()
        assertTrue(events.isEmpty(), "never twice a day")

        unreadable.clear()
        val tomorrow =
            FeedAddressDailyCheck(
                followedShows = { rows.values.toList() },
                fetchAddresses = { ids -> answer(ids) },
                readsAt = { _, _ -> true },
                replaceAddress = { change ->
                    changes += change
                    FeedAddressUpdate.UPDATED
                },
                markChecked = { _, _ -> },
                enabled = { true },
                nowMillis = { now + DAY },
            )
        tomorrow.run()
        assertEquals(listOf(NEW), changes.map { it.feedUrl })
    }

    @Test
    fun `a swap whose notification request couldn't be saved stays due for the next pass`() = runTest {
        add(show("1"))
        val notSaved =
            FeedAddressDailyCheck(
                followedShows = { rows.values.toList() },
                fetchAddresses = { ids -> answer(ids) },
                readsAt = { _, _ -> true },
                replaceAddress = { change ->
                    changes += change
                    FeedAddressUpdate.NOT_SAVED
                },
                markChecked = { id, at -> rows[id] = rows.getValue(id).copy(feedUrlCheckedAt = at) },
                enabled = { true },
                nowMillis = { now },
            )

        notSaved.run()
        assertNotEquals(now, rows.getValue("1").feedUrlCheckedAt, "not stamped: the swap is still owed")
        notSaved.run()

        assertEquals(2, changes.size, "tried again on the next pass")
        assertEquals(OLD, rows.getValue("1").feedUrl)
    }

    @Test
    fun `a show the server says Podcast Index merged moves its follow, keeps its address and is stamped`() = runTest {
        add(show("1258562"))
        add(show("2"))
        val merges = mutableListOf<Pair<String, String>>()
        val merging =
            FeedAddressDailyCheck(
                followedShows = { rows.values.toList() },
                fetchAddresses = { ids ->
                    V2ShowsFeedsResponse(
                        true,
                        ids.map { id -> if (id == 1258562L) address(id, url = null).copy(mergedInto = 7976624L) else address(id).copy(mergedInto = id) },
                    )
                },
                readsAt = { _, _ -> true },
                replaceAddress = { change ->
                    changes += change
                    FeedAddressUpdate.UPDATED
                },
                markChecked = { id, at -> rows[id] = rows.getValue(id).copy(feedUrlCheckedAt = at) },
                enabled = { true },
                nowMillis = { now },
                onMerged = { old, new -> merges += old to new },
            )

        merging.run()

        assertEquals(listOf("1258562" to "7976624"), merges, "a mergedInto naming the show itself is ignored")
        assertEquals(listOf("2"), changes.map { it.podcastId }, "the merged show's address is never swapped")
        assertEquals(now, rows.getValue("1258562").feedUrlCheckedAt)
    }

    @Test
    fun `a swapped show is stamped by the swap itself, never stamped first and left unswapped`() = runTest {
        add(show("1"))
        add(show("2"))
        answer = { ids -> V2ShowsFeedsResponse(true, ids.map { address(it, repair = it == 1L) }) }

        check.run()

        assertEquals(listOf("read 1 $NEW", "swap 1", "stamp 2"), events)
        assertEquals(now, rows.getValue("1").feedUrlCheckedAt)
    }

    @Test
    fun `the address the saved one replaced is never swapped back, whatever replaced it`() = runTest {
        // Dead-address recovery left a dead registry address X for a readable Z; the registry still flags X.
        add(show("1").copy(feedUrl = MOVED, previousFeedUrl = NEW, feedUrlSource = FeedUrlSources.FEED_REPORT))
        add(show("2").copy(feedUrl = MOVED, previousFeedUrl = NEW, feedUrlSource = FeedUrlSources.PODCAST_LOOKUP))
        // A move reached this phone by a cloud pull; its previous address is this phone's old one.
        add(show("3").copy(feedUrl = MOVED, previousFeedUrl = NEW, feedUrlSource = FeedUrlSources.CLOUD_SYNC))

        check.run()

        assertTrue(changes.isEmpty())
        assertTrue(events.none { it.startsWith("read") }, "nothing is even read")
        assertTrue(rows.values.all { it.feedUrl == MOVED && it.feedUrlCheckedAt == now })
    }

    @Test
    fun `follows changed on the phone wait for their push, other shows are left out`() = runTest {
        add(show("1").copy(isDirty = true, syncedAt = 5L))
        add(show("2").copy(isDirty = true, syncedAt = 0L))
        add(show("3").copy(isSubscribed = false))
        add(show("rss:abc").copy(sourceType = PodcastEntity.SOURCE_RSS))
        add(show("4").copy(feedUrl = null))
        add(show("5").copy(feedUrlCheckedAt = now - DAY + 1))

        check.run()

        assertEquals(listOf(listOf(2L)), calls, "a follow never synced to an account is this phone's alone and is checked")
        assertEquals(0L, rows.getValue("1").feedUrlCheckedAt)
    }

    @Test
    fun `a failed call or an unavailable registry stamps and swaps nothing, so the fallback may run`() = runTest {
        add(show("1"))
        answer = { null }
        assertEquals(1, check.run().unchecked)
        answer = { ids -> V2ShowsFeedsResponse(false, ids.map { address(it) }) }
        assertEquals(1, check.run().unchecked)

        assertTrue(changes.isEmpty())
        assertEquals(0L, rows.getValue("1").feedUrlCheckedAt)
        assertEquals(NEW, fallback("1"), "the /podcast re-check runs for a show the check couldn't reach")
        assertNull(fallback("1"), "and only once a day")
    }

    @Test
    fun `a show the daily check reached is never re-checked by the fallback the same day`() = runTest {
        add(show("1"))
        answer = { ids -> V2ShowsFeedsResponse(true, ids.map { address(it, repair = false) }) }

        check.run()

        assertNull(fallback("1"))
    }

    @Test
    fun `with the switch off nothing is asked or stamped`() = runTest {
        add(show("1"))
        enabled = false

        check.run()

        assertTrue(calls.isEmpty())
        assertEquals(0L, rows.getValue("1").feedUrlCheckedAt)
    }

    private suspend fun fallback(id: String): String? = DirectFeedRecovery.recheckGoneFeedAddress(
        id = id,
        now = now,
        savedFeedUrl = rows.getValue(id).feedUrl,
        lastLookupAt = { rows.getValue(id).feedUrlCheckedAt },
        markLookup = { at -> rows[id] = rows.getValue(id).copy(feedUrlCheckedAt = at) },
        lookupFeedUrl = { NEW },
    )

    private fun add(row: PodcastEntity) {
        rows[row.podcastId] = row
    }

    private fun show(id: String) = PodcastEntity(
        podcastId = id,
        title = "Show $id",
        author = "Author",
        imageUrl = "",
        description = null,
        isSubscribed = true,
        feedUrl = OLD,
    )

    private fun address(
        id: Long,
        url: String? = NEW,
        source: String = "registry:apple",
        repair: Boolean = true,
        via: String = "direct",
    ) = V2ShowAddress(id = id, fetchUrl = url, fetchVia = via, feedSource = source, epNs = id, flags = V2ShowFlags(addressRepair = repair))

    private companion object {
        const val DAY = FeedAddressDailyCheck.CHECK_INTERVAL_MS
        const val OLD = "https://old.example/feed.xml"
        const val NEW = "https://new.example/feed.xml"
        const val MOVED = "https://moved.example/feed.xml"
    }
}
