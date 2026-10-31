package cx.aswin.boxlore.feature.info.branded.waveform

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

/** Waveform: The MKBHD Podcast as Signal: the traced logo across the top, black page, one red. */
internal object WaveformPage : BrandedShowPage {
    override val ids = BrandedShowIds(
        podcastIndexId = "947138",
        podcastGuid = "dbaa1d5f-2d6d-5852-a6de-6cd11c25f732",
        feedUrl = "https://feeds.megaphone.fm/STU4418364045",
    )
    override val headerTop = 79.dp
    override val subscribeMoment: BrandedSubscribeMoment = WaveformLogoIntro
    override val darkOnly = true

    override fun headerToButtons(description: BrandedDescription) = 12.dp

    override fun style(base: ColorScheme, dark: Boolean): BrandedPageStyle {
        // Dark only: in light mode the app's light scheme is no base for this page.
        val from = if (base.background.luminance() < 0.5f) base else darkColorScheme()
        val colors = from.copy(
            primary = WaveformColors.Text,
            onPrimary = WaveformColors.Page,
            primaryContainer = WaveformColors.Text,
            onPrimaryContainer = WaveformColors.Page,
            secondary = WaveformColors.Text,
            onSecondary = WaveformColors.Page,
            secondaryContainer = WaveformColors.Text,
            onSecondaryContainer = WaveformColors.Page,
            background = WaveformColors.Page,
            onBackground = WaveformColors.Text,
            surface = WaveformColors.Page,
            onSurface = WaveformColors.Text,
            // Unique, so Material's matching content colour for cards and chips stays onSurface.
            surfaceVariant = WaveformColors.Placeholder,
            onSurfaceVariant = WaveformColors.Muted,
            surfaceDim = WaveformColors.Page,
            surfaceBright = WaveformColors.CardControl,
            surfaceContainerLowest = WaveformColors.Page,
            surfaceContainerLow = WaveformColors.Card,
            surfaceContainer = WaveformColors.Card,
            surfaceContainerHigh = WaveformColors.Control,
            surfaceContainerHighest = WaveformColors.CardControl,
            outline = WaveformColors.Muted,
            outlineVariant = WaveformColors.CardBorder,
            surfaceTint = WaveformColors.Text,
        )
        return BrandedPageStyle(
            colors = colors,
            navigationColors = colors.copy(
                surfaceContainerHigh = WaveformColors.Control,
                onSurfaceVariant = WaveformColors.NavigationText,
                outlineVariant = WaveformColors.NavigationBorder,
            ),
            chips = BrandedChipStyle(
                shape = RoundedCornerShape(17.dp),
                height = 34.dp,
                padding = PaddingValues(start = 9.dp, end = 12.dp),
                iconSize = 18.dp,
                gap = 6.dp,
                fontSize = 14.sp,
                fontWeight = 500,
                container = Color.Transparent,
                content = WaveformColors.ChipText,
                icon = WaveformColors.Muted,
                genreContainer = WaveformColors.Text,
                genreContent = WaveformColors.Page,
                genreIcon = WaveformColors.Page,
                outline = WaveformColors.ChipOutline,
            ),
            toolbar = BrandedToolbarStyle(
                subscribeContainer = WaveformColors.Text,
                subscribeContent = WaveformColors.Page,
                subscribedContainer = WaveformColors.Control,
                subscribedContent = WaveformColors.Text,
                toggleContainer = WaveformColors.Control,
                toggleContent = WaveformColors.Text,
                toggleCheckedContainer = WaveformColors.Text,
                toggleCheckedContent = WaveformColors.Page,
                actionContainer = WaveformColors.Control,
                actionContent = WaveformColors.Text,
            ),
            episodeTimeColor = WaveformColors.Text,
        )
    }

    @Composable
    override fun Header(podcast: Podcast, description: BrandedDescription, chips: @Composable () -> Unit, modifier: Modifier) =
        WaveformHeader(podcast, description, chips, modifier)

    @Composable
    override fun Tag(modifier: Modifier) = WaveformTag(modifier)

    @Composable
    override fun Spotlight(spotlight: BrandedSpotlight, modifier: Modifier) = WaveformSpotlight(spotlight, modifier)
}
