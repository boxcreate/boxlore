package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.catalog.content.ContentDaypart
import cx.aswin.boxlore.core.catalog.content.ContentLayout
import cx.aswin.boxlore.core.catalog.content.ContentRefreshPolicy
import cx.aswin.boxlore.core.ranking.RankingSurface
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Pure coverage for the top-level helpers in `PodcastRepositoryContentMapping.kt`. */
class PodcastRepositoryContentMappingHelpersTest {
    @Test
    fun mapRegionForBriefingCanonicalisesAliases() {
        assertEquals("us", mapRegionForBriefing("US"))
        assertEquals("in", mapRegionForBriefing("ind"))
        assertEquals("gb", mapRegionForBriefing("gb"))
        assertEquals("gb", mapRegionForBriefing(" uk "))
        assertEquals("global", mapRegionForBriefing("fr"))
    }

    @Test
    fun toSemanticFallbackStripsMarkupUrlsAndWhitespace() {
        val input = "<b>Hello</b>   world https://example.com/x more"
        assertEquals("Hello world more", input.toSemanticFallback())
    }

    @Test
    fun toSemanticFallbackReturnsNullForNullOrEmpty() {
        assertNull((null as String?).toSemanticFallback())
        assertNull("   ".toSemanticFallback())
        assertNull("<br/>".toSemanticFallback())
    }

    @Test
    fun toBoundedPositiveIdsFiltersDistinctsAndBounds() {
        val ids = listOf("1", "1", "-5", "abc", "2", "0")
        assertEquals(listOf(1L, 2L), ids.toBoundedPositiveIds())
        assertEquals(listOf(1L), listOf("1", "2", "3").toBoundedPositiveIds(maximum = 1))
    }

    @Test
    fun toBoundedLanguageCodesNormalisesAndDefaults() {
        assertEquals(listOf("en", "fr", "pt"), listOf("EN", " fr ", "pt-BR").toBoundedLanguageCodes())
        assertEquals(listOf("en"), listOf("123", "!!").toBoundedLanguageCodes())
        assertEquals(listOf("en"), emptyList<String>().toBoundedLanguageCodes())
    }

    @Test
    fun resolveContentLanguagesForQueryExpandsIndonesian() {
        assertEquals(
            listOf("en", "id", "in"),
            resolveContentLanguagesForQuery(listOf("id"), "id"),
        )
        assertEquals(
            listOf("en", "zh"),
            resolveContentLanguagesForQuery(listOf("zh"), "sg"),
        )
        assertEquals(
            listOf("en", "hi"),
            resolveContentLanguagesForQuery(null, "in"),
        )
    }

    @Test
    fun stringToRankingSurface() {
        assertEquals(RankingSurface.HOME, "home".toRankingSurface())
        assertEquals(RankingSurface.EXPLORE, "EXPLORE".toRankingSurface())
        assertEquals(RankingSurface.ANDROID_AUTO, "auto".toRankingSurface())
        assertNull("bogus".toRankingSurface())
    }

    @Test
    fun stringToContentDaypart() {
        assertEquals(ContentDaypart.MORNING, "early_morning".toContentDaypart())
        assertEquals(ContentDaypart.MORNING, "commute".toContentDaypart())
        assertEquals(ContentDaypart.AFTERNOON, "afternoon".toContentDaypart())
        assertEquals(ContentDaypart.EVENING, "evening".toContentDaypart())
        assertEquals(ContentDaypart.LATE_NIGHT, "late_night".toContentDaypart())
        assertNull("noon".toContentDaypart())
    }

    @Test
    fun stringToContentLayout() {
        assertEquals(ContentLayout.EPISODE_RAIL, "episode_rail".toContentLayout())
        assertEquals(ContentLayout.PODCAST_RAIL, "podcast_rail".toContentLayout())
        assertEquals(ContentLayout.COMPACT_LIST, "compact_list".toContentLayout())
        assertEquals(ContentLayout.PROTECTED_CARD, "protected_card".toContentLayout())
        assertNull("masonry".toContentLayout())
    }

    @Test
    fun resolveContentLanguagesForQuery_nullAndEmptyListMatch() {
        assertEquals(
            listOf("en", "hi"),
            resolveContentLanguagesForQuery(null, "in"),
        )
        assertEquals(
            listOf("en", "hi"),
            resolveContentLanguagesForQuery(emptyList(), "in"),
        )
    }

    @Test
    fun resolveContentLanguagesForQuery_unmappedCountryDefaultsToUsRecommended() {
        assertEquals(
            listOf("en"),
            resolveContentLanguagesForQuery(null, "zz"),
        )
    }

    @Test
    fun resolveContentLanguagesForQuery_deduplicatesMixedCase() {
        assertEquals(
            listOf("en", "fr", "pt"),
            resolveContentLanguagesForQuery(listOf("EN", " fr ", "pt-BR", "FR"), "fr"),
        )
    }

    @Test
    fun stringToContentRefreshPolicy() {
        assertEquals(ContentRefreshPolicy.SESSION, "session".toContentRefreshPolicy())
        assertEquals(ContentRefreshPolicy.MANUAL, "manual".toContentRefreshPolicy())
        assertEquals(ContentRefreshPolicy.DAYPART, "daypart".toContentRefreshPolicy())
        assertEquals(ContentRefreshPolicy.DAILY, "daily".toContentRefreshPolicy())
        assertNull("hourly".toContentRefreshPolicy())
        assertNull((null as String?).toContentRefreshPolicy())
    }
}
