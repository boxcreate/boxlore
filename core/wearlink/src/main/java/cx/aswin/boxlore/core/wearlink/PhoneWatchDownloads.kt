package cx.aswin.boxlore.core.wearlink

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Whether the watch holds an episode's audio, as far as the phone knows. */
enum class WatchCopy { None, Downloading, OnWatch }

/** Finds an episode among what the watch reported. */
object WatchCopyRules {
    /**
     * The watch may know an episode under another ID than the phone, so the audio address counts
     * as the same episode too.
     */
    fun of(episodeId: String, audioUrl: String?, stored: List<WatchStoredEpisode>): WatchCopy {
        val found = stored.firstOrNull { it.episodeId == episodeId || (audioUrl != null && it.audioUrl == audioUrl) }
        return when {
            found == null -> WatchCopy.None
            found.done -> WatchCopy.OnWatch
            else -> WatchCopy.Downloading
        }
    }
}

/**
 * What the watch keeps stored, as the phone last heard, and the phone's two orders about it:
 * download this episode, and remove it again. An order is only handed over while the watch is in
 * reach; the watch's storage limit still decides whether a download is kept.
 */
class PhoneWatchDownloads(private val transport: WearLinkTransport, private val scope: CoroutineScope) {
    private val reported = MutableStateFlow<List<WatchStoredEpisode>>(emptyList())

    val stored: StateFlow<List<WatchStoredEpisode>> = reported.asStateFlow()

    /** The watch's own account of its downloads; it replaces what the phone assumed meanwhile. */
    internal fun report(stored: List<WatchStoredEpisode>) {
        reported.value = stored
    }

    /** Asks the watch to download [episode]. [onResult] hears whether the order could be handed over. */
    fun send(episode: WearLinkEpisode, onResult: (Boolean) -> Unit = {}) {
        order(WearLinkCommand.DOWNLOAD_EPISODE, episode, onResult) { known ->
            known.filterNot { it.matches(episode) } + WatchStoredEpisode(episode.episodeId, episode.audioUrl, done = false)
        }
    }

    /** Asks the watch to remove [episode]'s audio, or to stop downloading it. */
    fun remove(episode: WearLinkEpisode, onResult: (Boolean) -> Unit = {}) {
        order(WearLinkCommand.REMOVE_DOWNLOAD, episode, onResult) { known -> known.filterNot { it.matches(episode) } }
    }

    private fun order(action: String, episode: WearLinkEpisode, onResult: (Boolean) -> Unit, assumed: (List<WatchStoredEpisode>) -> List<WatchStoredEpisode>) {
        scope.launch {
            val sent = transport.send(WearLinkProtocol.PATH_WATCH_COMMAND, WearLinkCodec.encode(WearLinkCommand(action, episode = episode)))
            // Shown at once; the watch's next report says what really happened.
            if (sent) reported.value = assumed(reported.value)
            onResult(sent)
        }
    }

    private fun WatchStoredEpisode.matches(episode: WearLinkEpisode): Boolean =
        episodeId == episode.episodeId || (episode.audioUrl != null && audioUrl == episode.audioUrl)
}
