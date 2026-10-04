package cx.aswin.boxlore.feature.home.components

import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.FeedPosterSpacing
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HomeLoadingGeometryTest {
    @Test
    fun `mix skeleton reserves the complete loaded rail height`() {
        assertEquals(212.dp, HomeMixLayout.totalHeight)
    }

    @Test
    fun `poster skeleton reserves the fixed three line title foot`() {
        assertEquals(78.dp, FeedPosterSpacing.textFootHeight(3) + HomeFeedSpacing.CardTextPadding * 2)
    }
}
