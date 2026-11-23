package cx.aswin.boxlore.core.designsystem.components

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.boxlorePosterColumns
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
import cx.aswin.boxlore.core.testing.walkWindows
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1000dp-h1000dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EqualHeightPosterGridTest {
    @get:Rule val compose = createComposeRule()

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

    @Test
    fun `turning a phone sideways and back repacks posters in order and keeps each one`() {
        val window = mutableStateOf(BoxloreTestWindows.PHONE)
        val ids = (1..7).toList()
        var created = 0
        compose.setContent {
            val current = window.value
            Box(Modifier.width(current.widthDp.dp)) {
                EqualHeightPosterGrid(columns = boxlorePosterColumns(current, current.widthDp)) {
                    ids.forEach { id ->
                        key(id) {
                            val instance = remember { ++created }
                            Box(Modifier.height(100.dp).testTag("poster-$id")) { Text("$instance") }
                        }
                    }
                }
            }
        }
        val expectedColumns = mapOf(BoxloreTestWindows.PHONE to 2, BoxloreTestWindows.PHONE_LAND to 5)
        compose.walkWindows(window, BoxloreTestWindows.PHONE, BoxloreTestWindows.PHONE_LAND, BoxloreTestWindows.PHONE) { step ->
            val bounds = ids.map { compose.onNodeWithTag("poster-$it").getUnclippedBoundsInRoot() }
            // Reading order follows the ids: row by row, left to right.
            assertEquals(ids, ids.sortedWith(compareBy({ bounds[it - 1].top.value }, { bounds[it - 1].left.value })))
            assertEquals(expectedColumns.getValue(step), bounds.count { it.top == bounds.first().top })
            assertEquals("posters keep their state while columns change", ids.size, created)
        }
    }
}
