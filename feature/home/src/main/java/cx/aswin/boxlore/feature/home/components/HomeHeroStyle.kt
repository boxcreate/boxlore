package cx.aswin.boxlore.feature.home.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.component.appBottomChromeContentPadding
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxlorePaneBounds
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.boxloreContentBottomPadding
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.text.ListeningTime
import cx.aswin.boxlore.feature.home.R

internal object HomeHeroStyle {
    val CardShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp, bottomEnd = 12.dp, bottomStart = 32.dp)
    val AttachedArtworkShape = RoundedCornerShape(bottomStart = 24.dp)
    val CollectionArtworkShape = RoundedCornerShape(16.dp)

    fun container(colors: ColorScheme): Color = if (colors.background.luminance() < 0.5f) colors.primaryContainer else colors.primary

    fun foreground(colors: ColorScheme): Color = if (colors.background.luminance() < 0.5f) colors.onPrimaryContainer else colors.onPrimary

    fun secondaryContainer(colors: ColorScheme): Color = lerp(colors.surfaceContainerHigh, colors.primaryContainer, 0.65f)

    fun progress(podcast: Podcast): Float = podcast.resumeProgress?.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f

    /** Minutes left, or the whole length for a new episode, rounded as the mini player rounds them ([ListeningTime]). */
    fun minutes(podcast: Podcast, resume: Boolean): Int? {
        val duration = podcast.latestEpisode?.duration?.takeIf { it > 0 } ?: return null
        return ListeningTime.minutesLeft(duration.toLong(), if (resume) progress(podcast) else 0f)?.toInt()
    }
}

/**
 * Shared by loaded heroes and their opaque loading cover, including enlarged text. Above a tabletop
 * fold the hero shortens to the space between Home's top bar and the bottom bar, never below
 * [MinHeroHeight]. In compact windows shorter than [ShortWindowHeight] (sideways phones, split-screen
 * halves) it shortens the same way, but never below [shortHeroFloor], which grows with the text size
 * so every style keeps its title and a full Play button. That floor is taller than the room those
 * windows have, so there the hero's foot can still sit under the nav bar or the mini player. The
 * mini player always counts, so starting playback never resizes the hero. Portrait phones keep the
 * full height.
 */
@Composable
internal fun homeHeroHeight(): androidx.compose.ui.unit.Dp {
    val scale = LocalDensity.current.fontScale
    val natural = 416.dp + 200.dp * (scale - 1f).coerceAtLeast(0f)
    val window = LocalBoxloreWindowLayout.current
    val pane = LocalBoxlorePaneBounds.current
    return when {
        window.tabletopFold != null && pane != null && scale <= 1.1f ->
            minOf(natural, (pane.height.dp - HomeTopBarReserve - boxloreContentBottomPadding(0.dp)).coerceAtLeast(MinHeroHeight))
        heroFitsShortWindow(window) -> minOf(natural, shortWindowHeroRoom()).coerceAtLeast(shortHeroFloor(scale))
        else -> natural
    }
}

/**
 * The room a short compact window leaves the hero: its height less the status bar, Home's top bar,
 * the bottom bar with the mini player and the system navigation, and the strip under the cards.
 */
@Composable
internal fun shortWindowHeroRoom(): androidx.compose.ui.unit.Dp {
    val window = LocalBoxloreWindowLayout.current
    val pane = LocalBoxlorePaneBounds.current
    val density = LocalDensity.current
    val statusBar = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val navigation = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val bottomChrome = appBottomChromeContentPadding(isMiniPlayerVisible = true, systemNavigationInset = navigation)
    return (pane?.height ?: window.heightDp).dp - statusBar - HomeTopBarReserve - bottomChrome - HeroIndicatorStrip
}

/** The shortest hero in a short compact window at [fontScale]: [MinHeroHeight], growing with the text as the full hero does. */
internal fun shortHeroFloor(fontScale: Float): androidx.compose.ui.unit.Dp = MinHeroHeight + 200.dp * (fontScale - 1f).coerceAtLeast(0f)

/** Compact windows shorter than the tablet layout's height gate (sideways phones, split-screen halves). */
internal fun heroFitsShortWindow(window: cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout): Boolean =
    !window.usesTabletLayout && window.heightDp > 0f && window.heightDp < ShortWindowHeight

/** Home's top bar above the hero, with its padding. */
private val HomeTopBarReserve = 80.dp

/** Compact windows shorter than this (the tablet layout's height gate) fit the hero to the window. */
private const val ShortWindowHeight = 480f

/** The shortest hero at normal text size: every style still fits its title and a full Play button. */
private val MinHeroHeight = 240.dp

internal data class HeroProgressState(val value: Float, val playing: Boolean)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun HeroProgress(progress: Float, color: Color, track: Color, modifier: Modifier = Modifier, playing: Boolean = false) {
    val description = stringResource(R.string.home_hero_listening_progress)
    val animatedProgress = animateFloatAsState(progress, WavyProgressIndicatorDefaults.ProgressAnimationSpec, label = "heroProgress")
    val stroke = Stroke(with(LocalDensity.current) { 4.dp.toPx() }, cap = StrokeCap.Round)
    LinearWavyProgressIndicator(
        progress = { animatedProgress.value },
        color = color,
        trackColor = track,
        stroke = stroke,
        trackStroke = stroke,
        gapSize = 4.dp,
        stopSize = 3.dp,
        amplitude = { if (playing && it > 0f && it < 1f) 0.6f else 0f },
        wavelength = 24.dp,
        waveSpeed = if (playing) 24.dp else 0.dp,
        modifier = modifier.fillMaxWidth().height(12.dp).semantics { contentDescription = description },
    )
}

/**
 * Below this height (the hero above a tabletop fold, on sideways phones and in split-screen halves) a
 * separate context block leaves no room for Play: Duotone moves its context into the copy, as Cover
 * does, Halo shows as Cover, and the artwork styles' words keep to two title lines without the
 * progress bar ([CoverCopy]). Portrait phone heroes are taller.
 */
internal val ShortHeroHeight = 320.dp

/**
 * The hero style Home shows for the saved [style]. Marquee's still cover and the sheet that docks
 * under the top bar are built for one phone-width column, so tablets show Cover instead.
 */
internal fun homeHeroStyle(style: String, tablet: Boolean): String {
    val saved = cx.aswin.boxlore.core.prefs.HeroCardStyle.sanitize(style)
    return if (tablet && saved == cx.aswin.boxlore.core.prefs.HeroCardStyle.MARQUEE) cx.aswin.boxlore.core.prefs.HeroCardStyle.COVER else saved
}

@Composable
internal fun heroContainerColor() = HomeHeroStyle.container(MaterialTheme.colorScheme)

@Composable
internal fun heroContentColor() = HomeHeroStyle.foreground(MaterialTheme.colorScheme)
