package cx.aswin.boxlore.widgets

import android.content.Context
import cx.aswin.boxlore.core.catalog.SubscriptionRepository
import cx.aswin.boxlore.core.playback.PlaybackRepository
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import cx.aswin.boxlore.core.ranking.AdaptiveCandidateScorer
import cx.aswin.boxlore.feature.widgets.LibraryWidgetCoordinator
import cx.aswin.boxlore.feature.widgets.LibraryWidgetDependencies
import cx.aswin.boxlore.feature.widgets.NowPlayingWidgetCoordinator
import cx.aswin.boxlore.feature.widgets.NowPlayingWidgetDependencies
import cx.aswin.boxlore.feature.widgets.configureLibraryWidgets
import cx.aswin.boxlore.feature.widgets.configureNowPlayingWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Installs home-screen widget ports from the app composition root.
 * Keeps [cx.aswin.boxlore.BoxLoreApplication.onCreate] under the detekt method-length cap.
 */
object HomeScreenWidgetsInstaller {
    fun install(
        context: Context,
        scope: CoroutineScope,
        playbackRepository: PlaybackRepository,
        subscriptionRepository: SubscriptionRepository,
        userPreferencesRepository: UserPreferencesRepository,
        adaptiveScorer: AdaptiveCandidateScorer,
    ) {
        configureNowPlayingWidget(
            object : NowPlayingWidgetDependencies {
                override val context: Context = context
                override val scope: CoroutineScope = scope
                override val playback =
                    NowPlayingWidgetPlaybackAdapter(
                        playbackRepository = playbackRepository,
                        scope = scope,
                    )
            },
        )
        configureLibraryWidgets(
            object : LibraryWidgetDependencies {
                override val context: Context = context
                override val scope: CoroutineScope = scope
                override val library =
                    WidgetLibrarySourceAdapter(
                        subscriptionRepository = subscriptionRepository,
                        playbackRepository = playbackRepository,
                        userPreferencesRepository = userPreferencesRepository,
                        adaptiveScorer = adaptiveScorer,
                        scope = scope,
                    )
            },
        )
        // Widget RemoteViews apply ROND from theme fast-cache; re-render when lettering changes.
        scope.launch {
            userPreferencesRepository.fontRoundnessStream.collect {
                NowPlayingWidgetCoordinator.requestRefresh(context)
                LibraryWidgetCoordinator.requestRefresh(context)
            }
        }
    }
}
