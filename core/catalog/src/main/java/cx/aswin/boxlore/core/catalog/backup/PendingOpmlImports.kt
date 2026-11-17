package cx.aswin.boxlore.core.catalog.backup

import android.annotation.SuppressLint
import android.content.SharedPreferences

/**
 * OPML outlines an import hasn't settled yet. An import saves every outline before its first
 * request and clears each one once it is followed (or turned out not to be a show), so outlines the
 * API couldn't answer for yet, and those an import stopped before (the app closed), are finished
 * later by the foreground catch-up ([LibraryBackupManager.finishPendingOpmlImports]). Keyed by the
 * outline's feed address. Each catch-up that tries an outline without an answer counts a miss
 * ([missed]): the next catch-up waits longer before trying it again, and one the API never answers
 * for is given up on in the end ([OpmlImportRun]).
 */
interface PendingOpmlImports {
    fun all(): List<OpmlFeed>

    /** Saves [feeds], keeping the misses of any already saved; false when they couldn't be saved. */
    fun add(feeds: List<OpmlFeed>): Boolean

    /** Forgets [feed] and its misses. */
    fun remove(feed: OpmlFeed)

    /** Counts a catch-up that tried [feed] at [nowMs] without an answer; returns its misses so far. */
    fun missed(feed: OpmlFeed, nowMs: Long): PendingOpmlMisses

    /** [feed]'s misses so far; null when no catch-up has missed it yet. */
    fun misses(feed: OpmlFeed): PendingOpmlMisses?

    /** Forgets every outline and its misses: an account switch, so one account's import never follows shows into another. */
    fun clear()
}

/** How many catch-ups tried a pending outline without an answer, when the first of them was, and when the latest was. */
data class PendingOpmlMisses(val count: Int, val firstAtMs: Long, val lastAtMs: Long = firstAtMs)

/** [PendingOpmlImports] for one process only (tests, and callers built without the app's wiring). */
class MemoryPendingOpmlImports : PendingOpmlImports {
    private val feeds = LinkedHashMap<String, OpmlFeed>()
    private val misses = HashMap<String, PendingOpmlMisses>()

    @Synchronized
    override fun all(): List<OpmlFeed> = feeds.values.toList()

    @Synchronized
    override fun add(feeds: List<OpmlFeed>): Boolean {
        feeds.forEach { this.feeds[it.xmlUrl] = it }
        return true
    }

    @Synchronized
    override fun remove(feed: OpmlFeed) {
        feeds -= feed.xmlUrl
        misses -= feed.xmlUrl
    }

    @Synchronized
    override fun missed(feed: OpmlFeed, nowMs: Long): PendingOpmlMisses {
        val before = misses[feed.xmlUrl]
        return PendingOpmlMisses((before?.count ?: 0) + 1, before?.firstAtMs ?: nowMs, nowMs).also { misses[feed.xmlUrl] = it }
    }

    @Synchronized
    override fun misses(feed: OpmlFeed): PendingOpmlMisses? = misses[feed.xmlUrl]

    @Synchronized
    override fun clear() {
        feeds.clear()
        misses.clear()
    }
}

/**
 * [PendingOpmlImports] in SharedPreferences file [FILE_NAME]: one key per outline's feed address,
 * its title as the value; an outline's misses sit beside it under [MISSES_PREFIX],
 * [FIRST_MISS_PREFIX] and [LAST_MISS_PREFIX] plus the address, as numbers. A restored copy only
 * imports those outlines again, which is harmless. Stable storage name and key prefixes: never rename.
 */
class SharedPreferencesPendingOpmlImports(private val prefs: SharedPreferences,) : PendingOpmlImports {
    override fun all(): List<OpmlFeed> = prefs.all.mapNotNull { (url, title) ->
        if (MISS_PREFIXES.any(url::startsWith)) return@mapNotNull null
        (title as? String)?.let { OpmlFeed(it, url) }
    }

    @Synchronized
    @SuppressLint("ApplySharedPref")
    override fun add(feeds: List<OpmlFeed>): Boolean {
        if (feeds.isEmpty()) return true
        val edit = prefs.edit()
        feeds.forEach { edit.putString(it.xmlUrl, it.title) }
        // Written to disk at once: the import's requests follow, and the app may close during them.
        return edit.commit()
    }

    @Synchronized
    override fun remove(feed: OpmlFeed) {
        prefs
            .edit()
            .remove(feed.xmlUrl)
            .remove(MISSES_PREFIX + feed.xmlUrl)
            .remove(FIRST_MISS_PREFIX + feed.xmlUrl)
            .remove(LAST_MISS_PREFIX + feed.xmlUrl)
            .apply()
    }

    @Synchronized
    override fun missed(feed: OpmlFeed, nowMs: Long): PendingOpmlMisses {
        val count = prefs.getInt(MISSES_PREFIX + feed.xmlUrl, 0) + 1
        val firstAt = if (prefs.contains(FIRST_MISS_PREFIX + feed.xmlUrl)) prefs.getLong(FIRST_MISS_PREFIX + feed.xmlUrl, nowMs) else nowMs
        prefs
            .edit()
            .putInt(MISSES_PREFIX + feed.xmlUrl, count)
            .putLong(FIRST_MISS_PREFIX + feed.xmlUrl, firstAt)
            .putLong(LAST_MISS_PREFIX + feed.xmlUrl, nowMs)
            .apply()
        return PendingOpmlMisses(count, firstAt, nowMs)
    }

    @Synchronized
    override fun misses(feed: OpmlFeed): PendingOpmlMisses? {
        if (!prefs.contains(MISSES_PREFIX + feed.xmlUrl)) return null
        val firstAt = prefs.getLong(FIRST_MISS_PREFIX + feed.xmlUrl, 0L)
        return PendingOpmlMisses(prefs.getInt(MISSES_PREFIX + feed.xmlUrl, 0), firstAt, prefs.getLong(LAST_MISS_PREFIX + feed.xmlUrl, firstAt))
    }

    @Synchronized
    @SuppressLint("ApplySharedPref")
    override fun clear() {
        // Written at once: the new account's first pull and catch-up follow right away.
        prefs.edit().clear().commit()
    }

    companion object {
        const val FILE_NAME = "boxlore_pending_opml_imports"

        /** Key prefix for an outline's miss count (an Int). Stable: never rename. */
        const val MISSES_PREFIX = "#misses:"

        /** Key prefix for when an outline's first miss was (a Long, epoch ms). Stable: never rename. */
        const val FIRST_MISS_PREFIX = "#first_miss:"

        /** Key prefix for when an outline's latest miss was (a Long, epoch ms). Stable: never rename. */
        const val LAST_MISS_PREFIX = "#last_miss:"

        private val MISS_PREFIXES = listOf(MISSES_PREFIX, FIRST_MISS_PREFIX, LAST_MISS_PREFIX)
    }
}
