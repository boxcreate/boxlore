package cx.aswin.boxlore.feature.info.branded.jre

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
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

/** The Joe Rogan Experience as The studio: the red curtain, the official badge and the show's orange. */
internal object JrePage : BrandedShowPage {
    override val ids = BrandedShowIds(
        podcastIndexId = "550168",
        podcastGuid = "9d783600-a77f-5c77-9042-0d9f3280465e",
        feedUrl = "https://feeds.megaphone.fm/GLT1412515089",
    )
    override val headerTop = 61.dp
    override val subscribeMoment: BrandedSubscribeMoment = JreThirdEye
    override val darkOnly = true

    override fun headerToButtons(description: BrandedDescription) = 12.dp

    override fun style(base: ColorScheme, dark: Boolean): BrandedPageStyle {
        val c = JreColors
        // Dark only: in light mode the app's light scheme is no base for this page.
        val from = if (base.background.luminance() < 0.5f) base else darkColorScheme()
        val colors = from.copy(
            primary = c.Orange,
            onPrimary = c.OnOrange,
            primaryContainer = c.Orange,
            onPrimaryContainer = c.OnOrange,
            secondary = c.Orange,
            onSecondary = c.OnOrange,
            secondaryContainer = c.Orange,
            onSecondaryContainer = c.OnOrange,
            background = c.Page,
            onBackground = c.Ink,
            surface = c.Page,
            onSurface = c.Ink,
            // Unique, so Material's matching content colour for cards and chips stays onSurface.
            surfaceVariant = c.Picture,
            onSurfaceVariant = c.Sub,
            surfaceDim = c.Page,
            surfaceBright = c.Copyright.popup,
            surfaceContainerLowest = c.Page,
            surfaceContainerLow = c.Panel,
            surfaceContainer = c.Navigation,
            surfaceContainerHigh = c.Control,
            surfaceContainerHighest = c.Control,
            outline = c.Sub,
            outlineVariant = c.ChipLine,
            surfaceTint = c.Orange,
        )
        return BrandedPageStyle(
            colors = colors,
            navigationColors = colors.copy(
                surfaceContainerHigh = c.Navigation,
                onSurfaceVariant = c.NavigationText,
                outlineVariant = c.Navigation,
            ),
            chips = BrandedChipStyle(
                shape = RoundedCornerShape(17.dp),
                height = 34.dp,
                padding = PaddingValues(start = 10.dp, end = 13.dp),
                iconSize = 18.dp,
                gap = 6.dp,
                fontSize = 14.sp,
                fontWeight = 500,
                container = c.Page,
                content = c.Ink,
                icon = c.Orange,
                genreContainer = c.Orange,
                genreContent = c.OnOrange,
                genreIcon = c.OnOrange,
                outline = c.ChipLine,
            ),
            toolbar = BrandedToolbarStyle(
                subscribeContainer = c.Orange,
                subscribeContent = c.OnOrange,
                subscribedContainer = c.Control,
                subscribedContent = c.Ink,
                toggleContainer = c.Control,
                toggleContent = c.Ink,
                toggleCheckedContainer = c.Orange,
                toggleCheckedContent = c.OnOrange,
                actionContainer = c.Control,
                actionContent = c.Ink,
            ),
            episodeTimeColor = c.Orange,
        )
    }

    @Composable
    override fun Header(podcast: Podcast, description: BrandedDescription, chips: @Composable () -> Unit, modifier: Modifier) =
        JreHeader(podcast, description, chips, modifier)

    @Composable
    override fun Tag(modifier: Modifier) = JreTag(modifier)

    @Composable
    override fun Spotlight(spotlight: BrandedSpotlight, modifier: Modifier) = JreSpotlight(spotlight, modifier)
}
