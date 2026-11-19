package cx.aswin.boxlore.backup

import android.content.Context
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.catalog.SharedAppDependenciesHolder
import cx.aswin.boxlore.core.catalog.SubscriptionRepository
import cx.aswin.boxlore.core.catalog.backup.LibraryBackupManager
import cx.aswin.boxlore.core.catalog.ports.ListeningHistoryBackupPort
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import cx.aswin.boxlore.core.ranking.AdaptiveRankingRepository
import cx.aswin.boxlore.core.rss.RssPodcastRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * The full JSON library backup, built the same way for Settings' export and the automatic backup, so both
 * write the same file. Settings are always included ([userPrefs] is required).
 */
internal object LibraryJsonBackup {
    /** One backup's bytes and how many shows it holds (for analytics). */
    class Export(val bytes: ByteArray, val subscriptionCount: Int)

    fun manager(
        subscriptions: SubscriptionRepository,
        history: ListeningHistoryBackupPort,
        podcasts: PodcastRepository,
        userPrefs: UserPreferencesRepository,
        context: Context,
        ranking: AdaptiveRankingRepository = SharedAppDependenciesHolder.require().adaptiveRankingRepository,
        rss: RssPodcastRepository = SharedAppDependenciesHolder.require().rssPodcastRepository,
    ): LibraryBackupManager = LibraryBackupManager(
        subscriptionRepository = subscriptions,
        listeningHistory = history,
        podcastRepository = podcasts,
        userPrefs = userPrefs,
        context = context,
        adaptiveRankingRepository = ranking,
        rssPodcastRepository = rss,
    )

    suspend fun bytes(manager: LibraryBackupManager): ByteArray = manager.exportLibraryAsJson().toByteArray(Charsets.UTF_8)

    /** For background work: history comes straight from Room, so no second `PlaybackRepository` is built. */
    suspend fun fromSharedGraph(context: Context): Export {
        val shared = SharedAppDependenciesHolder.require()
        val manager = manager(
            subscriptions = shared.subscriptionRepository,
            history = RoomListeningHistoryReader(shared.database),
            podcasts = shared.podcastRepository,
            userPrefs = shared.userPreferencesRepository,
            context = context,
            ranking = shared.adaptiveRankingRepository,
            rss = shared.rssPodcastRepository,
        )
        val count = shared.subscriptionRepository.getAllSubscribedPodcasts().first().size
        return Export(bytes(manager), count)
    }
}

/** Reads listening history from Room for a backup; it never writes. */
internal class RoomListeningHistoryReader(private val database: BoxLoreDatabase) : ListeningHistoryBackupPort {
    override fun getRecentHistory(): Flow<List<ListeningHistoryEntity>> = database.listeningHistoryDao().getRecentHistory()

    override suspend fun exportStoredHistory(): List<ListeningHistoryEntity> = database.listeningHistoryDao().getAllStoredHistory()

    override suspend fun upsertHistoryEntity(entity: ListeningHistoryEntity): Unit =
        throw UnsupportedOperationException("Backups only read listening history")

    override suspend fun markAllEpisodesCompleted(episodes: List<Episode>, podcastId: String, podcastTitle: String, podcastImageUrl: String?): Unit =
        throw UnsupportedOperationException("Backups only read listening history")
}
