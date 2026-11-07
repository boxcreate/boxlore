package cx.aswin.boxlore.feature.info.branded.serial

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
 * Serial as its own logo: the stack of black cards with white rims, each card one of its seasons,
 * Serial's own S card peeking out of the deck, on the plum of its cover art. Dark in both themes.
 */
internal object SerialPage : BrandedShowPage {
    override val ids = BrandedShowIds(
        podcastIndexId = "745392",
        podcastGuid = "2d7400e3-bacb-52fd-aabc-0da55e39f98b",
        feedUrl = "https://feeds.simplecast.com/xl36XBC2",
    )

    // The plum starts under the status bar; it draws itself up behind it.
    override val headerTop = 0.dp
    override val subscribeMoment: BrandedSubscribeMoment = SerialOneMore
    override val darkOnly = true

    // Each season's card shows its trailer's picture, or else its first episode's.
    override val headerKeepsTrailers = true

    override fun headerToButtons(description: BrandedDescription) = 18.dp

    override fun style(base: ColorScheme, dark: Boolean): BrandedPageStyle {
        val c = SerialColors
        val colors = base.copy(
            primary = c.Red,
            onPrimary = c.OnRed,
            primaryContainer = c.Red,
            onPrimaryContainer = c.OnRed,
            secondary = c.Red,
            onSecondary = c.OnRed,
            secondaryContainer = c.Red,
            onSecondaryContainer = c.OnRed,
            background = c.Page,
            onBackground = c.Text,
            surface = c.Page,
            onSurface = c.Text,
            // Unique, so Material's matching content colour for cards and chips stays onSurface.
            surfaceVariant = c.Placeholder,
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
            surfaceTint = c.Red,
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
                shape = RoundedCornerShape(10.dp),
                height = 34.dp,
                padding = PaddingValues(start = 10.dp, end = 13.dp),
                iconSize = 18.dp,
                gap = 6.dp,
                fontSize = 14.sp,
                fontWeight = 500,
                container = c.Page,
                content = c.Text,
                icon = c.RedText,
                genreContainer = c.Red,
                genreContent = c.OnRed,
                genreIcon = c.OnRed,
                outline = c.ChipLine,
            ),
            toolbar = BrandedToolbarStyle(
                subscribeContainer = c.Red,
                subscribeContent = c.OnRed,
                subscribedContainer = c.Control,
                subscribedContent = c.OnControl,
                toggleContainer = c.Control,
                toggleContent = c.OnControl,
                toggleCheckedContainer = c.Red,
                toggleCheckedContent = c.OnRed,
                actionContainer = c.Control,
                actionContent = c.OnControl,
            ),
            episodeTimeColor = c.RedText,
        )
    }

    @Composable
    override fun Header(podcast: Podcast, feed: List<Episode>, description: BrandedDescription, chips: @Composable () -> Unit, modifier: Modifier) =
        SerialHeader(podcast, feed, description, chips, modifier)

    @Composable
    override fun Spotlight(spotlight: BrandedSpotlight, modifier: Modifier) = SerialSpotlight(spotlight, modifier)
}
