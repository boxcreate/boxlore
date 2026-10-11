package cx.aswin.boxlore.feature.home.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HeroSpotlightContextTest {
    @Test fun `only exactly two words allow a second genre line`() {
        for (genre in listOf("Education", "Business", "Technology", "Science-fiction", "", "TV & Film", "International History And Current Affairs")) {
            assertEquals(1, heroGenreMaxLines(genre), genre)
        }
        for (genre in listOf("True Crime", "Mental Health", "  True   Crime  ", "True\tCrime")) {
            assertEquals(2, heroGenreMaxLines(genre), genre)
        }
    }

    @Test fun `rank and market come only from the supplied chart label`() {
        assertEquals(HeroSpotlightContext.Rank("1", "India"), heroSpotlightContext("#1 IN INDIA", "Science"))
        assertEquals(HeroSpotlightContext.Rank("2", "US"), heroSpotlightContext(" #2 IN US ", "Business"))
        assertEquals(HeroSpotlightContext.Rank("1", "UK"), heroSpotlightContext("#1 IN UK", "Comedy"))
        assertEquals(HeroSpotlightContext.Rank("1", "Netherlands"), heroSpotlightContext("#1 IN NETHERLANDS", "History"))
    }

    @Test fun `trending preserves the original genre name when it matches the label`() {
        assertEquals(HeroSpotlightContext.Genre("True Crime"), heroSpotlightContext("TRENDING IN TRUE CRIME", "True Crime"))
        assertEquals(HeroSpotlightContext.Genre("TV & Film"), heroSpotlightContext("TRENDING IN TV & FILM", "TV & Film"))
        assertEquals(HeroSpotlightContext.Genre("Technology"), heroSpotlightContext("TRENDING IN TECHNOLOGY", "Business"))
    }

    @Test fun `unknown and incomplete labels stay plain text without an invented rank or genre`() {
        assertEquals(HeroSpotlightContext.General("Trending"), heroSpotlightContext("TRENDING", "Podcast"))
        assertEquals(HeroSpotlightContext.General("Spotlight"), heroSpotlightContext("SPOTLIGHT", "Science"))
        assertEquals(HeroSpotlightContext.General("#0 in us"), heroSpotlightContext("#0 IN US", "Science"))
        assertEquals(HeroSpotlightContext.General("Trending in"), heroSpotlightContext("TRENDING IN ", "Science"))
    }
}
