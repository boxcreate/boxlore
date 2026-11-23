package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.carousel.CarouselItemDrawInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * How far a hero card has slid from the peek into place. The cards' words and buttons follow
 * [HeroPeekMotion.contentAlpha], so a peeking card shows only its artwork or colour, as the Block
 * collection card already does. Material's minimum item size includes an off-screen anchor, so the
 * reveal starts at the narrowest visible peek, 40dp.
 *
 * On the tablet row (tablets, sideways phones) material3 keeps every card at full size and the row's
 * edge cuts the last one, so there the reveal also follows how much of the card is on screen
 * ([visibleFraction]): its words and buttons show only once it has slid almost fully into view.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Stable
internal class HeroPeekReveal(
    private val info: CarouselItemDrawInfo,
    private val minimumPeekWidth: Float,
    private val visibleFraction: FloatState? = null,
) {
    val alpha: Float
        get() {
            val slide = HeroPeekMotion.contentAlpha(HeroPeekMotion.expansion(info.size, maxOf(info.minSize, minimumPeekWidth), info.maxSize))
            val onScreen = visibleFraction?.let { HeroPeekMotion.onScreenAlpha(it.floatValue) } ?: 1f
            return minOf(slide, onScreen)
        }
}

/** The current carousel card's reveal; null outside the carousel (Marquee, previews), where everything shows. */
internal val LocalHeroPeekReveal = staticCompositionLocalOf<HeroPeekReveal?> { null }

/**
 * Gives the hero card in [content] the reveal for its carousel slot. With [cutByRowEdge] (the tablet
 * row) it also tracks how much of the card the row shows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProvideHeroPeekReveal(info: CarouselItemDrawInfo, cutByRowEdge: Boolean = false, content: @Composable () -> Unit) {
    val minimumPeekWidth = with(LocalDensity.current) { 40.dp.toPx() }
    val visible = remember(cutByRowEdge) { if (cutByRowEdge) mutableFloatStateOf(1f) else null }
    val reveal = remember(info, minimumPeekWidth, visible) { HeroPeekReveal(info, minimumPeekWidth, visible) }
    Box(
        Modifier
            .then(
                if (visible == null) {
                    Modifier
                } else {
                    // boundsInRoot is cut to the row's clip, so its width is the part of the card on screen.
                    Modifier.onGloballyPositioned { coordinates ->
                        val full = coordinates.size.width
                        visible.floatValue = if (full <= 0) 1f else (coordinates.boundsInRoot().width / full).coerceIn(0f, 1f)
                    }
                },
            ),
        propagateMinConstraints = true,
    ) {
        CompositionLocalProvider(LocalHeroPeekReveal provides reveal, content = content)
    }
}

/** A card's words and buttons: [shown] is false while they are fully faded out. */
@Immutable
internal data class HeroPeekContent(val reveal: HeroPeekReveal?, val shown: Boolean)

/** The reveal state for words and buttons drawn here; it changes only when they appear or vanish. */
@Composable
internal fun heroPeekContent(): HeroPeekContent {
    val reveal = LocalHeroPeekReveal.current ?: return HeroPeekContent(null, shown = true)
    val shown by remember(reveal) { derivedStateOf { reveal.alpha > 0f } }
    return HeroPeekContent(reveal, shown)
}

/** Fades words and buttons with the card's reveal and keeps them out of TalkBack while they can't be seen. */
internal fun Modifier.heroPeekFade(content: HeroPeekContent): Modifier {
    val reveal = content.reveal ?: return this
    return graphicsLayer { alpha = reveal.alpha }.then(if (content.shown) Modifier else Modifier.clearAndSetSemantics {})
}

/**
 * A peeking card's own tap (it opens the episode) keeps a name while its words are hidden from
 * TalkBack, so it is never read as an unlabelled button.
 */
internal fun Modifier.heroPeekCardLabel(content: HeroPeekContent, label: String): Modifier =
    if (content.shown) this else semantics { contentDescription = label }
