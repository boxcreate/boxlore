package cx.aswin.boxlore.feature.info.branded.crimejunkie

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

/** Crime Junkie as Fingerprint: what signifies crime, in the show's own ink splatter colours. */
internal object CrimeJunkiePage : BrandedShowPage {
    override val ids = BrandedShowIds(
        podcastIndexId = "743411",
        podcastGuid = "1cd3128e-4576-5cd8-b53d-91f5da51f32c",
        feedUrl = "https://feeds.simplecast.com/qm_9xx0g",
    )
    override val headerTop = 81.dp
    override val subscribeMoment: BrandedSubscribeMoment = CrimeJunkieFlashlight

    override fun headerToButtons(description: BrandedDescription) = 14.dp

    override fun style(base: ColorScheme, dark: Boolean): BrandedPageStyle {
        val c = if (dark) CrimeJunkieColors.Dark else CrimeJunkieColors.Light
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
            surfaceVariant = c.toggle,
            onSurfaceVariant = c.sub,
            surfaceDim = c.page,
            surfaceBright = c.copyright.popup,
            surfaceContainerLowest = c.page,
            surfaceContainerLow = c.card,
            surfaceContainer = c.navigation,
            surfaceContainerHigh = c.control,
            surfaceContainerHighest = c.toggle,
            outline = c.sub,
            outlineVariant = c.chipLine,
            surfaceTint = c.accent,
        )
        return BrandedPageStyle(
            colors = colors,
            navigationColors = colors.copy(
                surfaceContainerHigh = c.navigation,
                onSurfaceVariant = c.navigationText,
                outlineVariant = c.navigation,
            ),
            chips = BrandedChipStyle(
                shape = RoundedCornerShape(17.dp),
                height = 34.dp,
                padding = PaddingValues(start = 10.dp, end = 13.dp),
                iconSize = 18.dp,
                gap = 6.dp,
                fontSize = 14.sp,
                fontWeight = 500,
                container = c.page,
                content = c.ink,
                icon = c.accent,
                genreContainer = c.accent,
                genreContent = c.onAccent,
                genreIcon = c.onAccent,
                outline = c.chipLine,
            ),
            toolbar = BrandedToolbarStyle(
                // The show's violet, like Play and the genre chip.
                subscribeContainer = c.accent,
                subscribeContent = c.onAccent,
                subscribedContainer = c.control,
                subscribedContent = c.ink,
                toggleContainer = c.control,
                toggleContent = c.ink,
                toggleCheckedContainer = c.checked,
                toggleCheckedContent = c.onChecked,
                actionContainer = c.control,
                actionContent = c.ink,
            ),
            episodeTimeColor = c.accent,
        )
    }

    @Composable
    override fun Header(podcast: Podcast, description: BrandedDescription, chips: @Composable () -> Unit, modifier: Modifier) =
        CrimeJunkieHeader(podcast, description, chips, modifier)

    @Composable
    override fun Tag(modifier: Modifier) = CrimeJunkieTag(modifier)

    @Composable
    override fun Spotlight(spotlight: BrandedSpotlight, modifier: Modifier) = CrimeJunkieSpotlight(spotlight, modifier)
}
