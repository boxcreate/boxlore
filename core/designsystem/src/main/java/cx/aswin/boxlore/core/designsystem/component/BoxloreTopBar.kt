package cx.aswin.boxlore.core.designsystem.component

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TwoRowsTopAppBar
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.lerp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.R
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth
import cx.aswin.boxlore.core.designsystem.layout.boxloreContentWidthDp
import cx.aswin.boxlore.core.designsystem.layout.boxloreHorizontalSafeInsetsDp
import cx.aswin.boxlore.core.designsystem.layout.boxloreIsShortPane
import cx.aswin.boxlore.core.designsystem.layout.boxlorePaneTopInset
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import kotlinx.coroutines.flow.first

/** A page title's text style and how many lines it may use. */
@Immutable
data class BoxloreLargeTitlePresentation(
    val style: TextStyle,
    val maxLines: Int = 2,
)

/**
 * The style of a large page title as its header collapses ([collapsedFraction] 0 to 1): it eases
 * from [largeStyle] in bold to `titleLarge` in semi-bold and may wrap to two lines while expanded.
 * The collapsed row ([expanded] false) uses `titleLarge` on [collapsedMaxLines]. With text over 115%
 * or a pane under 400dp the large style is one type step smaller, so long and translated titles fit.
 */
@Suppress("LongParameterList")
fun boxloreLargeTitlePresentation(
    typography: Typography,
    collapsedFraction: Float,
    expanded: Boolean = true,
    fontScale: Float = 1f,
    paneWidthDp: Float = Float.POSITIVE_INFINITY,
    largeStyle: TextStyle = typography.headlineLarge,
    collapsedMaxLines: Int = 1,
): BoxloreLargeTitlePresentation {
    val fraction = if (collapsedFraction.isFinite()) collapsedFraction.coerceIn(0f, 1f) else 0f
    val compactStyle = typography.titleLarge.copy(fontWeight = GoogleSansWeight.semiBold)
    if (!expanded) return BoxloreLargeTitlePresentation(compactStyle, collapsedMaxLines)
    val smaller = fontScale > LARGE_TEXT_FONT_SCALE || paneWidthDp < NARROW_PANE_DP
    val large = (if (smaller) typography.oneStepSmaller(largeStyle) else largeStyle).copy(fontWeight = GoogleSansWeight.bold)
    return BoxloreLargeTitlePresentation(lerp(large, compactStyle, fraction), maxLines = 2)
}

private fun Typography.oneStepSmaller(style: TextStyle): TextStyle {
    val ladder = listOf(displayLarge, displayMedium, displaySmall, headlineLarge, headlineMedium, headlineSmall, titleLarge, titleMedium, titleSmall)
    val index = ladder.indexOf(style)
    return if (index in 0 until ladder.lastIndex) ladder[index + 1] else style
}

private const val LARGE_TEXT_FONT_SCALE = 1.15f
private const val NARROW_PANE_DP = 400f

enum class BoxloreTopBarSize { Large, Small }

/**
 * boxlore's page header. [BoxloreTopBarSize.Large] shows the title in a large second row that
 * collapses into the first as the page scrolls ([scrollBehavior], usually exit-until-collapsed);
 * a tab root (no [onBack], no [actions]) shows the large title in its one 64dp row instead.
 * In a short pane (a sideways phone, a split-screen half) the bar is always one slim row so the
 * content shows first; when the pane grows again with the list at its top it opens back up. The
 * caller's [scrollBehavior] state is kept throughout. On tablets the bar follows the content
 * column's width, and it keeps clear of side system bars and cutouts.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BoxloreTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    size: BoxloreTopBarSize = BoxloreTopBarSize.Large,
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    val short = boxloreIsShortPane()
    val forcedSmall = short && size == BoxloreTopBarSize.Large
    ReexpandWhenTall(short, size, scrollBehavior)
    HoldOpenWhileSlim(forcedSmall, scrollBehavior)
    val typography = MaterialTheme.typography
    val colors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.surface,
        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    )
    val barModifier = modifier.boxloreCardWidth(BoxloreCardFamily.Utility)
    val insets = topBarInsets()
    val navigationIcon: @Composable () -> Unit = {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                // The brief's Back label is the module's one translated "Back".
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.briefing_back))
            }
        }
    }
    val barActions: @Composable RowScope.() -> Unit = { actions?.invoke(this) }
    val compact = boxloreLargeTitlePresentation(typography, 0f, expanded = false)
    val scrolled by remember(scrollBehavior) { derivedStateOf { (scrollBehavior?.state?.contentOffset ?: 0f) < 0f } }
    when {
        size == BoxloreTopBarSize.Small || forcedSmall -> TopAppBar(
            title = { TitleText(title, compact) },
            modifier = barModifier,
            navigationIcon = navigationIcon,
            actions = barActions,
            windowInsets = insets,
            // A forced slim row leaves the scroll state to the large bar it stands in for.
            colors = if (forcedSmall) colors.pinned(scrolled) else colors,
            scrollBehavior = if (forcedSmall) null else scrollBehavior,
        )
        onBack == null && actions == null -> TopAppBar(
            title = { TitleText(title, largeTitle(0f).copy(maxLines = 1)) },
            modifier = barModifier,
            windowInsets = insets,
            colors = colors,
            scrollBehavior = scrollBehavior,
        )
        else -> TwoRowsTopAppBar(
            title = { expandedRow ->
                val fraction = scrollBehavior?.state?.collapsedFraction ?: 0f
                TitleText(title, if (expandedRow) largeTitle(fraction) else compact)
            },
            modifier = barModifier,
            navigationIcon = navigationIcon,
            actions = barActions,
            collapsedHeight = TopAppBarDefaults.LargeAppBarCollapsedHeight,
            expandedHeight = TopAppBarDefaults.LargeAppBarExpandedHeight,
            windowInsets = insets,
            colors = colors,
            scrollBehavior = scrollBehavior,
        )
    }
}

@Composable
private fun largeTitle(collapsedFraction: Float): BoxloreLargeTitlePresentation = boxloreLargeTitlePresentation(
    MaterialTheme.typography,
    collapsedFraction,
    fontScale = LocalDensity.current.fontScale,
    paneWidthDp = boxloreContentWidthDp(),
)

@Composable
private fun TitleText(title: String, presentation: BoxloreLargeTitlePresentation) {
    Text(title, style = presentation.style, maxLines = presentation.maxLines, softWrap = true, overflow = TextOverflow.Ellipsis)
}

@OptIn(ExperimentalMaterial3Api::class)
private fun TopAppBarColors.pinned(scrolled: Boolean): TopAppBarColors =
    copy(containerColor = if (scrolled) scrolledContainerColor else containerColor)

/** The status bar and cutout above this pane, and side insets where the pane meets the window edge. */
@Composable
private fun topBarInsets(): WindowInsets {
    val direction = LocalLayoutDirection.current
    val (start, end) = boxloreHorizontalSafeInsetsDp()
    val (left, right) = if (direction == LayoutDirection.Ltr) start to end else end to start
    return WindowInsets(left = left, top = boxlorePaneTopInset(), right = right, bottom = 0.dp)
}

/**
 * The forced slim row has nothing to collapse, and the caller's exit-until-collapsed behavior
 * would otherwise take every upward scroll for an invisible collapse. Holds its state open, so
 * scrolling moves the list; the large bar sets its own limit again when it comes back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HoldOpenWhileSlim(forcedSmall: Boolean, scrollBehavior: TopAppBarScrollBehavior?) {
    if (!forcedSmall) return
    SideEffect {
        scrollBehavior?.state?.let { state ->
            state.heightOffsetLimit = 0f
            state.heightOffset = 0f
        }
    }
}

/**
 * When a short pane grows tall again, opens the large bar back up if the list is at its top, or
 * keeps it collapsed when the listener had scrolled.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReexpandWhenTall(short: Boolean, size: BoxloreTopBarSize, scrollBehavior: TopAppBarScrollBehavior?) {
    var wasShort by remember { mutableStateOf(short) }
    LaunchedEffect(short) {
        val grew = wasShort && !short
        wasShort = short
        val state = scrollBehavior?.state ?: return@LaunchedEffect
        if (!grew || size != BoxloreTopBarSize.Large) return@LaunchedEffect
        if (state.contentOffset > -AT_TOP_TOLERANCE_PX) {
            state.heightOffset = 0f
        } else {
            // The large bar sets its collapse limit when it lays out.
            val limit = snapshotFlow { state.heightOffsetLimit }.first { it < 0f && it > -Float.MAX_VALUE }
            state.heightOffset = limit
        }
    }
}

private const val AT_TOP_TOLERANCE_PX = 0.5f
