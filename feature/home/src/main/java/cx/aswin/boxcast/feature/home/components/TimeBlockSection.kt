package cx.aswin.boxcast.feature.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxcast.core.model.Episode
import cx.aswin.boxcast.core.model.Podcast
import cx.aswin.boxcast.feature.home.CuratedTimeBlock
import cx.aswin.boxcast.core.designsystem.theme.SectionHeaderFontFamily


import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Composable
fun TimeBlockSection(
    data: CuratedTimeBlock,
    onCuratedEpisodeClick: (Episode, Podcast, String, Int) -> Unit,
    onImpression: (String, List<String>) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {

    val gradientBrush = when (data.title) {
        "Good Morning" -> Brush.verticalGradient(
            colors = listOf(
                Color(0xFFFFE082).copy(alpha = 0.12f), // Sunrise Gold
                Color.Transparent
            )
        )
        "Afternoon Break" -> Brush.verticalGradient(
            colors = listOf(
                Color(0xFFB3E5FC).copy(alpha = 0.15f), // Sky Blue
                Color.Transparent
            )
        )
        "Evening Unwind" -> Brush.verticalGradient(
            colors = listOf(
                Color(0xFFE1BEE7).copy(alpha = 0.18f), // Sunset Purple
                Color.Transparent
            )
        )
        "Late Night Listen" -> Brush.verticalGradient(
            colors = listOf(
                Color(0xFFC5CAE9).copy(alpha = 0.18f), // Midnight Indigo
                Color.Transparent
            )
        )
        else -> null
    }

    LaunchedEffect(data.title) {
        onImpression(data.title, data.sections.map { it.category })
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (gradientBrush != null) {
                        Modifier.background(gradientBrush)
                    } else {
                        Modifier
                    }
                )
                .padding(16.dp)
        ) {
            // --- Master Header ---
            Column(
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Icon(
                        imageVector = data.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Column {
                        Text(
                            text = data.title,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontFamily = SectionHeaderFontFamily,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            ),
                            letterSpacing = (-0.5).sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = data.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // --- Genre Rails ---
            data.sections.forEachIndexed { index, section ->

                Column {
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    // Rail
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 0.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(section.podcasts.size) { i ->
                            val podcast = section.podcasts[i]
                            val episode = podcast.latestEpisode
                            
                            if (episode != null) {
                                CuratedEpisodeCard(
                                    podcast = podcast,
                                    episode = episode,
                                    onClick = {
                                        cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackCuratedCardTapped(
                                            podcastId = podcast.id,
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
                
                if (index < data.sections.size - 1) {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}
