package cx.aswin.boxlore.feature.info.branded.desicrime

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
 * The Desi Crime Podcast as the hosts' crime board: the cork wall behind Aryaan and Aishwarya on the
 * show's cover, pinned with prints of past cases; the latest case written up on a sticky note; and
 * a listener's chai and chithi when you subscribe. Dark in both themes.
 */
internal object DesiCrimePage : BrandedShowPage {
    override val ids = BrandedShowIds(
        podcastIndexId = "7976624",
        podcastGuid = "8ee0469f-e82f-54f1-9736-0e24334c51c4",
        feedUrl = "https://feeds.acast.com/public/shows/6a55242c7987e974eaf4ba29",
    )

    // The cork starts under the status bar; it draws itself up behind it.
    override val headerTop = 0.dp
    override val subscribeMoment: BrandedSubscribeMoment = DesiCrimeChithi
    override val darkOnly = true

    override fun headerToButtons(description: BrandedDescription) = 14.dp

    override fun style(base: ColorScheme, dark: Boolean): BrandedPageStyle {
        val c = DesiCrimeColors
        val colors = base.copy(
            primary = c.Saffron,
            onPrimary = c.OnSaffron,
            primaryContainer = c.Saffron,
            onPrimaryContainer = c.OnSaffron,
            secondary = c.Saffron,
            onSecondary = c.OnSaffron,
            secondaryContainer = c.Saffron,
            onSecondaryContainer = c.OnSaffron,
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
            surfaceTint = c.Saffron,
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
                icon = c.Saffron,
                genreContainer = c.Saffron,
                genreContent = c.OnSaffron,
                genreIcon = c.OnSaffron,
                outline = c.ChipLine,
            ),
            toolbar = BrandedToolbarStyle(
                subscribeContainer = c.Saffron,
                subscribeContent = c.OnSaffron,
                subscribedContainer = c.Control,
                subscribedContent = c.OnControl,
                toggleContainer = c.Control,
                toggleContent = c.OnControl,
                toggleCheckedContainer = c.Saffron,
                toggleCheckedContent = c.OnSaffron,
                actionContainer = c.Control,
                actionContent = c.OnControl,
            ),
            episodeTimeColor = c.Saffron,
        )
    }

    @Composable
    override fun Header(podcast: Podcast, feed: List<Episode>, description: BrandedDescription, chips: @Composable () -> Unit, modifier: Modifier) =
        DesiCrimeHeader(podcast, feed, description, chips, modifier)

    @Composable
    override fun Spotlight(spotlight: BrandedSpotlight, modifier: Modifier) = DesiCrimeSpotlight(spotlight, modifier)
}
