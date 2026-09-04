package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.database.DownloadedEpisodeEntity
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.ListeningRollupEntity
import cx.aswin.boxlore.core.database.LocalEpisodeEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LocalCatalogListenerStateReconcilerTest {
    @Test
    fun restoredNegativeHistoryIdMapsByExactEnclosure() {
        val target = localEpisode(id = "59023522163")
        val mappings =
            LocalCatalogListenerRemapLogic.mappings(
                catalogRows = listOf(target),
                references =
                listOf(
                    LocalCatalogListenerReference(
                        episodeId = "-9201381574733086607",
                        enclosureUrl = target.audioUrl,
                        title = target.title,
                        publishedDate = null,
                    ),
                ),
            )

        assertEquals(target.episodeId, mappings.getValue("-9201381574733086607").episodeId)
    }

    @Test
    fun positivePiReferencesAndAmbiguousMatchesAreNotRekeyed() {
        val first = localEpisode(id = "1")
        val second = localEpisode(id = "2")
        val references =
            listOf(
                LocalCatalogListenerReference(
                    episodeId = "99",
                    enclosureUrl = first.audioUrl,
                    title = first.title,
                    publishedDate = first.publishedDate,
                ),
                LocalCatalogListenerReference(
                    episodeId = "-9",
                    enclosureUrl = first.audioUrl,
                    title = first.title,
                    publishedDate = first.publishedDate,
                ),
            )

        val mappings =
            LocalCatalogListenerRemapLogic.mappings(
                catalogRows = listOf(first, second.copy(audioUrl = first.audioUrl)),
                references = references,
            )

        assertTrue(mappings.isEmpty())
    }

    @Test
    fun titleFallbackUsesSecondsAndRejectsThreeDayGap() {
        val target = localEpisode(id = "1", publishedDate = 1_000_000L)
        val mappings =
            LocalCatalogListenerRemapLogic.mappings(
                catalogRows = listOf(target),
                references =
                listOf(
                    LocalCatalogListenerReference(
                        episodeId = "-9",
                        enclosureUrl = null,
                        title = target.title,
                        publishedDate = target.publishedDate + 3L * 24L * 60L * 60L,
                    ),
                ),
            )

        assertTrue(mappings.isEmpty())
    }

    @Test
    fun historyMergePreservesPlaybackAndCompletionState() {
        val old = history(id = "-9", progress = 50L, completed = true, liked = false)
        val existing = history(id = "1", progress = 100L, completed = false, liked = true)

        val merged = mergeHistory(old, existing, localEpisode(id = "1"))

        assertEquals("1", merged.episodeId)
        assertEquals(100L, merged.progressMs)
        assertTrue(merged.isCompleted)
        assertTrue(merged.isLiked)
        assertTrue(merged.isDirty)
        assertEquals(0L, merged.syncedAt)
    }

    @Test
    fun completedLegacyDownloadKeepsItsCachedPathWhenRekeyed() {
        val old = download(id = "-9", status = DownloadedEpisodeEntity.STATUS_COMPLETED)

        val merged = mergeDownload(old, existing = null, localEpisode(id = "1"))

        assertEquals("1", merged.episodeId)
        assertEquals(DownloadedEpisodeEntity.STATUS_COMPLETED, merged.status)
        assertEquals(old.localFilePath, merged.localFilePath)
    }

    @Test
    fun rollupMergePreservesBothIdentitiesTotals() {
        val old = rollup(id = "-9", consumed = 10L, sessions = 1)
        val existing = rollup(id = "1", consumed = 20L, sessions = 2)

        val merged = mergeRollup(old, existing, newEpisodeId = "1")

        assertEquals("1", merged.episodeId)
        assertEquals(30L, merged.consumedMs)
        assertEquals(3, merged.sessionCount)
    }

    private fun localEpisode(id: String, publishedDate: Long = 1_000L,) = LocalEpisodeEntity(
        episodeId = id,
        podcastId = "9926",
        guid = "guid-$id",
        title = "The same episode",
        description = "Description",
        audioUrl = "https://cdn.example.com/$id.mp3",
        imageUrl = "https://images.example.com/$id.jpg",
        duration = 60,
        publishedDate = publishedDate,
        chaptersUrl = null,
        transcriptUrl = null,
        transcripts = null,
        persons = null,
        seasonNumber = null,
        episodeNumber = null,
        episodeType = null,
        enclosureType = "audio/mpeg",
    )

    private fun history(id: String, progress: Long, completed: Boolean, liked: Boolean,) = ListeningHistoryEntity(
        episodeId = id,
        podcastId = "9926",
        episodeTitle = "Old title",
        episodeImageUrl = null,
        podcastImageUrl = null,
        episodeAudioUrl = "https://cdn.example.com/1.mp3",
        podcastName = "Show",
        progressMs = progress,
        durationMs = 100L,
        isCompleted = completed,
        isLiked = liked,
        lastPlayedAt = progress,
    )

    private fun download(id: String, status: Int,) = DownloadedEpisodeEntity(
        episodeId = id,
        podcastId = "9926",
        episodeTitle = "Old title",
        episodeDescription = null,
        episodeImageUrl = null,
        podcastName = "Show",
        podcastImageUrl = null,
        durationMs = 1L,
        publishedDate = 1L,
        localFilePath = "/downloads/$id",
        downloadId = id.hashCode().toLong(),
        downloadedAt = 1L,
        sizeBytes = 1L,
        status = status,
    )

    private fun rollup(id: String, consumed: Long, sessions: Int,) = ListeningRollupEntity(
        localDay = 1L,
        episodeId = id,
        podcastId = "9926",
        consumedMs = consumed,
        sessionCount = sessions,
        completionCount = 0,
        lastListenedAt = consumed,
    )
}
