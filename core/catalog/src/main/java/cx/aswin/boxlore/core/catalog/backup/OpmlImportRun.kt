package cx.aswin.boxlore.core.catalog.backup

import cx.aswin.boxlore.core.model.Podcast

/** What importing one OPML outline did. */
sealed interface OpmlFeedImport {
    data class Imported(val podcast: Podcast) : OpmlFeedImport

    /** The catalog didn't answer (a server error, no connection): the outline stays pending for a later catch-up. */
    data object Pending : OpmlFeedImport

    /** Its route had no room or answered 429: the outline stays pending and this run asks again once there's room. */
    data object Limited : OpmlFeedImport

    /** Settled without a follow: not a show boxlore could add (logged). */
    data object Skipped : OpmlFeedImport
}

/**
 * The outcome of an OPML import: the shows it followed, the outlines still pending
 * ([PendingOpmlImports]), and those a catch-up gave up on after the API never answered for them.
 */
data class OpmlImportReport(
    val imported: List<Podcast>,
    val pending: List<OpmlFeed>,
    val gaveUp: List<OpmlFeed> = emptyList(),
    /** The outlines couldn't be saved first, so nothing was asked: none would survive the app closing. */
    val saveFailed: Boolean = false,
)

/**
 * The OPML import loop, shared by the import screen and the foreground catch-up. Every outline is
 * saved to [pending] before the first request, so none is lost if the import stops; each is cleared
 * once it settles. Outlines the rate limit held back ([OpmlFeedImport.Limited]) are tried again after
 * the rest (the route budget waits out any `Retry-After` first), round after round while each round
 * gets further with at least one; those the API answered with an error stay pending for a later
 * catch-up without a second try in this run. Each try reports progress.
 *
 * An import that followed nothing forgets its outlines when [forgetWhenNothingImported] is set: its
 * screen says nothing could be added and offers to try again. A catch-up ([countMisses]) counts a
 * miss for each outline that got an error, skips an outline until its wait since its latest miss
 * ([retryWaitMs]) has passed, and gives one up once it has missed [GIVE_UP_AFTER_MISSES] times over
 * at least [GIVE_UP_AFTER_MS], so a phone that was offline for a few passes loses nothing and an
 * outline the API can't answer for isn't asked on every pass. [shouldStop] (asked before each try)
 * ends the run early, counting no misses: the outlines not settled stay pending as they were.
 */
internal object OpmlImportRun {
    /** Catch-ups without an answer before an outline is given up on (with [GIVE_UP_AFTER_MS]). */
    const val GIVE_UP_AFTER_MISSES = 5

    /** How long since its first miss an outline stays pending at least: a week. */
    const val GIVE_UP_AFTER_MS = 7L * 24 * 60 * 60 * 1000

    private const val HOUR_MS = 60L * 60 * 1000

    /** How long a catch-up waits after an outline's 1st, 2nd and 3rd-or-later miss before trying it again. */
    private val RETRY_WAITS_MS = longArrayOf(HOUR_MS, 6 * HOUR_MS, 24 * HOUR_MS)

    @Suppress("LongParameterList", "kotlin:S107")
    suspend fun run(
        feeds: List<OpmlFeed>,
        pending: PendingOpmlImports,
        forgetWhenNothingImported: Boolean,
        importOne: suspend (OpmlFeed) -> OpmlFeedImport,
        onProgress: suspend (index: Int, feed: OpmlFeed, imported: List<Podcast>) -> Unit = { _, _, _ -> },
        countMisses: Boolean = false,
        shouldStop: () -> Boolean = { false },
        nowMs: () -> Long = System::currentTimeMillis,
    ): OpmlImportReport {
        if (!pending.add(feeds)) return OpmlImportReport(imported = emptyList(), pending = emptyList(), saveFailed = true)
        val startedAt = nowMs()
        val (due, waiting) = if (countMisses) feeds.partition { isDue(pending.misses(it), startedAt) } else feeds to emptyList()
        val imported = mutableListOf<Podcast>()
        val tries = Tries(pending, imported, importOne, onProgress, shouldStop)
        var limited = tries.round(due) { index -> index }
        // Retry rounds show the outline being tried again at the end of the bar.
        while (limited.isNotEmpty() && !tries.stopped) {
            val next = tries.round(limited) { due.lastIndex }
            val gotFurther = next.size < limited.size
            limited = next
            if (!gotFurther) break
        }
        val left = limited + tries.failed + tries.untried + waiting
        if (tries.stopped) return OpmlImportReport(imported.toList(), left)
        if (imported.isEmpty() && forgetWhenNothingImported) {
            // Outlines the rate limit held back, or never reached, stay for the catch-up; the rest are forgotten.
            val kept = (limited + tries.untried + waiting).toSet()
            feeds.filterNot { it in kept }.forEach(pending::remove)
            return OpmlImportReport(imported = emptyList(), pending = kept.toList())
        }
        val now = nowMs()
        val gaveUp = if (countMisses) tries.failed.filter { feed -> givesUp(pending.missed(feed, now), now) } else emptyList()
        gaveUp.forEach(pending::remove)
        return OpmlImportReport(imported = imported.toList(), pending = left - gaveUp.toSet(), gaveUp = gaveUp)
    }

    fun givesUp(misses: PendingOpmlMisses, nowMs: Long): Boolean = misses.count >= GIVE_UP_AFTER_MISSES && nowMs - misses.firstAtMs >= GIVE_UP_AFTER_MS

    /** How long a catch-up waits after an outline's [misses]th miss before trying it again. */
    fun retryWaitMs(misses: Int): Long = RETRY_WAITS_MS[(misses - 1).coerceIn(0, RETRY_WAITS_MS.lastIndex)]

    /** Whether a catch-up at [nowMs] tries an outline with [misses]: never missed, its wait passed, or the clock went back. */
    fun isDue(misses: PendingOpmlMisses?, nowMs: Long): Boolean {
        if (misses == null || nowMs < misses.lastAtMs) return true
        return nowMs - misses.lastAtMs >= retryWaitMs(misses.count)
    }

    /** Passes over outlines; remembers whether [shouldStop] ended them, which outlines got an error and which were never tried. */
    private class Tries(
        private val pending: PendingOpmlImports,
        private val imported: MutableList<Podcast>,
        private val importOne: suspend (OpmlFeed) -> OpmlFeedImport,
        private val onProgress: suspend (index: Int, feed: OpmlFeed, imported: List<Podcast>) -> Unit,
        private val shouldStop: () -> Boolean,
    ) {
        var stopped = false
            private set
        var untried: List<OpmlFeed> = emptyList()
            private set

        /** Outlines the API answered with an error (or not at all): pending, not tried again in this run. */
        val failed = mutableListOf<OpmlFeed>()

        /** Tries each of [feeds]; returns those the rate limit held back. */
        suspend fun round(feeds: List<OpmlFeed>, progressIndex: (Int) -> Int): List<OpmlFeed> {
            val limited = mutableListOf<OpmlFeed>()
            feeds.forEachIndexed { index, feed ->
                // An account switch clears the store: stop, so nothing of the old account is followed.
                if (shouldStop() || !pending.contains(feed)) {
                    stopped = true
                    untried = feeds.drop(index)
                    return limited
                }
                onProgress(progressIndex(index), feed, imported.toList())
                when (val result = importOne(feed)) {
                    OpmlFeedImport.Limited -> limited += feed
                    OpmlFeedImport.Pending -> failed += feed
                    else -> settle(feed, result)
                }
            }
            return limited
        }

        /** Clears [feed] from [pending] once [result] settled it. */
        private fun settle(feed: OpmlFeed, result: OpmlFeedImport) {
            if (result is OpmlFeedImport.Imported && imported.none { it.id == result.podcast.id }) imported += result.podcast
            pending.remove(feed)
        }
    }
}
