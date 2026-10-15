package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.m3Shimmer
import cx.aswin.boxlore.core.prefs.HeroCardStyle

/**
 * The loading card for each hero style, drawn where the loaded card's artwork, words and buttons
 * sit so the reveal settles in place. Every [HeroCardStyle] needs its own branch here, tagged
 * `home_hero_skeleton_<style>`; `HomeHeroSkeletonTest` fails for a style that falls back to Block.
 */
@Composable
internal fun HeroSkeletonCard(style: String) {
    val tones = heroSkeletonTones()
    when (style) {
        HeroCardStyle.COVER -> CoverHeroSkeletonCard(tones)
        HeroCardStyle.DUOTONE -> DuotoneHeroSkeletonCard(tones)
        HeroCardStyle.HALO -> HaloHeroSkeletonCard(tones)
        HeroCardStyle.TIDE -> TideHeroSkeletonCard(tones)
        HeroCardStyle.MARQUEE -> MarqueeHeroSkeletonCard(tones)
        else -> BlockHeroSkeletonCard(tones)
    }
}

/** The next card's sliver: artwork for Cover, Duotone and Tide, whose art fills its middle; solid otherwise. */
@Composable
internal fun HeroSkeletonPeek(style: String) {
    val tones = heroSkeletonTones()
    val artwork = style == HeroCardStyle.COVER || style == HeroCardStyle.DUOTONE || style == HeroCardStyle.TIDE
    Box(
        Modifier.clip(HomeHeroStyle.CardShape).background(tones.surface)
            .then(if (artwork) Modifier.m3Shimmer(tones.base, tones.highlight) else Modifier),
    )
}

internal data class HeroSkeletonTones(val surface: Color, val base: Color, val highlight: Color, val onArtwork: Color, val onArtworkHighlight: Color)

@Composable
private fun heroSkeletonTones(): HeroSkeletonTones {
    val ink = MaterialTheme.colorScheme.onSurface
    return HeroSkeletonTones(
        surface = MaterialTheme.colorScheme.surfaceContainer,
        base = ink.copy(alpha = 0.08f),
        highlight = ink.copy(alpha = 0.18f),
        // Lines drawn over the artwork's own shimmer need a little more weight to show.
        onArtwork = ink.copy(alpha = 0.12f),
        onArtworkHighlight = ink.copy(alpha = 0.24f),
    )
}

/** Block: context lines beside the square artwork flush in the top end corner, then the copy and actions. */
@Composable
private fun BlockHeroSkeletonCard(tones: HeroSkeletonTones) {
    Column(Modifier.fillMaxSize().clip(HomeHeroStyle.CardShape).background(tones.surface).testTag(skeletonTag(HeroCardStyle.BLOCK))) {
        Row(Modifier.fillMaxWidth().aspectRatio(1f / 0.65f)) {
            Column(
                Modifier.weight(1f).padding(start = 16.dp, end = 12.dp, top = 22.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SkeletonLine(0.85f, 12.dp, tones.base, tones.highlight)
                SkeletonLine(0.55f, 12.dp, tones.base, tones.highlight)
            }
            Box(Modifier.fillMaxHeight().aspectRatio(1f).m3Shimmer(tones.base, tones.highlight, HomeHeroStyle.AttachedArtworkShape))
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterVertically),
        ) {
            SkeletonLine(0.92f, 22.dp, tones.base, tones.highlight)
            SkeletonLine(0.66f, 22.dp, tones.base, tones.highlight)
            SkeletonLine(0.42f, 14.dp, tones.base, tones.highlight, Modifier.padding(top = 3.dp))
        }
        SkeletonActions(tones.base, tones.highlight, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp))
    }
}

/**
 * Cover: the artwork's place fills the card above a solid shade, which holds the context line, a
 * two-line title, the show name and the Play / details pair at the loaded card's 20dp insets.
 */
@Composable
private fun CoverHeroSkeletonCard(tones: HeroSkeletonTones) {
    Column(Modifier.fillMaxSize().clip(HomeHeroStyle.CardShape).background(tones.surface).testTag(skeletonTag(HeroCardStyle.COVER))) {
        Box(Modifier.weight(1f).fillMaxWidth().m3Shimmer(tones.base, tones.highlight))
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp)) {
            SkeletonLine(0.5f, 12.dp, tones.base, tones.highlight)
            Spacer(Modifier.height(12.dp))
            FullArtworkSkeletonCopy(tones.base, tones.highlight)
            Spacer(Modifier.height(16.dp))
            SkeletonActions(tones.base, tones.highlight)
        }
    }
}

/**
 * Duotone: the artwork's place is the whole card, with the solid cards' context block at the top
 * and the title, show name and Play / details pair at the bottom, drawn straight on it.
 */
@Composable
private fun DuotoneHeroSkeletonCard(tones: HeroSkeletonTones) {
    Box(
        Modifier.fillMaxSize().clip(HomeHeroStyle.CardShape).background(tones.surface).m3Shimmer(tones.base, tones.highlight)
            .testTag(skeletonTag(HeroCardStyle.DUOTONE)),
    ) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            SkeletonLine(0.45f, 12.dp, tones.onArtwork, tones.onArtworkHighlight)
            Spacer(Modifier.height(10.dp))
            SkeletonLine(0.3f, 24.dp, tones.onArtwork, tones.onArtworkHighlight)
            Spacer(Modifier.weight(1f))
            FullArtworkSkeletonCopy(tones.onArtwork, tones.onArtworkHighlight)
            Spacer(Modifier.height(16.dp))
            SkeletonActions(tones.onArtwork, tones.onArtworkHighlight)
        }
    }
}

/**
 * Halo: the ring's track and a cookie where the cover goes, both placed by [haloGeometry], the context
 * lines in the room beside them, and the copy and Play / details pair at the bottom. A plain layout,
 * so nothing subcomposes on a cold start.
 */
@Composable
private fun HaloHeroSkeletonCard(tones: HeroSkeletonTones) {
    Layout(
        content = {
            Box(Modifier.m3Shimmer(tones.base, tones.highlight, HaloCookie))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SkeletonLine(0.7f, 12.dp, tones.base, tones.highlight)
                SkeletonLine(0.5f, 24.dp, tones.base, tones.highlight)
            }
            Column(Modifier.padding(20.dp)) {
                Spacer(Modifier.weight(1f))
                FullArtworkSkeletonCopy(tones.base, tones.highlight)
                Spacer(Modifier.height(16.dp))
                SkeletonActions(tones.base, tones.highlight)
            }
        },
        modifier = Modifier.fillMaxSize().clip(HomeHeroStyle.CardShape).background(tones.surface)
            .drawBehind { drawHaloRing(haloGeometry(size.width / density, size.height / density), tones.base) }
            .testTag(skeletonTag(HeroCardStyle.HALO)),
    ) { measurables, constraints ->
        val geometry = haloGeometry(constraints.maxWidth / density, constraints.maxHeight / density)
        val coverSize = geometry.cover.dp.roundToPx()
        val cover = measurables[0].measure(Constraints.fixed(coverSize, coverSize))
        val context = measurables[1].measure(Constraints(maxWidth = geometry.contextWidth.dp.roundToPx()))
        val copy = measurables[2].measure(Constraints.fixed(constraints.maxWidth, constraints.maxHeight))
        layout(constraints.maxWidth, constraints.maxHeight) {
            copy.placeRelative(0, 0)
            context.placeRelative(20.dp.roundToPx(), 20.dp.roundToPx())
            cover.placeRelative(geometry.coverStart.dp.roundToPx(), geometry.coverTop.dp.roundToPx())
        }
    }
}

/**
 * Tide: the cover's square shimmering at the top, the solid wavy panel and the Play disc laid out by the
 * card's own [TideCardLayout] for the context line, two-line title and show line below.
 */
@Composable
private fun TideHeroSkeletonCard(tones: HeroSkeletonTones) {
    Box(Modifier.fillMaxSize().clip(HomeHeroStyle.CardShape).background(tones.surface).testTag(skeletonTag(HeroCardStyle.TIDE))) {
        TideCardLayout(
            cover = { Box(Modifier.m3Shimmer(tones.base, tones.highlight)) },
            panel = { TidePanel(tones.surface) },
            copy = {
                Column {
                    SkeletonLine(0.5f, 12.dp, tones.base, tones.highlight, Modifier.padding(end = TideDiscReserve))
                    Spacer(Modifier.height(12.dp))
                    FullArtworkSkeletonCopy(tones.base, tones.highlight)
                }
            },
            disc = {
                Box(Modifier.background(tones.surface, CircleShape), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(56.dp).m3Shimmer(tones.base, tones.highlight, CircleShape))
                }
            },
        )
    }
}

/**
 * Marquee: the cover's place shimmers across the whole hero, with the words, Play and details at the
 * card's insets and Your shows' rounded sheet beginning over its foot.
 */
@Composable
private fun MarqueeHeroSkeletonCard(tones: HeroSkeletonTones) {
    Box(Modifier.fillMaxSize().background(tones.surface).m3Shimmer(tones.base, tones.highlight).testTag(skeletonTag(HeroCardStyle.MARQUEE))) {
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 20.dp + MarqueeSheetOverlap)) {
            SkeletonLine(0.5f, 12.dp, tones.onArtwork, tones.onArtworkHighlight)
            Spacer(Modifier.height(12.dp))
            FullArtworkSkeletonCopy(tones.onArtwork, tones.onArtworkHighlight)
            Spacer(Modifier.height(16.dp))
            SkeletonActions(tones.onArtwork, tones.onArtworkHighlight, Modifier.width(MarqueeActionsWidth))
        }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(MarqueeSheetOverlap).background(MaterialTheme.colorScheme.surface, MarqueeSheetShape))
    }
}

/** The full-artwork cards' larger title on two lines and the show name below it. */
@Composable
private fun FullArtworkSkeletonCopy(base: Color, highlight: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SkeletonLine(0.9f, 26.dp, base, highlight)
        SkeletonLine(0.62f, 26.dp, base, highlight)
        SkeletonLine(0.4f, 14.dp, base, highlight, Modifier.padding(top = 2.dp))
    }
}

/** The connected Play / details pair at its 48dp resting height. */
@Composable
private fun SkeletonActions(base: Color, highlight: Color, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().testTag("home_hero_skeleton_actions"), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(Modifier.weight(1f).height(48.dp).m3Shimmer(base, highlight, HeroSkeletonPlayShape))
        Box(Modifier.size(width = 64.dp, height = 48.dp).m3Shimmer(base, highlight, HeroSkeletonDetailsShape))
    }
}

@Composable
private fun SkeletonLine(fraction: Float, height: Dp, base: Color, highlight: Color, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(fraction).height(height).m3Shimmer(base, highlight, SkeletonLineShape))
}

internal fun skeletonTag(style: String) = "home_hero_skeleton_$style"

private val SkeletonLineShape = RoundedCornerShape(6.dp)
private val HeroSkeletonPlayShape = RoundedCornerShape(topStart = 26.dp, bottomStart = 26.dp, topEnd = 8.dp, bottomEnd = 8.dp)
private val HeroSkeletonDetailsShape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 26.dp, bottomEnd = 26.dp)
