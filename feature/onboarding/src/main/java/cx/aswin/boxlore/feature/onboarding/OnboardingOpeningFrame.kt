package cx.aswin.boxlore.feature.onboarding

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.R as DesignR
import cx.aswin.boxlore.core.designsystem.components.BoxLoreLogo
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.foldSafeRegions
import cx.aswin.boxlore.core.designsystem.theme.LocalEffectiveDarkTheme

/** The opening screens' panel colours: a rich panel that keeps the brand in dark mode without a pale billboard. */
internal data class LanguagePanelColors(val container: Color, val content: Color)

@Composable
internal fun openingPanelColors(): LanguagePanelColors {
    val colors = MaterialTheme.colorScheme
    val dark = LocalEffectiveDarkTheme.current
    return LanguagePanelColors(
        container = if (dark) colors.primaryContainer else colors.primary,
        content = if (dark) colors.onPrimaryContainer else colors.onPrimary,
    )
}

/** The size a pane of the opening frame has. */
internal data class PaneSize(val width: Dp, val height: Dp)

/**
 * Wide windows wider than tall (a phone on its side, a tablet, an open foldable held sideways) put
 * the panel beside the content on the opening screens; everything else stacks them.
 */
internal fun languageQuestionTwoPane(width: Dp, height: Dp): Boolean = width >= 600.dp && width > height

/**
 * The coloured panel the language question and Welcome share: the logo row at the top, then
 * [content]. Both screens draw it the same way, so moving from one to the other keeps it in place.
 */
@Composable
internal fun OpeningPanel(
    colors: LanguagePanelColors,
    shape: RoundedCornerShape,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    contentInsets: WindowInsets? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        // The colour reaches the window's edges; only the logo row and the content keep clear of the insets.
        modifier.testTag(OpeningPanelTag).clip(shape).background(colors.container)
            .then(if (contentInsets != null) Modifier.windowInsetsPadding(contentInsets) else Modifier)
            .statusBarsPadding().padding(top = 16.dp, bottom = bottomPadding),
        verticalArrangement = verticalArrangement,
    ) {
        OpeningLogoRow(colors)
        content()
    }
}

/**
 * The wordmark and the app mark. In a narrow panel the wordmark shrinks to keep [OpeningLogoGap]
 * from the mark, so the two never run together.
 */
@Composable
private fun OpeningLogoRow(colors: LanguagePanelColors) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 26.dp).testTag(OpeningLogoTag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BoxLoreLogo(
            Modifier.weight(1f, fill = false).padding(end = OpeningLogoGap).testTag(OpeningWordmarkTag),
            textColor = colors.content,
            height = OpeningLogoHeight,
        )
        Icon(painterResource(DesignR.drawable.ic_boxlore_app_mark), null, tint = colors.content, modifier = Modifier.size(OpeningMarkSize).testTag(OpeningMarkTag))
    }
}

private val OpeningLogoHeight = 24.dp
private val OpeningMarkSize = 34.dp
internal val OpeningLogoGap = 12.dp

/**
 * The opening screens side by side: the panel on the start side at [OpeningPanelPaneShare] of the
 * width, full height, and the screen's content centred in the end pane, which scrolls only when
 * even that is too short (very large text). One [scroll] state serves both arrangements, so a
 * rotation keeps the place.
 */
@Composable
internal fun OnboardingOpeningFrame(
    colors: LanguagePanelColors,
    width: Dp,
    height: Dp,
    scroll: ScrollState,
    panelBottomPadding: Dp,
    panelContent: @Composable ColumnScope.(PaneSize) -> Unit,
    content: @Composable ColumnScope.(PaneSize) -> Unit,
) {
    // A vertical fold or hinge splits the window (the shared hinge-split policy): the panel takes the
    // start half and the content the end half, so nothing lies across the fold.
    val window = LocalBoxloreWindowLayout.current
    val halves = window.foldSafeRegions().sortedBy { it.left }.takeIf { window.usesHingeSplit && it.size == 2 }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val panelWidth = halves?.let { (if (rtl) it.last() else it.first()).width.dp } ?: (width * OpeningPanelPaneShare)
    val hingeGap = halves?.let { (it.last().left - it.first().right).coerceAtLeast(0f).dp } ?: 0.dp
    Row(Modifier.fillMaxSize()) {
        OpeningPanel(
            colors,
            shape = RoundedCornerShape(topEnd = 46.dp, bottomEnd = 46.dp),
            bottomPadding = panelBottomPadding,
            modifier = Modifier.width(panelWidth).fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween,
            // A cutout or the navigation buttons at the start edge: the panel's colour runs behind them, its logo and content don't.
            contentInsets = WindowInsets.displayCutout.union(WindowInsets.navigationBars).only(WindowInsetsSides.Start),
        ) {
            panelContent(PaneSize(panelWidth, height))
        }
        if (hingeGap > 0.dp) Spacer(Modifier.width(hingeGap))
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
            val pane = PaneSize(maxWidth, maxHeight)
            Column(
                Modifier.fillMaxSize().verticalScroll(scroll).heightIn(min = pane.height)
                    .statusBarsPadding()
                    // The end edge's cutout and buttons, and the bottom's; the start edge belongs to the panel.
                    .windowInsetsPadding(WindowInsets.displayCutout.union(WindowInsets.navigationBars).only(WindowInsetsSides.End + WindowInsetsSides.Bottom))
                    .padding(horizontal = 32.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(Modifier.widthIn(max = OpeningPaneContentWidth).fillMaxWidth()) { content(pane) }
            }
        }
    }
}

/** The side panel's share of a wide window. */
internal const val OpeningPanelPaneShare = 0.42f
internal val OpeningPaneContentWidth = 520.dp

/** Beside the panel, Echo peeks over the main button when the pane is at least this tall. */
internal val OpeningPaneEchoHeight = 560.dp
internal val OpeningPaneEchoRoom = 64.dp

internal const val OpeningPanelTag = "opening_panel"
internal const val OpeningLogoTag = "opening_logo"
internal const val OpeningWordmarkTag = "opening_wordmark"
internal const val OpeningMarkTag = "opening_mark"
