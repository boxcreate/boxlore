package cx.aswin.boxlore.feature.home.logic

import cx.aswin.boxlore.core.model.Podcast

internal sealed class FilterSelectionAction {
    data object Clear : FilterSelectionAction()

    data class AutoSelect(
        val podcast: Podcast,
    ) : FilterSelectionAction()

    data object Keep : FilterSelectionAction()
}

internal object HomeFilterSelectionLogic {
    /**
     * Decides Home filter selection as the subscription set changes:
     * - 0 shows → clear to the mix
     * - exactly 1 show → auto-select it
     * - more than 1 show → clear only when invalid or previously auto-selected
     */
    fun decide(
        currentSelectedId: String?,
        subs: List<Podcast>,
        filterSelectionIsAuto: Boolean,
    ): FilterSelectionAction =
        when {
            subs.isEmpty() -> {
                if (currentSelectedId != null) FilterSelectionAction.Clear else FilterSelectionAction.Keep
            }
            subs.size == 1 -> {
                val only = subs.first()
                if (currentSelectedId != only.id) {
                    FilterSelectionAction.AutoSelect(only)
                } else {
                    FilterSelectionAction.Keep
                }
            }
            else -> {
                val subIds = subs.map { it.id }.toSet()
                if (currentSelectedId != null && (currentSelectedId !in subIds || filterSelectionIsAuto)) {
                    FilterSelectionAction.Clear
                } else {
                    FilterSelectionAction.Keep
                }
            }
        }
}
