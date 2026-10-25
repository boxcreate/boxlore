package cx.aswin.boxlore.feature.home

import cx.aswin.boxlore.core.catalog.HomeRecommendations
import cx.aswin.boxlore.core.model.Episode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Picked for you keeps the picks Home restored from its cache when a request fails with nothing to fall back on. */
class HomeRecommendationsApplyTest {
    private val restored = listOf(episode("1", "Restored pick"))
    private val picks = MutableStateFlow(restored)
    private val isFallback = MutableStateFlow(false)
    private val saved = mutableListOf<Pair<List<Episode>, Boolean>>()

    @Test
    fun `a failed request keeps the restored picks and saves nothing`() = runTest {
        val replaced = applyHomeRecommendations(HomeRecommendations(failed = true), picks, isFallback) { list, fallback -> saved += list to fallback }

        assertFalse(replaced)
        assertEquals(restored, picks.value)
        assertFalse(isFallback.value)
        assertTrue(saved.isEmpty())
    }

    @Test
    fun `fresh picks replace the restored ones once per episode and are saved with their fallback flag`() = runTest {
        val fresh = listOf(episode("2", "New"), episode("2", "Same ID"), episode("3", "new "))

        val replaced = applyHomeRecommendations(HomeRecommendations(fresh, isFallback = true), picks, isFallback) { list, fallback -> saved += list to fallback }

        assertTrue(replaced)
        assertEquals(listOf("2"), picks.value.map { it.id })
        assertTrue(isFallback.value)
        assertEquals(listOf(picks.value to true), saved)
    }

    @Test
    fun `an answer with no picks that did not fail clears them`() = runTest {
        applyHomeRecommendations(HomeRecommendations(), picks, isFallback) { list, fallback -> saved += list to fallback }

        assertTrue(picks.value.isEmpty())
        assertEquals(listOf(emptyList<Episode>() to true), saved)
    }

    private fun episode(id: String, title: String) = Episode(id = id, title = title, description = "", audioUrl = "https://cdn.example/$id.mp3")
}
