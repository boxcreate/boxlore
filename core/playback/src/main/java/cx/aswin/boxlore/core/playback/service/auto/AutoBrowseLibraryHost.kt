package cx.aswin.boxlore.core.playback.service.auto

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.playback.QueueRepository
import cx.aswin.boxlore.core.playback.SmartQueueSources
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import cx.aswin.boxlore.core.ranking.AdaptiveCandidateScorer
import kotlinx.coroutines.CoroutineScope

interface AutoBrowseLibraryHost {
    val serviceScope: CoroutineScope
    val database: BoxLoreDatabase
    val podcastRepository: PodcastRepository
    val queueRepository: QueueRepository
    val smartQueueSources: SmartQueueSources
    val adaptiveCandidateScorer: AdaptiveCandidateScorer
    val userPreferencesRepository: UserPreferencesRepository
    val mediaSession: MediaLibrarySession?
    val seekBackAction: CommandButton
    val seekForwardAction: CommandButton
    val markCompleteAction: CommandButton
    val autoCollageUris: Map<String, Uri>
    var isRefilling: Boolean

    fun asContext(): Context

    /** Rebuild folder collage tiles when resume/history/queue content changes. */
    fun requestAutoCollageRefresh(force: Boolean = false)

    fun getString(@StringRes id: Int, vararg formatArgs: Any,): String

    fun observeManualCompletion(episodeId: String)

    fun markCurrentEpisodeCompletedAndSkip(session: MediaSession)

    /** A media button arrived from [controller]; the play it causes reaches the player as the notification. */
    fun noteMediaButton(session: MediaSession, controller: MediaSession.ControllerInfo,) {
        // Optional: only the playback service labels listens; other hosts and test fakes ignore it.
    }

    /** A play request arrived with nothing loaded, before its items are resolved. */
    fun notePlaybackResumptionRequested() {
        // Optional: only the playback service times the wait before sound; other hosts ignore it.
    }

    suspend fun refillQueue(player: Player)

    fun toAutoPodcast(entity: PodcastEntity): Podcast

    fun getTimeBasedGenres(hour: Int): List<Pair<String, String>>
}
