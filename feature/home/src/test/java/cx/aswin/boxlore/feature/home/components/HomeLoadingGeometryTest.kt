package cx.aswin.boxlore.feature.home.components

import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.FeedPosterSpacing
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HomeLoadingGeometryTest {
    @Test
    fun `mix skeleton reserves the complete loaded rail height`() {
        assertEquals(324.dp, HomeMixLayout.totalHeight)
        assertEquals(176.dp, HomeMixLayout.CardWidth)
        assertEquals(224.dp, HomeMixLayout.CardHeight)
    }

    @Test
    fun `legacy poster geometry retains its fixed three line title foot`() {
        assertEquals(78.dp, FeedPosterSpacing.textFootHeight(3) + HomeFeedSpacing.CardTextPadding * 2)
    }
}
