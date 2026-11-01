package cx.aswin.boxlore.feature.settings.feedback

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import cx.aswin.boxlore.core.designsystem.component.LocalAdaptivePlayerCompactProgress
import cx.aswin.boxlore.core.designsystem.component.LocalNavigationStyle
import cx.aswin.boxlore.core.designsystem.component.NavigationStyle
import cx.aswin.boxlore.core.designsystem.component.appBottomChromeContentPadding
import cx.aswin.boxlore.core.designsystem.component.appBottomChromeOverlayOffset

/**
 * [chrome] less the room the compact player gives back as it docks beside the navigation bar
 * (Feedback keeps the player compact), never less than the navigation bar alone needs.
 */
internal fun feedbackChromePadding(chrome: Dp, navigationOnly: Dp, style: NavigationStyle, compactProgress: Float): Dp =
    maxOf(minOf(chrome, navigationOnly), chrome - appBottomChromeOverlayOffset(style, isMiniPlayerVisible = true, compactProgress))

/**
 * Space under a Feedback pane: the keyboard while it's up, otherwise the bottom chrome (as
 * [feedbackChromePadding] leaves it) plus [extra]. Measured in layout, so the player's docking
 * animation doesn't recompose the page.
 */
@Composable
internal fun Modifier.feedbackBottomSpace(chrome: Dp, extra: Dp, imeBottom: Dp = 0.dp): Modifier {
    val style = LocalNavigationStyle.current
    val progress = LocalAdaptivePlayerCompactProgress.current
    val navigationOnly = appBottomChromeContentPadding(isMiniPlayerVisible = false)
    return layout { measurable, constraints ->
        val docked = feedbackChromePadding(chrome, navigationOnly, style, progress?.value ?: 0f)
        val bottom = feedbackBottomPadding(imeBottom, docked, extra).roundToPx()
        val child = measurable.measure(constraints.offset(vertical = -bottom))
        layout(child.width, constraints.constrainHeight(child.height + bottom)) {
            child.placeRelative(0, 0)
        }
    }
}
