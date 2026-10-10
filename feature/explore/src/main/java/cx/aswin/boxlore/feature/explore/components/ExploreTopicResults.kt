package cx.aswin.boxlore.feature.explore.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.catalog.content.CuratedMoods
import cx.aswin.boxlore.core.designsystem.list.LazyListKeyPolicy
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.rememberSectionHeaderFontFamily
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.explore.R

/** Topic destinations retain the catalog's ordering and card positions. */
internal fun LazyStaggeredGridScope.exploreTopicResults(
    title: String,
    podcasts: List<Podcast>,
    loading: Boolean,
    onBack: () -> Unit,
    onPodcastClick: (Podcast, Int) -> Unit,
) {
    item(key = "topic_heading", span = StaggeredGridItemSpan.FullLine) {
        ExploreTopicHeading(title)
    }
    when {
        loading && podcasts.isEmpty() -> items(6, key = { "topic_loading_$it" }) { index ->
            Box(Modifier.testTag("topic_loading_$index")) { ExploreBrowseCardSkeleton(featured = false) }
        }
        podcasts.isEmpty() -> item(key = "topic_empty", span = StaggeredGridItemSpan.FullLine) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.explore_topic_empty),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                FilledTonalButton(onClick = onBack) {
                    Text(stringResource(R.string.explore_back_to_browse))
                }
            }
        }
        else -> itemsIndexed(
            podcasts,
            key = { index, podcast -> LazyListKeyPolicy.safeKey(podcast.id, index, prefix = "topic") },
        ) { index, podcast ->
            ExploreBrowsePodcastCard(
                podcast = podcast,
                featured = false,
                showGenre = false,
                onClick = { onPodcastClick(podcast, index) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExploreTopicHeading(title: String) {
    val mood = remember(title) { CuratedMoods.all.find { it.title == title } }
    val icon = remember(mood?.id) { moodIconForId(mood?.id.orEmpty()) }
    val shape = MaterialShapes.Cookie6Sided.toShape()
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = shape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(52.dp),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(26.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = rememberSectionHeaderFontFamily()),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            mood?.subtitle?.let { subtitle ->
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun ExploreTopicToolbar(onBack: () -> Unit, onSearch: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().testTag("explore_topic_toolbar"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalButton(
            onClick = onBack,
            modifier = Modifier.weight(1f, fill = false).heightIn(min = 48.dp).testTag("explore_topic_back"),
            shape = CircleShape,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                stringResource(R.string.explore_back_to_browse),
                modifier = Modifier.padding(start = 8.dp),
                fontWeight = GoogleSansWeight.semiBold,
            )
        }
        Box(Modifier.width(12.dp))
        FilledTonalIconButton(
            onClick = onSearch,
            modifier = Modifier.width(64.dp).height(48.dp).testTag("explore_topic_search"),
            shape = CircleShape,
        ) {
            Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.explore_search))
        }
    }
}
