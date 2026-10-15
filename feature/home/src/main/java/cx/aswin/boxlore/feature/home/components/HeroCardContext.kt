package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.icon.GenreIcons
import cx.aswin.boxlore.feature.home.HeroType
import cx.aswin.boxlore.feature.home.R
import cx.aswin.boxlore.feature.home.SmartHeroItem
import java.util.Locale

@Composable
internal fun heroWideContextHeight(item: SmartHeroItem, width: Dp): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    fun height(text: String, style: TextStyle, available: Dp, lines: Int): Dp {
        val constraints = Constraints(maxWidth = with(density) { available.roundToPx().coerceAtLeast(1) })
        val result = measurer.measure(text, style, maxLines = lines, overflow = TextOverflow.Ellipsis, constraints = constraints)
        return with(density) { result.size.height.toDp() }
    }
    val contentWidth = width - 12.dp
    val result = if (item.type != HeroType.SPOTLIGHT) {
        val label = height(episodeContextLabel(item), HeroContextTypography.label(), contentWidth * 0.43f, 1)
        val minutes = HomeHeroStyle.minutes(item.podcast, item.type == HeroType.RESUME)
        val time = if (minutes == null) 0.dp else height(heroDurationText(minutes), HeroContextTypography.duration(wide = true).copy(fontSize = 24.sp), contentWidth * 0.57f, 1)
        val caption = episodeDurationCaption(item)
        val captionHeight = if (caption != null && minutes != null) height(caption, HeroContextTypography.durationCaption(), contentWidth * 0.57f, 1) + 2.dp else 0.dp
        maxOf(label, time + captionHeight)
    } else {
        when (val context = heroSpotlightContext(item.label, item.podcast.genre)) {
            is HeroSpotlightContext.Rank -> {
                val number = height(stringResource(R.string.home_hero_rank_number, context.number), HeroContextTypography.rank(wide = true).copy(fontSize = 42.sp), contentWidth * 0.35f, 1)
                val market = height(stringResource(R.string.home_hero_rank_market, context.market), HeroContextTypography.market(), contentWidth * 0.65f, 1)
                maxOf(number, market)
            }
            is HeroSpotlightContext.Genre -> {
                val genreWidth = width - 58.dp
                maxOf(
                    48.dp,
                    height(stringResource(R.string.home_hero_trending_in), HeroContextTypography.trendingLabel(), genreWidth, 1) +
                    height(context.name, HeroContextTypography.genre(), genreWidth, heroGenreMaxLines(context.name)) + 4.dp
                )
            }
            is HeroSpotlightContext.General -> 56.dp + height(context.label, HeroContextTypography.label(), width, 1)
        }
    }
    return result + 4.dp
}

@Composable
internal fun HeroCardContext(item: SmartHeroItem, foreground: Color, wide: Boolean, modifier: Modifier) {
    val context = remember(item.label, item.podcast.genre) { heroSpotlightContext(item.label, item.podcast.genre) }
    val group = modifier.testTag("home_hero_context_group").semantics(mergeDescendants = true) {}
    if (item.type == HeroType.SPOTLIGHT) {
        SpotlightContext(context, foreground, wide, group)
    } else {
        EpisodeContext(item, foreground, wide, group)
    }
}

@Composable
private fun EpisodeContext(item: SmartHeroItem, foreground: Color, wide: Boolean, modifier: Modifier) {
    val label = episodeContextLabel(item)
    val minutes = HomeHeroStyle.minutes(item.podcast, item.type == HeroType.RESUME)
    if (wide) {
        Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ContextLabel(label, foreground, Modifier.weight(0.43f))
            if (minutes != null) HeroDuration(minutes, item, foreground, wide = true, Modifier.weight(0.57f))
        }
    } else {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)) {
            ContextLabel(label, foreground, Modifier.fillMaxWidth())
            if (minutes != null) HeroDuration(minutes, item, foreground, wide = false, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun episodeContextLabel(item: SmartHeroItem) = when {
        item.type == HeroType.RESUME -> stringResource(R.string.home_hero_resume)
        item.label == "FRESH DROP" -> stringResource(R.string.home_hero_fresh_drop)
        item.label == "NEW EPISODE" -> stringResource(R.string.home_hero_new_episode)
        item.label == "NEXT" -> stringResource(R.string.home_hero_next_episode)
        else -> item.label.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }
}

private val NewEpisodeLabels = setOf("FRESH DROP", "NEW EPISODE", "NEXT")

@Composable
private fun episodeDurationCaption(item: SmartHeroItem): String? = when {
    item.type == HeroType.RESUME -> stringResource(R.string.home_hero_time_left)
    // Every unplayed new episode reads "Listen" under its length, not only the freshest one.
    item.label in NewEpisodeLabels -> stringResource(R.string.home_hero_listen)
    else -> null
}

@Composable
private fun ContextLabel(label: String, foreground: Color, modifier: Modifier) {
    Text(
        text = label,
        style = HeroContextTypography.label(),
        color = foreground.copy(alpha = 0.85f),
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 14.sp),
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.fillMaxWidth().testTag("home_hero_context"),
    )
}

@Composable
private fun HeroDuration(minutes: Int, item: SmartHeroItem, foreground: Color, wide: Boolean, modifier: Modifier) {
    val caption = episodeDurationCaption(item)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = if (wide || minutes < 60) heroDurationText(minutes) else stringResource(R.string.home_hero_hours_minutes_stacked, minutes / 60, minutes % 60),
            style = HeroContextTypography.duration(wide),
            color = foreground,
            maxLines = if (wide) 1 else 2,
            softWrap = false,
            // Ellipsis with wrapping disabled collapses explicit line breaks to one line.
            // Fit the bounded duration instead, preserving only the intentional hour/minute break.
            overflow = TextOverflow.Clip,
            autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = if (wide) 24.sp else 32.sp),
            modifier = Modifier.fillMaxWidth().testTag("home_hero_duration"),
        )
        if (caption != null) {
            Text(
                caption,
                style = HeroContextTypography.durationCaption(),
                color = foreground.copy(alpha = 0.85f),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 16.sp),
                modifier = Modifier.fillMaxWidth().testTag(if (item.type == HeroType.RESUME) "home_hero_time_left" else "home_hero_listen"),
            )
        }
    }
}

@Composable
private fun SpotlightContext(context: HeroSpotlightContext, foreground: Color, wide: Boolean, modifier: Modifier) {
    when (context) {
        is HeroSpotlightContext.Rank -> RankContext(context, foreground, wide, modifier)
        is HeroSpotlightContext.Genre -> TrendingContext(context.name, foreground, wide, modifier)
        is HeroSpotlightContext.General -> Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
            Icon(Icons.AutoMirrored.Rounded.TrendingUp, null, Modifier.size(48.dp), tint = foreground)
            ContextLabel(context.label, foreground, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun RankContext(context: HeroSpotlightContext.Rank, foreground: Color, wide: Boolean, modifier: Modifier) {
    if (wide) {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RankNumber(context.number, foreground, wide = true, Modifier.weight(0.35f))
            RankMarket(context.market, foreground, Modifier.weight(0.65f))
        }
    } else {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)) {
            RankNumber(context.number, foreground, wide = false, Modifier.fillMaxWidth())
            RankMarket(context.market, foreground, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun RankNumber(number: String, foreground: Color, wide: Boolean, modifier: Modifier) {
    Text(
        stringResource(R.string.home_hero_rank_number, number),
        style = HeroContextTypography.rank(wide),
        color = foreground,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(minFontSize = 18.sp, maxFontSize = if (wide) 42.sp else 54.sp),
        modifier = modifier.fillMaxWidth().testTag("home_hero_rank"),
    )
}

@Composable
private fun RankMarket(market: String, foreground: Color, modifier: Modifier) {
    Text(
        stringResource(R.string.home_hero_rank_market, market),
        style = HeroContextTypography.market(),
        color = foreground.copy(alpha = 0.9f),
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 14.sp),
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.fillMaxWidth().testTag("home_hero_context"),
    )
}

@Composable
private fun TrendingContext(genre: String, foreground: Color, wide: Boolean, modifier: Modifier) {
    val icon = GenreIcons.defaultGenreIcon(genre)
    if (wide) {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(48.dp).testTag("home_hero_genre_icon"), tint = foreground)
            TrendingWords(genre, foreground, Modifier.weight(1f))
        }
    } else {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
            Icon(icon, null, Modifier.size(48.dp).testTag("home_hero_genre_icon"), tint = foreground)
            TrendingWords(genre, foreground, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun TrendingWords(genre: String, foreground: Color, modifier: Modifier) {
    val genreLines = heroGenreMaxLines(genre)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(R.string.home_hero_trending_in),
            style = HeroContextTypography.trendingLabel(),
            color = foreground.copy(alpha = 0.85f),
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 12.sp),
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().testTag("home_hero_context"),
        )
        Text(
            genre,
            style = HeroContextTypography.genre(),
            color = foreground,
            maxLines = genreLines,
            softWrap = genreLines == 2,
            autoSize = TextAutoSize.StepBased(minFontSize = if (genreLines == 1) 8.sp else 10.sp, maxFontSize = 22.sp),
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().testTag("home_hero_genre"),
        )
    }
}

@Composable
private fun heroDurationText(minutes: Int) = if (minutes >= 60) stringResource(R.string.home_hero_hours_minutes, minutes / 60, minutes % 60) else stringResource(R.string.home_hero_minutes, minutes)
