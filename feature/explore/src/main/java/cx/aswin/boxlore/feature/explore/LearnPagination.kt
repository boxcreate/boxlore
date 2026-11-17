package cx.aswin.boxlore.feature.explore

/**
 * Pages one deck scan asks for at most. A load and a check right after it, plus the next page of a
 * deck, stay under the API's per-route burst limit; a longer run of seen pages carries on in the next
 * check ([CuriosityScanResume]).
 */
internal const val MAX_CURIOSITY_SCAN_PAGES = 12

internal sealed interface InitialCuriosityDeckResult {
    data class Found(
        val page: Int,
        val unseenItems: List<LearnCuriosityCard>
    ) : InitialCuriosityDeckResult

    /** The server's list ended (an empty page) before any unseen card. */
    data class Exhausted(val lastPage: Int) : InitialCuriosityDeckResult

    /** The scan reached its page limit with every card seen; later pages may still hold unseen cards. */
    data class Capped(val lastPage: Int) : InitialCuriosityDeckResult

    data class Failed(val page: Int) : InitialCuriosityDeckResult
}

/**
 * The first page from [startPage] with cards outside [dismissedIds], reading up to [maxAttempts]
 * pages: [InitialCuriosityDeckResult.Exhausted] only when the server's list ends, and
 * [InitialCuriosityDeckResult.Capped] when the limit stops the scan first. A scan that starts past
 * page 1 reads page 1 first, within the same limit, since new cards arrive there.
 */
internal suspend fun findFirstUnseenCuriosityDeck(
    dismissedIds: Set<String>,
    startPage: Int = 1,
    maxAttempts: Int = MAX_CURIOSITY_SCAN_PAGES,
    fetchPage: suspend (Int) -> List<LearnCuriosityCard>?
): InitialCuriosityDeckResult {
    val firstPage = startPage.coerceAtLeast(1)
    var attempts = maxAttempts.coerceAtLeast(1)
    if (firstPage > 1) {
        val top = scanCuriosityPages(dismissedIds, fromPage = 1, attempts = 1, fetchPage = fetchPage)
        if (top !is InitialCuriosityDeckResult.Capped) return top
        attempts--
        if (attempts == 0) return InitialCuriosityDeckResult.Capped(firstPage - 1)
    }
    return scanCuriosityPages(dismissedIds, firstPage, attempts, fetchPage)
}

private suspend fun scanCuriosityPages(
    dismissedIds: Set<String>,
    fromPage: Int,
    attempts: Int,
    fetchPage: suspend (Int) -> List<LearnCuriosityCard>?
): InitialCuriosityDeckResult {
    var page = fromPage
    repeat(attempts) {
        val questionsStack = fetchPage(page) ?: return InitialCuriosityDeckResult.Failed(page)
        if (questionsStack.isEmpty()) {
            return InitialCuriosityDeckResult.Exhausted(page)
        }

        val unseenItems = questionsStack.filterNot {
            it.episodeId in dismissedIds
        }
        if (unseenItems.isNotEmpty()) {
            return InitialCuriosityDeckResult.Found(page, unseenItems)
        }
        page++
    }
    return InitialCuriosityDeckResult.Capped(page - 1)
}

/**
 * Where the next deck scan carries on. Page 1, where new cards arrive, is always read first
 * ([findFirstUnseenCuriosityDeck]); after a scan with fresh pages stopped at its page limit
 * ([InitialCuriosityDeckResult.Capped]) or failed partway, the next scan carries on from the page it
 * reached, so unseen cards further down the list are found. A deck found on page 1 keeps that place;
 * a deck found further down or the end of the list starts the next scan from page 1 again. A scan
 * that may have read cached pages never moves the place on. When cards dismissed at that point are
 * back (Lore history was cleared or a card restored), the next scan starts from page 1 too, since
 * the pages it skips may hold them.
 */
internal class CuriosityScanResume {
    private var nextPage: Int? = null
    private var dismissedAtResume: Set<String> = emptySet()

    /** The page the next scan carries on from, for the listener's current [dismissedIds]. */
    fun startPage(dismissedIds: Set<String>): Int {
        val resumeAt = nextPage ?: return 1
        return if (dismissedIds.containsAll(dismissedAtResume)) resumeAt else 1
    }

    /**
     * Records how a scan with [dismissedIds] ended. [freshPages] is true when the scan asked the
     * server past its caches (Check for new cards).
     */
    fun after(result: InitialCuriosityDeckResult, dismissedIds: Set<String>, freshPages: Boolean) {
        // Where this scan actually started: page 1 when a restored card reset the place.
        val resumeAt = startPage(dismissedIds)
        when (result) {
            is InitialCuriosityDeckResult.Capped -> if (freshPages) resumeFrom(result.lastPage + 1, dismissedIds)
            is InitialCuriosityDeckResult.Failed -> if (freshPages && result.page > resumeAt) resumeFrom(result.page, dismissedIds)
            is InitialCuriosityDeckResult.Found -> if (result.page >= resumeAt) clear()
            is InitialCuriosityDeckResult.Exhausted -> clear()
        }
    }

    private fun resumeFrom(page: Int, dismissedIds: Set<String>) {
        nextPage = page
        dismissedAtResume = dismissedIds
    }

    private fun clear() {
        nextPage = null
        dismissedAtResume = emptySet()
    }
}

/**
 * The page a Lore deck has reached and whether the server's list has ended. Only a scan answer moves
 * it, so a failed check keeps the paging behind the deck still on screen.
 */
internal class CuriosityDeckPaging {
    var currentPage = 1
        private set
    var isEndOfContent = false
        private set

    fun reset() {
        currentPage = 1
        isEndOfContent = false
    }

    fun onScan(result: InitialCuriosityDeckResult) {
        when (result) {
            is InitialCuriosityDeckResult.Found -> {
                currentPage = result.page
                isEndOfContent = false
            }
            is InitialCuriosityDeckResult.Exhausted -> {
                currentPage = result.lastPage
                isEndOfContent = true
            }
            is InitialCuriosityDeckResult.Capped -> {
                currentPage = result.lastPage
                isEndOfContent = false
            }
            is InitialCuriosityDeckResult.Failed -> Unit
        }
    }

    fun onPageLoaded(page: Int) {
        currentPage = page
    }

    fun onListEnded() {
        isEndOfContent = true
    }
}
