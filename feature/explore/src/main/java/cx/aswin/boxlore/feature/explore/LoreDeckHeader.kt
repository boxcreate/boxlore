package cx.aswin.boxlore.feature.explore

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth

/**
 * The Lore logo with the History button beside it. Lore draws it once above every state (loading,
 * cards, caught up, failed), so it never moves when the state changes and history is always a tap away.
 */
@Composable
internal fun LoreDeckHeader(
    accentColor: State<Color>,
    onNavigateToHistory: () -> Unit,
    compactSpacing: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .testTag(LORE_DECK_HEADER_TAG)
            .boxloreCardWidth(BoxloreCardFamily.Reading, maximumWidthDp = 560f)
            // boxloreCardWidth leaves compact windows alone; a sideways phone still caps here.
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = if (compactSpacing) 0.dp else 4.dp),
    ) {
        LoreLogo(accentColor = accentColor, modifier = Modifier.height(34.dp).align(Alignment.Center))
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 2.dp,
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            IconButton(onClick = onNavigateToHistory) {
                Icon(
                    imageVector = Icons.Rounded.History,
                    contentDescription = stringResource(R.string.lore_history),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
internal fun LoreLogo(accentColor: State<Color>, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(id = cx.aswin.boxlore.core.designsystem.R.drawable.logo_lore),
        contentDescription = stringResource(R.string.lore_name),
        colorFilter = ColorFilter.tint(accentColor.value),
        modifier = modifier,
    )
}

internal const val LORE_DECK_HEADER_TAG = "lore_deck_header"
