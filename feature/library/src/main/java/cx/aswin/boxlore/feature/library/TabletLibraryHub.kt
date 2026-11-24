package cx.aswin.boxlore.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth
import cx.aswin.boxlore.core.designsystem.layout.boxloreContentBottomPadding
import cx.aswin.boxlore.core.designsystem.layout.boxloreContentWidthDp
import cx.aswin.boxlore.core.designsystem.list.LazyListKeyPolicy
import cx.aswin.boxlore.core.designsystem.text.currentLocale
import cx.aswin.boxlore.core.designsystem.theme.ExpressiveShapes
import cx.aswin.boxlore.core.model.isLatestEpisodeNew
import cx.aswin.boxlore.core.model.text.LocaleFormats
import cx.aswin.boxlore.feature.library.downloads.downloadSummary
import java.util.TimeZone
import kotlinx.coroutines.delay

/** The hub's cards in order, keyed so a window change keeps the card in view. */
internal object LibraryHubKeys {
    const val SUBSCRIPTIONS = "subscriptions"
    const val LIKED = "liked"
    const val DOWNLOADS = "downloads"
    const val HISTORY = "history"
    const val PLAYLISTS = "playlists"
}

/**
 * The hub's cards in one grid on every window: one column on phones, and on tablets one or two
 * bounded columns. The same grid and [gridState] serve both, so folding or unfolding keeps the hub
 * where it was.
 */
@Composable
internal fun LibraryHubGrid(
    state: LibraryUiState.Success,
    gridState: LazyGridState,
    destinations: LibraryHubDestinations,
    modifier: Modifier,
    playlists: LibraryPlaylistsCard? = null,
    lastSeenEpisodes: Map<String, String> = emptyMap(),
) {
    val status = rememberLibraryHubStatus(state, lastSeenEpisodes)
    LazyVerticalGrid(
        columns = GridCells.Fixed(libraryHubColumns()),
        state = gridState,
        modifier = modifier.boxloreCardWidth(BoxloreCardFamily.Utility).fillMaxSize(),
        // Phones keep room for the floating bar and mini player under the last card; tablets reserve only the player.
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = boxloreContentBottomPadding(PHONE_BOTTOM_PADDING)),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = LibraryHubKeys.SUBSCRIPTIONS) {
            LibraryMenuCard(
                title = stringResource(R.string.library_hub_subscriptions),
                // A plain section icon: the card opens the list, it doesn't add a show.
                icon = Icons.Rounded.Podcasts,
                onClick = destinations.subscriptions,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                images = state.subscribedPodcasts.take(3).map { it.imageUrl },
                shapes = listOf(ExpressiveShapes.Circle, ExpressiveShapes.Puffy, ExpressiveShapes.Diamond),
                status = status.subscriptions,
            )
        }
        item(key = LibraryHubKeys.LIKED) {
            LibraryMenuCard(
                title = stringResource(R.string.library_hub_liked_episodes),
                icon = Icons.Rounded.Favorite,
                onClick = destinations.liked,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                images = state.likedEpisodes.take(3).map { it.episodeImageUrl ?: it.podcastImageUrl ?: "" },
                shapes = listOf(ExpressiveShapes.Heart, ExpressiveShapes.Star, ExpressiveShapes.SoftBurst),
                status = status.liked,
            )
        }
        item(key = LibraryHubKeys.DOWNLOADS) {
            LibraryMenuCard(
                title = stringResource(R.string.library_hub_downloads),
                icon = Icons.Rounded.DownloadDone,
                onClick = destinations.downloads,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                images = state.downloadedEpisodes.take(3).map { it.episodeImageUrl ?: it.podcastImageUrl ?: "" },
                shapes = listOf(ExpressiveShapes.Hexagon, ExpressiveShapes.Gem, ExpressiveShapes.Cookie4),
                status = status.downloads,
            )
        }
        item(key = LibraryHubKeys.HISTORY) {
            LibraryMenuCard(
                title = stringResource(R.string.library_hub_listening_history),
                icon = Icons.Rounded.History,
                onClick = destinations.history,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                images = state.recentHistory.take(3).map { it.episodeImageUrl ?: it.podcastImageUrl ?: "" },
                shapes = listOf(ExpressiveShapes.Circle, ExpressiveShapes.Burst, ExpressiveShapes.Sunny),
                status = status.history,
            )
        }
        playlists?.let { card ->
            item(key = LibraryHubKeys.PLAYLISTS) { LibraryPlaylistsMenuCard(card) }
        }
    }
}

/** Where each hub card leads. */
internal data class LibraryHubDestinations(
    val subscriptions: () -> Unit,
    val liked: () -> Unit,
    val downloads: () -> Unit,
    val history: () -> Unit,
)

/** Tablet content this wide or wider shows the hub's cards in two columns. */
private const val TWO_COLUMNS_FROM_DP = 640f

/** The hub's columns: two on tablets with room for them, one everywhere else. Its loading outline uses the same. */
@Composable
internal fun libraryHubColumns(): Int =
    if (LocalBoxloreWindowLayout.current.usesTabletLayout && boxloreContentWidthDp() >= TWO_COLUMNS_FROM_DP) 2 else 1

/** Phones: room under the last card for the floating bar and mini player. */
private val PHONE_BOTTOM_PADDING = 180.dp

/** Each hub card's status line. */
internal data class LibraryHubStatus(val subscriptions: String, val liked: String, val downloads: String, val history: String)

/**
 * What each hub card says is inside: shows and how many have a new episode (the same NEW rule as
 * Subscriptions), liked episodes (each once, as the Liked page lists them), episodes on the phone
 * (as Downloads counts them, without failed downloads), and the day the listener last played
 * something. Worked out when the lists or the day change, never per playback tick.
 */
@Composable
internal fun rememberLibraryHubStatus(state: LibraryUiState.Success, lastSeenEpisodes: Map<String, String>): LibraryHubStatus {
    val newCount = remember(state.subscribedPodcasts, lastSeenEpisodes) {
        state.subscribedPodcasts.count { it.isLatestEpisodeNew(lastSeenEpisodes[it.id]) }
    }
    val locale = currentLocale()
    val shows = state.subscribedPodcasts.size
    val subscriptions = buildList {
        add(pluralStringResource(R.plurals.library_show_count, shows, shows))
        if (newCount > 0) add(pluralStringResource(R.plurals.library_folder_new_count, newCount, newCount))
    }.joinToString(STATUS_SEPARATOR)
    val liked = remember(state.likedEpisodes) { LazyListKeyPolicy.deduplicateById(state.likedEpisodes) { it.episodeId }.size }
    val downloaded = remember(state.downloadedEpisodes) { state.downloadedEpisodes.downloadSummary().episodeCount }
    val lastPlayed = remember(state.recentHistory) { state.recentHistory.maxOfOrNull { it.lastPlayedAt }?.takeIf { it > 0L } }
    val (today, zone) = rememberLibraryToday()
    val now = LocalLibraryClock.current()
    val daysAgo = remember(lastPlayed, today, zone) { lastPlayed?.let { today - localEpochDay(it, zone) } }
    return LibraryHubStatus(
        subscriptions = subscriptions,
        liked = if (liked == 0) stringResource(R.string.library_hub_no_likes) else pluralStringResource(R.plurals.library_hub_episode_count, liked, liked),
        downloads = if (downloaded == 0) stringResource(R.string.library_hub_no_downloads) else pluralStringResource(R.plurals.library_hub_episode_count, downloaded, downloaded),
        history = when {
            daysAgo == null -> stringResource(R.string.library_hub_no_history)
            daysAgo <= 0L -> stringResource(R.string.library_hub_played_today)
            daysAgo == 1L -> stringResource(R.string.library_hub_played_yesterday)
            // Whole days: "3 days ago", then weeks and months.
            else -> stringResource(R.string.library_hub_last_played, LocaleFormats.relativeTime(now - daysAgo * DAY_MS, now, locale))
        },
    )
}

/** The clock the hub reads the day from; tests move it past midnight. */
internal val LocalLibraryClock = staticCompositionLocalOf<() -> Long> { System::currentTimeMillis }

/**
 * Today's calendar day and the device's time zone. Moves on at midnight while the hub is shown, and
 * catches up when the app comes back to the foreground (a day or a zone may have changed meanwhile).
 */
@Composable
internal fun rememberLibraryToday(): Pair<Long, TimeZone> {
    val now = LocalLibraryClock.current
    var zone by remember { mutableStateOf(TimeZone.getDefault()) }
    var today by remember { mutableLongStateOf(localEpochDay(now(), zone)) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                zone = TimeZone.getDefault()
                val moment = now()
                today = localEpochDay(moment, zone)
                val midnight = (today + 1) * DAY_MS - zone.getOffset(moment)
                delay((midnight - moment).coerceIn(MIN_DAY_CHECK_MS, MAX_DAY_CHECK_MS))
            }
        }
    }
    return today to zone
}

private const val MIN_DAY_CHECK_MS = 1_000L
private const val MAX_DAY_CHECK_MS = 60 * 60 * 1_000L

/** The calendar day [epochMillis] falls on in [zone], counted from 1 January 1970. */
internal fun localEpochDay(epochMillis: Long, zone: TimeZone): Long = Math.floorDiv(epochMillis + zone.getOffset(epochMillis), DAY_MS)

private const val DAY_MS = 86_400_000L
private const val STATUS_SEPARATOR = " · "
