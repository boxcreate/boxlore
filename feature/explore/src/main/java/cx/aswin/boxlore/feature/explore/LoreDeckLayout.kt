package cx.aswin.boxlore.feature.explore

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxlorePaneBounds
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth

/**
 * Where Lore's cards sit: centred, at most 520dp wide on every window, capped in height on tablets,
 * with a rest below the deck. The loading placeholder uses the same frame, so the first card lands
 * exactly where its placeholder was. [content] learns whether the deck is short.
 */
@Composable
internal fun LoreDeckFrame(modifier: Modifier = Modifier, content: @Composable BoxScope.(short: Boolean) -> Unit) {
    val window = LocalBoxloreWindowLayout.current
    val tablet = window.usesTabletLayout
    val paneHeight = LocalBoxlorePaneBounds.current?.height ?: window.heightDp
    BoxWithConstraints(
        modifier = modifier
            .boxloreCardWidth(BoxloreCardFamily.Reading, maximumWidthDp = 520f)
            .then(if (tablet) Modifier.wrapContentHeight(Alignment.Top).heightIn(max = loreTabletDeckHeightDp(paneHeight).dp) else Modifier)
            .fillMaxSize()
            // Centred and capped on every window, so a sideways phone gets the cover's card, not the full width.
            .wrapContentWidth()
            .widthIn(max = 520.dp),
    ) {
        // A deck this short (tabletop lower half, sideways phone, split-screen half) gives its rest and
        // padding to the question instead.
        val short = maxHeight < LoreShortDeckHeight
        Box(
            modifier = Modifier.fillMaxSize().padding(bottom = if (short) 0.dp else 28.dp),
            contentAlignment = Alignment.TopCenter,
        ) { content(short) }
    }
}

/**
 * The tallest a tablet deck gets: about 60% of the pane, so a tall upright tablet grows the card a
 * little, never under the 640dp landscape tablets have always had and never over 760dp.
 */
internal fun loreTabletDeckHeightDp(paneHeightDp: Float): Float =
    (paneHeightDp * LORE_TABLET_DECK_SHARE).coerceIn(LORE_TABLET_DECK_MIN_DP, LORE_TABLET_DECK_MAX_DP)

private const val LORE_TABLET_DECK_SHARE = 0.62f
private const val LORE_TABLET_DECK_MIN_DP = 640f
private const val LORE_TABLET_DECK_MAX_DP = 760f

/** Below this deck height the card drops its bottom rest and uses its tightest padding. */
internal val LoreShortDeckHeight = 260.dp

/** An episode's length in whole minutes, rounded up (never 0), or null when it isn't known. */
internal fun loreDurationMinutes(durationSeconds: Int): Int? =
    if (durationSeconds <= 0) null else (durationSeconds + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE

private const val SECONDS_PER_MINUTE = 60
