package cx.aswin.boxlore.feature.widgets

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/** One subscribed show row for the Subscriptions list widget. */
@Serializable
data class WidgetShowRow(
    val podcastId: String,
    val title: String,
    val subtitle: String = "",
    val artworkUrl: String? = null,
    val artworkCachePath: String? = null,
    val deepLinkUri: String,
    val isNew: Boolean = false,
)

/** One latest-episode row for the New Episodes list widget. */
@Serializable
data class WidgetEpisodeRow(
    val episodeId: String,
    val episodeTitle: String,
    val podcastId: String,
    val podcastTitle: String,
    val artworkUrl: String? = null,
    val artworkCachePath: String? = null,
    val deepLinkUri: String,
)

/** Combined library-widget snapshot persisted for cold updates. */
@Serializable
data class LibraryWidgetSnapshot(
    val subscriptions: List<WidgetShowRow> = emptyList(),
    val newEpisodes: List<WidgetEpisodeRow> = emptyList(),
    val updatedAtMs: Long = 0L,
)

enum class LibraryWidgetKind {
    SUBSCRIPTIONS,
    NEW_EPISODES,
}

/**
 * Narrow library port wired from `:app`.
 * Lists are already sorted/filtered to match Library → Subscriptions; deep-link URIs are absolute.
 */
interface WidgetLibrarySource {
    val subscriptions: Flow<List<WidgetShowRow>>

    val newEpisodes: Flow<List<WidgetEpisodeRow>>
}

interface LibraryWidgetDependencies {
    val context: android.content.Context
    val scope: kotlinx.coroutines.CoroutineScope
    val library: WidgetLibrarySource
}

object LibraryWidgetDependenciesHolder {
    @Volatile
    var instance: LibraryWidgetDependencies? = null
        internal set

    fun require(): LibraryWidgetDependencies = instance
        ?: error(
            "LibraryWidgetDependencies not installed. " +
                "Call configureLibraryWidgets from Application after AppContainer is ready.",
        )
}

/** Installs dependencies and starts the library widget coordinator. Call from `:app` only. */
fun configureLibraryWidgets(dependencies: LibraryWidgetDependencies) {
    LibraryWidgetDependenciesHolder.instance = dependencies
    WidgetThemeSync.install(dependencies.context)
    LibraryWidgetCoordinator.start(dependencies)
}
