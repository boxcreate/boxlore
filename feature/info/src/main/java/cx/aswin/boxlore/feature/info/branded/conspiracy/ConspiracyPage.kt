package cx.aswin.boxlore.feature.info.branded.conspiracy

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.info.branded.BrandedChipStyle
import cx.aswin.boxlore.feature.info.branded.BrandedDescription
import cx.aswin.boxlore.feature.info.branded.BrandedPageStyle
import cx.aswin.boxlore.feature.info.branded.BrandedShowIds
import cx.aswin.boxlore.feature.info.branded.BrandedShowPage
import cx.aswin.boxlore.feature.info.branded.BrandedSpotlight
import cx.aswin.boxlore.feature.info.branded.BrandedSubscribeMoment
import cx.aswin.boxlore.feature.info.branded.BrandedToolbarStyle

/**
 * Conspiracy Theories as an evidence board: the wordmark pinned on a scrap of the show's yellow
 * paper, the latest episodes pinned round it and tied together with red yarn, on white paper by day
 * and black by night.
 */
internal object ConspiracyPage : BrandedShowPage {
    override val ids = BrandedShowIds(
        podcastIndexId = "9926",
        podcastGuid = "b20e2ba1-cace-5f92-aa6a-335343b8ba0a",
        feedUrl = "https://feeds.megaphone.fm/END5064265457",
    )

    // The board starts under the status bar; it draws itself up behind it.
    override val headerTop = 0.dp
    override val subscribeMoment: BrandedSubscribeMoment = ConspiracyItAllConnects

    override fun headerToButtons(description: BrandedDescription) = 10.dp

    override fun style(base: ColorScheme, dark: Boolean): BrandedPageStyle {
        val c = if (dark) ConspiracyColors.Dark else ConspiracyColors.Light
        val yellow = ConspiracyColors.Yellow
        val onYellow = ConspiracyColors.OnYellow
        val colors = base.copy(
            primary = yellow,
            onPrimary = onYellow,
            primaryContainer = yellow,
            onPrimaryContainer = onYellow,
            secondary = yellow,
            onSecondary = onYellow,
            secondaryContainer = yellow,
            onSecondaryContainer = onYellow,
            background = c.page,
            onBackground = c.text,
            surface = c.page,
            onSurface = c.text,
            // Unique, so Material's matching content colour for cards and chips stays onSurface.
            surfaceVariant = c.placeholder,
            onSurfaceVariant = c.textMuted,
            surfaceDim = c.page,
            surfaceBright = c.copyright.popup,
            surfaceContainerLowest = c.page,
            surfaceContainerLow = c.card,
            surfaceContainer = c.bar,
            surfaceContainerHigh = c.control,
            surfaceContainerHighest = c.cardControl,
            outline = c.textMuted,
            outlineVariant = c.divider,
            surfaceTint = yellow,
        )
        return BrandedPageStyle(
            colors = colors,
            popup = c.copyright,
            navigationColors = colors.copy(
                surfaceContainerHigh = c.navigation,
                onSurfaceVariant = c.navigationText,
                outlineVariant = c.navigation,
            ),
            chips = BrandedChipStyle(
                // Square-cut labels: a fine ring on the page, the genre in the show's yellow.
                shape = RoundedCornerShape(2.dp),
                height = 34.dp,
                padding = PaddingValues(start = 10.dp, end = 12.dp),
                iconSize = 18.dp,
                gap = 6.dp,
                fontSize = 14.sp,
                fontWeight = 500,
                container = c.page,
                content = c.text,
                icon = c.accentOnPage,
                genreContainer = yellow,
                genreContent = onYellow,
                genreIcon = onYellow,
                outline = c.chipLine,
            ),
            toolbar = BrandedToolbarStyle(
                subscribeContainer = yellow,
                subscribeContent = onYellow,
                subscribedContainer = c.control,
                subscribedContent = c.onControl,
                toggleContainer = c.control,
                toggleContent = c.onControl,
                toggleCheckedContainer = yellow,
                toggleCheckedContent = onYellow,
                actionContainer = c.control,
                actionContent = c.onControl,
            ),
            episodeTimeColor = c.accentOnPage,
        )
    }

    @Composable
    override fun Header(podcast: Podcast, feed: List<Episode>, description: BrandedDescription, chips: @Composable () -> Unit, modifier: Modifier) =
        ConspiracyHeader(podcast, feed, description, chips, modifier)

    @Composable
    override fun Spotlight(spotlight: BrandedSpotlight, modifier: Modifier) = ConspiracySpotlight(spotlight, modifier)
}
