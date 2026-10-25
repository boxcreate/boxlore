package cx.aswin.boxlore.core.playback

import android.content.Context
import androidx.room.Room
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.DownloadedEpisodeEntity
import cx.aswin.boxlore.core.database.EpisodeAliasEntity
import cx.aswin.boxlore.core.database.EpisodeAliasProvenance
import cx.aswin.boxlore.core.database.EpisodeAliasState
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.episodeListenerStore
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.ranking.RankingFeedbackRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import org.mockito.Mockito.mock

/**
 * Rows for an episode stored under several IDs (docs/EPISODE_IDS.md §5), shared by the alias
 * playback tests. "A" and "B" are trusted IDs of one episode of [SHOW]; "A" is marked owner.
 */
internal object EpisodeAliasFixtures {
    const val SHOW = "100"
    const val KEY = "guid-a"
    const val RECIPE = 4_503_599_627_370_500L

    /** An in-memory database whose queries run on the calling thread, so test dispatchers see their results. */
    fun directDatabase(context: Context): BoxLoreDatabase = Room
        .inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java)
        .allowMainThreadQueries()
        .setQueryExecutor { it.run() }
        .setTransactionExecutor { it.run() }
        .build()

    suspend fun twoIds(database: BoxLoreDatabase) {
        database.episodeIdentityDao().insertAliasesIfAbsent(listOf(alias("A", owner = true, createdAt = 1L), alias("B", createdAt = 2L)))
    }

    fun alias(id: String, owner: Boolean = false, createdAt: Long = 1L, key: String = KEY,) = EpisodeAliasEntity(
        aliasId = id,
        podcastId = SHOW,
        catalogKey = key,
        recipeId = RECIPE,
        state = EpisodeAliasState.TRUSTED,
        owner = owner,
        provenance = EpisodeAliasProvenance.UPGRADE_CATALOG,
        createdAt = createdAt,
    )

    @Suppress("LongParameterList")
    fun history(
        id: String,
        progress: Long = 0L,
        liked: Boolean = false,
        likedAt: Long = 0L,
        completed: Boolean = false,
        playedAt: Long = 1L,
        dirty: Boolean = true,
        manual: Boolean = false,
    ) = ListeningHistoryEntity(
        episodeId = id,
        podcastId = SHOW,
        episodeTitle = "Episode $id",
        episodeImageUrl = null,
        podcastImageUrl = null,
        episodeAudioUrl = "https://example.com/$id.mp3",
        podcastName = "Show",
        progressMs = progress,
        durationMs = 1_800_000L,
        isCompleted = completed,
        isLiked = liked,
        likedAt = likedAt,
        lastPlayedAt = playedAt,
        isDirty = dirty,
        isManualCompletion = manual,
    )

    fun download(id: String) = DownloadedEpisodeEntity(
        episodeId = id,
        podcastId = SHOW,
        episodeTitle = "Episode $id",
        episodeDescription = null,
        episodeImageUrl = null,
        podcastName = "Show",
        podcastImageUrl = null,
        durationMs = 1_800_000L,
        publishedDate = 1L,
        localFilePath = "",
        downloadId = 0L,
        downloadedAt = 1L,
        sizeBytes = 1L,
        status = DownloadedEpisodeEntity.STATUS_COMPLETED,
    )

    fun episode(id: String) = Episode(
        id = id,
        title = "Episode $id",
        description = "",
        audioUrl = "https://example.com/$id.mp3",
        podcastId = SHOW,
        podcastTitle = "Show",
        duration = 1800,
        publishedDate = 1_700_000_000L,
    )

    fun historyStore(
        context: Context,
        database: BoxLoreDatabase,
        scope: CoroutineScope,
        playerStateFlow: MutableStateFlow<PlayerState> = MutableStateFlow(PlayerState()),
    ): PlaybackHistoryStore = PlaybackHistoryStore(
        player = PlaybackHistoryPlayerDeps(context, scope, playerStateFlow, playerStateFlow),
        data = PlaybackHistoryDataDeps(
            listeningHistoryDao = database.listeningHistoryDao(),
            listeningSessionDao = database.listeningSessionDao(),
            listeningRollupDao = database.listeningRollupDao(),
            listeningInsightsMaintenance = database.listeningInsightsMaintenance(),
            podcastRepository = mock(PodcastRepository::class.java),
            rankingFeedbackRepository = RankingFeedbackRepository.create(null),
            listenerStore = database.episodeListenerStore(),
        ),
    )
}
