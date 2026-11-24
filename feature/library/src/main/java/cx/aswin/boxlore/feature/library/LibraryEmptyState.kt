package cx.aswin.boxlore.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.BoxloreScrollableCenteredColumn
import cx.aswin.boxlore.core.designsystem.layout.boxloreContentBottomPadding
import cx.aswin.boxlore.core.designsystem.layout.boxloreIsShortPane
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight

/** What an empty Library page shows: its section mark, a title, a line of text and one action. */
internal data class LibraryEmptyContent(
    val title: String,
    val body: String,
    val icon: ImageVector,
    val shape: Shape,
    val actionText: String? = null,
    /** A quieter tonal button, when the page's own action isn't the main way forward. */
    val secondaryAction: Boolean = false,
)

/**
 * An empty Library page: a medallion in the section's shape and colour with its icon, a short title
 * and text, and a standard button, centred in the space above [bottomPadding] (the bar and mini
 * player). In a short window the medallion shrinks and the page scrolls, so the button is never
 * under the bar.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun LibraryEmptyState(
    content: LibraryEmptyContent,
    containerColor: Color,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = libraryEmptyBottomPadding(),
    contentColor: Color = contentColorFor(containerColor),
) {
    val short = boxloreIsShortPane()
    BoxloreScrollableCenteredColumn(
        modifier = modifier.fillMaxSize().testTag(LIBRARY_EMPTY_TAG),
        contentPadding = PaddingValues(start = 32.dp, end = 32.dp, top = 24.dp, bottom = 24.dp + bottomPadding),
    ) {
        Box(
            modifier = Modifier
                .size(if (short) 64.dp else 96.dp)
                .clip(content.shape)
                .background(containerColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(content.icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(if (short) 28.dp else 40.dp).testTag(LIBRARY_EMPTY_ICON_TAG))
        }
        Spacer(Modifier.height(if (short) 12.dp else 20.dp))
        Text(
            text = content.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = GoogleSansWeight.semiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = TextMaxWidth),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = content.body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = TextMaxWidth),
        )
        val action = content.actionText
        if (action != null) {
            Spacer(Modifier.height(24.dp))
            val buttonModifier = Modifier.heightIn(min = 48.dp)
            if (content.secondaryAction) {
                FilledTonalButton(onClick = onAction, shapes = ButtonDefaults.shapes(), modifier = buttonModifier) { Text(action) }
            } else {
                Button(onClick = onAction, shapes = ButtonDefaults.shapes(), modifier = buttonModifier) { Text(action) }
            }
        }
    }
}

private val TextMaxWidth = 360.dp

/** Room under an empty page: the bar and mini player on phones, the mini player on tablets. */
@Composable
internal fun libraryEmptyBottomPadding(): Dp = boxloreContentBottomPadding(PHONE_EMPTY_BOTTOM)

private val PHONE_EMPTY_BOTTOM = 150.dp

/** Test tags for an empty Library page and its section icon. */
internal const val LIBRARY_EMPTY_TAG = "library_empty_state"
internal const val LIBRARY_EMPTY_ICON_TAG = "library_empty_icon"
