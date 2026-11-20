package cx.aswin.boxlore.feature.info.branded.daily

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

/** The Daily at first light: the cover's dawn sky over the page, the sun on the horizon, and the sky's blue as the accent. */
internal object DailyPage : BrandedShowPage {
    override val ids = BrandedShowIds(
        podcastIndexId = "743229",
        podcastGuid = "304b84f0-07b0-5265-b6b7-da5cf5aeb56e",
        feedUrl = "https://feeds.simplecast.com/54nAGcIl",
    )

    // The sky starts under the status bar; the header draws it up behind the status bar and top bar.
    override val headerTop = 56.dp
    override val subscribeMoment: BrandedSubscribeMoment = DailyDaybreak

    override fun headerToButtons(description: BrandedDescription) = 16.dp

    override fun style(base: ColorScheme, dark: Boolean): BrandedPageStyle {
        val c = if (dark) DailyColors.Dark else DailyColors.Light
        val colors = base.copy(
            primary = c.accent,
            onPrimary = c.onAccent,
            primaryContainer = c.accent,
            onPrimaryContainer = c.onAccent,
            secondary = c.accent,
            onSecondary = c.onAccent,
            secondaryContainer = c.accent,
            onSecondaryContainer = c.onAccent,
            background = c.page,
            onBackground = c.ink,
            surface = c.page,
            onSurface = c.ink,
            // Unique, so Material's matching content colour for cards and chips stays onSurface.
            surfaceVariant = c.variant,
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
            surfaceTint = c.accent,
        )
        return BrandedPageStyle(
            colors = colors,
            popup = c.copyright,
            navigationColors = colors.copy(
                surfaceContainerHigh = c.navigation,
                onSurfaceVariant = c.navigationText,
                outlineVariant = c.navigation,
            ),
            // Pills, as round as the horizon: a tonal cadence chip with a blue icon, and the genre in blue.
            chips = BrandedChipStyle(
                shape = RoundedCornerShape(50),
                height = 34.dp,
                padding = PaddingValues(start = 11.dp, end = 15.dp),
                iconSize = 18.dp,
                gap = 6.dp,
                fontSize = 14.sp,
                fontWeight = 500,
                container = c.control,
                content = c.ink,
                icon = c.accent,
                genreContainer = c.accent,
                genreContent = c.onAccent,
                genreIcon = c.onAccent,
            ),
            toolbar = BrandedToolbarStyle(
                subscribeContainer = c.accent,
                subscribeContent = c.onAccent,
                subscribedContainer = c.control,
                subscribedContent = c.ink,
                toggleContainer = c.control,
                toggleContent = c.ink,
                toggleCheckedContainer = c.accent,
                toggleCheckedContent = c.onAccent,
                actionContainer = c.control,
                actionContent = c.ink,
            ),
            episodeTimeColor = c.accent,
            // The sky stays a dawn sky by day, so the top bar and status bar keep white icons over it.
            headerContent = if (dark) null else Color.White,
        )
    }

    @Composable
    override fun Header(podcast: Podcast, feed: List<Episode>, description: BrandedDescription, chips: @Composable () -> Unit, modifier: Modifier) =
        DailyHeader(podcast, description, chips, modifier)

    @Composable
    override fun Spotlight(spotlight: BrandedSpotlight, modifier: Modifier) = DailySpotlight(spotlight, modifier)
}
