package cx.aswin.boxlore.feature.info.sections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Person
import cx.aswin.boxlore.feature.info.PodcastInfoUiState
import cx.aswin.boxlore.feature.info.components.PodcastInfoMetadataChipsRow
import cx.aswin.boxlore.feature.info.components.calculateUpdateFrequencyData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


@Composable
internal fun PodcastInfoHeroSection(
    state: PodcastInfoUiState.Success,
    sortedPersons: List<Person>,
    isDescExpanded: Boolean,
    onDescExpandedChange: (Boolean) -> Unit,
    onPlayEpisode: (Episode) -> Unit,
    onPodcastClick: (String) -> Unit,
    context: android.content.Context,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 1. Centered Large Image
        Surface(
            modifier = Modifier.size(180.dp),
            shape = MaterialTheme.shapes.extraLarge,
            shadowElevation = 8.dp,
        ) {
            OptimizedImage(
                url = state.podcast.imageUrl.takeIf { it.isNotEmpty() } ?: state.podcast.fallbackImageUrl,
                proxyWidth = 600, // 180dp * ~3x density
                contentDescription = state.podcast.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 2. Title & Artist
        Text(
            text = state.podcast.title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = state.podcast.artist,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Update Frequency Calculation — filters/sorts episodes and analyzes release intervals,
        // which can be expensive for large RSS catalogs, so it runs off the composition thread
        // and is cancelled/restarted automatically as its keys change.
        var cachedFrequencyData by remember(state.podcast.id) { mutableStateOf<Pair<String, ImageVector>?>(null) }

        val frequencyData by produceState(
            initialValue = cachedFrequencyData,
            state.podcast,
            state.episodes,
            state.currentSort,
            state.searchQuery,
        ) {
            val computed =
                withContext(Dispatchers.Default) {
                    calculateUpdateFrequencyData(
                        podcast = state.podcast,
                        episodes = state.episodes,
                        currentSort = state.currentSort,
                        searchQuery = state.searchQuery,
                        cachedFrequencyData = cachedFrequencyData,
                    )
                }
            cachedFrequencyData = computed
            value = computed
        }

        // 3. Scrollable Metadata Chips Row — centered
        val trailerEpisode =
            remember(state.episodes) {
                state.episodes.firstOrNull { it.episodeType == "trailer" }
            }

        PodcastInfoMetadataChipsRow(
            podcast = state.podcast,
            sortedPersons = sortedPersons,
            trailerEpisode = trailerEpisode,
            frequencyData = frequencyData,
            context = context,
            onPlayTrailer = onPlayEpisode,
        )

        Spacer(modifier = Modifier.height(18.dp))

        val strippedDesc = remember(state.podcast.description) { stripHtml(state.podcast.description) }
        PodcastInfoDescriptionSection(
            strippedDesc = strippedDesc,
            isLocked = state.podcast.isLocked,
            podroll = state.podcast.podroll,
            isDescExpanded = isDescExpanded,
            onToggleExpanded = { onDescExpandedChange(!isDescExpanded) },
            onPodcastClick = onPodcastClick,
        )
    }
}

