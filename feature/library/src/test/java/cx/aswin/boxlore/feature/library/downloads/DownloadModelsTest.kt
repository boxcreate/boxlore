package cx.aswin.boxlore.feature.library.downloads

import cx.aswin.boxlore.core.database.DownloadedEpisodeEntity
import cx.aswin.boxlore.core.model.text.UiText
import cx.aswin.boxlore.feature.library.R
import java.util.Locale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DownloadModelsTest {
    private fun entity(
        episodeId: String = "ep-1",
        podcastId: String = "pod-1",
        durationMs: Long = 120_000L,
    ): DownloadedEpisodeEntity = DownloadedEpisodeEntity(
        episodeId = episodeId,
        podcastId = podcastId,
        episodeTitle = "Episode Title",
        episodeDescription = "Episode description",
        episodeImageUrl = "https://example.com/ep.jpg",
        podcastName = "Podcast Name",
        podcastImageUrl = "https://example.com/pod.jpg",
        durationMs = durationMs,
        publishedDate = 42L,
        localFilePath = "/downloads/ep-1.mp3",
        downloadId = 7L,
        downloadedAt = 1_000L,
        sizeBytes = 2_048L,
    )

    @Test
    fun toEpisodeMapsLocalFileAsAudioAndDurationInSeconds() {
        val episode = entity(durationMs = 180_000L).toEpisode()
        assertEquals("ep-1", episode.id)
        assertEquals("Episode Title", episode.title)
        assertEquals("/downloads/ep-1.mp3", episode.audioUrl)
        assertEquals(180, episode.duration)
        assertEquals(42L, episode.publishedDate)
        assertEquals("https://example.com/pod.jpg", episode.podcastImageUrl)
    }

    @Test
    fun toEpisodeKeepsItsShow() {
        // Regression: a downloaded episode queued while another show played took that show's name.
        val episode = entity().toEpisode()
        assertEquals("pod-1", episode.podcastId)
        assertEquals("Podcast Name", episode.podcastTitle)

        val showless = entity(podcastId = "").copy(podcastName = "").toEpisode()
        assertEquals(null, showless.podcastId)
        assertEquals(null, showless.podcastTitle)
    }

    @Test
    fun toEpisodeDefaultsMissingDescriptionToEmpty() {
        val episode = entity().copy(episodeDescription = null).toEpisode()
        assertEquals("", episode.description)
    }

    @Test
    fun toPodcastMapsIdentityFields() {
        val podcast = entity().toPodcast()
        assertEquals("pod-1", podcast.id)
        assertEquals("Podcast Name", podcast.title)
        assertEquals("https://example.com/pod.jpg", podcast.imageUrl)
        assertEquals("", podcast.artist)
    }

    @Test
    fun toPodcastDefaultsNullImageToEmpty() {
        val podcast = entity().copy(podcastImageUrl = null).toPodcast()
        assertEquals("", podcast.imageUrl)
    }

    @Test
    fun formatSizeCoversAllUnitBuckets() {
        assertEquals(UiText.res(R.string.library_size_bytes, "0"), formatSize(0L, Locale.US))
        assertEquals(UiText.res(R.string.library_size_bytes, "0"), formatSize(-5L, Locale.US))
        assertEquals(UiText.res(R.string.library_size_bytes, "512.0"), formatSize(512L, Locale.US))
        assertEquals(UiText.res(R.string.library_size_kilobytes, "1.0"), formatSize(1024L, Locale.US))
        assertEquals(UiText.res(R.string.library_size_megabytes, "1.0"), formatSize(1024L * 1024L, Locale.US))
        assertEquals(UiText.res(R.string.library_size_gigabytes, "1.0"), formatSize(1024L * 1024L * 1024L, Locale.US))
        // The number follows the language: German writes a decimal comma.
        assertEquals(UiText.res(R.string.library_size_megabytes, "1,5"), formatSize(1024L * 1536L, Locale.GERMANY))
    }

    @Test
    fun formatRelativeDateIsNullForZeroAndJustNowWhenAhead() {
        val nowMs = System.currentTimeMillis()
        assertNull(formatRelativeDate(0L, Locale.US, nowMs))
        assertEquals(UiText.res(R.string.library_relative_just_now), formatRelativeDate(nowMs / 1000 + 100, Locale.US, nowMs))
    }

    @Test
    fun longPressDownloadSelectionEntersModeWithPressedItem() {
        val next =
            longPressDownloadSelection(
                selectionActive = false,
                selectedIds = emptySet(),
                itemId = "ep-2",
            )
        assertEquals(true, next.active)
        assertEquals(setOf("ep-2"), next.ids)
    }

    @Test
    fun longPressDownloadSelectionTogglesWhenAlreadySelecting() {
        val removed =
            longPressDownloadSelection(
                selectionActive = true,
                selectedIds = setOf("ep-1", "ep-2"),
                itemId = "ep-1",
            )
        assertEquals(true, removed.active)
        assertEquals(setOf("ep-2"), removed.ids)

        val added =
            longPressDownloadSelection(
                selectionActive = true,
                selectedIds = setOf("ep-2"),
                itemId = "ep-1",
            )
        assertEquals(setOf("ep-1", "ep-2"), added.ids)
    }

    @Test
    fun applyLongPressDownloadSelectionMutatesListAndReturnsActive() {
        val selected = mutableListOf("ep-1")
        val active =
            selected.applyLongPressDownloadSelection(
                selectionActive = false,
                itemId = "ep-3",
            )
        assertEquals(true, active)
        assertEquals(setOf("ep-1", "ep-3"), selected.toSet())
    }
}
