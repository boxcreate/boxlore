package cx.aswin.boxlore.core.designsystem.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EqualHeightPosterGridTest {
    @Test
    fun `packs rows with gap between only`() {
        // Row0 max(100,120)=120; Row1 max(80)=80; +8 gap → 208
        assertEquals(208, equalHeightPosterGridExtent(listOf(100, 120, 80), columns = 2, verticalGap = 8))
    }

    @Test
    fun `even equal heights sum rows and gaps`() {
        // 2 rows of 100 + one gap 16 → 216
        assertEquals(216, equalHeightPosterGridExtent(listOf(100, 100, 100, 100), columns = 2, verticalGap = 16))
    }

    @Test
    fun `empty input is zero height`() {
        assertEquals(0, equalHeightPosterGridExtent(emptyList(), columns = 2, verticalGap = 8))
    }
}
