package cx.aswin.boxlore.feature.explore.logic

/** An Explore read the API can refuse until this phone passes the quick check. */
enum class ExploreCheckedRead {
    /** Ask anything: the topic search. */
    TOPIC_SEARCH,

    /** The open mood collection's shows. */
    COLLECTION,

    /** For you's picks. */
    FOR_YOU,
}

/**
 * The quick check on Explore. A read the API refused until this phone passes the check
 * ([needed]) shows a notice with a button that starts it, not "nothing found" or "check your
 * connection". [checking] while the check the listener started runs; [missed] when it didn't pass
 * (skipped, failed or couldn't run), so the notice says so. One check that passes is enough for
 * every read, so all of them are read again.
 */
data class ExploreQuickCheck(
    val needed: Set<ExploreCheckedRead> = emptySet(),
    val checking: Boolean = false,
    val missed: Boolean = false,
) {
    fun needs(read: ExploreCheckedRead): Boolean = read in needed

    /** [read] answered: refused for the check when [needsCheck], else read (or failed for another reason). */
    fun answered(read: ExploreCheckedRead, needsCheck: Boolean): ExploreQuickCheck = copy(needed = if (needsCheck) needed + read else needed - read)

    /** The listener started the check. */
    fun started(): ExploreQuickCheck = copy(checking = true, missed = false)

    /** The check ended; when it [passed], every read it held back is read again, so none needs it any more. */
    fun finished(passed: Boolean): ExploreQuickCheck = copy(needed = if (passed) emptySet() else needed, checking = false, missed = !passed)
}
