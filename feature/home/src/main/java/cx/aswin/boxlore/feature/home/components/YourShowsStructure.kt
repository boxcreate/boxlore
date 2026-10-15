package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight

/** Text roles shared by the Daily Mix and selected-show headers, so both modes read the same. */
internal object YourShowsText {
    @Composable fun title(): TextStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = GoogleSansWeight.bold)

    @Composable fun subtitle(): TextStyle = MaterialTheme.typography.bodyMedium
}

/**
 * The header above Daily Mix or a selected show's episodes: a title block that takes the free width
 * and the mode's actions at the end, both centred on one 48dp line.
 */
@Composable
internal fun YourShowsContentHeader(
    modifier: Modifier = Modifier,
    lead: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = HomeMixLayout.HeaderHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.weight(1f)) { lead() }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** Separates the show covers from the content they control. */
@Composable
internal fun YourShowsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = HomeMixLayout.ContentInset).testTag("home_your_shows_divider"),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

private val ListOuterCorner = 20.dp
private val ListInnerCorner = 6.dp

/** Connected list segments: round outer corners on the first and last rows, tight joins between. */
internal fun yourShowsListShape(index: Int, count: Int): Shape {
    val top = if (index == 0) ListOuterCorner else ListInnerCorner
    val bottom = if (index == count - 1) ListOuterCorner else ListInnerCorner
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}
