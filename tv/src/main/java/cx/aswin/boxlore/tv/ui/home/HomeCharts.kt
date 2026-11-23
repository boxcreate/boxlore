package cx.aswin.boxlore.tv.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import cx.aswin.boxlore.tv.R
import cx.aswin.boxlore.tv.data.TvLibrary
import cx.aswin.boxlore.tv.ui.TvNavigator
import cx.aswin.boxlore.tv.ui.search.ShowRow

/** Genre charts on Home, in the phone's genre order, under the names the API's charts use. */
internal enum class ChartGenre(val apiName: String, @StringRes val label: Int) {
    News("news", R.string.tv_genre_news),
    Technology("technology", R.string.tv_genre_technology),
    Comedy("comedy", R.string.tv_genre_comedy),
    TrueCrime("true crime", R.string.tv_genre_true_crime),
    History("history", R.string.tv_genre_history),
    Business("business", R.string.tv_genre_business),
    Sports("sports", R.string.tv_genre_sports),
    Science("science", R.string.tv_genre_science),
}

/**
 * The charts at the end of Home: top shows in the TV's country, then one row per genre. A row
 * loads when it scrolls near the screen, and one that could not load takes no room.
 */
internal fun LazyListScope.chartItems(library: TvLibrary, go: TvNavigator) {
    item(key = "chart-top") {
        val top by library.trending.collectAsState()
        if (top.isNotEmpty()) ShowRow(stringResource(R.string.tv_top_shows), top) { go.show(it.id) }
    }
    items(ChartGenre.entries, key = { "chart-${it.name}" }) { genre ->
        val shows by remember(genre) { library.chart(genre.apiName) }.collectAsState(initial = emptyList())
        if (shows.isNotEmpty()) ShowRow(stringResource(R.string.tv_top_in, stringResource(genre.label)), shows) { go.show(it.id) }
    }
}
