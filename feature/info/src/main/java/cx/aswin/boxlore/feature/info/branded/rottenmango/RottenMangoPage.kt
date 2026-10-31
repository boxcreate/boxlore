package cx.aswin.boxlore.feature.info.branded.rottenmango

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

/** Rotten Mango as Her room: Stephanie Soo in her red room, the couch's gold and a worn old frame. */
internal object RottenMangoPage : BrandedShowPage {
    override val ids = BrandedShowIds(
        podcastIndexId = "490022",
        podcastGuid = "da0493ca-8ce4-5bab-be6e-37b7b0b945f9",
        feedUrl = "https://feeds.simplecast.com/6Qp23t6h",
    )

    /** The photo starts under the top bar; the header places its text below her lap itself. */
    override val headerTop = 0.dp
    override val subscribeMoment: BrandedSubscribeMoment = RottenMangoOnTape
    override val darkOnly = true

    override fun headerToButtons(description: BrandedDescription) = 12.dp

    override fun style(base: ColorScheme, dark: Boolean): BrandedPageStyle {
        val c = RottenMangoColors
        // Dark only: in light mode the app's light scheme is no base for this page.
        val from = if (base.background.luminance() < 0.5f) base else darkColorScheme()
        val colors = from.copy(
            primary = c.Gold,
            onPrimary = c.OnGold,
            primaryContainer = c.Gold,
            onPrimaryContainer = c.OnGold,
            secondary = c.Gold,
            onSecondary = c.OnGold,
            secondaryContainer = c.Gold,
            onSecondaryContainer = c.OnGold,
            background = c.Page,
            onBackground = c.Ink,
            surface = c.Page,
            onSurface = c.Ink,
            // Unique, so Material's matching content colour for cards and chips stays onSurface.
            surfaceVariant = Color(0xFF180607),
            onSurfaceVariant = c.Sub,
            surfaceDim = c.Page,
            surfaceBright = c.Copyright.popup,
            surfaceContainerLowest = c.Page,
            surfaceContainerLow = c.Card,
            surfaceContainer = c.Navigation,
            surfaceContainerHigh = c.Control,
            surfaceContainerHighest = c.Control,
            outline = c.Sub,
            outlineVariant = c.ChipLine,
            surfaceTint = c.Gold,
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
                icon = c.Gold,
                genreContainer = c.Gold,
                genreContent = c.OnGold,
                genreIcon = c.OnGold,
                outline = c.ChipLine,
            ),
            toolbar = BrandedToolbarStyle(
                subscribeContainer = c.Gold,
                subscribeContent = c.OnGold,
                subscribedContainer = c.Control,
                subscribedContent = c.Ink,
                toggleContainer = c.Control,
                toggleContent = c.Ink,
                toggleCheckedContainer = c.Gold,
                toggleCheckedContent = c.OnGold,
                actionContainer = c.Control,
                actionContent = c.Ink,
            ),
            episodeTimeColor = c.Gold,
        )
    }

    @Composable
    override fun Header(podcast: Podcast, description: BrandedDescription, chips: @Composable () -> Unit, modifier: Modifier) =
        RottenMangoHeader(podcast, description, chips, modifier)

    @Composable
    override fun Tag(modifier: Modifier) = RottenMangoTag(modifier)

    @Composable
    override fun Spotlight(spotlight: BrandedSpotlight, modifier: Modifier) = RottenMangoSpotlight(spotlight, modifier)
}
