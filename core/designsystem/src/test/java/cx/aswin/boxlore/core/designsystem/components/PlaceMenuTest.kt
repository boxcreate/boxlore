package cx.aswin.boxlore.core.designsystem.components

import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlaceMenuTest {
    @Test
    fun `the menu sits centred under the pill, whichever of the two is wider`() {
        // A pill narrower than the menu: the menu starts to the left of it by half the difference.
        assertEquals((-45).dp, PlaceMenu.startOffset(150.dp))
        // The minimal player's small control is narrower still.
        assertEquals((-96).dp, PlaceMenu.startOffset(48.dp))
        assertEquals(0.dp, PlaceMenu.startOffset(PlaceMenu.Width))
        assertEquals(10.dp, PlaceMenu.startOffset(260.dp))
    }
}
