package cx.aswin.boxlore.feature.info.branded.history

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
 * The Rest Is History as a gallery: the show's claret velvet as a museum wall hung with its latest
 * episodes in bronze frames, David and Churchill from its cover standing in front, and the lights
 * coming on when the page opens. The wall stays velvet in light mode, over an ivory page.
 */
internal object HistoryPage : BrandedShowPage {
    override val ids = BrandedShowIds(
        podcastIndexId = "1365765",
        podcastGuid = "ca979534-b06b-5b00-8b4d-17ee18d4f73d",
        feedUrl = "https://feeds.megaphone.fm/GLT4787413333",
    )

    // The wall starts under the status bar; it draws itself up behind it.
    override val headerTop = 0.dp
    override val subscribeMoment: BrandedSubscribeMoment = HistoryThroughTheAges

    override fun headerToButtons(description: BrandedDescription) = 18.dp

    override fun style(base: ColorScheme, dark: Boolean): BrandedPageStyle {
        val c = if (dark) HistoryColors.Dark else HistoryColors.Light
        val colors = base.copy(
            primary = c.primary,
            onPrimary = c.onPrimary,
            primaryContainer = c.primary,
            onPrimaryContainer = c.onPrimary,
            secondary = c.primary,
            onSecondary = c.onPrimary,
            secondaryContainer = c.primary,
            onSecondaryContainer = c.onPrimary,
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
            surfaceContainer = c.card,
            surfaceContainerHigh = c.control,
            surfaceContainerHighest = c.cardControl,
            outline = c.textMuted,
            outlineVariant = c.chipLine,
            surfaceTint = c.primary,
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
                shape = RoundedCornerShape(4.dp),
                height = 34.dp,
                padding = PaddingValues(start = 10.dp, end = 13.dp),
                iconSize = 18.dp,
                gap = 6.dp,
                fontSize = 14.sp,
                fontWeight = 500,
                container = c.page,
                content = c.text,
                icon = c.rose,
                genreContainer = c.primary,
                genreContent = c.onPrimary,
                genreIcon = c.onPrimary,
                outline = c.chipLine,
            ),
            toolbar = BrandedToolbarStyle(
                subscribeContainer = c.primary,
                subscribeContent = c.onPrimary,
                subscribedContainer = c.control,
                subscribedContent = c.onControl,
                toggleContainer = c.control,
                toggleContent = c.onControl,
                toggleCheckedContainer = c.primary,
                toggleCheckedContent = c.onPrimary,
                actionContainer = c.control,
                actionContent = c.onControl,
            ),
            episodeTimeColor = c.rose,
            // The velvet wall stays dark by day, so the top bar keeps ivory icons over it.
            headerContent = if (dark) null else HistoryColors.OnWall,
        )
    }

    @Composable
    override fun Header(podcast: Podcast, feed: List<Episode>, description: BrandedDescription, chips: @Composable () -> Unit, modifier: Modifier) =
        HistoryHeader(podcast, feed, description, chips, modifier)

    @Composable
    override fun Spotlight(spotlight: BrandedSpotlight, modifier: Modifier) = HistorySpotlight(spotlight, modifier)
}
