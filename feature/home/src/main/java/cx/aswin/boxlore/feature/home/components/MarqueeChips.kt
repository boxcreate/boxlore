package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.theme.ArtworkShade
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.feature.home.HeroType
import cx.aswin.boxlore.feature.home.R
import cx.aswin.boxlore.feature.home.SmartHeroItem

/** How a piece of a Marquee label reads: the main words, quieter detail, or a counter or rank in the accent. */
internal enum class MarqueeChipTone { Strong, Soft, Badge }

internal data class MarqueeChipPart(val text: String, val tone: MarqueeChipTone)

/** A Marquee label's parts and the one phrase screen readers hear for it. */
internal data class MarqueeChipContent(val parts: List<MarqueeChipPart>, val description: String)

/**
 * Marquee's labels: one solid chip in the card's colours, readable over any cover, with its main words
 * in bold, quieter detail beside them and a rank or counter as a small pill in the card's accent.
 * Screen readers hear [MarqueeChipContent.description] as one phrase.
 */
@Composable
internal fun MarqueeChip(content: MarqueeChipContent, shade: ArtworkShade, tag: String, modifier: Modifier = Modifier) {
    val parts = content.parts
    if (parts.isEmpty()) return
    val label = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, lineHeight = 18.sp)
    val firstStrong = parts.indexOfFirst { it.tone == MarqueeChipTone.Strong }
    Row(
        modifier.clip(CircleShape).background(shade.container)
            .padding(
                start = if (parts.first().tone == MarqueeChipTone.Badge) 6.dp else 12.dp,
                end = if (parts.last().tone == MarqueeChipTone.Badge) 6.dp else 12.dp,
                top = 5.dp,
                bottom = 5.dp,
            )
            .clearAndSetSemantics {
                contentDescription = content.description
                testTag = tag
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        parts.forEachIndexed { index, part ->
            when (part.tone) {
                MarqueeChipTone.Badge -> Text(
                    part.text,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = GoogleSansWeight.bold),
                    color = shade.onAccent,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.clip(CircleShape).background(shade.accent).padding(horizontal = 8.dp, vertical = 2.dp),
                )
                MarqueeChipTone.Strong -> Text(
                    part.text,
                    style = label.copy(fontWeight = GoogleSansWeight.bold),
                    color = shade.content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // The first main words give way on narrow cards; the rest keep their room.
                    modifier = if (index == firstStrong) Modifier.weight(1f, fill = false) else Modifier,
                )
                MarqueeChipTone.Soft -> Text(part.text, style = label, color = shade.softContent, maxLines = 1, softWrap = false)
            }
        }
    }
}

/**
 * A single Marquee card's label: "#1" in the accent then "in US"; "Trending in" then the genre in bold;
 * otherwise the episode's state in bold ("Resume", "New episode") then its time left or length.
 */
@Composable
internal fun marqueeContext(item: SmartHeroItem): MarqueeChipContent {
    if (item.type == HeroType.SPOTLIGHT) {
        return when (val context = remember(item.label, item.podcast.genre) { heroSpotlightContext(item.label, item.podcast.genre) }) {
            is HeroSpotlightContext.Rank -> {
                val number = stringResource(R.string.home_hero_rank_number, context.number)
                val market = stringResource(R.string.home_hero_rank_market, context.market)
                MarqueeChipContent(listOf(MarqueeChipPart(number, MarqueeChipTone.Badge), MarqueeChipPart(market, MarqueeChipTone.Strong)), "$number $market")
            }
            is HeroSpotlightContext.Genre -> {
                val trending = stringResource(R.string.home_hero_trending_in)
                MarqueeChipContent(
                    listOf(MarqueeChipPart(trending, MarqueeChipTone.Soft), MarqueeChipPart(context.name, MarqueeChipTone.Strong)),
                    "$trending ${context.name}",
                )
            }
            is HeroSpotlightContext.General -> MarqueeChipContent(listOf(MarqueeChipPart(context.label, MarqueeChipTone.Strong)), context.label)
        }
    }
    val label = episodeContextLabel(item)
    val resume = item.type == HeroType.RESUME
    val detail = HomeHeroStyle.minutes(item.podcast, resume)?.let { heroDurationText(it) }
        ?.let { if (resume) stringResource(R.string.home_hero_remaining, it) else it }
    val parts = listOfNotNull(MarqueeChipPart(label, MarqueeChipTone.Strong), detail?.let { MarqueeChipPart(it, MarqueeChipTone.Soft) })
    return MarqueeChipContent(parts, listOfNotNull(label, detail).joinToString(", "))
}

/**
 * A collection card's label: its name in bold, the chosen episode's [time], and a "2/4" counter; heard
 * as "Jump back in, 2 of 4, 12m left".
 */
@Composable
internal fun marqueeCollectionLabel(title: String, index: Int, count: Int, time: String?): MarqueeChipContent {
    val position = stringResource(R.string.home_hero_collection_position, index + 1, count)
    val counter = stringResource(R.string.home_hero_collection_counter, index + 1, count)
    val parts = listOfNotNull(
        MarqueeChipPart(title, MarqueeChipTone.Strong),
        time?.let { MarqueeChipPart(it, MarqueeChipTone.Soft) },
        MarqueeChipPart(counter, MarqueeChipTone.Badge),
    )
    return MarqueeChipContent(parts, listOfNotNull(title, position, time).joinToString(", "))
}
