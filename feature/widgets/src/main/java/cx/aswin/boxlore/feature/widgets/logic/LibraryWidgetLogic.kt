package cx.aswin.boxlore.feature.widgets.logic

import cx.aswin.boxlore.feature.widgets.WidgetEpisodeRow
import cx.aswin.boxlore.feature.widgets.WidgetShowRow

/** Pure helpers for library list widgets (cap / empty / artwork merge). */
object LibraryWidgetLogic {
    /** Soft ceiling for scrollable ListView widgets (not a visible-page limit). */
    const val MAX_ROWS = 50

    fun truncateShows(rows: List<WidgetShowRow>): List<WidgetShowRow> = rows.take(MAX_ROWS)

    fun truncateEpisodes(rows: List<WidgetEpisodeRow>): List<WidgetEpisodeRow> = rows.take(MAX_ROWS)

    fun mergeArtworkPaths(
        previous: List<WidgetShowRow>,
        next: List<WidgetShowRow>,
    ): List<WidgetShowRow> {
        val previousById = previous.associateBy { it.podcastId }
        return next.map { row ->
            val path =
                row.artworkCachePath
                    ?: previousById[row.podcastId]
                        ?.takeIf { prev ->
                            prev.artworkUrl != null && prev.artworkUrl == row.artworkUrl
                        }?.artworkCachePath
            row.copy(artworkCachePath = path)
        }
    }

    fun mergeEpisodeArtworkPaths(
        previous: List<WidgetEpisodeRow>,
        next: List<WidgetEpisodeRow>,
    ): List<WidgetEpisodeRow> {
        val previousById = previous.associateBy { it.episodeId }
        return next.map { row ->
            val path =
                row.artworkCachePath
                    ?: previousById[row.episodeId]
                        ?.takeIf { prev ->
                            prev.artworkUrl != null && prev.artworkUrl == row.artworkUrl
                        }?.artworkCachePath
            row.copy(artworkCachePath = path)
        }
    }
}
