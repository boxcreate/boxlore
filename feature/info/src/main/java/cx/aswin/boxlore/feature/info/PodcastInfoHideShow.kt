package cx.aswin.boxlore.feature.info

import cx.aswin.boxlore.core.model.HiddenShow
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.listenerShowIds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What unfollowing a show clears and Undo puts back: its follow settings. */
internal data class HiddenFollowSettings(
    val notificationsEnabled: Boolean,
    val autoDownloadEnabled: Boolean,
    val customGenre: String?,
    val customGenreIcon: String?,
) {
    companion object {
        fun of(podcast: Podcast) =
            HiddenFollowSettings(podcast.notificationsEnabled, podcast.autoDownloadEnabled, podcast.customGenre, podcast.customGenreIcon)
    }
}

/** Where a followed show sat in the library: its Home pin and its place in the manual order. */
internal data class HiddenLibraryPlace(val pinnedToHome: Boolean, val manualOrderIndex: Int?) {
    companion object {
        val NONE = HiddenLibraryPlace(pinnedToHome = false, manualOrderIndex = null)
    }
}

/**
 * Hide show everywhere on the show page. Hiding a followed show also unfollows it; Undo unhides it
 * and follows it again when hiding unfollowed it, with the settings, Home pin and library place the
 * follow had. Hiding and unhiding run one at a time, in order. Unhiding matches every ID of the page's
 * show, so a show hidden under a linked copy is unhidden too.
 */
internal class PodcastInfoHideShow(
    private val scope: CoroutineScope,
    private val hiddenIds: Flow<Set<String>>,
    private val hide: suspend (HiddenShow) -> Unit,
    private val unhide: suspend (Set<String>) -> Unit,
    /** The page as it is now, or null while it loads. */
    private val page: () -> PodcastInfoUiState.Success?,
    /** Follows or unfollows the page's show and returns once the page shows it. */
    private val toggleFollow: suspend (PodcastInfoUiState.Success) -> Unit,
    private val track: (action: String, podcastId: String) -> Unit,
    /** Reads the show's library place before the unfollow clears it. */
    private val libraryPlace: suspend (podcastId: String) -> HiddenLibraryPlace = { HiddenLibraryPlace.NONE },
    /** Puts a refollowed show's settings and library place back. */
    private val restoreFollow: suspend (PodcastInfoUiState.Success, HiddenFollowSettings, HiddenLibraryPlace) -> Unit = { _, _, _ -> },
) {
    /** Built when Hide is tapped, so an Undo tapped before the hide is stored still finds it. */
    private class PendingUndo(val podcastId: String, val showIds: Set<String>, val follow: HiddenFollowSettings?) {
        /** Filled under the lock before the unfollow; Undo reads it under the same lock. */
        var place: HiddenLibraryPlace = HiddenLibraryPlace.NONE
    }

    private val mutex = Mutex()

    @Volatile
    private var pendingUndo: PendingUndo? = null

    /** True while the page's show is hidden. */
    fun isHidden(states: Flow<PodcastInfoUiState>): Flow<Boolean> = combine(
        states.map { (it as? PodcastInfoUiState.Success)?.podcast?.listenerShowIds.orEmpty() }.distinctUntilChanged(),
        hiddenIds,
    ) { showIds, hidden -> showIds.any { it in hidden } }.distinctUntilChanged()

    fun hideShow() {
        val state = page() ?: return
        val podcast = state.podcast
        val undo = PendingUndo(podcast.id, podcast.listenerShowIds, follow = HiddenFollowSettings.of(podcast).takeIf { state.isSubscribed })
        pendingUndo = undo
        scope.launch {
            mutex.withLock {
                if (state.isSubscribed) undo.place = libraryPlace(podcast.id)
                hide(HiddenShow.of(podcast))
                if (state.isSubscribed) {
                    page()?.takeIf { it.podcast.id == podcast.id && it.isSubscribed }?.let { toggleFollow(it) }
                }
                track(ACTION_HIDDEN, podcast.id)
            }
        }
    }

    fun unhideShow() {
        val state = page() ?: return
        pendingUndo = null
        scope.launch {
            mutex.withLock {
                unhide(state.podcast.listenerShowIds)
                track(ACTION_UNHIDDEN, state.podcast.id)
            }
        }
    }

    /** Undoes the last hide on this page; nothing when there is none. */
    fun undoHide() {
        val undo = pendingUndo ?: return
        pendingUndo = null
        scope.launch {
            mutex.withLock {
                unhide(undo.showIds)
                val follow = undo.follow
                val unfollowed = page()?.takeIf { follow != null && it.podcast.id == undo.podcastId && !it.isSubscribed }
                if (follow != null && unfollowed != null) {
                    toggleFollow(unfollowed)
                    page()?.takeIf { it.podcast.id == undo.podcastId && it.isSubscribed }?.let { restoreFollow(it, follow, undo.place) }
                }
                track(ACTION_UNHIDDEN, undo.podcastId)
            }
        }
    }

    companion object {
        /** `settings_interaction` actions; the value is the show ID. */
        const val ACTION_HIDDEN = "show_hidden"
        const val ACTION_UNHIDDEN = "show_unhidden"
    }
}
