package cx.aswin.boxlore.feature.info.components

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.icon.GenreIcons
import cx.aswin.boxlore.core.designsystem.links.openLink
import cx.aswin.boxlore.core.designsystem.text.asString
import cx.aswin.boxlore.core.designsystem.text.currentLocale
import cx.aswin.boxlore.core.designsystem.theme.ExpressiveShapes
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.rememberGoogleSansFamily
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Person
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.isTrailer
import cx.aswin.boxlore.core.model.text.UiText
import cx.aswin.boxlore.feature.info.EpisodeSort
import cx.aswin.boxlore.feature.info.R
import cx.aswin.boxlore.feature.info.branded.BrandedChipRowHeight
import cx.aswin.boxlore.feature.info.branded.BrandedChipStyle
import cx.aswin.boxlore.feature.info.branded.LocalBrandedPage
import java.util.Locale

internal fun calculateUpdateFrequencyData(
    podcast: Podcast,
    episodes: List<Episode>,
    currentSort: EpisodeSort,
    searchQuery: String,
    cachedFrequencyData: Pair<UiText, ImageVector>?,
): Pair<UiText, ImageVector>? {
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

internal fun filterValidFrequencyEpisodes(episodes: List<Episode>): List<Episode> = episodes
    .filter { !it.isTrailer && it.episodeType != "bonus" && it.publishedDate > 0 }
    .sortedByDescending { it.publishedDate }
    .take(15)

internal fun computeDaysSinceLatest(
    podcast: Podcast,
    validEpisodes: List<Episode>,
): Long? {
    // The newest valid episode, else the show's stored latest episode.
    val latest = (validEpisodes + listOfNotNull(podcast.latestEpisode)).firstOrNull() ?: return null
    return (System.currentTimeMillis() / 1000 - latest.publishedDate) / (60 * 60 * 24)
}

internal fun dormancyStatus(daysSinceLatest: Long?): Pair<UiText, ImageVector>? {
    if (daysSinceLatest == null || daysSinceLatest <= 0) return null
    return when {
        daysSinceLatest > 365 -> Pair(UiText.res(R.string.podcast_info_frequency_inactive), Icons.Rounded.PauseCircle)
        daysSinceLatest > 180 -> Pair(UiText.res(R.string.podcast_info_frequency_hiatus), Icons.Rounded.PauseCircle)
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
): Pair<UiText, ImageVector>? {
    if (medianIntervalDays == null || medianIntervalDays <= 3 || daysSinceLatest == null) return null
    return if (daysSinceLatest > (medianIntervalDays * 2)) {
        Pair(UiText.res(R.string.podcast_info_frequency_between_seasons), Icons.Rounded.HourglassBottom)
    } else {
        null
    }
}

internal fun explicitFrequencyTag(tag: String?): Pair<UiText, ImageVector>? {
    if (tag.isNullOrBlank()) return null
    val cleanText = tag.trim().lowercase(Locale.ROOT)
    val known: Int? = cleanText.toDoubleOrNull()?.let(::frequencyForEpisodesPerWeek) ?: NamedFrequencies[cleanText]
    // A tag the app doesn't know is the feed's own text, shown as the feed wrote it.
    val text = known?.let { UiText.res(it) } ?: UiText.raw(tag.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() })
    val daily = if (known != null) known == R.string.podcast_info_frequency_daily else cleanText.contains("daily")
    val icon = if (daily) Icons.Rounded.Bolt else Icons.Rounded.CalendarMonth
    return Pair(text, icon)
}

/** The frequency chip for a feed that gives episodes per week as a number. */
private fun frequencyForEpisodesPerWeek(perWeek: Double): Int = when {
    perWeek >= 7.0 -> R.string.podcast_info_frequency_daily
    perWeek >= 2.0 -> R.string.podcast_info_frequency_multi_weekly
    perWeek >= 1.0 -> R.string.podcast_info_frequency_weekly
    perWeek >= 0.5 -> R.string.podcast_info_frequency_biweekly
    perWeek >= 0.1 -> R.string.podcast_info_frequency_monthly
    else -> R.string.podcast_info_frequency_occasionally
}

/** The feed's own frequency words the app knows, keyed in lower case. */
private val NamedFrequencies = mapOf(
    "daily" to R.string.podcast_info_frequency_daily,
    "weekly" to R.string.podcast_info_frequency_weekly,
    "monthly" to R.string.podcast_info_frequency_monthly,
    "biweekly" to R.string.podcast_info_frequency_biweekly,
    "bi-weekly" to R.string.podcast_info_frequency_biweekly,
)

/** Most common weekday ([java.util.Calendar.DAY_OF_WEEK]) across [validEpisodes], if at least half of them share one. */
internal fun commonReleaseDay(validEpisodes: List<Episode>): Int? {
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
    return maxDay.takeIf { it in 1..7 }
}

/** "Weekly on Mondays" and "Every 2 weeks on Mondays" as whole sentences, one per weekday. */
private val WeeklyOnDay = mapOf(
    java.util.Calendar.SUNDAY to R.string.podcast_info_frequency_weekly_sunday,
    java.util.Calendar.MONDAY to R.string.podcast_info_frequency_weekly_monday,
    java.util.Calendar.TUESDAY to R.string.podcast_info_frequency_weekly_tuesday,
    java.util.Calendar.WEDNESDAY to R.string.podcast_info_frequency_weekly_wednesday,
    java.util.Calendar.THURSDAY to R.string.podcast_info_frequency_weekly_thursday,
    java.util.Calendar.FRIDAY to R.string.podcast_info_frequency_weekly_friday,
    java.util.Calendar.SATURDAY to R.string.podcast_info_frequency_weekly_saturday,
)
private val BiweeklyOnDay = mapOf(
    java.util.Calendar.SUNDAY to R.string.podcast_info_frequency_biweekly_sunday,
    java.util.Calendar.MONDAY to R.string.podcast_info_frequency_biweekly_monday,
    java.util.Calendar.TUESDAY to R.string.podcast_info_frequency_biweekly_tuesday,
    java.util.Calendar.WEDNESDAY to R.string.podcast_info_frequency_biweekly_wednesday,
    java.util.Calendar.THURSDAY to R.string.podcast_info_frequency_biweekly_thursday,
    java.util.Calendar.FRIDAY to R.string.podcast_info_frequency_biweekly_friday,
    java.util.Calendar.SATURDAY to R.string.podcast_info_frequency_biweekly_saturday,
)

internal fun predictedFrequencyFromPattern(
    validEpisodes: List<Episode>,
    medianIntervalDays: Long,
): Pair<UiText, ImageVector>? {
    val commonDay = commonReleaseDay(validEpisodes)
    return when (medianIntervalDays) {
        in 0..1 -> Pair(UiText.res(R.string.podcast_info_frequency_daily), Icons.Rounded.Bolt)
        in 2..4 -> Pair(UiText.res(R.string.podcast_info_frequency_multi_weekly), Icons.Rounded.CalendarMonth)
        in 5..8 -> Pair(UiText.res(WeeklyOnDay[commonDay] ?: R.string.podcast_info_frequency_weekly), Icons.Rounded.CalendarMonth)
        in 12..16 -> Pair(UiText.res(BiweeklyOnDay[commonDay] ?: R.string.podcast_info_frequency_biweekly), Icons.Rounded.CalendarMonth)
        in 25..35 -> Pair(UiText.res(R.string.podcast_info_frequency_monthly), Icons.Rounded.CalendarMonth)
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

internal fun mediumIconFor(medium: String): ImageVector = when (medium.lowercase()) {
    "music" -> Icons.Rounded.MusicNote
    "video" -> Icons.Rounded.Videocam
    "film" -> Icons.Rounded.Movie
    "audiobook" -> Icons.Rounded.AutoStories
    "newsletter" -> Icons.Rounded.Email
    "blog" -> Icons.AutoMirrored.Rounded.Article
    else -> Icons.Rounded.Headphones
}

/** The Podcasting 2.0 medium as a chip names it; null for a medium the app doesn't know, shown as the feed wrote it. */
internal fun mediumLabelFor(medium: String): Int? = when (medium.lowercase(Locale.ROOT)) {
    "music" -> R.string.podcast_info_medium_music
    "video" -> R.string.podcast_info_medium_video
    "film" -> R.string.podcast_info_medium_film
    "audiobook" -> R.string.podcast_info_medium_audiobook
    "newsletter" -> R.string.podcast_info_medium_newsletter
    "blog" -> R.string.podcast_info_medium_blog
    else -> null
}

@Composable
internal fun CompactPersonChip(person: Person, onClick: () -> Unit) {
    val locale = currentLocale()
    val displayText = if (!person.role.isNullOrBlank()) {
        // The role comes from the feed (Podcasting 2.0 person tag), so only the pattern is translated.
        stringResource(R.string.podcast_info_person_role, person.name, person.role!!.replaceFirstChar { it.titlecase(locale) })
    } else {
        person.name
    }
    PodcastMetadataChip(
        label = displayText,
        onClick = onClick.takeIf { !person.href.isNullOrBlank() },
        leadingIcon = {
            if (!person.img.isNullOrBlank()) {
                OptimizedImage(
                    url = person.img,
                    proxyWidth = 60,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(Icons.Rounded.Person, null, Modifier.size(16.dp))
            }
        },
    )
}

@Composable
internal fun RssFeedChip() {
    PodcastMetadataChip(label = stringResource(R.string.podcast_info_rss_chip), icon = Icons.Rounded.RssFeed)
}

@Composable
internal fun UpdateFrequencyChip(frequencyData: Pair<UiText, ImageVector>) {
    PodcastMetadataChip(label = frequencyData.first.asString(), icon = frequencyData.second)
}

@Composable
internal fun GenreChip(genre: String, icon: ImageVector? = null, onClick: (() -> Unit)? = null) {
    PodcastMetadataChip(label = genre, icon = icon ?: genreIconFor(genre), onClick = onClick, genre = true)
}

@Composable
internal fun PlayTrailerChip(onClick: () -> Unit) {
    PodcastMetadataChip(label = stringResource(R.string.podcast_info_play_trailer), icon = Icons.Rounded.PlayArrow, onClick = onClick)
}

@Composable
internal fun MediumChip(medium: String) {
    val label = mediumLabelFor(medium)?.let { stringResource(it) } ?: medium.replaceFirstChar { it.titlecase(currentLocale()) }
    PodcastMetadataChip(label = label, icon = mediumIconFor(medium))
}

@Composable
internal fun FundingChip(fundingMessage: String?, onClick: () -> Unit) {
    PodcastMetadataChip(label = fundingMessage ?: stringResource(R.string.podcast_info_support), icon = Icons.Filled.Favorite, onClick = onClick)
}

@Composable
private fun PodcastMetadataChip(
    label: String,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    genre: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val branded = LocalBrandedPage.current?.style?.chips
    if (branded != null) {
        BrandedMetadataChip(branded, label, icon, onClick, genre, leadingIcon)
        return
    }
    val container = if (genre) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val foreground = if (genre) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val content: @Composable () -> Unit = {
        Row(
            Modifier.heightIn(min = 36.dp).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (leadingIcon != null) {
                leadingIcon()
            } else if (icon != null) {
                Icon(icon, null, Modifier.size(16.dp))
            }
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = GoogleSansWeight.medium)
        }
    }
    if (onClick != null) {
        Surface(onClick = onClick, shape = ExpressiveShapes.Pill, color = container, contentColor = foreground, content = content)
    } else {
        Surface(shape = ExpressiveShapes.Pill, color = container, contentColor = foreground, content = content)
    }
}

/** The same chip in a branded page's shape and colours. */
@Composable
@Suppress("LongParameterList")
private fun BrandedMetadataChip(
    style: BrandedChipStyle,
    label: String,
    icon: ImageVector?,
    onClick: (() -> Unit)?,
    genre: Boolean,
    leadingIcon: (@Composable () -> Unit)?,
) {
    val container = if (genre) style.genreContainer else style.container
    val foreground = if (genre) style.genreContent else style.content
    val textStyle = MaterialTheme.typography.labelLarge.copy(
        fontFamily = rememberGoogleSansFamily(weight = style.fontWeight, opticalSize = style.fontSize.value),
        fontWeight = FontWeight.Normal,
        fontSize = style.fontSize,
        letterSpacing = 0.sp,
    )
    val border = style.outline?.takeUnless { genre }?.let { BorderStroke(1.dp, it) }
    val content: @Composable () -> Unit = {
        Row(
            // A ring sits outside the chip's padding, as in the designs.
            Modifier.height(style.height).padding(horizontal = if (border != null) 1.dp else 0.dp).padding(style.padding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(style.gap),
        ) {
            if (leadingIcon != null) {
                leadingIcon()
            } else if (icon != null) {
                Icon(icon, null, Modifier.size(style.iconSize), tint = if (genre) style.genreIcon else style.icon)
            }
            Text(label, style = textStyle, maxLines = 1)
        }
    }
    if (onClick != null) {
        Surface(onClick = onClick, shape = style.shape, color = container, contentColor = foreground, border = border, content = content)
    } else {
        Surface(shape = style.shape, color = container, contentColor = foreground, border = border, content = content)
    }
}

@Composable
internal fun PodcastInfoMetadataChipsRow(
    podcast: Podcast,
    sortedPersons: List<Person>,
    trailerEpisode: Episode?,
    frequencyData: Pair<UiText, ImageVector>?,
    onEditGenre: (() -> Unit)? = null,
    onPlayTrailer: (Episode) -> Unit,
) {
    val context = LocalContext.current
    val medium = resolveDisplayMedium(podcast)
    // Chips sit centred while they fit; a branded header keeps them off its margins and lets a long row
    // scroll to the screen edge.
    val branded = LocalBrandedPage.current != null

    LazyRow(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        contentPadding = PaddingValues(horizontal = if (branded) 16.dp else 0.dp),
        modifier = if (branded) Modifier.fillMaxWidth().height(BrandedChipRowHeight) else Modifier.fillMaxWidth(),
    ) {
        podcastIdentityChips(podcast, frequencyData, onEditGenre)

        items(sortedPersons) { person ->
            CompactPersonChip(person = person, onClick = { openPersonLink(context, person) })
        }

        trailerAndMediumChips(trailerEpisode, medium, onPlayTrailer)
        fundingChip(podcast, context)
    }
}

/** "video" if this is a video-only podcast feed masquerading as "podcast" medium; otherwise unchanged. */
private fun resolveDisplayMedium(podcast: Podcast): String? = if (podcast.medium == "podcast" && podcast.latestEpisode?.enclosureType?.startsWith("video/") == true) {
    "video"
} else {
    podcast.medium
}

private fun openPersonLink(
    context: android.content.Context,
    person: Person,
) {
    val href = person.href?.takeIf { it.isNotBlank() } ?: return
    context.openLink(Uri.parse(href))
}

private fun LazyListScope.podcastIdentityChips(
    podcast: Podcast,
    frequencyData: Pair<UiText, ImageVector>?,
    onEditGenre: (() -> Unit)? = null,
) {
    if (podcast.isRss) {
        item { RssFeedChip() }
    }
    if (frequencyData != null) {
        item { UpdateFrequencyChip(frequencyData) }
    }
    val effective = podcast.effectiveGenre
    if (effective.isNotEmpty()) {
        val icon = GenreIcons.findIcon(podcast.customGenreIcon)
            ?: genreIconFor(effective)
        item {
            GenreChip(
                genre = effective,
                icon = icon,
                onClick = onEditGenre,
            )
        }
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
                podcast.fundingUrl?.let { context.openLink(Uri.parse(it)) }
            },
        )
    }
}
