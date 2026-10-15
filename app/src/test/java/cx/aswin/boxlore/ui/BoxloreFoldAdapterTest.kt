package cx.aswin.boxlore.ui

import android.app.Application
import android.graphics.Rect
import androidx.window.layout.FoldingFeature
import cx.aswin.boxlore.core.designsystem.layout.BoxloreFoldOrientation
import cx.aswin.boxlore.core.designsystem.layout.BoxloreFoldState
import cx.aswin.boxlore.core.designsystem.layout.BoxloreLayoutBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class BoxloreFoldAdapterTest {
    @Test
    fun `physical hinge bounds convert exactly once from window pixels to dp`() {
        val feature = FakeFold(Rect(0, 820, 1_346, 862), FoldingFeature.State.HALF_OPENED, FoldingFeature.Orientation.HORIZONTAL, true, FoldingFeature.OcclusionType.FULL)
        val fold = boxloreFold(feature, 2f)
        assertEquals(BoxloreFoldState.HalfOpened, fold.state)
        assertEquals(BoxloreFoldOrientation.Horizontal, fold.orientation)
        assertEquals(BoxloreLayoutBounds(0f, 410f, 673f, 431f), fold.bounds)
        assertTrue(fold.isSeparating)
        assertTrue(fold.isOccluding)
    }

    @Test
    fun `flat inner screen retains a non-occluding vertical fold`() {
        val feature = FakeFold(Rect(672, 0, 672, 1_682), FoldingFeature.State.FLAT, FoldingFeature.Orientation.VERTICAL, false, FoldingFeature.OcclusionType.NONE)
        val fold = boxloreFold(feature, 2f)
        assertEquals(BoxloreFoldState.Flat, fold.state)
        assertEquals(BoxloreFoldOrientation.Vertical, fold.orientation)
        assertFalse(fold.isSeparating)
        assertFalse(fold.isOccluding)
    }

    private data class FakeFold(
        override val bounds: Rect,
        override val state: FoldingFeature.State,
        override val orientation: FoldingFeature.Orientation,
        override val isSeparating: Boolean,
        override val occlusionType: FoldingFeature.OcclusionType,
    ) : FoldingFeature
}
