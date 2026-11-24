package cx.aswin.boxlore.feature.library

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.Typography
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.lerp
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.component.boxloreLargeTitlePresentation
import cx.aswin.boxlore.core.designsystem.layout.boxloreContentHeightDp
import cx.aswin.boxlore.core.designsystem.layout.boxloreContentWidthDp
import cx.aswin.boxlore.core.designsystem.layout.boxloreIsShortPane
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import kotlinx.coroutines.flow.first

/**
 * The large header of a Library page. It remembers how tall its expanded title needs to be, so a
 * long or translated title that wraps to two lines gets the room for them instead of being cut at
 * the top.
 */
@Stable
internal class LibraryHeaderState {
    /** Height of a two-line expanded title block (title, and its count line), in pixels; 0 for one line. */
    var titleBlockPx by mutableIntStateOf(0)

    /** The bar's expanded height: Material's large bar, or taller when the title block needs it. */
    @Composable
    fun expandedHeight(): Dp {
        val block = with(LocalDensity.current) { titleBlockPx.toDp() }
        return maxOf(TopAppBarDefaults.LargeAppBarExpandedHeight, TopAppBarDefaults.LargeAppBarCollapsedHeight + LargeTitleBottomPadding + block)
    }
}

@Composable
internal fun rememberLibraryHeaderState(): LibraryHeaderState = remember { LibraryHeaderState() }

/**
 * An exit-until-collapsed header that starts as one slim row in a short window (a sideways phone,
 * a split-screen half, the lower half of a half-open fold), so the content shows first. When the
 * window changes to one that isn't short, the header opens back up as soon as the list is at its
 * top (at once if it already is), so a list that now fits never sits under a collapsed title. The
 * same state is kept throughout.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun rememberLibraryHeaderScrollBehavior(listAtTop: () -> Boolean): TopAppBarScrollBehavior {
    val short = boxloreIsShortPane()
    val paneWidth = boxloreContentWidthDp()
    val paneHeight = boxloreContentHeightDp()
    val collapseLimit = with(LocalDensity.current) { (TopAppBarDefaults.LargeAppBarCollapsedHeight - TopAppBarDefaults.LargeAppBarExpandedHeight).toPx() }
    val state = rememberTopAppBarState(initialHeightOffsetLimit = collapseLimit, initialHeightOffset = if (short) collapseLimit else 0f)
    val atTop by rememberUpdatedState(listAtTop)
    LaunchedEffect(short, paneWidth, paneHeight) {
        if (short) {
            // The bar sets its own limit when it lays out (taller for a two-line title).
            snapshotFlow { state.heightOffsetLimit }.collect { limit -> if (limit > -Float.MAX_VALUE) state.heightOffset = limit }
        } else {
            snapshotFlow { atTop() }.first { it }
            state.heightOffset = 0f
        }
    }
    return TopAppBarDefaults.exitUntilCollapsedScrollBehavior(state)
}

/**
 * The bar's colour once content scrolls under it. In a short window the header starts slim with
 * the list at its top, so it keeps the page's surface until the list really scrolls.
 */
@Composable
internal fun libraryHeaderScrolledColor(listAtTop: () -> Boolean, scrolled: Color = MaterialTheme.colorScheme.surfaceContainer): Color {
    val short = boxloreIsShortPane()
    val currentAtTop by rememberUpdatedState(listAtTop)
    val atTop by remember { derivedStateOf { currentAtTop() } }
    return if (short && atTop) MaterialTheme.colorScheme.surface else scrolled
}

/**
 * A Library page's large title: `displayMedium` in bold on regular phones, easing to `titleLarge`
 * as the header collapses. A title that doesn't fit one line steps down a size and wraps to two
 * lines before it ends with "…"; large text and narrow panes start a step smaller ([boxloreLargeTitlePresentation]).
 * [subtitle] is a count line under the expanded title.
 */
@Composable
internal fun LibraryLargeTitle(
    title: String,
    collapsedFraction: Float,
    header: LibraryHeaderState,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    largeStyle: TextStyle = MaterialTheme.typography.displayMedium,
) {
    BoxWithConstraints(modifier) {
        val typography = MaterialTheme.typography
        val measurer = rememberTextMeasurer()
        val fontScale = LocalDensity.current.fontScale
        val paneWidth = boxloreContentWidthDp()
        val maxWidth = constraints.maxWidth
        val expanded = remember(title, subtitle, maxWidth, fontScale, paneWidth, typography, largeStyle) {
            // Regular phones (360dp and wider) keep today's size; narrower panes and large text start a
            // step smaller. Only the size comes from the shared helper: the style keeps [largeStyle]'s
            // font family, so the title stays bold.
            val sized = boxloreLargeTitlePresentation(
                typography,
                0f,
                fontScale = fontScale,
                paneWidthDp = if (paneWidth < PHONE_MIN_WIDTH_DP) paneWidth else Float.POSITIVE_INFINITY,
                largeStyle = largeStyle,
            ).style
            val start = largeStyle.resized(sized).copy(fontWeight = GoogleSansWeight.bold)
            val oneLine = measurer.measure(title, start, maxLines = 1, softWrap = false, constraints = Constraints(maxWidth = maxWidth))
            val style = if (oneLine.hasVisualOverflow) typography.stepDown(start) else start
            val wrapped = measurer.measure(title, style, maxLines = 2, overflow = TextOverflow.Ellipsis, constraints = Constraints(maxWidth = maxWidth))
            // A one-line title keeps Material's bar height, as it always had; two lines get the room they need.
            val block = if (wrapped.lineCount < 2) {
                0
            } else {
                wrapped.size.height + (subtitle?.let { measurer.measure(it, typography.bodyMedium, maxLines = 1, constraints = Constraints(maxWidth = maxWidth)).size.height } ?: 0)
            }
            style to block
        }
        SideEffect { header.titleBlockPx = expanded.second }
        val compact = typography.titleLarge.copy(fontWeight = GoogleSansWeight.semiBold)
        val fraction = collapsedFraction.coerceIn(0f, 1f)
        Column {
            Text(
                text = title,
                style = lerp(expanded.first, compact, fraction),
                maxLines = if (fraction >= 1f) 1 else 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null && fraction < SUBTITLE_HIDES_AT) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** One size down the display and headline scale, in [style]'s own font family (so it stays bold). */
private fun Typography.stepDown(style: TextStyle): TextStyle {
    val ladder = listOf(displayLarge, displayMedium, displaySmall, headlineLarge, headlineMedium, headlineSmall, titleLarge)
    val index = ladder.indexOfFirst { it.fontSize == style.fontSize }
    val next = if (index in 0 until ladder.lastIndex) ladder[index + 1] else style
    return style.resized(next)
}

/** This style at [size]'s font size, line height and letter spacing; everything else, the font family included, stays. */
private fun TextStyle.resized(size: TextStyle): TextStyle =
    copy(fontSize = size.fontSize, lineHeight = size.lineHeight, letterSpacing = size.letterSpacing)

/** Panes narrower than the smallest regular phone start the title a step smaller. */
private const val PHONE_MIN_WIDTH_DP = 360f

/** Material's large bar keeps this much room under its expanded title. */
private val LargeTitleBottomPadding = 28.dp

/** The count line under a title shows until the header is this far collapsed. */
private const val SUBTITLE_HIDES_AT = 0.4f
