package cx.aswin.boxlore.feature.library

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.library.subscriptions.episodeMetaDurationLabel
import cx.aswin.boxlore.feature.library.subscriptions.extractDistinctGenres
import cx.aswin.boxlore.feature.library.subscriptions.filterPodcastsByGenre
import cx.aswin.boxlore.feature.library.subscriptions.formatRelativeUpdateLabel
import cx.aswin.boxlore.feature.library.subscriptions.getChronologicalHeader
import cx.aswin.boxlore.feature.library.subscriptions.latestSortLabel
import cx.aswin.boxlore.feature.library.subscriptions.resolveSubscriptionGenreItem
import cx.aswin.boxlore.feature.library.subscriptions.showsSortLabel
import java.util.Calendar
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SubscriptionFilterLogicTest {

    private fun podcast(
        id: String,
        title: String = id,
        genre: String = "",
        publishedSeconds: Long = 0L,
        customGenre: String? = null,
        customGenreIcon: String? = null,
    ): Podcast {
        val episode = if (publishedSeconds > 0L) {
            Episode(
                id = "${id}_ep",
                title = "Latest",
                description = "",
                audioUrl = "https://example.com/a.mp3",
                imageUrl = null,
                publishedDate = publishedSeconds,
                duration = 600,
                podcastId = id,
            )
        } else {
            null
        }
        return Podcast(
            id = id,
            title = title,
            artist = "Host",
            description = "",
            imageUrl = "",
            feedUrl = "https://example.com/feed",
            genre = genre,
            latestEpisode = episode,
            customGenre = customGenre,
            customGenreIcon = customGenreIcon,
        )
    }

    @Test
    fun extractDistinctGenres_titleCasesAndDropsPodcast() {
        val genres = extractDistinctGenres(
            listOf(
                podcast("1", genre = "news, Technology"),
                podcast("2", genre = "podcast, comedy"),
                podcast("3", genre = "News"),
            )
        )
        assertEquals(listOf("Comedy", "News", "Technology"), genres)
    }

    @Test
    fun extractDistinctGenres_usesCustomGenreWhenPresent() {
        val genres = extractDistinctGenres(
            listOf(
                podcast("1", genre = "News", customGenre = "Deep Dives"),
                podcast("2", genre = "Technology", customGenre = "news"),
            )
        )
        assertEquals(listOf("Deep Dives", "News"), genres)
    }

    @Test
    fun extractDistinctGenres_prioritizesCustomTagsByFrequencyThenCatalogGenresAZ() {
        val podcasts = listOf(
            podcast("1", genre = "Comedy", customGenre = "Tech"),
            podcast("2", genre = "Society", customGenre = "Alpha"),
            podcast("3", genre = "Music", customGenre = "Tech"),
            podcast("4", genre = "News"),
            podcast("5", genre = "Arts"),
            podcast("6", genre = "Tech"),
        )
        val genres = extractDistinctGenres(podcasts)
        assertEquals(listOf("Tech", "Alpha", "Arts", "News"), genres)
    }

    @Test
    fun extractDistinctGenres_combinesFrequencyForMixedCaseCustomTags() {
        val podcasts = listOf(
            podcast("1", customGenre = "rock"),
            podcast("2", customGenre = "ROCK"),
            podcast("3", customGenre = "Pop"),
        )
        val genres = extractDistinctGenres(podcasts)
        assertEquals(listOf("Rock", "Pop"), genres)
    }

    @Test
    fun filterPodcastsByGenre_allReturnsInput() {
        val podcasts = listOf(podcast("1", genre = "News"), podcast("2", genre = "Comedy"))
        assertEquals(podcasts, filterPodcastsByGenre(podcasts, "All"))
    }

    @Test
    fun filterPodcastsByGenre_matchesCaseInsensitive() {
        val podcasts = listOf(
            podcast("1", genre = "News, Politics"),
            podcast("2", genre = "Comedy"),
        )
        assertEquals(listOf("1"), filterPodcastsByGenre(podcasts, "news").map { it.id })
    }

    @Test
    fun filterPodcastsByGenre_matchesCatalogLabelAndValue() {
        val podcasts = listOf(
            podcast("1", genre = "Technology"),
            podcast("2", genre = "Comedy"),
        )
        assertEquals(listOf("1"), filterPodcastsByGenre(podcasts, "Tech").map { it.id })
    }

    @Test
    fun filterPodcastsByGenre_matchesCustomGenreOverride() {
        val podcasts = listOf(
            podcast("1", genre = "News", customGenre = "Deep Dives"),
            podcast("2", genre = "Comedy", customGenre = "Tech"),
        )
        assertEquals(listOf("1"), filterPodcastsByGenre(podcasts, "Deep Dives").map { it.id })
        assertEquals(listOf("2"), filterPodcastsByGenre(podcasts, "Technology").map { it.id })
        assertTrue(filterPodcastsByGenre(podcasts, "News").isEmpty())
    }

    @Test
    fun resolveSubscriptionGenreItem_mapsExploreShortLabels() {
        val tech = resolveSubscriptionGenreItem("Technology")
        assertEquals("Tech", tech.label)
        assertEquals("Technology", tech.value)
    }

    @Test
    fun resolveSubscriptionGenreItem_resolvesCustomIconFromPodcasts() {
        val podcasts = listOf(
            podcast("1", genre = "News", customGenre = "Coding", customGenreIcon = "code")
        )
        val item = resolveSubscriptionGenreItem("Coding", podcasts)
        assertEquals("Coding", item.label)
        assertEquals("Coding", item.value)
        assertEquals(cx.aswin.boxlore.core.designsystem.icon.GenreIcons.findIcon("code"), item.icon)
    }

    @Test
    fun resolveSubscriptionGenreItem_resolvesCustomIconFromDirectKey() {
        val item = resolveSubscriptionGenreItem("Music", customIconKey = "headphones")
        assertEquals("Music", item.label)
        assertEquals(cx.aswin.boxlore.core.designsystem.icon.GenreIcons.findIcon("headphones"), item.icon)
    }

    @Test
    fun resolveSubscriptionGenreItems_dedupeCollidingLabelAndValue() {
        val raw = listOf("Technology", "Tech", "Society & Culture", "Society")
        val resolved =
            raw
                .map { resolveSubscriptionGenreItem(it) }
                .distinctBy { it.value.lowercase(java.util.Locale.ROOT) }
        assertEquals(2, resolved.size)
        assertEquals(setOf("Technology", "Society & Culture"), resolved.map { it.value }.toSet())
    }

    @Test
    fun resolveSubscriptionGenreItems_dedupesWithCustomIconPresent() {
        val podcasts = listOf(
            podcast("1", genre = "Technology", customGenre = "Tech", customGenreIcon = "tech"),
            podcast("2", genre = "Technology"),
        )
        val raw = listOf("Technology", "Tech")
        val resolved = raw
            .map { resolveSubscriptionGenreItem(it, podcasts) }
            .distinctBy { it.value.lowercase(java.util.Locale.ROOT) }
        assertEquals(1, resolved.size)
        assertEquals("Technology", resolved.first().value)
        assertEquals(cx.aswin.boxlore.core.designsystem.icon.GenreIcons.findIcon("tech"), resolved.first().icon)
    }

    @Test
    fun formatRelativeUpdateLabel_nullForMissing() {
        assertNull(formatRelativeUpdateLabel(0L))
        assertEquals("today", formatRelativeUpdateLabel(System.currentTimeMillis() / 1000L))
    }

    @Test
    fun formatRelativeUpdateLabel_includesYearWhenOlderThanMonth() {
        val cal = Calendar.getInstance()
        cal.add(Calendar.YEAR, -1)
        val label = formatRelativeUpdateLabel(cal.timeInMillis / 1000L)!!
        assertTrue(label.contains(cal.get(Calendar.YEAR).toString()))
        assertFalse(label.startsWith("Updated"))
    }

    @Test
    fun showsSortLabel_coversAllModes() {
        assertEquals("Smart", showsSortLabel(SubscriptionSort.SmartRank))
        assertEquals("Updated", showsSortLabel(SubscriptionSort.RecentlyUpdated))
        assertEquals("A–Z", showsSortLabel(SubscriptionSort.Alphabetical))
        assertEquals("Listened", showsSortLabel(SubscriptionSort.MostListened))
        assertEquals("Manual", showsSortLabel(SubscriptionSort.Manual))
    }

    @Test
    fun latestSortLabel_reflectsMode() {
        assertEquals("Smart", latestSortLabel(useSmartRank = true))
        assertEquals("Chronological", latestSortLabel(useSmartRank = false))
    }

    @Test
    fun getChronologicalHeader_todayAndYesterday() {
        val now = Calendar.getInstance()
        now.set(Calendar.HOUR_OF_DAY, 12)
        now.set(Calendar.MINUTE, 0)
        now.set(Calendar.SECOND, 0)
        now.set(Calendar.MILLISECOND, 0)
        val todaySeconds = now.timeInMillis / 1000L
        val yesterdaySeconds = todaySeconds - TimeUnit.DAYS.toSeconds(1)
        assertEquals("Today", getChronologicalHeader(todaySeconds))
        assertEquals("Yesterday", getChronologicalHeader(yesterdaySeconds))
        assertEquals("Older", getChronologicalHeader(0L))
    }

    @Test
    fun episodeMetaDurationLabel_formatsElapsedAndRemaining() {
        val hourPlus =
            Episode(
                id = "e",
                title = "T",
                description = "",
                audioUrl = "https://example.com/a.mp3",
                imageUrl = null,
                publishedDate = 1L,
                duration = 3660,
                podcastId = "p",
            )
        assertEquals("1h 1m", episodeMetaDurationLabel(hourPlus, isInProgress = false, progress = 0f))
        assertEquals("45m", episodeMetaDurationLabel(hourPlus.copy(duration = 2700), isInProgress = false, progress = 0f))
        assertEquals(
            "1h 0m left",
            episodeMetaDurationLabel(hourPlus.copy(duration = 7200), isInProgress = true, progress = 0.5f),
        )
        assertEquals(
            "15m left",
            episodeMetaDurationLabel(hourPlus.copy(duration = 1800), isInProgress = true, progress = 0.5f),
        )
    }
}
