package cx.aswin.boxlore.feature.widgets

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NowPlayingWidgetDependenciesHolderTest {
    @AfterEach
    fun tearDown() {
        NowPlayingWidgetDependenciesHolder.instance = null
    }

    @Test
    fun `require throws when instance is unset`() {
        NowPlayingWidgetDependenciesHolder.instance = null
        val error =
            assertThrows(IllegalStateException::class.java) {
                NowPlayingWidgetDependenciesHolder.require()
            }
        assertTrue(error.message!!.contains("NowPlayingWidgetDependencies not installed"))
    }
}
