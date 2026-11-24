package cx.aswin.boxlore.feature.explore.logic

import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ExploreListRowLogicTest {
    @Test
    fun `Find a show columns follow the shared width classes on both sides of each gate`() {
        assertEquals(1, ExploreListRowLogic.showResultColumns(BoxloreWindowLayout(599f, 900f)))
        assertEquals(2, ExploreListRowLogic.showResultColumns(BoxloreWindowLayout(600f, 900f)))
        assertEquals(2, ExploreListRowLogic.showResultColumns(BoxloreWindowLayout(839f, 900f)))
        assertEquals(3, ExploreListRowLogic.showResultColumns(BoxloreWindowLayout(840f, 900f)))
        // The height gate doesn't change the count: a sideways phone lists results several across.
        assertEquals(3, ExploreListRowLogic.showResultColumns(BoxloreWindowLayout(900f, 479f)))
        assertEquals(3, ExploreListRowLogic.showResultColumns(BoxloreWindowLayout(900f, 480f)))
    }

    @Test
    fun `tablet chart rows take two columns on medium widths and three from expanded`() {
        assertEquals(2, ExploreListRowLogic.chartColumns(BoxloreWindowLayout(839f, 900f)))
        assertEquals(3, ExploreListRowLogic.chartColumns(BoxloreWindowLayout(840f, 900f)))
    }
}
