package cx.aswin.boxlore.ui

import android.net.Uri
import androidx.navigation.NavController
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.playback.PlaybackRepository
import cx.aswin.boxlore.feature.player.v2.PlayerSheetActions

internal fun playerSheetActions(
    navController: NavController,
    playbackRepository: PlaybackRepository,
    onSheetInteractionChanged: (Boolean) -> Unit,
    navigate: (String) -> Unit = { route -> navController.navigate(route) { launchSingleTop = true } },
): PlayerSheetActions = PlayerSheetActions(
    onSheetInteractionChanged = onSheetInteractionChanged,
    onEpisodeInfoClick = { episode ->
        navigate(playerEpisodeDetailRoute(episode, playbackRepository.playerState.value.currentPodcast))
    },
    onPodcastInfoClick = { podcast ->
        navigate(playerPodcastDetailRoute(podcast))
    },
)

internal fun playerEpisodeDetailRoute(episode: Episode, podcast: Podcast?): String {
    if (episode.id.startsWith("briefing_")) {
        return "briefing?region=${episode.id.removePrefix("briefing_").substringBefore("_")}"
    }
    fun encode(value: String?): String = Uri.encode(value?.ifEmpty { "_" } ?: "_")
    val podcastId = episode.podcastId ?: podcast?.id ?: "unknown"
    val podcastTitle = episode.podcastTitle ?: podcast?.title ?: "Podcast"
    val imageUrl = episode.imageUrl ?: episode.podcastImageUrl ?: podcast?.imageUrl
    return "episode/${encode(episode.id)}/${encode(episode.title)}/" +
        "${encode(episode.description.take(500))}/${encode(imageUrl)}/${encode(episode.audioUrl)}/" +
        "${episode.duration}/${encode(podcastId)}/${encode(podcastTitle)}?entryPoint=player_ui"
}

internal fun playerPodcastDetailRoute(podcast: Podcast): String =
    if (podcast.id.startsWith("briefing_")) {
        "briefing?region=${podcast.id.removePrefix("briefing_")}"
    } else {
        "podcast/${Uri.encode(podcast.id)}?entryPoint=player_ui"
    }
