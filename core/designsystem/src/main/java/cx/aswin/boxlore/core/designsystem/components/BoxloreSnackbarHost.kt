package cx.aswin.boxlore.core.designsystem.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.component.adaptivePlayerOverlayOffset
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreMiniPlayerVisible
import cx.aswin.boxlore.core.designsystem.layout.boxloreHorizontalSafePadding
import cx.aswin.boxlore.core.designsystem.layout.boxloreSnackbarBottomPadding

/**
 * boxlore's snackbar: a rounded inverse-surface card at most 480dp wide, centred, that sits just
 * above the bottom bar and mini player (and moves with the mini player as it docks). Place it at the
 * bottom of the screen's full-height container; [extraBottom] lifts it over screen controls such as
 * Play all. Screens keep their own [SnackbarHostState] and messages.
 */
@Composable
fun BoxloreSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    extraBottom: Dp = 0.dp,
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier
            .adaptivePlayerOverlayOffset(LocalBoxloreMiniPlayerVisible.current)
            .fillMaxWidth()
            .boxloreHorizontalSafePadding()
            .padding(start = SNACKBAR_SIDE_MARGIN, end = SNACKBAR_SIDE_MARGIN, bottom = boxloreSnackbarBottomPadding(extraBottom))
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = SNACKBAR_MAX_WIDTH),
    ) { data -> BoxloreSnackbar(data) }
}

@Composable
private fun BoxloreSnackbar(data: SnackbarData) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val measurer = rememberTextMeasurer()
        val typography = MaterialTheme.typography
        val visuals = data.visuals
        val actionOnNewLine = with(LocalDensity.current) {
            val label = visuals.actionLabel ?: return@with false
            val actionWidth = measurer.measure(label, typography.labelLarge).size.width + ACTION_PADDING.roundToPx()
            val dismissWidth = if (visuals.withDismissAction) DISMISS_WIDTH.roundToPx() else 0
            val textWidth = (constraints.maxWidth - TEXT_SIDE_SPACING.roundToPx() - actionWidth - dismissWidth).coerceAtLeast(1)
            measurer.measure(visuals.message, typography.bodyMedium, constraints = Constraints(maxWidth = textWidth)).lineCount > 1
        }
        val colors = MaterialTheme.colorScheme
        Snackbar(
            snackbarData = data,
            modifier = Modifier.fillMaxWidth().testTag(BOXLORE_SNACKBAR_TAG),
            actionOnNewLine = actionOnNewLine,
            shape = MaterialTheme.shapes.large,
            containerColor = colors.inverseSurface,
            contentColor = colors.inverseOnSurface,
            actionColor = colors.inversePrimary,
            actionContentColor = colors.inversePrimary,
            dismissActionContentColor = colors.inverseOnSurface,
        )
    }
}

/** The snackbar card, for tests. */
const val BOXLORE_SNACKBAR_TAG = "boxlore_snackbar"

private val SNACKBAR_MAX_WIDTH = 480.dp
private val SNACKBAR_SIDE_MARGIN = 16.dp

// Material's one-row snackbar: 16dp before the text, 16dp after it, 8dp after the buttons.
private val TEXT_SIDE_SPACING = 40.dp

// A text button's 12dp padding either side of its label.
private val ACTION_PADDING = 24.dp
private val DISMISS_WIDTH = 48.dp
