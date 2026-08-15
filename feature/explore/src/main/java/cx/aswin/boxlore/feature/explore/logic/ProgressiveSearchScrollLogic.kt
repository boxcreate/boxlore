package cx.aswin.boxlore.feature.explore.logic

/**
 * Progressive Find-a-show paints local substring hits first, then prepends
 * Meili catalog matches and may insert a Matches header. The staggered grid
 * keeps the old index, which leaves the new top results off-screen.
 *
 * Ask-anything (semantic) waits for one complete result set behind a loader,
 * so it does not need this pin.
 */
internal object ProgressiveSearchScrollLogic {
    data class Snapshot(
        val query: String,
        val topResultId: String?,
        val hasAlsoFoundSection: Boolean,
    )

    fun shouldPinToTop(
        previous: Snapshot?,
        current: Snapshot,
    ): Boolean {
        if (current.query.isBlank()) return false
        if (previous == null) return current.topResultId != null || current.hasAlsoFoundSection
        if (previous.query != current.query) return true
        if (previous.topResultId != current.topResultId) return true
        if (!previous.hasAlsoFoundSection && current.hasAlsoFoundSection) return true
        return false
    }
}
