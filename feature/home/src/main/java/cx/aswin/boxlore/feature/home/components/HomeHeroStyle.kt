package cx.aswin.boxlore.feature.home.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.home.R

internal object HomeHeroStyle {
    val CardShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp, bottomEnd = 12.dp, bottomStart = 32.dp)
    val AttachedArtworkShape = RoundedCornerShape(bottomStart = 24.dp)
    val CollectionArtworkShape = RoundedCornerShape(16.dp)

    fun container(colors: ColorScheme): Color = if (colors.background.luminance() < 0.5f) colors.primaryContainer else colors.primary

    fun foreground(colors: ColorScheme): Color = if (colors.background.luminance() < 0.5f) colors.onPrimaryContainer else colors.onPrimary

    fun secondaryContainer(colors: ColorScheme): Color = lerp(colors.surfaceContainerHigh, colors.primaryContainer, 0.65f)

    fun progress(podcast: Podcast): Float = podcast.resumeProgress?.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f

    fun minutes(podcast: Podcast, resume: Boolean): Int? {
        val duration = podcast.latestEpisode?.duration?.takeIf { it > 0 } ?: return null
        val remaining = if (resume) 1.0 - progress(podcast) else 1.0
        return (duration * remaining / 60).toInt().coerceAtLeast(1)
    }
}

/** Shared by loaded heroes and their opaque loading cover, including enlarged text. */
@Composable
internal fun homeHeroHeight() = 416.dp + 200.dp * (LocalDensity.current.fontScale - 1f).coerceAtLeast(0f)

internal data class HeroProgressState(val value: Float, val playing: Boolean)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun HeroProgress(progress: Float, color: Color, track: Color, modifier: Modifier = Modifier, playing: Boolean = false) {
    val description = stringResource(R.string.home_hero_listening_progress)
    val animatedProgress = animateFloatAsState(progress, WavyProgressIndicatorDefaults.ProgressAnimationSpec, label = "hero progress")
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

@Composable
internal fun heroContainerColor() = HomeHeroStyle.container(MaterialTheme.colorScheme)

@Composable
internal fun heroContentColor() = HomeHeroStyle.foreground(MaterialTheme.colorScheme)
