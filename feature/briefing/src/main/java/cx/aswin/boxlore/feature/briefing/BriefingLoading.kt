package cx.aswin.boxlore.feature.briefing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.R
import cx.aswin.boxlore.core.designsystem.briefing.BRIEFING_COVER_ASPECT_RATIO
import cx.aswin.boxlore.core.designsystem.briefing.BriefingCover
import cx.aswin.boxlore.core.designsystem.briefing.briefingRegionLabel
import cx.aswin.boxlore.core.designsystem.theme.ShimmerScope
import cx.aswin.boxlore.core.designsystem.theme.m3Shimmer
import java.time.LocalDate

/**
 * The edition loading in the page's own layout. The cover art is bundled, so the real cover for the
 * chosen edition shows at once; the date line, headline, Listen button and first story shimmer in
 * their final places, so the page settles in place instead of swapping a spinner for content.
 */
@Composable
internal fun BriefingLoadingContent(region: String, topPadding: Dp) {
    val label = stringResource(R.string.briefing_loading_edition, stringResource(briefingRegionLabel(region)))
    val today = remember { LocalDate.now().toString() }
    ShimmerScope(active = true) {
        Column(
            Modifier.fillMaxSize().testTag("briefingLoading").semantics { contentDescription = label },
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BriefingCover(
                region = region,
                editionDate = today,
                modifier = Modifier.fillMaxWidth().aspectRatio(BRIEFING_COVER_ASPECT_RATIO).clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)),
                mastheadTopPadding = topPadding + 12.dp,
                mastheadScrim = true,
            )
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    LoadingBlock(Modifier.width(150.dp).height(14.dp))
                    LoadingBlock(Modifier.width(96.dp).height(40.dp), RoundedCornerShape(20.dp))
                }
                LoadingBlock(Modifier.fillMaxWidth(0.92f).height(30.dp))
                LoadingBlock(Modifier.fillMaxWidth(0.58f).height(30.dp))
                LoadingBlock(Modifier.fillMaxWidth().height(52.dp), RoundedCornerShape(28.dp))
            }
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth(),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LoadingBlock(Modifier.width(72.dp).height(20.dp))
                    LoadingBlock(Modifier.fillMaxWidth(0.85f).height(24.dp))
                    Box(Modifier.height(4.dp))
                    repeat(5) { line -> LoadingBlock(Modifier.fillMaxWidth(if (line == 4) 0.6f else 1f).height(14.dp)) }
                    Box(Modifier.height(8.dp))
                    LoadingBlock(Modifier.fillMaxWidth().height(52.dp), RoundedCornerShape(28.dp))
                }
            }
        }
    }
}

@Composable
private fun LoadingBlock(modifier: Modifier, shape: Shape = RoundedCornerShape(8.dp)) {
    val ink = MaterialTheme.colorScheme.onSurface
    Box(modifier.m3Shimmer(baseColor = ink.copy(alpha = 0.08f), highlightColor = ink.copy(alpha = 0.18f), shape = shape))
}
