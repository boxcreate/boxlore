package cx.aswin.boxlore.feature.home.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.carousel.CarouselItemDrawInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

/**
 * How far a hero card has slid from the peek into place. The cards' words and buttons follow
 * [HeroPeekMotion.contentAlpha], so a peeking card shows only its artwork or colour, as the Block
 * collection card already does. Material's minimum item size includes an off-screen anchor, so the
 * reveal starts at the narrowest visible peek, 40dp.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Stable
internal class HeroPeekReveal(private val info: CarouselItemDrawInfo, private val minimumPeekWidth: Float) {
    val alpha: Float
        get() = HeroPeekMotion.contentAlpha(HeroPeekMotion.expansion(info.size, maxOf(info.minSize, minimumPeekWidth), info.maxSize))
}

/** The current carousel card's reveal; null outside the carousel (Marquee, previews), where everything shows. */
internal val LocalHeroPeekReveal = staticCompositionLocalOf<HeroPeekReveal?> { null }

/** Gives the hero card in [content] the reveal for its carousel slot. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProvideHeroPeekReveal(info: CarouselItemDrawInfo, content: @Composable () -> Unit) {
    val minimumPeekWidth = with(LocalDensity.current) { 40.dp.toPx() }
    val reveal = remember(info, minimumPeekWidth) { HeroPeekReveal(info, minimumPeekWidth) }
    CompositionLocalProvider(LocalHeroPeekReveal provides reveal, content = content)
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
