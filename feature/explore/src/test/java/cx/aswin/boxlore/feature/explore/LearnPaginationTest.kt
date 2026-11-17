package cx.aswin.boxlore.feature.explore

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LearnPaginationTest {
    @Test
    fun firstPageWithUnseenCardsReturnsImmediately() = runTest {
        val requestedPages = mutableListOf<Int>()

        val result =
            findFirstUnseenCuriosityDeck(emptySet()) { page ->
                requestedPages += page
                response(card(1), card(2))
            }

        assertTrue(result is InitialCuriosityDeckResult.Found)
        result as InitialCuriosityDeckResult.Found
        assertEquals(1, result.page)
        assertEquals(listOf("1", "2"), result.unseenItems.map { it.episodeId })
        assertEquals(listOf(1), requestedPages)
    }

    @Test
    fun fullyDismissedPageIsSkipped() = runTest {
        val requestedPages = mutableListOf<Int>()

        val result =
            findFirstUnseenCuriosityDeck(setOf("1", "2")) { page ->
                requestedPages += page
                when (page) {
                    1 -> response(card(1), card(2))
                    else -> response(card(3), card(4))
                }
            }

        assertTrue(result is InitialCuriosityDeckResult.Found)
        result as InitialCuriosityDeckResult.Found
        assertEquals(2, result.page)
        assertEquals(listOf("3", "4"), result.unseenItems.map { it.episodeId })
        assertEquals(listOf(1, 2), requestedPages)
    }

    @Test
    fun mixedPageReturnsOnlyUnseenCards() = runTest {
        val result =
            findFirstUnseenCuriosityDeck(setOf("2")) {
                response(card(1), card(2), card(3))
            }

        assertTrue(result is InitialCuriosityDeckResult.Found)
        result as InitialCuriosityDeckResult.Found
        assertEquals(listOf("1", "3"), result.unseenItems.map { it.episodeId })
    }

    @Test
    fun emptyServerPageMarksDeckExhausted() = runTest {
        val result =
            findFirstUnseenCuriosityDeck(emptySet()) {
                response()
            }

        assertEquals(
            InitialCuriosityDeckResult.Exhausted(lastPage = 1),
            result,
        )
    }

    @Test
    fun unseenCardsPastFiveSeenPagesAreFound() = runTest {
        val dismissedIds = (1L..7L).map { it.toString() }.toSet()

        val result =
            findFirstUnseenCuriosityDeck(dismissedIds) { page ->
                response(card(page.toLong()))
            }

        assertEquals(InitialCuriosityDeckResult.Found(page = 8, unseenItems = listOf(card(8))), result)
    }

    @Test
    fun aScanThatReachesItsPageLimitIsCappedNotCaughtUp() = runTest {
        val requestedPages = mutableListOf<Int>()
        val dismissedIds = (1L..MAX_CURIOSITY_SCAN_PAGES.toLong()).map { it.toString() }.toSet()

        val result =
            findFirstUnseenCuriosityDeck(dismissedIds) { page ->
                requestedPages += page
                response(card(page.toLong()))
            }

        assertEquals(InitialCuriosityDeckResult.Capped(lastPage = MAX_CURIOSITY_SCAN_PAGES), result)
        assertEquals((1..MAX_CURIOSITY_SCAN_PAGES).toList(), requestedPages)
    }

    @Test
    fun checkForNewCardsCarriesOnPastTheLimitAndFindsLaterUnseenCards() = runTest {
        // Every card on the first 13 pages was seen; page 14 has a new one.
        val lore = FakeLore(lastPage = 30)
        lore.dismissed += (1L..13L).map { it.toString() }

        assertEquals(InitialCuriosityDeckResult.Capped(lastPage = MAX_CURIOSITY_SCAN_PAGES), lore.check())
        assertEquals(InitialCuriosityDeckResult.Found(page = 14, unseenItems = listOf(card(14))), lore.check())
        assertEquals(
            (1..12).toList() + listOf(1, 13, 14),
            lore.requestedPages,
            "the second check reads page 1, then carries on where the first stopped",
        )
        assertEquals(1, lore.resume.startPage(lore.dismissed), "after a deck is found further down, the next check starts from the top")
    }

    @Test
    fun aResumedCheckFindsANewCardOnPageOne() = runTest {
        // Pages 1 to 15 were all seen and the list ends at page 15.
        val lore = FakeLore(lastPage = 15)
        lore.dismissed += (1L..15L).map { it.toString() }
        assertEquals(InitialCuriosityDeckResult.Capped(lastPage = 12), lore.check())

        // A new card arrives at the top of the list.
        lore.pages[1] = listOf(card(100))

        assertEquals(InitialCuriosityDeckResult.Found(page = 1, unseenItems = listOf(card(100))), lore.check())
        assertEquals(13, lore.resume.startPage(lore.dismissed), "a new card on page 1 keeps the place further down")

        lore.dismissed += "100"
        lore.requestedPages.clear()
        assertEquals(InitialCuriosityDeckResult.Exhausted(lastPage = 16), lore.check())
        assertEquals(listOf(1, 13, 14, 15, 16), lore.requestedPages)
    }

    @Test
    fun aCachedLoadNeverSkipsTheFreshPageOne() = runTest {
        val lore = FakeLore(lastPage = 15)
        lore.dismissed += (1L..15L).map { it.toString() }

        assertEquals(InitialCuriosityDeckResult.Capped(lastPage = 12), lore.load())
        assertEquals(1, lore.resume.startPage(lore.dismissed), "cached pages never move the place on")

        lore.pages[1] = listOf(card(100))
        lore.requests.clear()
        assertEquals(InitialCuriosityDeckResult.Found(page = 1, unseenItems = listOf(card(100))), lore.check())
        assertEquals(listOf(1 to true), lore.requests, "the check asks the server for page 1 past its caches")
    }

    @Test
    fun everyCheckReadsAFreshPageOneBeforeItCarriesOn() = runTest {
        val lore = FakeLore(lastPage = 40)
        lore.dismissed += (1L..40L).map { it.toString() }

        assertEquals(InitialCuriosityDeckResult.Capped(lastPage = 12), lore.check())
        lore.requests.clear()
        assertEquals(InitialCuriosityDeckResult.Capped(lastPage = 23), lore.check())
        assertEquals((listOf(1) + (13..23)).map { it to true }, lore.requests)
        lore.requests.clear()
        assertEquals(InitialCuriosityDeckResult.Capped(lastPage = 34), lore.check())
        assertEquals((listOf(1) + (24..34)).map { it to true }, lore.requests)
    }

    @Test
    fun aLoadAndACheckRightAfterStayUnderTheBurstLimit() = runTest {
        // Every card seen on a long list: the most pages a load and a check can ask for.
        val lore = FakeLore(lastPage = 100)
        lore.dismissed += (1L..100L).map { it.toString() }

        lore.load()
        lore.check()

        assertEquals(2 * MAX_CURIOSITY_SCAN_PAGES, lore.requests.size)
        assertTrue(lore.requests.size + DECK_NEXT_PAGES < BURST_LIMIT_PER_MINUTE, "with the next pages of a deck, still under the limit")
    }

    @Test
    fun aCheckThatFailsPartwayCarriesOnFromTheFailedPage() = runTest {
        val lore = FakeLore(lastPage = 30)
        lore.dismissed += (1L..30L).map { it.toString() }
        lore.failingPages += 6

        assertEquals(InitialCuriosityDeckResult.Failed(page = 6), lore.check())
        assertEquals(6, lore.resume.startPage(lore.dismissed), "pages 1 to 5 were all seen")

        lore.failingPages.clear()
        lore.requests.clear()
        lore.check()
        assertEquals((listOf(1) + (6..16)).map { it to true }, lore.requests)
    }

    @Test
    fun aCheckStartsFromTheTopAfterTheListEndsOrWhenSeenCardsAreBack() {
        val dismissed = setOf("1", "2")
        val resume = CuriosityScanResume()
        assertEquals(1, resume.startPage(dismissed))

        resume.after(InitialCuriosityDeckResult.Capped(lastPage = 12), dismissed, freshPages = true)
        assertEquals(13, resume.startPage(dismissed))
        assertEquals(13, resume.startPage(dismissed + "3"), "more seen cards: carry on")
        assertEquals(1, resume.startPage(setOf("1")), "a card is back (history cleared or restored): the skipped pages may hold it")

        resume.after(InitialCuriosityDeckResult.Failed(page = 1), dismissed, freshPages = true)
        assertEquals(13, resume.startPage(dismissed), "a check that fails on page 1 keeps its place")

        resume.after(InitialCuriosityDeckResult.Capped(lastPage = 30), dismissed, freshPages = false)
        assertEquals(13, resume.startPage(dismissed), "cached pages never move the place on")

        resume.after(InitialCuriosityDeckResult.Exhausted(lastPage = 24), dismissed, freshPages = true)
        assertEquals(1, resume.startPage(dismissed), "the list ended: new cards arrive at the top")
    }

    @Test
    fun aFailedCheckKeepsThePagingBehindTheDeckOnScreen() {
        val paging = CuriosityDeckPaging()
        paging.onScan(InitialCuriosityDeckResult.Found(page = 15, unseenItems = listOf(card(15))))

        paging.onScan(InitialCuriosityDeckResult.Failed(page = 1))

        assertEquals(15, paging.currentPage, "the deck goes on from page 16, not page 2")
        assertEquals(false, paging.isEndOfContent)

        paging.onListEnded()
        paging.onScan(InitialCuriosityDeckResult.Failed(page = 1))
        assertEquals(true, paging.isEndOfContent, "the list still ended, so the last card dismissed shows caught up")

        paging.onScan(InitialCuriosityDeckResult.Found(page = 1, unseenItems = listOf(card(1))))
        assertEquals(1, paging.currentPage)
        assertEquals(false, paging.isEndOfContent, "a new deck pages on again")
    }

    @Test
    fun failedLaterPageReportsItsPage() = runTest {
        val result =
            findFirstUnseenCuriosityDeck(setOf("1")) { page ->
                if (page == 1) response(card(1)) else null
            }

        assertEquals(InitialCuriosityDeckResult.Failed(page = 2), result)
    }

    /** A curiosity list of one card per page up to [lastPage], with the scan state a [LearnViewModel] keeps. */
    private inner class FakeLore(lastPage: Int) {
        val pages = (1..lastPage).associateWith { listOf(card(it.toLong())) }.toMutableMap()
        val dismissed = mutableSetOf<String>()
        val failingPages = mutableSetOf<Int>()
        val requests = mutableListOf<Pair<Int, Boolean>>()
        val requestedPages = mutableListOf<Int>()
        val resume = CuriosityScanResume()

        suspend fun load() = scan(freshPages = false)

        suspend fun check() = scan(freshPages = true)

        private suspend fun scan(freshPages: Boolean): InitialCuriosityDeckResult {
            val dismissedIds = dismissed.toSet()
            return findFirstUnseenCuriosityDeck(dismissedIds, startPage = resume.startPage(dismissedIds)) { page ->
                requests += page to freshPages
                requestedPages += page
                if (page in failingPages) null else pages[page].orEmpty()
            }.also { resume.after(it, dismissedIds, freshPages) }
        }
    }

    private companion object {
        /** Pages the deck itself asks for at most when it runs low. */
        const val DECK_NEXT_PAGES = 5
        const val BURST_LIMIT_PER_MINUTE = 30
    }

    private fun response(vararg cards: LearnCuriosityCard): List<LearnCuriosityCard> = cards.toList()

    private fun card(id: Long): LearnCuriosityCard = LearnCuriosityCard(
        episodeId = id.toString(),
        question = "Question $id?",
        explanation = "Explanation $id",
        curiosityScore = 8,
        episodeTitle = "Episode $id",
        podcastTitle = "Podcast $id",
        imageUrl = null,
        feedImage = null,
        podcastId = null,
        audioUrl = "https://example.com/$id.mp3",
        duration = 0,
        description = null,
    )

    @Test
    fun aFailedCheckAfterARestoredCardResumesFromThePageItReached() {
        // Reviewed: the place reset to page 1 when a card was restored, but a failure on page 6 was
        // compared with the old place (13), so the next check read pages 1 to 5 again.
        val resume = CuriosityScanResume()
        resume.after(InitialCuriosityDeckResult.Capped(12), dismissedIds = setOf("a", "b"), freshPages = true)
        val afterRestore = setOf("b")
        assertEquals(1, resume.startPage(afterRestore))

        resume.after(InitialCuriosityDeckResult.Failed(6), afterRestore, freshPages = true)

        assertEquals(6, resume.startPage(afterRestore))
    }
}
