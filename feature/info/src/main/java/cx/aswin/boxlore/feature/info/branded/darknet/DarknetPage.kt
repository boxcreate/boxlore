package cx.aswin.boxlore.feature.info.branded.darknet

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
 * Darknet Diaries in its own ink: the show's flag-bearer art, cut out of its black, its flag waving
 * in a red light, with the episode illustrations' grit and drifting red ink. Dark in both themes.
 */
internal object DarknetPage : BrandedShowPage {
    override val ids = BrandedShowIds(
        podcastIndexId = "577105",
        podcastGuid = "ac631a54-a629-5367-a1a5-c2e2f7c25054",
        feedUrl = "https://podcast.darknetdiaries.com",
    )

    // The art starts under the status bar; it draws itself up behind it.
    override val headerTop = 0.dp
    override val subscribeMoment: BrandedSubscribeMoment = DarknetMaskedHost
    override val darkOnly = true

    override fun headerToButtons(description: BrandedDescription) = 18.dp

    override fun style(base: ColorScheme, dark: Boolean): BrandedPageStyle {
        val c = DarknetColors
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
                shape = RoundedCornerShape(3.dp),
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
        DarknetHeader(podcast, description, chips, modifier)

    @Composable
    override fun Spotlight(spotlight: BrandedSpotlight, modifier: Modifier) = DarknetSpotlight(spotlight, modifier)
}
