package cx.aswin.boxlore.feature.home.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The strip under the hero cards, the same 12dp that always separated them from Your shows. */
internal val HeroIndicatorStrip = 12.dp

/**
 * Where the dots' centre sits below the cards. The strip and the grid gap after it leave 28dp before
 * the Your shows row, whose title sits lower still, so the dots are drawn past the strip to look
 * centred between the two. Drawing only: layout keeps the 12dp strip.
 */
private val DotCentreBelowCards = 18.dp

private val DotSize = 6.dp
private val ActiveDotWidth = 18.dp
private val DotGap = 6.dp

/**
 * Where the hero carousel is: one dot per card, the current card stretched into a primary pill.
 * It never adds space; nothing shows for a single card.
 * Decorative only: the carousel itself is what assistive technology navigates.
 */
@Composable
internal fun HeroPageIndicator(count: Int, current: Int, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(HeroIndicatorStrip).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        if (count <= 1) return@Box
        Row(
            modifier = Modifier.offset(y = DotCentreBelowCards - HeroIndicatorStrip / 2),
            horizontalArrangement = Arrangement.spacedBy(DotGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(count) { index -> HeroDot(active = index == current) }
        }
    }
}

@Composable
private fun HeroDot(active: Boolean) {
    val width: Dp by animateDpAsState(
        if (active) ActiveDotWidth else DotSize,
        spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "hero_dot_width",
    )
    val color by animateColorAsState(
        if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        YourShowsMotion.effects(),
        label = "hero_dot_color",
    )
    Box(Modifier.width(width).height(DotSize).background(color, CircleShape))
}
