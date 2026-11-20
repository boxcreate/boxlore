package cx.aswin.boxlore.feature.library.playlists

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import cx.aswin.boxlore.core.designsystem.icon.GenreIconItem
import cx.aswin.boxlore.core.designsystem.icon.GenreIcons
import cx.aswin.boxlore.core.model.PLAYLIST_DEFAULT_PER_SHOW_LIMIT
import cx.aswin.boxlore.core.model.PlaylistMix
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.SubscriptionFolder
import cx.aswin.boxlore.core.model.normalizePlaylistName
import cx.aswin.boxlore.feature.library.subscriptions.ICONS_PER_ROW
import cx.aswin.boxlore.feature.library.subscriptions.extractDistinctGenres
import cx.aswin.boxlore.feature.library.subscriptions.matchingShows

/** New playlist makes one in three steps; Add shows is only the pick step, for an existing playlist. */
internal enum class NewPlaylistMode { Create, AddShows }

/**
 * A playlist being made, or shows being added to [playlistId]. It lives in saved state, so the step,
 * the picks and every choice survive rotation, resizing and folding. New playlist's steps are
 * [START] (suggestions, or pick your own), [PICK] (shows) and [FINISH] (name, icon, how it plays).
 */
internal data class NewPlaylistDraft(
    val mode: NewPlaylistMode = NewPlaylistMode.Create,
    val playlistId: String? = null,
    val step: Int = START,
    val pickedIds: List<String> = emptyList(),
    val search: String = "",
    /** The genre the show grid is narrowed to; null shows every genre. */
    val genreFilter: String? = null,
    val name: String = "",
    val nameEdited: Boolean = false,
    val icon: String? = null,
    val iconChosen: Boolean = false,
    val mix: PlaylistMix = PlaylistMix.TAKE_TURNS,
    val perShowLimit: Int? = PLAYLIST_DEFAULT_PER_SHOW_LIMIT,
    /** Started from a suggestion: Back from [FINISH] returns to the suggestions, not the show grid. */
    val fromSuggestion: Boolean = false,
    /** Where the shows came from, for analytics: a suggestion's kind (`genre`, `folder`, `recent`) or `own`. */
    val origin: String = ORIGIN_OWN,
) {
    val stepCount: Int get() = if (mode == NewPlaylistMode.Create) STEPS else 1

    companion object {
        const val START = 0
        const val PICK = 1
        const val FINISH = 2
        private const val STEPS = 3
        const val ORIGIN_OWN = "own"
    }
}

/** A ready-made playlist built from the listener's shows: one genre, one Library folder, or recent follows. */
internal data class PlaylistSuggestion(
    val key: String,
    val kind: Kind,
    /** The genre (shown through its translated name) or the folder's own name; null for recent follows. */
    val label: String?,
    val iconKey: String?,
    val podcastIds: List<String>,
    val covers: List<String>,
) {
    enum class Kind { GENRE, FOLDER, RECENT }
}

internal object NewPlaylistFlowLogic {
    fun start(): NewPlaylistDraft = NewPlaylistDraft()

    fun addShows(playlistId: String): NewPlaylistDraft =
        NewPlaylistDraft(mode = NewPlaylistMode.AddShows, playlistId = playlistId, step = NewPlaylistDraft.PICK)

    /** A suggestion picks its shows and, unless the listener set them, names the playlist and gives it the icon; then the last step. */
    fun withSuggestion(draft: NewPlaylistDraft, suggestion: PlaylistSuggestion, name: String): NewPlaylistDraft = draft.copy(
        pickedIds = suggestion.podcastIds,
        name = if (draft.nameEdited) draft.name else name,
        icon = if (draft.iconChosen) draft.icon else suggestion.iconKey,
        step = NewPlaylistDraft.FINISH,
        fromSuggestion = true,
        origin = suggestion.kind.name.lowercase(),
    )

    /** Pick shows yourself. */
    fun pickOwn(draft: NewPlaylistDraft): NewPlaylistDraft =
        draft.copy(step = NewPlaylistDraft.PICK, fromSuggestion = false, origin = NewPlaylistDraft.ORIGIN_OWN)

    fun togglePick(draft: NewPlaylistDraft, podcastId: String): NewPlaylistDraft =
        draft.copy(pickedIds = if (podcastId in draft.pickedIds) draft.pickedIds - podcastId else draft.pickedIds + podcastId)

    /** Picks every one of [podcastIds] (a whole genre), or unpicks them all when they already are. */
    fun toggleAll(draft: NewPlaylistDraft, podcastIds: List<String>): NewPlaylistDraft {
        val all = podcastIds.isNotEmpty() && draft.pickedIds.containsAll(podcastIds)
        return draft.copy(pickedIds = if (all) draft.pickedIds - podcastIds.toSet() else (draft.pickedIds + podcastIds).distinct())
    }

    fun withGenreFilter(draft: NewPlaylistDraft, genre: String?): NewPlaylistDraft = draft.copy(genreFilter = genre)

    /** A typed name that matches a genre suggests its icon, unless the listener chose one. */
    fun withName(draft: NewPlaylistDraft, name: String, matchingIconKey: String?): NewPlaylistDraft = draft.copy(
        name = name,
        nameEdited = true,
        icon = if (draft.iconChosen) draft.icon else matchingIconKey ?: draft.icon,
    )

    fun withIcon(draft: NewPlaylistDraft, iconKey: String): NewPlaylistDraft = draft.copy(icon = iconKey, iconChosen = true)

    /** The collapsed icon row: the chosen icon first, then the first others, one row's worth. */
    fun iconRow(selectedKey: String?): List<GenreIconItem> {
        val chosen = GenreIcons.all.firstOrNull { it.key.equals(selectedKey, ignoreCase = true) }
        return (listOfNotNull(chosen) + GenreIcons.all.filter { it != chosen }).take(ICONS_PER_ROW)
    }

    fun withPlayback(draft: NewPlaylistDraft, mix: PlaylistMix, perShowLimit: Int?): NewPlaylistDraft = draft.copy(mix = mix, perShowLimit = perShowLimit)

    /**
     * Leaving the show grid: when every pick shares one genre and the listener hasn't named the playlist,
     * it is named after that genre ([displayName]) and takes its icon ([iconKeyFor]).
     */
    fun next(
        draft: NewPlaylistDraft,
        followed: List<Podcast>,
        iconKeyFor: (String) -> String?,
        displayName: (String) -> String = { it },
    ): NewPlaylistDraft {
        if (draft.step != NewPlaylistDraft.PICK) return draft
        val shared = PlaylistShowGrid.sharedGenre(followed.filter { it.id in draft.pickedIds })
        val named = if (draft.nameEdited || draft.name.isNotBlank() || shared == null) draft else draft.copy(name = displayName(shared))
        val iconed = if (draft.iconChosen || shared == null || named.icon != null) named else named.copy(icon = iconKeyFor(shared))
        return iconed.copy(step = NewPlaylistDraft.FINISH)
    }

    /** Back one step; null leaves the flow. */
    fun back(draft: NewPlaylistDraft): NewPlaylistDraft? = when {
        draft.mode == NewPlaylistMode.AddShows -> null
        draft.step == NewPlaylistDraft.FINISH && draft.fromSuggestion -> draft.copy(step = NewPlaylistDraft.START)
        draft.step == NewPlaylistDraft.FINISH -> draft.copy(step = NewPlaylistDraft.PICK)
        draft.step == NewPlaylistDraft.PICK -> draft.copy(step = NewPlaylistDraft.START)
        else -> null
    }

    /** Picking needs a show; finishing needs a name and a show. */
    fun canGoNext(draft: NewPlaylistDraft): Boolean = when {
        draft.mode == NewPlaylistMode.AddShows || draft.step == NewPlaylistDraft.PICK -> draft.pickedIds.isNotEmpty()
        draft.step == NewPlaylistDraft.FINISH -> draft.pickedIds.isNotEmpty() && normalizePlaylistName(draft.name).isNotEmpty()
        else -> false
    }
}

/** Ready-made playlists from the listener's shows, for New playlist's first step. */
internal object PlaylistSuggestions {
    /** Fewer shows than this make no suggestion. */
    const val MIN_SUGGESTION_SHOWS = 2
    const val MAX_GENRE_SUGGESTIONS = 6
    const val MAX_FOLDER_SUGGESTIONS = 3
    const val RECENT_FOLLOW_DAYS = 30L
    private const val DAY_MS = 24L * 60 * 60 * 1000
    private const val COVERS = 4

    /** Icon for "Recently followed"; a [cx.aswin.boxlore.core.designsystem.icon.GenreIcons] key. */
    const val RECENT_ICON_KEY = "star"

    /**
     * Suggestions, best first: genres with the most followed shows, then Library folders the listener
     * made (genre folders repeat the genre suggestions, so they are left out), then shows followed in the
     * last [RECENT_FOLLOW_DAYS] days. Each needs [MIN_SUGGESTION_SHOWS]; a name that an existing
     * playlist already has ([existingNames], compared as [displayName] gives it) is skipped.
     */
    fun suggestions(
        followed: List<Podcast>,
        folders: List<SubscriptionFolder>,
        existingNames: Collection<String>,
        nowMs: Long,
        iconKeyFor: (String) -> String?,
        displayName: (String) -> String = { it },
        recentName: String = "",
    ): List<PlaylistSuggestion> {
        val taken = existingNames.map { normalizePlaylistName(it).lowercase() }.toSet()
        fun free(name: String) = normalizePlaylistName(name).lowercase() !in taken
        val byId = followed.associateBy { it.id }

        val genres = extractDistinctGenres(followed)
            .map { genre -> genre to matchingShows(followed, genre) }
            .filter { (genre, shows) -> shows.size >= MIN_SUGGESTION_SHOWS && free(displayName(genre)) }
            .sortedWith(compareByDescending<Pair<String, List<Podcast>>> { it.second.size }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.first })
            .take(MAX_GENRE_SUGGESTIONS)
            .map { (genre, shows) -> PlaylistSuggestion("genre:$genre", PlaylistSuggestion.Kind.GENRE, genre, iconKeyFor(genre), shows.map { it.id }, covers(shows)) }

        val fromFolders = folders
            .filter { it.linkedGenre == null && free(it.name) }
            .mapNotNull { folder ->
                val shows = folder.podcastIds.mapNotNull(byId::get)
                if (shows.size < MIN_SUGGESTION_SHOWS) return@mapNotNull null
                PlaylistSuggestion("folder:${folder.id}", PlaylistSuggestion.Kind.FOLDER, folder.name, folder.icon, shows.map { it.id }, covers(shows))
            }
            .take(MAX_FOLDER_SUGGESTIONS)

        val recentShows = followed
            .filter { it.subscribedAt > 0L && nowMs - it.subscribedAt <= RECENT_FOLLOW_DAYS * DAY_MS }
            .sortedByDescending { it.subscribedAt }
        val recent = if (recentShows.size >= MIN_SUGGESTION_SHOWS && free(recentName)) {
            listOf(PlaylistSuggestion("recent", PlaylistSuggestion.Kind.RECENT, null, RECENT_ICON_KEY, recentShows.map { it.id }, covers(recentShows)))
        } else {
            emptyList()
        }
        return genres + fromFolders + recent
    }

    private fun covers(shows: List<Podcast>): List<String> = shows.map { it.imageUrl }.filter(String::isNotBlank).take(COVERS)
}

/** What New playlist's show grid offers and how it can be narrowed. */
internal object PlaylistShowGrid {
    /** The one genre every show in [shows] has, or null. */
    fun sharedGenre(shows: List<Podcast>): String? {
        if (shows.isEmpty()) return null
        return extractDistinctGenres(shows).firstOrNull { genre -> matchingShows(shows, genre).size == shows.size }
    }

    /** The shows the grid offers: followed shows not yet in the playlist, narrowed by genre and search. */
    fun visibleShows(followed: List<Podcast>, inPlaylist: Set<String>, genre: String?, search: String): List<Podcast> {
        val pickable = followed.filter { it.id !in inPlaylist }
        val byGenre = if (genre == null) pickable else matchingShows(pickable, genre)
        val query = search.trim()
        return if (query.isEmpty()) byGenre else byGenre.filter { it.title.contains(query, ignoreCase = true) }
    }

    /** Genre chips over the grid: genres of the pickable shows with how many each holds, most first. */
    fun genreChips(followed: List<Podcast>, inPlaylist: Set<String>): List<Pair<String, Int>> {
        val pickable = followed.filter { it.id !in inPlaylist }
        return extractDistinctGenres(pickable)
            .map { it to matchingShows(pickable, it).size }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.first })
    }
}

internal val NewPlaylistDraftSaver: Saver<NewPlaylistDraft, Any> = listSaver(
    save = { d ->
        listOf(
            d.mode.name, d.playlistId, d.step, ArrayList(d.pickedIds), d.search, d.genreFilter, d.name, d.nameEdited,
            d.icon, d.iconChosen, d.mix.stored, d.perShowLimit ?: 0, d.fromSuggestion, d.origin,
        )
    },
    restore = { v ->
        NewPlaylistDraft(
            mode = NewPlaylistMode.entries.firstOrNull { it.name == v[0] } ?: NewPlaylistMode.Create,
            playlistId = v[1] as? String,
            step = v[2] as? Int ?: NewPlaylistDraft.START,
            pickedIds = (v[3] as? List<*>)?.filterIsInstance<String>().orEmpty(),
            search = v[4] as? String ?: "",
            genreFilter = v[5] as? String,
            name = v[6] as? String ?: "",
            nameEdited = v[7] as? Boolean ?: false,
            icon = v[8] as? String,
            iconChosen = v[9] as? Boolean ?: false,
            mix = PlaylistMix.fromStored(v[10] as? String),
            perShowLimit = (v[11] as? Int)?.takeIf { it > 0 },
            fromSuggestion = v[12] as? Boolean ?: false,
            origin = v.getOrNull(13) as? String ?: NewPlaylistDraft.ORIGIN_OWN,
        )
    },
)
