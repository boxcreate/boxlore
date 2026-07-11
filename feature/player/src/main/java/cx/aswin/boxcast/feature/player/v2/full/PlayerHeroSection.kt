package cx.aswin.boxcast.feature.player.v2.full

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import cx.aswin.boxcast.core.model.Chapter
import cx.aswin.boxcast.core.model.Episode
import cx.aswin.boxcast.core.model.Podcast
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Composable
fun PlayerHeroSection(
    episode: Episode,
    podcast: Podcast,
    queue: List<Episode>,
    podcasts: Map<String, Podcast> = emptyMap(),
    positionFlow: Flow<Long>,
    chapters: List<Chapter> = emptyList(),
    colorScheme: ColorScheme,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    skipDirection: Int? = null,
    onEpisodeTitleClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val activeChapter by remember(chapters, positionFlow) {
        positionFlow
            .map { pos -> chapters.lastOrNull { (it.startTime * 1000).toLong() <= pos } }
            .distinctUntilChanged()
    }.collectAsState(initial = null)

    val artworkUrl = activeChapter?.img?.takeIf { it.isNotBlank() }
        ?: episode.imageUrl?.takeIf { it.isNotBlank() }
        ?: podcast.imageUrl

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(420.dp),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(artworkUrl)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.45f)
                .blur(50.dp, edgeTreatment = androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            colorScheme.surface.copy(alpha = 0.55f),
                            colorScheme.surface,
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.weight(0.15f))

            EpisodeArtCarousel(
                currentEpisode = episode,
                queue = queue,
                podcasts = podcasts,
                colorScheme = colorScheme,
                onSkipPrevious = onSkipPrevious,
                onSkipNext = onSkipNext,
                skipDirection = skipDirection,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = episode.title.replace("+", " "),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onEpisodeTitleClick)
                    .basicMarquee(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            PlayerMetadataChips(
                publishedDateSeconds = episode.publishedDate,
                durationSeconds = episode.duration,
                currentChapter = activeChapter,
                colorScheme = colorScheme,
            )

            Spacer(modifier = Modifier.weight(0.1f))
        }
    }
}
