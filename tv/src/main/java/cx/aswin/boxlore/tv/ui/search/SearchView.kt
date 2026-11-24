package cx.aswin.boxlore.tv.ui.search

import cx.aswin.boxlore.tv.model.TvSearchResult

/** What Search shows under its box. */
enum class SearchView {
    /** Nothing typed yet: what is trending. */
    Trending,

    /** The search could not reach boxlore: say so, with Try again. */
    Failed,

    /** It reached boxlore and found nothing. */
    Empty,
    Results,
    ;

    companion object {
        fun of(result: TvSearchResult?): SearchView = when (result) {
            null -> Trending
            is TvSearchResult.NoConnection -> Failed
            is TvSearchResult.Found -> if (result.shows.isEmpty() && result.episodes.isEmpty()) Empty else Results
        }
    }
}
