package cx.aswin.boxlore.feature.info.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EpisodeExpandableTitleTest {
    @Test
    fun `two line show names keep the expansion target on the second line without extra spacing`() {
        val placement = episodeTitleControlPlacement(72, 63, EpisodeTitleLineMetrics(36, 27, 27), 48)
        assertEquals(36, placement.tailY)
        assertEquals(72, placement.height)
        assertEquals(30, placement.controlY)
        assertEquals(63, placement.tailY + 27)
    }

    @Test
    fun `48dp expand target does not create a gap before the third text line`() {
        val placement = episodeTitleControlPlacement(108, 99, EpisodeTitleLineMetrics(36, 27, 27), 48)
        assertEquals(72, placement.tailY)
        assertEquals(108, placement.height)
        assertEquals(66, placement.controlY)
        assertEquals(99, placement.tailY + 27)
    }

    @Test
    fun `different tail font padding still aligns to the original paragraph baseline`() {
        val placement = episodeTitleControlPlacement(120, 110, EpisodeTitleLineMetrics(34, 22, 22), 48)
        assertEquals(110, placement.tailY + 22)
        assertEquals(122, placement.height)
    }

    @Test
    fun `enlarged text can determine the line height instead of the icon target`() {
        val placement = episodeTitleControlPlacement(216, 198, EpisodeTitleLineMetrics(72, 54, 54), 48)
        assertEquals(144, placement.tailY)
        assertEquals(216, placement.height)
        assertEquals(156, placement.controlY)
    }

    @Test
    fun `expanded final text can wrap without overlapping the collapse control`() {
        val placement = episodeTitleControlPlacement(180, 171, EpisodeTitleLineMetrics(72, 27, 63), 48)
        assertEquals(144, placement.tailY)
        assertEquals(216, placement.height)
        assertEquals(174, placement.controlY)
    }
}
