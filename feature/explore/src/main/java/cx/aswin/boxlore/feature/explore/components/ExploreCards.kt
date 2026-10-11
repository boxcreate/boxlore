package cx.aswin.boxlore.feature.explore.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.SentimentVerySatisfied
import androidx.compose.material.icons.rounded.SportsBaseball
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.list.LazyListKeyPolicy
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.designsystem.theme.rememberSectionHeaderFontFamily
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.explore.R

/** Cap for By-concept podcast-vector rail — keep episodes as the primary feed. */
private const val RELATED_SHOWS_RAIL_MAX = 8

/**
 * Compact horizontal show strip for concept search: podcast hits above the fold
 * without burying the episode hero / bento stream.
 */
@Composable
fun ExploreRelatedShowsRail(
    podcasts: List<Podcast>,
    onPodcastClick: (podcast: Podcast, index: Int) -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
) {
    val railItems = remember(podcasts) {
        LazyListKeyPolicy.deduplicateById(podcasts) { it.id }.take(RELATED_SHOWS_RAIL_MAX)
    }
    if (railItems.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        ExploreSearchSectionHeader(
            title = title ?: stringResource(R.string.explore_related_shows),
            icon = Icons.Rounded.Podcasts,
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 4.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(
                railItems,
                key = { index, podcast -> LazyListKeyPolicy.safeKey(podcast.id, index, prefix = "related_show") }
            ) { index, podcast ->
                // Clickable without a clip shape — clipping the whole column was eating title glyphs
                // at the bottom-left of the rounded bounds.
                Column(
                    modifier =
                    Modifier
                        .width(104.dp)
                        .expressiveClickable {
                            onPodcastClick(podcast, index)
                        },
                ) {
                    OptimizedImage(
                        url = podcast.imageUrl,
                        proxyWidth = 220,
                        contentDescription = podcast.title,
                        contentScale = ContentScale.Crop,
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = podcast.title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = GoogleSansWeight.semiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun ExploreVibeCard(
    vibe: Pair<String, String>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val icon = remember(vibe.first) { moodIconForId(vibe.first) }
    val titleHeight = with(androidx.compose.ui.platform.LocalDensity.current) {
        MaterialTheme.typography.titleSmall.lineHeight.toDp() * 2
    }
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(titleHeight), contentAlignment = Alignment.CenterStart) {
                Text(
                    text = vibe.second,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = GoogleSansWeight.semiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun CuratedVibeHeader(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Icon(
            imageVector = Icons.Rounded.Star,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontFamily = rememberSectionHeaderFontFamily()
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * For You navigation shortcuts in the collapsing header, distinct from genre filters.
 */
@Composable
fun ExploreVibeChipRow(
    vibes: List<Pair<String, String>>,
    onVibeSelected: (id: String, name: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val distinctVibes = remember(vibes) {
        LazyListKeyPolicy.deduplicateById(vibes) { it.first }
    }
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        itemsIndexed(
            distinctVibes,
            key = { _, vibe -> LazyListKeyPolicy.safeKey(vibe.first, prefix = "vibe_chip") }
        ) { index, vibe ->
            ExploreVibeChip(
                vibe = vibe,
                onClick = { onVibeSelected(vibe.first, vibe.second) },
                first = index == 0,
                last = index == distinctVibes.lastIndex,
            )
        }
    }
}

/**
 * Connected topic shortcuts: strong standalone icons, curved outer ends and tight inner corners.
 */
@Composable
fun ExploreVibeChip(
    vibe: Pair<String, String>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    first: Boolean = true,
    last: Boolean = true,
) {
    val icon = remember(vibe.first) { vibeCatcherIcon(vibe.first) }
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp).widthIn(max = 248.dp),
        shape = RoundedCornerShape(
            topStart = if (first) 26.dp else 8.dp,
            bottomStart = if (first) 26.dp else 8.dp,
            topEnd = if (last) 26.dp else 8.dp,
            bottomEnd = if (last) 26.dp else 8.dp,
        ),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = vibe.second,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = GoogleSansWeight.semiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun vibeCatcherIcon(vibeId: String): androidx.compose.ui.graphics.vector.ImageVector = moodIconForId(vibeId)

/** Per-mood icon for For You chips and the mood results header. */
internal fun moodIconForId(moodId: String): androidx.compose.ui.graphics.vector.ImageVector = when (moodId) {
    "morning_news" -> Icons.Rounded.Newspaper
    "morning_motivation" -> Icons.Rounded.Bolt
    "business_insider" -> Icons.Rounded.Work
    "science_explainer" -> Icons.Rounded.Science
    "tech_culture" -> Icons.Rounded.Computer
    "creative_focus" -> Icons.Rounded.Palette
    "comedy_gold" -> Icons.Rounded.SentimentVerySatisfied
    "tv_film_buff" -> Icons.Rounded.Movie
    "sports_fan" -> Icons.Rounded.SportsBaseball
    "true_crime_sleep" -> Icons.Rounded.Fingerprint
    "history_buff" -> Icons.Rounded.AccountBalance
    "mystery_thriller" -> Icons.Rounded.Visibility
    else -> Icons.Rounded.AutoAwesome
}
