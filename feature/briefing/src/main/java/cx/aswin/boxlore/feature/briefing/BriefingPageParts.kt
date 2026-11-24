package cx.aswin.boxlore.feature.briefing

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.R
import cx.aswin.boxlore.core.designsystem.briefing.BRIEFING_COVER_ASPECT_RATIO
import cx.aswin.boxlore.core.designsystem.briefing.BriefingRegionMenu
import cx.aswin.boxlore.core.designsystem.components.BoxloreScrollableCenteredColumn
import cx.aswin.boxlore.core.designsystem.layout.boxloreContentHeightDp
import cx.aswin.boxlore.core.designsystem.layout.boxloreHorizontalSafeInsetsDp
import cx.aswin.boxlore.core.designsystem.layout.boxloreHorizontalSafePadding
import cx.aswin.boxlore.core.designsystem.layout.boxloreIsShortPane

/**
 * The page cover's size: the frame's width at 1.6:1, except in short panes (a sideways phone, a split
 * half, a tabletop's lower half), where it is at most 42% of the height so the headline and Listen
 * show on the first screen. The art is then cropped ([briefingCoverScale]).
 */
@Composable
internal fun Modifier.briefingPageCoverSize(): Modifier {
    val maxHeight = if (boxloreIsShortPane()) boxloreContentHeightDp() * SHORT_PANE_COVER_SHARE else Float.MAX_VALUE
    return fillMaxWidth().layout { measurable, constraints ->
        val width = constraints.maxWidth
        val height = minOf(width / BRIEFING_COVER_ASPECT_RATIO, maxHeight * density).toInt()
        val placeable = measurable.measure(Constraints.fixed(width, height))
        layout(width, height) { placeable.place(0, 0) }
    }
}

/** Fit (uncropped) art, or cropped in short panes where the cover is shorter than 1.6:1. */
@Composable
internal fun briefingCoverScale(): ContentScale = if (boxloreIsShortPane()) ContentScale.Crop else ContentScale.Fit

private const val SHORT_PANE_COVER_SHARE = 0.42f

/** The page's top padding, its bottom chrome clearance and how far the cover's logo moves right for the top-left button. */
internal data class BriefingPagePadding(val top: Dp, val bottom: Dp, val mastheadLeft: Dp)

/**
 * How far the cover's logo moves right: past the 48dp button at the cover's top left (Back, or
 * Preferences in right-to-left), and past a cutout or side buttons on that side.
 */
@Composable
internal fun briefingMastheadLeftPadding(): Dp {
    val (start, end) = boxloreHorizontalSafeInsetsDp()
    val left = if (LocalLayoutDirection.current == LayoutDirection.Ltr) start else end
    return left + BRIEFING_TOP_BUTTON_SIZE + 8.dp
}

private val BRIEFING_TOP_BUTTON_SIZE = 48.dp

/** A round tonal 48dp button over the cover, with the usual press feedback. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun BriefingTopButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier.size(BRIEFING_TOP_BUTTON_SIZE),
        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = MaterialTheme.colorScheme.onSurface),
        shapes = IconButtonDefaults.shapes(),
    ) {
        Icon(icon, description)
    }
}

/**
 * An edition that failed to load: the message centred with Try again and the edition menu below. It
 * clears the floating buttons at the top and the mini player at the bottom, and scrolls in short windows.
 */
@Composable
internal fun BriefingErrorContent(state: BriefingUiState.Error, topPadding: Dp, bottomContentPadding: Dp, onRegionSelect: (String) -> Unit) {
    BoxloreScrollableCenteredColumn(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)).boxloreHorizontalSafePadding().testTag("briefingError"),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = topPadding + 72.dp, bottom = bottomContentPadding + 24.dp),
    ) {
        Text(state.message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        // The page's one way forward, filled; the edition menu below stays tonal.
        Button(onClick = { onRegionSelect(state.selectedRegion) }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.briefing_try_again))
        }
        Spacer(Modifier.height(12.dp))
        BriefingRegionMenu(state.selectedRegion, onRegionSelect)
    }
}
