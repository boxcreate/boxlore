package cx.aswin.boxlore.analytics

import cx.aswin.boxlore.core.analytics.FeatureAdoption
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FeatureAdoptionWiringTest {
    @Test
    fun `each source fills its own field`() = runTest {
        val adoption =
            FeatureAdoptionWiring
                .adoption(
                    playlistsCount = flowOf(4),
                    hiddenShowsCount = flowOf(2),
                    episodeTitlesOnly = flowOf(true),
                    trimSilence = flowOf(false),
                    showsWithOwnSpeed = flowOf(1),
                ).first()

        assertEquals(FeatureAdoption(playlistsCount = 4, hiddenShowsCount = 2, episodeTitlesOnly = true, trimSilence = false, showsWithOwnSpeed = 1), adoption)
    }
}
