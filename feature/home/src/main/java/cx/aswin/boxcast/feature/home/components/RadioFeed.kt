package cx.aswin.boxcast.feature.home.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.compose.AsyncImagePainter
import cx.aswin.boxcast.core.designsystem.components.RadioShapesFallback

// --- Data Models ---

data class MoodTile(
    val name: String,
    val tag: String,
    val gradient: List<Color>,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

// --- Removed Hardcoded Demo Stations ---

private val moodTiles = listOf(
    MoodTile("Chill", "ambient",
        listOf(Color(0xFF2A4858), Color(0xFF1A3040)), Icons.Rounded.GraphicEq),
    MoodTile("Energize", "dance",
        listOf(Color(0xFF4A2C2A), Color(0xFF3D1F1D)), Icons.Rounded.GraphicEq),
    MoodTile("Focus", "classical",
        listOf(Color(0xFF2D2A4A), Color(0xFF1E1B38)), Icons.Rounded.GraphicEq),
    MoodTile("Party", "pop",
        listOf(Color(0xFF4A2A3E), Color(0xFF381D2E)), Icons.Rounded.GraphicEq),
    MoodTile("News", "news",
        listOf(Color(0xFF1E3A5F), Color(0xFF152A45)), Icons.Rounded.GraphicEq),
    MoodTile("Indie", "indie",
        listOf(Color(0xFF3E3428), Color(0xFF2A231A)), Icons.Rounded.GraphicEq),
)

// Sorted: All first, then by installed user audience sort
private val countryOptions = listOf(
    "ALL" to "All",
    "US" to "United States",
    "IN" to "India",
    "GB" to "United Kingdom",
    "PH" to "Philippines",
    "RU" to "Russia",
    "MA" to "Morocco",
    "ET" to "Ethiopia",
    "AF" to "Afghanistan",
    "KE" to "Kenya",
    "JP" to "Japan",
    "BR" to "Brazil",
    "ID" to "Indonesia",
    "DE" to "Germany",
    "FR" to "France",
    "AU" to "Australia",
    "CA" to "Canada"
)

// --- Main Feed ---

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RadioFeed(
    listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    podcastRepository: cx.aswin.boxcast.core.data.PodcastRepository,
    playingStationId: String? = null,
    isRadioPlaying: Boolean = false,
    onPlayStation: (RadioStation) -> Unit = { _ -> },
    onOpenStory: (List<RadioStation>, Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    var selectedCountry by remember { mutableStateOf<String?>(null) } // null = loading
    var featured by remember { mutableStateOf<List<RadioStation>>(emptyList()) }
    var allStations by remember { mutableStateOf<List<RadioStation>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Helper to map API items to UI model
    fun mapStation(item: cx.aswin.boxcast.core.network.model.RadioStationItem): RadioStation {
        val colorInt = try {
            if (item.image.isNotEmpty()) {
                val hash = item.image.hashCode()
                val r = (hash and 0xFF0000) shr 16
                val g = (hash and 0x00FF00) shr 8
                val b = (hash and 0x0000FF)
                // Ensure darker tones for the fallback UI
                Color(r / 2, g / 2, b / 2)
            } else {
                Color(0xFF2A3A4A)
            }
        } catch(e: Exception) { Color(0xFF2A3A4A) }
        
        return RadioStation(
            id = item.id,
            name = item.title.trim(),
            genre = item.tags.firstOrNull { it.lowercase() != "radio" }?.replaceFirstChar { it.uppercase() } ?: "Radio",
            tags = item.tags.filter { it.lowercase() != "radio" && it.isNotBlank() },
            frequency = "Online",
            location = item.country.ifEmpty { "Global" },
            accentColor = colorInt,
            imageUrl = item.image,
            streamUrl = item.streamUrl,
            country = item.country,
            language = item.language,
            bitrate = item.bitrate,
            codec = item.codec,
            votes = item.votes
        )
    }

    // Fetch user's country and initial data
    androidx.compose.runtime.LaunchedEffect(Unit) {
        try {
            val cfResult = podcastRepository.getRadioLocate()
            val detectedCountry = cfResult?.country ?: "US"
            
            selectedCountry = if (countryOptions.any { it.first == detectedCountry }) {
                detectedCountry
            } else {
                "ALL"
            }
        } catch (e: Exception) {
            selectedCountry = "ALL"
        }
    }

    // Re-fetch trending and popular stations when country changes
    androidx.compose.runtime.LaunchedEffect(selectedCountry) {
        if (selectedCountry == null) return@LaunchedEffect
        isLoading = true
        try {
            val qCountry = if (selectedCountry == "ALL") null else selectedCountry
            
            val trendingData = podcastRepository.getTrendingRadioStations(country = qCountry, limit = 5)
            if (trendingData.isNotEmpty()) {
                featured = trendingData.map { mapStation(it) }
            }
            
            val popularData = podcastRepository.getPopularRadioStations(country = qCountry, limit = 50)
            if (popularData.isNotEmpty()) {
                allStations = popularData.map { mapStation(it) }
            }
        } catch(e: Exception) {
            if (e is java.util.concurrent.CancellationException || e is kotlinx.coroutines.CancellationException) throw e
            android.util.Log.e("RadioFeed", "Failed to load radio data", e)
        } finally {
            isLoading = false
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 160.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Country & Language selector bar
            item {
                CountryLanguageBar(
                    selectedCountry = selectedCountry ?: "",
                    onCountrySelected = { selectedCountry = it }
                )
            }

            // Hero — Trending Carousel
            item {
                if (featured.isNotEmpty()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Header
                        val currentCountryName = countryOptions.find { it.first == (selectedCountry ?: "ALL") }?.second ?: "Global"
                        val headerText = if (currentCountryName == "All" || currentCountryName == "Global") "Top Globally" else "Top in $currentCountryName"
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = headerText,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        val pagerState = rememberPagerState(pageCount = { featured.size })
                        HorizontalPager(
                            state = pagerState,
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            pageSpacing = 16.dp,
                            key = { page -> featured[page].id },
                            modifier = Modifier.fillMaxWidth()
                        ) { page ->
                            RadioHeroCard(
                                station = featured[page],
                                isPlaying = playingStationId == featured[page].id && isRadioPlaying,
                                onPlayClick = { onPlayStation(featured[page]) }
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            repeat(featured.size) { iteration ->
                                val color = if (pagerState.currentPage == iteration) 
                                    MaterialTheme.colorScheme.primary 
                                else 
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                                Box(
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .size(if (pagerState.currentPage == iteration) 8.dp else 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Quick Tune — Circular avatars
            item {
                if (allStations.isNotEmpty()) {
                    Column {
                        SectionHeader(title = "Quick Tune", modifier = Modifier.padding(horizontal = 20.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            itemsIndexed(allStations, key = { _, station -> station.id }) { index, station ->
                                QuickTuneAvatar(
                                    station = station,
                                    onClick = { onOpenStory(allStations, index) }
                                )
                            }
                        }
                    }
                }
            }

        // Browse by Mood — Genre Grid
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                SectionHeader(title = "Browse by Mood")
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    maxItemsInEachRow = 3
                ) {
                    moodTiles.forEach { mood ->
                        MoodGenreTile(
                            mood = mood,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Popular Stations — Horizontal cards
        item {
            Column {
                SectionHeader(
                    title = "Popular Stations",
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(allStations, key = { it.id }) { station ->
                        PopularStationCard(
                            station = station,
                            isPlaying = playingStationId == station.id && isRadioPlaying,
                            onClick = { onPlayStation(station) }
                        )
                    }
                }
            }
        }

        // All Stations list
        item {
            SectionHeader(
                title = "All Stations",
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }

        items(allStations, key = { it.id }) { station ->
            RadioStationRow(
                station = station,
                isPlaying = playingStationId == station.id && isRadioPlaying,
                onClick = { onPlayStation(station) },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}
}

// --- Section Header ---

@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
    )
}

// --- Country Chip Scroller ---

@Composable
private fun CountryLanguageBar(
    selectedCountry: String,
    onCountrySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val displayOptions = remember(selectedCountry) {
        val allOption = countryOptions.find { it.first == "ALL" } ?: ("ALL" to "All")
        val selectedOption = countryOptions.find { it.first == selectedCountry && it.first != "ALL" }
        val remainingOptions = countryOptions.filter { it.first != "ALL" && it.first != selectedCountry }
        
        buildList {
            add(allOption)
            if (selectedOption != null) {
                add(selectedOption)
            }
            addAll(remainingOptions)
        }
    }

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(displayOptions) { (code, label) ->
            val isSelected = selectedCountry == code
            Surface(
                shape = RoundedCornerShape(50),
                color = if (isSelected)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.clickable { onCountrySelected(code) }
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}

// --- Hero Card ---

@Composable
private fun RadioHeroCard(
    station: RadioStation,
    isPlaying: Boolean = false,
    onPlayClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .clickable { },
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = station.accentColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Blurred ambient background layer
            if (station.imageUrl.isNotEmpty()) {
                SubcomposeAsyncImage(
                    model = station.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().blur(80.dp).alpha(0.6f)
                )
            }
            
            // Subtle dark gradient
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.1f),
                                Color.Black.copy(alpha = 0.6f)
                            )
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // High Quality Artwork
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .shadow(16.dp, RoundedCornerShape(20.dp))
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (station.imageUrl.isNotEmpty()) {
                        SubcomposeAsyncImage(
                            model = station.imageUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (painter.state is AsyncImagePainter.State.Error) {
                                Icon(Icons.Rounded.Radio, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.White)
                            } else {
                                SubcomposeAsyncImageContent()
                            }
                        }
                    } else {
                        Icon(Icons.Rounded.Radio, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.White)
                    }
                }
                
                Spacer(modifier = Modifier.width(20.dp))
                
                // Content
                Column(
                    modifier = Modifier.fillMaxHeight(),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = station.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 24.sp
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (station.language.isNotEmpty()) {
                        Text(
                            text = station.language.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.85f),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    
                    if (station.tags.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = station.tags.take(2).joinToString(" • ") { it.replaceFirstChar { c -> c.uppercase() } },
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Tune In Button
                    androidx.compose.material3.Button(
                        onClick = onPlayClick,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(
                            if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isPlaying) "Playing" else "Tune In", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoPill(text: String, highlight: Boolean = false) {
    Box(
        modifier = Modifier
            .background(
                color = if (highlight) Color(0xFFFFD700).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (highlight) Color(0xFFFFD700) else Color.White,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// --- Quick Tune Avatar ---

@Composable
private fun QuickTuneAvatar(
    station: RadioStation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .border(
                    width = 2.5.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            station.accentColor,
                            station.accentColor.copy(alpha = 0.5f)
                        )
                    ),
                    shape = CircleShape
                )
                .padding(3.dp)
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (station.imageUrl.isNotEmpty()) {
                SubcomposeAsyncImage(
                    model = station.imageUrl,
                    contentDescription = station.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                ) {
                    if (painter.state is AsyncImagePainter.State.Error ||
                        painter.state is AsyncImagePainter.State.Loading) {
                        StationFallbackCircle(station)
                    } else {
                        SubcomposeAsyncImageContent()
                    }
                }
            } else {
                StationFallbackCircle(station)
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = station.name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(72.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun StationFallbackCircle(station: RadioStation) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        station.accentColor.copy(alpha = 0.6f),
                        station.accentColor.copy(alpha = 0.3f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = station.name.take(2).uppercase(),
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}

// --- Mood Genre Tile ---

@Composable
private fun MoodGenreTile(
    mood: MoodTile,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(72.dp)
            .clickable { },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(colors = mood.gradient)
                )
                .padding(12.dp)
        ) {
            Icon(
                imageVector = mood.icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.15f),
                modifier = Modifier
                    .size(32.dp)
                    .align(Alignment.TopEnd)
            )
            Text(
                text = mood.name,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White.copy(alpha = 0.9f),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomStart)
            )
        }
    }
}

// --- Popular Station Card ---

@Composable
private fun PopularStationCard(
    station: RadioStation,
    isPlaying: Boolean = false,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(160.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column {
            // Station image or fallback
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            ) {
                if (station.imageUrl.isNotEmpty()) {
                    SubcomposeAsyncImage(
                        model = station.imageUrl,
                        contentDescription = station.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (painter.state is AsyncImagePainter.State.Error ||
                            painter.state is AsyncImagePainter.State.Loading) {
                            RadioShapesFallback()
                        } else {
                            SubcomposeAsyncImageContent()
                        }
                    }
                } else {
                    RadioShapesFallback()
                }
            }

            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = station.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isPlaying) {
                        Icon(
                            Icons.Rounded.Pause,
                            contentDescription = "Playing",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = station.genre,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

// --- Station Row (All Stations list) ---

@Composable
private fun RadioStationRow(
    station: RadioStation,
    isPlaying: Boolean = false,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Station image — circular
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (station.imageUrl.isNotEmpty()) {
                    SubcomposeAsyncImage(
                        model = station.imageUrl,
                        contentDescription = station.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    ) {
                        if (painter.state is AsyncImagePainter.State.Error ||
                            painter.state is AsyncImagePainter.State.Loading) {
                            StationFallbackCircle(station)
                        } else {
                            SubcomposeAsyncImageContent()
                        }
                    }
                } else {
                    StationFallbackCircle(station)
                }
            }

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = station.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isPlaying) {
                        Icon(
                            Icons.Rounded.Pause,
                            contentDescription = "Playing",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "${station.genre} • ${station.location}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Frequency badge
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Text(
                    text = station.frequency,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }

            // Play button
            Surface(
                shape = CircleShape,
                color = station.accentColor.copy(alpha = 0.15f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Play ${station.name}",
                        tint = station.accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
