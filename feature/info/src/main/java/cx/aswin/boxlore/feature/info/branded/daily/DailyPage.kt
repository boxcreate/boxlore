package cx.aswin.boxlore.feature.info.branded.daily

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.info.branded.BrandedChipStyle
import cx.aswin.boxlore.feature.info.branded.BrandedDescription
import cx.aswin.boxlore.feature.info.branded.BrandedPageStyle
import cx.aswin.boxlore.feature.info.branded.BrandedShowIds
import cx.aswin.boxlore.feature.info.branded.BrandedShowPage
import cx.aswin.boxlore.feature.info.branded.BrandedSpotlight
import cx.aswin.boxlore.feature.info.branded.BrandedSubscribeMoment
import cx.aswin.boxlore.feature.info.branded.BrandedToolbarStyle

/** The Daily as a front page: newsprint and ink, the night edition in dark mode, and the show's dawn. */
internal object DailyPage : BrandedShowPage {
    override val ids = BrandedShowIds(
        podcastIndexId = "743229",
        podcastGuid = "304b84f0-07b0-5265-b6b7-da5cf5aeb56e",
        feedUrl = "https://feeds.simplecast.com/54nAGcIl",
    )
    override val headerTop = 74.dp
    override val subscribeMoment: BrandedSubscribeMoment = DailyDaybreak

    override fun headerToButtons(description: BrandedDescription) = 14.dp

    override fun style(base: ColorScheme, dark: Boolean): BrandedPageStyle {
        val c = if (dark) DailyColors.Dark else DailyColors.Light
        val colors = base.copy(
            primary = c.ink,
            onPrimary = c.page,
            primaryContainer = c.ink,
            onPrimaryContainer = c.page,
            secondary = c.ink,
            onSecondary = c.page,
            secondaryContainer = c.ink,
            onSecondaryContainer = c.page,
            background = c.page,
            onBackground = c.ink,
            surface = c.page,
            onSurface = c.ink,
            // Unique, so Material's matching content colour for cards and chips stays onSurface.
            surfaceVariant = c.soft,
            onSurfaceVariant = c.sub,
            surfaceDim = c.page,
            surfaceBright = c.copyright.popup,
            surfaceContainerLowest = c.page,
            surfaceContainerLow = c.card,
            surfaceContainer = c.navigation,
            surfaceContainerHigh = c.control,
            surfaceContainerHighest = c.control,
            outline = c.sub,
            outlineVariant = c.chipLine,
            surfaceTint = c.ink,
        )
        return BrandedPageStyle(
            colors = colors,
            navigationColors = colors.copy(
                surfaceContainerHigh = c.navigation,
                onSurfaceVariant = c.navigationText,
                outlineVariant = c.navigation,
            ),
            chips = BrandedChipStyle(
                shape = RoundedCornerShape(2.dp),
                height = 34.dp,
                padding = PaddingValues(start = 9.dp, end = 12.dp),
                iconSize = 18.dp,
                gap = 6.dp,
                fontSize = 14.sp,
                fontWeight = 500,
                container = c.page,
                content = c.ink,
                icon = c.ink,
                genreContainer = c.ink,
                genreContent = c.page,
                genreIcon = c.page,
                outline = c.chipLine,
            ),
            toolbar = BrandedToolbarStyle(
                subscribeContainer = c.ink,
                subscribeContent = c.page,
                subscribedContainer = c.control,
                subscribedContent = c.ink,
                toggleContainer = c.control,
                toggleContent = c.ink,
                toggleCheckedContainer = c.ink,
                toggleCheckedContent = c.page,
                actionContainer = c.control,
                actionContent = c.ink,
            ),
            episodeTimeColor = c.kicker,
        )
    }

    @Composable
    override fun Header(podcast: Podcast, description: BrandedDescription, chips: @Composable () -> Unit, modifier: Modifier) =
        DailyHeader(podcast, description, chips, modifier)

    @Composable
    override fun Tag(modifier: Modifier) = DailyTag(modifier)

    @Composable
    override fun Spotlight(spotlight: BrandedSpotlight, modifier: Modifier) = DailySpotlight(spotlight, modifier)
}
