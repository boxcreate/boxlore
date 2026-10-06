package cx.aswin.boxlore.feature.explore

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
import cx.aswin.boxlore.core.designsystem.component.appBottomChromeOverlayOffset

internal fun lorePlayerClearance(base: Dp, style: NavigationStyle, visible: Boolean, progress: Float): Dp =
    (base - appBottomChromeOverlayOffset(style, visible, progress)).coerceAtLeast(0.dp)

/** Only Lore's bounded card viewport grows; ordinary list tail padding remains stable. */
@Composable
internal fun Modifier.lorePlayerClearance(base: Dp, visible: Boolean): Modifier {
    val style = LocalNavigationStyle.current
    val progress = LocalAdaptivePlayerCompactProgress.current
    return layout { measurable, constraints ->
        val bottom = lorePlayerClearance(base, style, visible, progress?.value ?: 0f).roundToPx()
        val child = measurable.measure(constraints.offset(vertical = -bottom))
        layout(child.width, constraints.constrainHeight(child.height + bottom)) {
            child.placeRelative(0, 0)
        }
    }
}
