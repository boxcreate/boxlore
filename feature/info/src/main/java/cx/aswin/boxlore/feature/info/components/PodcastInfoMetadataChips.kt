package cx.aswin.boxlore.feature.info.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.theme.ExpressiveShapes
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Person
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.info.EpisodeSort


internal fun calculateUpdateFrequencyData(
    podcast: Podcast,
    episodes: List<Episode>,
    currentSort: EpisodeSort,
    searchQuery: String,
    cachedFrequencyData: Pair<String, ImageVector>?,
): Pair<String, ImageVector>? {
    // If sorted by oldest or searching, use the cached calculation (to prevent "Inactive / Ended" bug)
    if (currentSort != EpisodeSort.NEWEST || searchQuery.isNotEmpty()) {
        return cachedFrequencyData
    }

    // 1. Securely sort and take the latest 15 episodes (Recent History)
    val validEpisodes = filterValidFrequencyEpisodes(episodes)
    val daysSinceLatest = computeDaysSinceLatest(podcast, validEpisodes)

    // 2. Check if it's dead or on hiatus
    dormancyStatus(daysSinceLatest)?.let { return it }

    // 3. Check for decay / delayed seasons (Between Seasons check)
    val medianIntervalDays = computeMedianIntervalDays(validEpisodes, podcast)
    betweenSeasonsStatus(medianIntervalDays, daysSinceLatest)?.let { return it }

    // 4. Use the explicit updateFrequency tag if available
    explicitFrequencyTag(podcast.updateFrequency)?.let { return it }

    // 5. Fallback: Predict frequency using Median and Day of Week counts
    if (validEpisodes.size < 4 || medianIntervalDays == null) return cachedFrequencyData
    return predictedFrequencyFromPattern(validEpisodes, medianIntervalDays) ?: cachedFrequencyData
}

internal fun filterValidFrequencyEpisodes(episodes: List<Episode>): List<Episode> =
    episodes
        .filter { it.episodeType != "trailer" && it.episodeType != "bonus" && it.publishedDate > 0 }
        .sortedByDescending { it.publishedDate }
        .take(15)

internal fun computeDaysSinceLatest(
    podcast: Podcast,
    validEpisodes: List<Episode>,
): Long? {
    val latestEpisodeDate = validEpisodes.firstOrNull()?.publishedDate ?: podcast.latestEpisode?.publishedDate
    return latestEpisodeDate?.let { (System.currentTimeMillis() / 1000 - it) / (60 * 60 * 24) }
}

internal fun dormancyStatus(daysSinceLatest: Long?): Pair<String, ImageVector>? {
    if (daysSinceLatest == null || daysSinceLatest <= 0) return null
    return when {
        daysSinceLatest > 365 -> Pair("Inactive / Ended", Icons.Rounded.PauseCircle)
        daysSinceLatest > 180 -> Pair("On Hiatus", Icons.Rounded.PauseCircle)
        else -> null
    }
}

/** Median gap (days) between the latest episodes, falling back to the feed's declared tag. */
internal fun computeMedianIntervalDays(
    validEpisodes: List<Episode>,
    podcast: Podcast,
): Long? {
    if (validEpisodes.size >= 4) {
        val intervals = mutableListOf<Long>()
        for (i in 0 until validEpisodes.size - 1) {
            val newer = validEpisodes[i].publishedDate
            val older = validEpisodes[i + 1].publishedDate
            val daysDiff = (newer - older) / (60 * 60 * 24)
            if (daysDiff >= 0) intervals.add(daysDiff)
        }
        if (intervals.isEmpty()) return null
        val sortedIntervals = intervals.sorted()
        return sortedIntervals[sortedIntervals.size / 2]
    }
    // Estimate based on tag if episodes are scarce
    val tag = podcast.updateFrequency?.lowercase() ?: ""
    return when {
        tag.contains("daily") -> 1L
        tag.contains("weekly") -> 7L
        tag.contains("bi-weekly") || tag.contains("2 weeks") -> 14L
        tag.contains("monthly") -> 30L
        else -> null
    }
}

internal fun betweenSeasonsStatus(
    medianIntervalDays: Long?,
    daysSinceLatest: Long?,
): Pair<String, ImageVector>? {
    if (medianIntervalDays == null || medianIntervalDays <= 3 || daysSinceLatest == null) return null
    return if (daysSinceLatest > (medianIntervalDays * 2)) {
        Pair("Between Seasons", Icons.Rounded.HourglassBottom)
    } else {
        null
    }
}

internal fun explicitFrequencyTag(tag: String?): Pair<String, ImageVector>? {
    if (tag.isNullOrBlank()) return null
    val cleanText = tag.trim().lowercase()
    val parsedDouble = cleanText.toDoubleOrNull()
    val formattedText =
        if (parsedDouble != null) {
            when {
                parsedDouble >= 7.0 -> "Releases Daily"
                parsedDouble >= 2.0 -> "Releases Multi-Weekly"
                parsedDouble >= 1.0 -> "Releases Weekly"
                parsedDouble >= 0.5 -> "Releases Every 2 Weeks"
                parsedDouble >= 0.1 -> "Releases Monthly"
                else -> "Releases Occasionally"
            }
        } else {
            when (cleanText) {
                "daily" -> "Releases Daily"
                "weekly" -> "Releases Weekly"
                "monthly" -> "Releases Monthly"
                "biweekly", "bi-weekly" -> "Releases Every 2 Weeks"
                else -> tag.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
        }
    val icon = if (formattedText.contains("Daily", ignoreCase = true)) Icons.Rounded.Bolt else Icons.Rounded.CalendarMonth
    return Pair(formattedText, icon)
}

/** Most common weekday name across [validEpisodes], if at least half of them share one. */
internal fun commonReleaseDayName(validEpisodes: List<Episode>): String? {
    val calendar = java.util.Calendar.getInstance()
    val dayCounts = IntArray(8)
    for (ep in validEpisodes) {
        calendar.timeInMillis = ep.publishedDate * 1000
        dayCounts[calendar.get(java.util.Calendar.DAY_OF_WEEK)]++
    }
    var maxDay = -1
    var maxCount = 0
    for (i in 1..7) {
        if (dayCounts[i] > maxCount) {
            maxCount = dayCounts[i]
            maxDay = i
        }
    }
    if (maxCount < (validEpisodes.size * 0.5).toInt()) return null
    return when (maxDay) {
        java.util.Calendar.SUNDAY -> "Sundays"
        java.util.Calendar.MONDAY -> "Mondays"
        java.util.Calendar.TUESDAY -> "Tuesdays"
        java.util.Calendar.WEDNESDAY -> "Wednesdays"
        java.util.Calendar.THURSDAY -> "Thursdays"
        java.util.Calendar.FRIDAY -> "Fridays"
        java.util.Calendar.SATURDAY -> "Saturdays"
        else -> null
    }
}

internal fun predictedFrequencyFromPattern(
    validEpisodes: List<Episode>,
    medianIntervalDays: Long,
): Pair<String, ImageVector>? {
    val commonDayName = commonReleaseDayName(validEpisodes)
    return when (medianIntervalDays) {
        in 0..1 -> Pair("Releases Daily", Icons.Rounded.Bolt)
        in 2..4 -> Pair("Releases Multi-Weekly", Icons.Rounded.CalendarMonth)
        in 5..8 ->
            Pair(
                if (commonDayName != null) "Weekly on $commonDayName" else "Releases Weekly",
                Icons.Rounded.CalendarMonth,
            )
        in 12..16 ->
            Pair(
                if (commonDayName != null) "Every 2 Weeks on $commonDayName" else "Releases Every 2 Weeks",
                Icons.Rounded.CalendarMonth,
            )
        in 25..35 -> Pair("Releases Monthly", Icons.Rounded.CalendarMonth)
        else -> null
    }
}

internal fun genreIconFor(genre: String): ImageVector {
    val genreLc = genre.lowercase()
    return when {
        genreLc.contains("music") -> Icons.Rounded.MusicNote
        genreLc.contains("comedy") -> Icons.Rounded.SentimentVerySatisfied
        genreLc.contains("sport") -> Icons.Rounded.EmojiEvents
        genreLc.contains("science") -> Icons.Rounded.Science
        genreLc.contains("tech") -> Icons.Rounded.Computer
        genreLc.contains("news") -> Icons.Rounded.Newspaper
        genreLc.contains("health") -> Icons.Rounded.MonitorHeart
        genreLc.contains("history") -> Icons.Rounded.AccountBalance
        genreLc.contains("arts") -> Icons.Rounded.Palette
        genreLc.contains("education") -> Icons.Rounded.School
        genreLc.contains("tv") || genreLc.contains("film") -> Icons.Rounded.Movie
        genreLc.contains("fiction") -> Icons.Rounded.AutoStories
        genreLc.contains("religion") || genreLc.contains("spiritual") -> Icons.Rounded.SelfImprovement
        genreLc.contains("family") || genreLc.contains("kids") -> Icons.Rounded.ChildCare
        genreLc.contains("leisure") -> Icons.Rounded.Weekend
        genreLc.contains("business") -> Icons.Rounded.Work
        genreLc.contains("government") -> Icons.Rounded.Gavel
        genreLc.contains("society") || genreLc.contains("culture") -> Icons.Rounded.Groups
        genreLc.contains("crime") -> Icons.Rounded.Fingerprint
        else -> Icons.Rounded.Category
    }
}

internal fun mediumIconFor(medium: String): ImageVector =
    when (medium.lowercase()) {
        "music" -> Icons.Rounded.MusicNote
        "video" -> Icons.Rounded.Videocam
        "film" -> Icons.Rounded.Movie
        "audiobook" -> Icons.Rounded.AutoStories
        "newsletter" -> Icons.Rounded.Email
        "blog" -> Icons.AutoMirrored.Rounded.Article
        else -> Icons.Rounded.Headphones
    }


@Composable
internal fun CompactPersonChip(
    person: Person,
    onClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = ExpressiveShapes.Pill,
        modifier =
            Modifier.expressiveClickable(
                enabled = !person.href.isNullOrBlank(),
                onClick = onClick,
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (!person.img.isNullOrBlank()) {
                OptimizedImage(
                    url = person.img,
                    proxyWidth = 40,
                    contentDescription = person.name,
                    modifier =
                        Modifier
                            .size(16.dp)
                            .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }

            val displayText =
                if (!person.role.isNullOrBlank()) {
                    "${person.name} (${person.role!!.replaceFirstChar { it.uppercaseChar() }})"
                } else {
                    person.name
                }
            Text(
                text = displayText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}


@Composable
internal fun RssFeedChip() {
    Surface(
        shape = ExpressiveShapes.Pill,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.RssFeed,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "RSS",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
internal fun UpdateFrequencyChip(frequencyData: Pair<String, ImageVector>) {
    Surface(
        shape = ExpressiveShapes.Pill,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = frequencyData.second,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = frequencyData.first,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
internal fun GenreChip(genre: String) {
    Surface(
        shape = ExpressiveShapes.Pill,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = genreIconFor(genre),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = genre,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
internal fun PlayTrailerChip(onClick: () -> Unit) {
    Surface(
        shape = ExpressiveShapes.Pill,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.expressiveClickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onPrimary,
            )
            Text(
                text = "Play Trailer",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
internal fun MediumChip(medium: String) {
    Surface(
        shape = ExpressiveShapes.Pill,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = mediumIconFor(medium),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = medium.replaceFirstChar { c -> c.uppercaseChar() },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
internal fun FundingChip(
    fundingMessage: String?,
    onClick: () -> Unit,
) {
    Surface(
        shape = ExpressiveShapes.Pill,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.expressiveClickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Text(
                text = fundingMessage ?: "Support",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
internal fun PodcastInfoMetadataChipsRow(
    podcast: Podcast,
    sortedPersons: List<Person>,
    trailerEpisode: Episode?,
    frequencyData: Pair<String, ImageVector>?,
    context: android.content.Context,
    onPlayTrailer: (Episode) -> Unit,
) {
    val medium = resolveDisplayMedium(podcast)

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        contentPadding = PaddingValues(horizontal = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        podcastIdentityChips(podcast, frequencyData)

        items(sortedPersons) { person ->
            CompactPersonChip(person = person, onClick = { openPersonLink(context, person) })
        }

        trailerAndMediumChips(trailerEpisode, medium, onPlayTrailer)
        fundingChip(podcast, context)
    }
}

/** "video" if this is a video-only podcast feed masquerading as "podcast" medium; otherwise unchanged. */
private fun resolveDisplayMedium(podcast: Podcast): String? =
    if (podcast.medium == "podcast" && podcast.latestEpisode?.enclosureType?.startsWith("video/") == true) {
        "video"
    } else {
        podcast.medium
    }

private fun openPersonLink(
    context: android.content.Context,
    person: Person,
) {
    if (person.href.isNullOrBlank()) return
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(person.href)))
}

private fun LazyListScope.podcastIdentityChips(
    podcast: Podcast,
    frequencyData: Pair<String, ImageVector>?,
) {
    if (podcast.isRss) {
        item { RssFeedChip() }
    }
    if (frequencyData != null) {
        item { UpdateFrequencyChip(frequencyData) }
    }
    if (podcast.genre.isNotEmpty()) {
        item { GenreChip(podcast.genre) }
    }
}

private fun LazyListScope.trailerAndMediumChips(
    trailerEpisode: Episode?,
    medium: String?,
    onPlayTrailer: (Episode) -> Unit,
) {
    if (trailerEpisode != null) {
        item { PlayTrailerChip(onClick = { onPlayTrailer(trailerEpisode) }) }
    }
    if (!medium.isNullOrEmpty() && medium != "podcast") {
        item { MediumChip(medium) }
    }
}

private fun LazyListScope.fundingChip(
    podcast: Podcast,
    context: android.content.Context,
) {
    if (podcast.fundingUrl == null) return
    item {
        FundingChip(
            fundingMessage = podcast.fundingMessage,
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(podcast.fundingUrl))
                context.startActivity(intent)
            },
        )
    }
}

