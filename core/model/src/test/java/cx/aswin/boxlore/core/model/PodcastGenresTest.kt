package cx.aswin.boxlore.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Coverage for [PodcastGenres.canonicalize] — canonical names, aliases, and normalization. */
class PodcastGenresTest {
    @Test
    fun canonicalizeReturnsExactCanonicalName() {
        assertEquals("Technology", PodcastGenres.canonicalize("Technology"))
        assertEquals("Society & Culture", PodcastGenres.canonicalize("Society & Culture"))
    }

    @Test
    fun canonicalizeIsCaseAndWhitespaceInsensitive() {
        assertEquals("Technology", PodcastGenres.canonicalize("  technology  "))
        assertEquals("True Crime", PodcastGenres.canonicalize("TRUE   CRIME"))
    }

    @Test
    fun canonicalizeResolvesAliases() {
        assertEquals("Health", PodcastGenres.canonicalize("Health & Fitness"))
        assertEquals("Health", PodcastGenres.canonicalize("fitness"))
        assertEquals("Society & Culture", PodcastGenres.canonicalize("society"))
        assertEquals("Society & Culture", PodcastGenres.canonicalize("culture"))
        assertEquals("Religion & Spirituality", PodcastGenres.canonicalize("religion"))
        assertEquals("Kids & Family", PodcastGenres.canonicalize("family"))
        assertEquals("TV & Film", PodcastGenres.canonicalize("tv"))
        assertEquals("TV & Film", PodcastGenres.canonicalize("movie"))
        assertEquals("TV & Film", PodcastGenres.canonicalize("movies"))
        assertEquals("TV & Film", PodcastGenres.canonicalize("film"))
        assertEquals("TV & Film", PodcastGenres.canonicalize("films"))
        assertEquals("TV & Film", PodcastGenres.canonicalize("cinema"))
        assertEquals("Technology", PodcastGenres.canonicalize("tech"))
        assertEquals("Technology", PodcastGenres.canonicalize("Technology & Science"))
        assertEquals("Technology", PodcastGenres.canonicalize("software"))
        assertEquals("Technology", PodcastGenres.canonicalize("coding"))
        assertEquals("Comedy", PodcastGenres.canonicalize("comedy"))
        assertEquals("Comedy", PodcastGenres.canonicalize("standup"))
        assertEquals("Comedy", PodcastGenres.canonicalize("funny"))
        assertEquals("True Crime", PodcastGenres.canonicalize("crime"))
        assertEquals("True Crime", PodcastGenres.canonicalize("true crime"))
        assertEquals("Sports", PodcastGenres.canonicalize("sport"))
        assertEquals("Business", PodcastGenres.canonicalize("finance"))
        assertEquals("Business", PodcastGenres.canonicalize("money"))
        assertEquals("Leisure", PodcastGenres.canonicalize("gaming"))
        assertEquals("Government", PodcastGenres.canonicalize("politics"))
    }

    @Test
    fun canonicalizeReturnsNullForUnknownOrBlank() {
        assertNull(PodcastGenres.canonicalize(null))
        assertNull(PodcastGenres.canonicalize(""))
        assertNull(PodcastGenres.canonicalize("   "))
        assertNull(PodcastGenres.canonicalize("Underwater Basket Weaving"))
        assertNull(PodcastGenres.canonicalize("Favorites"))
        assertNull(PodcastGenres.canonicalize("Deep Dives"))
    }

    @Test
    fun allGenresRoundTripThroughCanonicalize() {
        PodcastGenres.all.forEach { genre ->
            assertEquals(genre, PodcastGenres.canonicalize(genre.lowercase()))
        }
    }

    @Test
    fun podcastEffectiveGenreFallsBackToGenreWhenCustomGenreNullOrBlank() {
        val defaultPod = Podcast(
            id = "1",
            title = "Test",
            artist = "Host",
            imageUrl = "",
            genre = "Technology",
        )
        assertEquals("Technology", defaultPod.effectiveGenre)

        val blankCustomPod = defaultPod.copy(customGenre = "   ")
        assertEquals("Technology", blankCustomPod.effectiveGenre)
    }

    @Test
    fun podcastEffectiveGenreUsesCustomGenreWhenPresent() {
        val customPod = Podcast(
            id = "1",
            title = "Test",
            artist = "Host",
            imageUrl = "",
            genre = "Technology",
            customGenre = "Tech Deep Dives",
            customGenreIcon = "code",
        )
        assertEquals("Tech Deep Dives", customPod.effectiveGenre)
        assertEquals("code", customPod.customGenreIcon)
    }

    @Test
    fun podcastRecommendationGenreResolvesCanonicalCustomGenre() {
        val customPod = Podcast(
            id = "1",
            title = "Test",
            artist = "Host",
            imageUrl = "",
            genre = "Comedy",
            customGenre = "movie",
        )
        assertEquals("TV & Film", customPod.recommendationGenre)
        assertEquals("movie", customPod.effectiveGenre)
    }

    @Test
    fun podcastRecommendationGenreFallsBackToCatalogGenreWhenCustomGenreIsArbitrary() {
        val customPod = Podcast(
            id = "1",
            title = "Test",
            artist = "Host",
            imageUrl = "",
            genre = "Comedy",
            customGenre = "Favorites",
        )
        assertEquals("Comedy", customPod.recommendationGenre)
        assertEquals("Favorites", customPod.effectiveGenre)
    }

    @Test
    fun podcastRecommendationGenreFallsBackToCatalogGenreWhenCustomGenreNullOrBlank() {
        val nullCustomPod = Podcast(
            id = "1",
            title = "Test",
            artist = "Host",
            imageUrl = "",
            genre = "News",
            customGenre = null,
        )
        assertEquals("News", nullCustomPod.recommendationGenre)

        val blankCustomPod = nullCustomPod.copy(customGenre = "   ")
        assertEquals("News", blankCustomPod.recommendationGenre)
    }

    @Test
    fun canonicalizeHandlesExpandedAliases() {
        assertEquals("Fiction", PodcastGenres.canonicalize("sci-fi"))
        assertEquals("Fiction", PodcastGenres.canonicalize("scifi"))
        assertEquals("Fiction", PodcastGenres.canonicalize("sci fi"))
        assertEquals("Fiction", PodcastGenres.canonicalize("science fiction"))
        assertEquals("Fiction", PodcastGenres.canonicalize("drama"))
        assertEquals("True Crime", PodcastGenres.canonicalize("true-crime"))
        assertEquals("Technology", PodcastGenres.canonicalize("developer"))
        assertEquals("Technology", PodcastGenres.canonicalize("programming"))
        assertEquals("Religion & Spirituality", PodcastGenres.canonicalize("Religion"))
        assertEquals("Kids & Family", PodcastGenres.canonicalize("Family"))
        assertEquals("Government", PodcastGenres.canonicalize("Govt"))
        assertEquals("Government", PodcastGenres.canonicalize("law"))
        assertEquals("Health", PodcastGenres.canonicalize("mental health"))
        assertEquals("Sports", PodcastGenres.canonicalize("soccer"))
    }
}
