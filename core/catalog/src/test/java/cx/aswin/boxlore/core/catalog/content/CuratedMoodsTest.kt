package cx.aswin.boxlore.core.catalog.content

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CuratedMoodsTest {
    @Test
    fun `catalog has twelve moods three per daypart without vibe wording`() {
        assertEquals(12, CuratedMoods.all.size)
        ContentDaypart.entries.forEach { daypart ->
            assertEquals(3, CuratedMoods.forDaypart(daypart).size)
        }
        assertFalse(
            CuratedMoods.all.any { mood ->
                mood.title.contains("vibe", ignoreCase = true) ||
                    mood.subtitle.contains("vibe", ignoreCase = true)
            },
        )
    }

    @Test
    fun `hour ordering puts current daypart first`() {
        assertEquals("morning_news", CuratedMoods.forHourOfDay(8).first().id)
        assertEquals("true_crime_sleep", CuratedMoods.forHourOfDay(1).first().id)
        assertEquals("comedy_gold", CuratedMoods.forHourOfDay(19).first().id)
    }

    @Test
    fun `titleForId resolves canonical home titles`() {
        assertEquals("What's happening", CuratedMoods.titleForId("morning_news"))
        assertEquals("A good laugh", CuratedMoods.titleForId("comedy_gold"))
        assertTrue(CuratedMoods.titleForId("missing") == null)
    }
}
