package cx.aswin.boxlore.feature.info.logic

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtworkThemeLogicTest {
    @Test fun episodeArtworkBeatsAnAlreadyCachedShowPalette() = runTest {
        assertEquals(10, loadArtworkSeed(listOf("episode", "show"), { if (it == "show") 20 else null }, { 10 }))
    }

    @Test fun failedOrMonochromeEpisodeArtworkFallsBackToTheShow() = runTest {
        assertEquals(20, loadArtworkSeed(listOf("episode", "show"), { if (it == "show") 20 else null }, { null }))
    }

    @Test fun cachedEpisodePaletteDoesNotRefetchArtworkAndMissingArtHasNoSeed() = runTest {
        assertEquals(10, loadArtworkSeed(listOf("episode", "show"), { 10 }, { error("must not refetch") }))
        assertNull(loadArtworkSeed(emptyList(), { null }, { error("no artwork") }))
        assertNull(loadArtworkSeed(listOf("failed"), { null }, { null }))
    }
}
