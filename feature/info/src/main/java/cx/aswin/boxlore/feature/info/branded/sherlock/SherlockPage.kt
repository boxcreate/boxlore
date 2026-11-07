package cx.aswin.boxlore.feature.info.branded.sherlock

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
 * Sherlock Holmes Short Stories as The Strand Magazine, where the stories first ran: an issue on a
 * lamp-lit desk that opens to its contents, the latest story set as its opening page, and the issue
 * delivered through 221B's letterbox when you subscribe. Dark in both themes.
 */
internal object SherlockPage : BrandedShowPage {
    override val ids = BrandedShowIds(
        podcastIndexId = "7131280",
        podcastGuid = "0a245e72-7c89-5da6-9e43-0041937f1a7c",
        feedUrl = "https://feeds.megaphone.fm/NSR5430340583",
    )

    // The desk starts under the status bar; it draws itself up behind it.
    override val headerTop = 0.dp
    override val subscribeMoment: BrandedSubscribeMoment = SherlockLetterbox
    override val darkOnly = true

    override fun headerToButtons(description: BrandedDescription) = 14.dp

    override fun style(base: ColorScheme, dark: Boolean): BrandedPageStyle {
        val c = SherlockColors
        val colors = base.copy(
            primary = c.Blue,
            onPrimary = c.OnBlue,
            primaryContainer = c.Blue,
            onPrimaryContainer = c.OnBlue,
            secondary = c.Blue,
            onSecondary = c.OnBlue,
            secondaryContainer = c.Blue,
            onSecondaryContainer = c.OnBlue,
            background = c.Page,
            onBackground = c.Text,
            surface = c.Page,
            onSurface = c.Text,
            // Unique, so Material's matching content colour for cards and chips stays onSurface.
            surfaceVariant = c.Control,
            onSurfaceVariant = c.Muted,
            surfaceDim = c.Page,
            surfaceBright = c.Copyright.popup,
            surfaceContainerLowest = c.Page,
            surfaceContainerLow = c.Card,
            surfaceContainer = c.Card,
            surfaceContainerHigh = c.Control,
            surfaceContainerHighest = c.CardControl,
            outline = c.Muted,
            outlineVariant = c.ChipLine,
            surfaceTint = c.Blue,
        )
        return BrandedPageStyle(
            colors = colors,
            popup = c.Copyright,
            navigationColors = colors.copy(
                surfaceContainerHigh = c.Navigation,
                onSurfaceVariant = c.NavigationText,
                outlineVariant = c.Navigation,
            ),
            chips = BrandedChipStyle(
                shape = RoundedCornerShape(2.dp),
                height = 34.dp,
                padding = PaddingValues(start = 10.dp, end = 13.dp),
                iconSize = 18.dp,
                gap = 6.dp,
                fontSize = 14.sp,
                fontWeight = 500,
                container = c.Page,
                content = c.Text,
                icon = c.Blue,
                genreContainer = c.Blue,
                genreContent = c.OnBlue,
                genreIcon = c.OnBlue,
                outline = c.ChipLine,
            ),
            toolbar = BrandedToolbarStyle(
                subscribeContainer = c.Blue,
                subscribeContent = c.OnBlue,
                subscribedContainer = c.Control,
                subscribedContent = c.OnControl,
                toggleContainer = c.Control,
                toggleContent = c.OnControl,
                toggleCheckedContainer = c.Blue,
                toggleCheckedContent = c.OnBlue,
                actionContainer = c.Control,
                actionContent = c.OnControl,
            ),
            episodeTimeColor = c.Blue,
        )
    }

    @Composable
    override fun Header(podcast: Podcast, feed: List<Episode>, description: BrandedDescription, chips: @Composable () -> Unit, modifier: Modifier) =
        SherlockHeader(podcast, feed, description, chips, modifier)

    @Composable
    override fun Spotlight(spotlight: BrandedSpotlight, modifier: Modifier) = SherlockSpotlight(spotlight, modifier)
}
