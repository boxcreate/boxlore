package cx.aswin.boxlore.feature.widgets.logic

import cx.aswin.boxlore.core.model.artworkUrls
import cx.aswin.boxlore.feature.widgets.LibraryWidgetSnapshot
import cx.aswin.boxlore.feature.widgets.WidgetEpisodeRow
import cx.aswin.boxlore.feature.widgets.WidgetShowRow

/**
 * Fetches the covers a library snapshot is missing and hands back the snapshot as they arrive, a few
 * at a time. Waiting for the whole library first left a widget without covers whenever the app was
 * closed before the last one came in.
 */
internal object LibraryArtworkBatches {
    const val PUBLISH_EVERY = 4

    /**
     * Loads every missing cover in [snapshot] through [load], shows first, then episodes. [publish]
     * gets the snapshot with the covers so far after every [publishEvery] loads that found one, and
     * once more for the rest. A cover that cannot be loaded is skipped. Cancelling the caller stops
     * the loading, and nothing is published after that.
     */
    suspend fun load(
        snapshot: LibraryWidgetSnapshot,
        publishEvery: Int = PUBLISH_EVERY,
        load: suspend (url: String?, fallbackUrls: List<String>) -> String?,
        publish: (LibraryWidgetSnapshot) -> Unit,
    ) {
        var current = snapshot
        var unpublished = 0

        fun loaded(next: LibraryWidgetSnapshot) {
            current = next
            unpublished++
            if (unpublished >= publishEvery) {
                publish(current)
                unpublished = 0
            }
        }

        snapshot.subscriptions.forEachIndexed { index, row ->
            if (!row.needsArtwork()) return@forEachIndexed
            val path = load(row.artworkUrl, row.fallbackArtworkUrls) ?: return@forEachIndexed
            loaded(current.copy(subscriptions = current.subscriptions.toMutableList().also { it[index] = row.copy(artworkCachePath = path) }))
        }
        snapshot.newEpisodes.forEachIndexed { index, row ->
            if (!row.needsArtwork()) return@forEachIndexed
            val path = load(row.artworkUrl, row.fallbackArtworkUrls) ?: return@forEachIndexed
            loaded(current.copy(newEpisodes = current.newEpisodes.toMutableList().also { it[index] = row.copy(artworkCachePath = path) }))
        }
        if (unpublished > 0) publish(current)
    }

    private fun WidgetShowRow.needsArtwork(): Boolean = artworkCachePath.isNullOrBlank() && artworkUrls(artworkUrl, *fallbackArtworkUrls.toTypedArray()).isNotEmpty()

    private fun WidgetEpisodeRow.needsArtwork(): Boolean = artworkCachePath.isNullOrBlank() && artworkUrls(artworkUrl, *fallbackArtworkUrls.toTypedArray()).isNotEmpty()
}
