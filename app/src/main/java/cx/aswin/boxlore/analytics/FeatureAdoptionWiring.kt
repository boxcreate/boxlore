package cx.aswin.boxlore.analytics

import android.content.Context
import cx.aswin.boxlore.AppContainer
import cx.aswin.boxlore.core.analytics.FeatureAdoption
import cx.aswin.boxlore.core.analytics.FeatureAdoptionAnalytics
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Feeds the feature-adoption person properties ([FeatureAdoptionAnalytics]) from flows the app already
 * keeps plus one Room count; nothing is read per playback tick.
 */
internal object FeatureAdoptionWiring {
    fun adoption(
        playlistsCount: Flow<Int>,
        hiddenShowsCount: Flow<Int>,
        episodeTitlesOnly: Flow<Boolean>,
        trimSilence: Flow<Boolean>,
        showsWithOwnSpeed: Flow<Int>,
    ): Flow<FeatureAdoption> = combine(playlistsCount, hiddenShowsCount, episodeTitlesOnly, trimSilence, showsWithOwnSpeed) { playlists, hidden, titles, trim, ownSpeed ->
        FeatureAdoption(playlists, hidden, titles, trim, ownSpeed)
    }

    /** Starts after [awaitStartupReady], so launch never waits on it. */
    fun start(
        context: Context,
        scope: CoroutineScope,
        container: AppContainer,
        preferences: UserPreferencesRepository,
        awaitStartupReady: suspend () -> Unit,
    ): Job = scope.launch {
        awaitStartupReady()
        val values =
            adoption(
                playlistsCount = container.playlistRepository.playlists.map { it.size },
                hiddenShowsCount = preferences.hiddenShows.showsStream.map { it.size },
                episodeTitlesOnly = preferences.episodeTitlesOnlyStream,
                trimSilence = preferences.trimSilenceEnabledStream,
                showsWithOwnSpeed = container.database.podcastDao().observeFollowedOwnSpeedCount(),
            )
        FeatureAdoptionAnalytics.sync(values, FeatureAdoptionAnalytics.store(context))
    }
}
