package cx.aswin.boxlore.feature.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TabletQueueIndexTest {
    @Test
    fun `continuation header never changes the queue index delivered by reordering`() {
        assertEquals(0, queueReorderIndex(1, 1, 4))
        assertEquals(3, queueReorderIndex(4, 1, 4))
        assertEquals(0, queueReorderIndex(0, 1, 4))
        assertEquals(2, queueReorderIndex(2, 0, 4))
        assertEquals(0, queueReorderIndex(0, 1, 0))
    }
}
