package cx.aswin.boxlore.feature.home.logic

/**
 * What selecting a show chip on Home refreshes: an `rss:` show's catalog repair (a full read), an
 * `rss:` show's tap read (a listener's check, which keeps the show's wait if it fails before the
 * show is due), or a Podcast Index show's feed read.
 */
internal enum class HomeChipRefresh { NONE, RSS_REPAIR, RSS_TAP_READ, FEED_READ }

/** How Home applies a chip tap. */
internal enum class HomeChipTap {
    /** The tapped show is already selected: only check it again. */
    RECHECK,

    /** The library's only show, while nothing is selected: select it automatically again, and check it. */
    RESTORE_ONLY_SHOW,

    /** Any other tap: an explicit selection (or the Mix). */
    SELECT,
}

internal object HomeChipSelection {
    /**
     * An `rss:` show repairs its catalog when it is stale or due its once-a-session refresh, and
     * otherwise reads its feed only on [tapRead] ([HomeChipFeedReads.claim]); a Podcast Index show
     * reads its feed only on [tapRead]. A [recheck] (a tap on a chip that already looks selected)
     * refreshes nothing without [tapRead], so repeated taps never repeat a read within the 15-minute
     * limit.
     */
    fun refreshFor(
        isRss: Boolean,
        rssCatalogStale: Boolean,
        manualRefreshDue: Boolean,
        tapRead: Boolean,
        recheck: Boolean = false,
    ): HomeChipRefresh =
        when {
            recheck && !tapRead -> HomeChipRefresh.NONE
            isRss && (rssCatalogStale || manualRefreshDue) -> HomeChipRefresh.RSS_REPAIR
            isRss && tapRead -> HomeChipRefresh.RSS_TAP_READ
            tapRead -> HomeChipRefresh.FEED_READ
            else -> HomeChipRefresh.NONE
        }

    /**
     * A tap on the show that is already selected only checks it again. The chips send it for the
     * only show in a library, which is selected automatically and has nothing to toggle to; when
     * leaving Home cleared that selection, the tap restores it as automatic, so following a second
     * show still falls back to the Mix.
     */
    fun tapKind(
        tappedId: String?,
        selectedId: String?,
        onlyShowId: String?,
    ): HomeChipTap =
        when {
            tappedId == null -> HomeChipTap.SELECT
            tappedId == selectedId -> HomeChipTap.RECHECK
            tappedId == onlyShowId -> HomeChipTap.RESTORE_ONLY_SHOW
            else -> HomeChipTap.SELECT
        }
}
