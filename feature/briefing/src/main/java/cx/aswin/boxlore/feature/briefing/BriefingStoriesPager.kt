package cx.aswin.boxlore.feature.briefing

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.palette.graphics.Palette
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import cx.aswin.boxlore.core.designsystem.components.BoxLoreLoader
import cx.aswin.boxlore.core.designsystem.components.ExpressivePlayButton
import cx.aswin.boxlore.core.designsystem.components.ExpressivePlayButtonState
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.theme.SectionHeaderFontFamily
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Briefing
import cx.aswin.boxlore.core.model.Episode
import java.net.URI

@Composable
internal fun BriefingStoriesPager(
    briefing: Briefing,
    chapters: List<cx.aswin.boxlore.core.model.Chapter>,
    isPlaying: Boolean,
    currentPositionMs: Long,
    accentColor: Color,
    onEpisodeClick: (Episode) -> Unit,
    onPlayPauseClick: (Long?) -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { chapters.size })
    val isDragged by pagerState.interactionSource.collectIsDraggedAsState()
    val scope = rememberCoroutineScope()
    var userClickedPage by remember { mutableStateOf<Int?>(null) }

    // 1. Playback -> UI Paging Sync
    // Find the currently active chapter index based on playback position
    val activeChapterIndex = remember(chapters, currentPositionMs) {
        val index = chapters.indexOfLast { currentPositionMs >= it.startTime * 1000 }
        if (index != -1) index else 0
    }

    LaunchedEffect(activeChapterIndex) {
        if (!isDragged) {
            val clickedPage = userClickedPage
            if (clickedPage != null) {
                if (activeChapterIndex == clickedPage) {
                    userClickedPage = null
                }
            } else if (activeChapterIndex >= 0 && activeChapterIndex < chapters.size) {
                pagerState.animateScrollToPage(activeChapterIndex)
            }
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        val chapter = chapters.getOrNull(pagerState.currentPage)
        if (chapter != null && (isDragged || userClickedPage != null)) {
            val method = if (isDragged) "swipe" else "click"
            cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackDailyBriefingInteraction(
                action = "chapter_swiped",
                region = briefing.region,
                date = briefing.date,
                extraProps = mapOf(
                    "chapter_index" to pagerState.currentPage,
                    "chapter_title" to chapter.title,
                    "method" to method
                )
            )
        }
    }

    val paragraphs = remember(briefing.script) {
        val raw = briefing.script.split("\n\n").filter { it.isNotBlank() }
        raw.mapIndexed { index, paragraph ->
            var text = paragraph.trim()
            if (index == 0) {
                val greetingPrefixes = listOf(
                    "This is the boxlore brief for",
                    "This is the boxcast brief for",
                    "Welcome to the boxlore brief for",
                    "Welcome to the boxcast brief for",
                    "Welcome to the daily brief for"
                )
                for (prefix in greetingPrefixes) {
                    if (text.startsWith(prefix, ignoreCase = true)) {
                        val periodIndex = text.indexOf('.')
                        if (periodIndex != -1 && periodIndex < 120) {
                            text = text.substring(periodIndex + 1).trim()
                        }
                        break
                    }
                }
            }
            if (index == raw.lastIndex) {
                val outroSubstrings = listOf(
                    "That's your boxlore brief. See you tomorrow.",
                    "That's your boxcast brief. See you tomorrow.",
                    "That's your boxlore brief. See you tomorrow",
                    "That's your boxcast brief. See you tomorrow",
                    "See you tomorrow.",
                    "See you tomorrow"
                )
                for (outro in outroSubstrings) {
                    val outroIndex = text.indexOf(outro, ignoreCase = true)
                    if (outroIndex != -1) {
                        text = text.substring(0, outroIndex).trim()
                        break
                    }
                }
                val lastBoxloreIndex = text.lastIndexOf("boxlore brief", ignoreCase = true)
                val lastBoxcastIndex = text.lastIndexOf("boxcast brief", ignoreCase = true)
                val lastIndex = lastBoxloreIndex.coerceAtLeast(lastBoxcastIndex)
                if (lastIndex != -1 && text.length - lastIndex < 100) {
                    val lastPeriod = text.lastIndexOf('.', text.length - 2)
                    if (lastPeriod != -1) {
                        text = text.substring(0, lastPeriod + 1).trim()
                    }
                }
            }
            text
        }.filter { it.isNotBlank() }
    }

    // Swipable cards pager
    if (chapters.isNotEmpty()) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 24.dp),
            pageSpacing = 16.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(500.dp)
        ) { page ->
            val chapter = chapters[page]
            val paragraph = paragraphs.getOrNull(page) ?: ""
            val isThisCardActive = activeChapterIndex == page && isPlaying

            Card(
               shape = RoundedCornerShape(24.dp),
               colors = CardDefaults.cardColors(
                   containerColor = if (isThisCardActive) MaterialTheme.colorScheme.primaryContainer
                                   else MaterialTheme.colorScheme.surfaceContainerHigh
               ),
               border = BorderStroke(
                   1.dp,
                   if (isThisCardActive) accentColor
                   else MaterialTheme.colorScheme.outlineVariant
               ),
               modifier = Modifier
                   .fillMaxWidth()
                   .fillMaxHeight()
                   .shadow(6.dp, RoundedCornerShape(24.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        // Card Header: STORY X OF Y & Time Badge
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isThisCardActive) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)
                                        else MaterialTheme.colorScheme.secondaryContainer,
                            ) {
                                Text(
                                    text = "STORY ${page + 1} OF ${chapters.size}".uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isThisCardActive) MaterialTheme.colorScheme.onPrimaryContainer
                                            else MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            Text(
                                text = formatChapterTime(chapter.startTime.toLong()),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isThisCardActive) accentColor
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Story Headline
                        Text(
                            text = chapter.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isThisCardActive) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Story Paragraph (Scrollable inside the card)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = paragraph.trim(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    lineHeight = 22.sp,
                                    color = if (isThisCardActive) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                )


                            }
                        }
                    }

                    // Related Episodes inline (compact row)
                    val recs = chapter.relatedEpisodes
                    if (!recs.isNullOrEmpty()) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            thickness = 0.8.dp,
                            color = if (isThisCardActive) Color.White.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Podcasts,
                                    contentDescription = null,
                                    tint = if (isThisCardActive) Color.White.copy(alpha = 0.6f)
                                           else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "LISTEN DEEPER",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        letterSpacing = 1.2.sp,
                                        fontSize = 9.sp
                                    ),
                                    fontWeight = FontWeight.Bold,
                                    color = if (isThisCardActive) Color.White.copy(alpha = 0.7f)
                                           else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(recs) { episode ->
                                    CompactEpisodeChip(
                                        episode = episode,
                                        isActiveCard = isThisCardActive,
                                        accentColor = accentColor,
                                        onClick = {
                                            cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackDailyBriefingRelatedEpisodeClicked(
                                                region = briefing.region,
                                                date = briefing.date,
                                                chapterIndex = page,
                                                episodeId = episode.id,
                                                episodeTitle = episode.title,
                                                podcastId = episode.podcastId ?: "",
                                                podcastTitle = episode.podcastTitle ?: ""
                                            )
                                            onEpisodeClick(episode)
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Card prominent play button
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isThisCardActive) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .expressiveClickable(
                                shape = RoundedCornerShape(16.dp),
                                onClick = {
                                    val storyProps = mapOf(
                                        "chapter_index" to page,
                                        "chapter_title" to chapter.title,
                                        "start_time_seconds" to chapter.startTime.toLong()
                                    )
                                    if (isThisCardActive) {
                                        cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackDailyBriefingInteraction(
                                            action = "story_pause_clicked",
                                            region = briefing.region,
                                            date = briefing.date,
                                            extraProps = storyProps
                                        )
                                        onPlayPauseClick(null)
                                    } else {
                                        userClickedPage = page
                                        scope.launch {
                                            pagerState.animateScrollToPage(page)
                                        }
                                        cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackDailyBriefingInteraction(
                                            action = "story_play_clicked",
                                            region = briefing.region,
                                            date = briefing.date,
                                            extraProps = storyProps
                                        )
                                        onPlayPauseClick(chapter.startTime.toLong() * 1000L)
                                    }
                                }
                            )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = if (isThisCardActive) Icons.Rounded.Pause
                                              else Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = if (isThisCardActive) MaterialTheme.colorScheme.primaryContainer
                                       else MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isThisCardActive) "PAUSE STORY" else "PLAY THIS STORY",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (isThisCardActive) MaterialTheme.colorScheme.primaryContainer
                                       else MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }
        }
    } else {
        // Fallback if chapters are empty
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(380.dp)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Loading script...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Page indicators dots
    if (chapters.isNotEmpty()) {
        Row(
            modifier = Modifier
                .height(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(chapters.size) { iteration ->
                val isSelected = pagerState.currentPage == iteration
                val color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(color)
                        .size(if (isSelected) 8.dp else 6.dp)
                )
            }
        }
    }
}
