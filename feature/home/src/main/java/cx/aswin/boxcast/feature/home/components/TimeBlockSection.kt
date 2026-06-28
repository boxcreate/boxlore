package cx.aswin.boxcast.feature.home.components

import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxcast.core.model.Episode
import cx.aswin.boxcast.core.model.Podcast
import cx.aswin.boxcast.feature.home.CuratedTimeBlock
import cx.aswin.boxcast.core.designsystem.theme.SectionHeaderFontFamily

import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.SportsBaseball
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.automirrored.outlined.TrendingUp

@Composable
private fun getGenreStyle(categoryId: String): ImageVector {
    return when (categoryId) {
        "morning_news" -> Icons.Outlined.Newspaper
        "morning_motivation" -> Icons.Outlined.Bolt
        "business_insider" -> Icons.AutoMirrored.Outlined.TrendingUp
        "science_explainer" -> Icons.Outlined.Science
        "tech_culture" -> Icons.Outlined.Memory
        "creative_focus" -> Icons.Outlined.Palette
        "comedy_gold" -> Icons.Outlined.EmojiEmotions
        "tv_film_buff" -> Icons.Outlined.Movie
        "sports_fan" -> Icons.Outlined.SportsBaseball
        "true_crime_sleep" -> Icons.Outlined.Fingerprint
        "history_buff" -> Icons.Outlined.AccountBalance
        "mystery_thriller" -> Icons.Outlined.Visibility
        else -> Icons.Outlined.AutoAwesome
    }
}

@Composable
fun TimeBlockSection(
    data: CuratedTimeBlock,
    onCuratedEpisodeClick: (Episode, Podcast, String, Int) -> Unit,
    onImpression: (String, List<String>) -> Unit = { _, _ -> },
    onSeeAllClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    LaunchedEffect(data.title) {
        onImpression(data.title, data.sections.map { it.category })
    }

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // --- Secondary Time Block Header ---
        val themeColor = MaterialTheme.colorScheme.primary
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 0.dp, bottom = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                AnimatedTimeBlockIcon(
                    title = data.title,
                    themeColor = themeColor,
                    fallbackIcon = data.icon
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = data.title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = SectionHeaderFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Action chevron decorator
            FilledTonalIconButton(
                onClick = onSeeAllClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = "See All",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        // --- Genre Rails ---
        data.sections.forEachIndexed { index, section ->
            Column {
                val icon = getGenreStyle(section.category)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(
                        top = if (index == 0) 8.dp else 16.dp,
                        bottom = 8.dp
                    )
                ) {
                    // Clean, standard floating icon matching Discover & OnTheRise section headers exactly
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, // Unified M3 primary theme color
                        modifier = Modifier.size(22.dp) // 22dp is perfect for junior curated rails subheaders
                    )
                    
                    Spacer(modifier = Modifier.width(10.dp))
                    
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.1).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Rail
                val distinctPodcasts = remember(section.podcasts) { section.podcasts.distinctBy { it.id } }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 0.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        count = distinctPodcasts.size,
                        key = { i -> distinctPodcasts[i].id }
                    ) { i ->
                        val podcast = distinctPodcasts[i]
                        val episode = podcast.latestEpisode
                        
                        if (episode != null) {
                            CuratedEpisodeCard(
                                podcast = podcast,
                                episode = episode,
                                onClick = {
                                    cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackCuratedCardTapped(
                                        podcastId = podcast.id,
                                        podcastName = podcast.title,
                                        vibeId = section.category,
                                        positionIndex = i
                                    )
                                    onCuratedEpisodeClick(episode, podcast, section.category, i)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AnimatedTimeBlockIcon(title: String, themeColor: Color, fallbackIcon: androidx.compose.ui.graphics.vector.ImageVector) {
    val icon = when (title) {
        "Good Morning", "Afternoon Break" -> Icons.Rounded.WbSunny
        "Evening Unwind" -> Icons.Rounded.WbSunny
        "Late Night Listen" -> Icons.Rounded.NightsStay
        else -> fallbackIcon
    }
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = themeColor,
        modifier = Modifier.size(24.dp)
    )
}
