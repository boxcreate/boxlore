package cx.aswin.boxlore.feature.info

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.catalog.PlaylistAddResult
import cx.aswin.boxlore.core.catalog.PlaylistRepository
import cx.aswin.boxlore.core.designsystem.components.AddToPlaylistCopy
import cx.aswin.boxlore.core.designsystem.components.AddToPlaylistSheet
import cx.aswin.boxlore.core.designsystem.components.AddToPlaylistSheetState
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Playlist
import cx.aswin.boxlore.core.model.Podcast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Add to playlist for the show and episode pages. The page's ViewModel owns it, so an add finishes
 * in the ViewModel's scope; [onMessage] gets the confirmation to show ([AddToPlaylistCopy.result]).
 * Sends `playlist_modified` with [source].
 */
class InfoPlaylists internal constructor(private val repository: PlaylistRepository, private val scope: CoroutineScope, private val source: String,) {
    val playlists: Flow<List<Playlist>> get() = repository.playlists

    fun addTo(playlist: Playlist, episodes: List<Episode>, onMessage: (String) -> Unit) {
        if (episodes.isEmpty()) return
        scope.launch {
            val result = repository.addEpisodes(playlist.id, episodes)
            report("add", result, playlist.episodeCount + result.added)
            onMessage(message(playlist.name, result))
        }
    }

    fun create(name: String, episodes: List<Episode>, onMessage: (String) -> Unit) {
        scope.launch {
            val created = repository.createPlaylist(name, episodes)
            AnalyticsHelper.trackPlaylistModified("create", source, itemsCount = created.result.added, playlistSize = created.playlist.episodeCount)
            onMessage(message(created.playlist.name, created.result))
        }
    }

    private fun report(action: String, result: PlaylistAddResult, size: Int) {
        if (result.added > 0) AnalyticsHelper.trackPlaylistModified(action, source, itemsCount = result.added, playlistSize = size)
    }

    internal companion object {
        const val SOURCE_SHOW_PAGE = "show_page"
        const val SOURCE_EPISODE_PAGE = "episode_page"

        fun message(name: String, result: PlaylistAddResult): String =
            AddToPlaylistCopy.result(name, result.added, result.alreadyIn, result.didNotFit)

        /**
         * [episodes] with [show]'s ID, title, artist, genre and artwork where they carry none, so a
         * playlist row keeps the page's show details. An episode naming another show is left as it is.
         */
        fun withShow(episodes: List<Episode>, show: Podcast?): List<Episode> {
            if (show == null || show.id.isBlank()) return episodes
            return episodes.map { episode ->
                if (!episode.podcastId.isNullOrBlank() && episode.podcastId != show.id) return@map episode
                episode.copy(
                    podcastId = show.id,
                    podcastTitle = episode.podcastTitle?.takeIf(String::isNotBlank) ?: show.title.takeIf(String::isNotBlank),
                    podcastArtist = episode.podcastArtist?.takeIf(String::isNotBlank) ?: show.artist.takeIf(String::isNotBlank),
                    podcastGenre = episode.podcastGenre?.takeIf(String::isNotBlank) ?: show.genre.takeIf(String::isNotBlank),
                    podcastImageUrl = episode.podcastImageUrl?.takeIf(String::isNotBlank) ?: show.imageUrl.takeIf(String::isNotBlank),
                )
            }
        }

        /** Null without a repository (tests, previews), which hides Add to playlist. */
        fun create(repository: PlaylistRepository?, scope: CoroutineScope, source: String): InfoPlaylists? =
            repository?.let { InfoPlaylists(it, scope, source) }
    }
}

/** Whether the Add to playlist sheet is open and what it shows; saved, so it survives resizing and recreation. */
@Stable
internal class InfoPlaylistPicker(open: Boolean = false, sheet: AddToPlaylistSheetState = AddToPlaylistSheetState()) {
    var open by mutableStateOf(open)
    val sheet = sheet

    fun close() {
        open = false
        sheet.stopCreating()
    }

    companion object {
        val Saver: Saver<InfoPlaylistPicker, Any> = Saver(
            save = { picker -> listOf(picker.open, picker.sheet.isCreating, picker.sheet.newName.orEmpty()) },
            restore = { saved ->
                val values = saved as List<*>
                InfoPlaylistPicker(
                    open = values.getOrNull(0) == true,
                    sheet = AddToPlaylistSheetState(if (values.getOrNull(1) == true) values.getOrNull(2) as? String ?: "" else null),
                )
            },
        )
    }
}

@Composable
internal fun rememberInfoPlaylistPicker(key: Any? = null): InfoPlaylistPicker =
    rememberSaveable(key, saver = InfoPlaylistPicker.Saver) { InfoPlaylistPicker() }

/**
 * The Add to playlist sheet for [episodes] while [picker] is open. Waits while [episodes] is empty
 * (a recreated show page still looking its selection up). [show] fills in show details the
 * episodes lack ([InfoPlaylists.withShow]). [onAdded] runs once the listener picks.
 */
@Composable
internal fun InfoPlaylistPickerHost(
    picker: InfoPlaylistPicker,
    playlists: InfoPlaylists?,
    episodes: List<Episode>,
    snackbarHostState: SnackbarHostState,
    show: Podcast? = null,
    onAdded: () -> Unit = {},
) {
    // Remembered before the sheet, so a confirmation still shows after the sheet closes.
    val scope = rememberCoroutineScope()
    if (!picker.open || playlists == null || episodes.isEmpty()) return
    val all by playlists.playlists.collectAsStateWithLifecycle(initialValue = emptyList())
    val showMessage: (String) -> Unit = { message -> scope.launch { snackbarHostState.showSnackbar(message) } }
    AddToPlaylistSheet(
        playlists = all,
        episodeCount = episodes.size,
        state = picker.sheet,
        onAddTo = { playlist ->
            playlists.addTo(playlist, InfoPlaylists.withShow(episodes, show), showMessage)
            picker.close()
            onAdded()
        },
        onCreate = { name ->
            playlists.create(name, InfoPlaylists.withShow(episodes, show), showMessage)
            picker.close()
            onAdded()
        },
        onDismissRequest = picker::close,
    )
}
