package cx.aswin.boxlore.feature.info.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.ShimmerScope
import cx.aswin.boxlore.core.designsystem.theme.SkeletonBlock
import cx.aswin.boxlore.core.designsystem.theme.SkeletonTextLines
import cx.aswin.boxlore.feature.info.EpisodePageBlockGutter
import cx.aswin.boxlore.feature.info.EpisodePageTextGutter
import cx.aswin.boxlore.feature.info.R
import cx.aswin.boxlore.feature.info.logic.EpisodeHeroForm

internal const val EPISODE_PAGE_SKELETON_TAG = "episode_info_skeleton"
internal const val EPISODE_SKELETON_ART_TAG = "episode_info_skeleton_art"
internal const val EPISODE_SKELETON_RAIL_TAG = "episode_info_skeleton_rail"

/**
 * Opening an episode: the page's outline in the loaded page's places (the cover, show link, title
 * lines, details, the Play row and the About card) for [form], so nothing jumps when the episode
 * arrives. [topPadding] is the loaded list's top padding; the top bar with Back is drawn by the page.
 */
@Composable
internal fun EpisodePageSkeleton(
    form: EpisodeHeroForm,
    artSize: Dp,
    topPadding: Dp,
    railMaxWidth: Dp,
    blockModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    val loading = stringResource(R.string.episode_info_loading)
    ShimmerScope(active = true) {
        Column(
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState(), enabled = false)
                .padding(top = topPadding)
                .testTag(EPISODE_PAGE_SKELETON_TAG)
                .clearAndSetSemantics { contentDescription = loading },
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            val art: @Composable (Modifier) -> Unit = { artModifier ->
                SkeletonPiece(artModifier.size(artSize).testTag(EPISODE_SKELETON_ART_TAG), RoundedCornerShape(if (form == EpisodeHeroForm.Row) 24.dp else 30.dp))
            }
            val rail: @Composable (Modifier) -> Unit = { railModifier -> SkeletonRail(railModifier) }
            val hero = Modifier.padding(horizontal = EpisodePageTextGutter)
            val railEdge = Modifier.padding(horizontal = EpisodePageBlockGutter)
            when (form) {
                EpisodeHeroForm.Stacked -> Column(hero.fillMaxWidth()) {
                    art(Modifier.align(Alignment.CenterHorizontally))
                    Spacer(Modifier.height(20.dp))
                    SkeletonDetails(MaterialTheme.typography.headlineMedium)
                }
                EpisodeHeroForm.Row -> Row(hero.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    art(Modifier.padding(top = 4.dp))
                    Column(Modifier.weight(1f)) { SkeletonDetails(MaterialTheme.typography.headlineSmall) }
                }
                EpisodeHeroForm.TwoColumn -> Row(hero.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    art(Modifier)
                    Column(Modifier.weight(1f)) {
                        SkeletonDetails(MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(16.dp))
                        rail(Modifier.widthIn(max = railMaxWidth))
                    }
                }
            }
            when (form) {
                EpisodeHeroForm.Stacked -> rail(railEdge)
                EpisodeHeroForm.Row -> rail(railEdge.widthIn(max = railMaxWidth))
                EpisodeHeroForm.TwoColumn -> Unit
            }
            Box(blockModifier.padding(horizontal = 16.dp)) {
                SkeletonPiece(Modifier.fillMaxWidth().height(AboutCardHeight), MaterialTheme.shapes.extraLarge)
            }
        }
    }
}

/** The show link, two title lines and the details row, at the loaded hero's heights. */
@Composable
private fun ColumnScope.SkeletonDetails(titleStyle: TextStyle) {
    Box(Modifier.fillMaxWidth().heightIn(min = 48.dp), contentAlignment = Alignment.CenterStart) {
        SkeletonTextLines(MaterialTheme.typography.titleSmall, lines = 1, widths = listOf(0.4f), modifier = Modifier.width(160.dp))
    }
    SkeletonTextLines(titleStyle, lines = 2, widths = listOf(0.95f, 0.6f))
    Spacer(Modifier.height(8.dp))
    Box(Modifier.fillMaxWidth().heightIn(min = 48.dp), contentAlignment = Alignment.CenterStart) {
        SkeletonTextLines(MaterialTheme.typography.bodySmall, lines = 1, widths = listOf(0.5f))
    }
}

/** The Play row: three connected toggles and the wide Play button, shaped like [EpisodeActionRail]. */
@Composable
private fun SkeletonRail(modifier: Modifier) {
    Row(modifier.fillMaxWidth().height(56.dp).testTag(EPISODE_SKELETON_RAIL_TAG), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        SkeletonPiece(Modifier.weight(1f).fillMaxSize(), RoundedCornerShape(topStart = 28.dp, topEnd = 9.dp, bottomEnd = 9.dp, bottomStart = 28.dp))
        SkeletonPiece(Modifier.weight(1f).fillMaxSize(), RoundedCornerShape(9.dp))
        SkeletonPiece(Modifier.weight(1f).fillMaxSize(), RoundedCornerShape(9.dp))
        SkeletonPiece(Modifier.weight(2.1f).fillMaxSize(), RoundedCornerShape(topStart = 9.dp, topEnd = 28.dp, bottomEnd = 28.dp, bottomStart = 9.dp))
    }
}

@Composable
private fun SkeletonPiece(modifier: Modifier, shape: Shape) {
    val colors = MaterialTheme.colorScheme
    SkeletonBlock(modifier, shape = shape, baseColor = colors.surfaceContainerHigh, highlightColor = colors.surfaceContainerLowest)
}

private val AboutCardHeight = 160.dp
