package cx.aswin.boxlore.feature.info.branded.vergecast

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

/** The Vergecast in The Verge's ultraviolet and lime, with its wordmark and sticker art. */
internal object VergecastPage : BrandedShowPage {
    override val ids = BrandedShowIds(
        podcastIndexId = "415434",
        podcastGuid = "b9daf664-eed0-5f5b-876a-22afdf28bd97",
        feedUrl = "https://feeds.megaphone.fm/vergecast",
    )
    override val headerTop = 71.dp
    override val subscribeMoment: BrandedSubscribeMoment = VergecastStickerSlap

    override fun style(base: ColorScheme, dark: Boolean): BrandedPageStyle {
        val c = if (dark) VergecastColors.Dark else VergecastColors.Light
        val colors = base.copy(
            primary = c.accent,
            onPrimary = c.onAccent,
            primaryContainer = c.accent,
            onPrimaryContainer = c.onAccent,
            secondary = VergecastColors.Ultraviolet,
            onSecondary = Color.White,
            secondaryContainer = VergecastColors.Ultraviolet,
            onSecondaryContainer = Color.White,
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
            surfaceContainer = c.bar,
            surfaceContainerHigh = c.chip,
            surfaceContainerHighest = c.cardControl,
            outline = c.copyright.icon,
            outlineVariant = c.divider,
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
                // Sharp, like The Verge's own type and stickers.
                shape = RoundedCornerShape(2.dp),
                height = 34.dp,
                padding = PaddingValues(start = 9.dp, end = 12.dp),
                iconSize = 18.dp,
                gap = 6.dp,
                fontSize = 14.sp,
                fontWeight = 500,
                container = c.chip,
                content = c.chipText,
                icon = c.accentOnPage,
                genreContainer = VergecastColors.Ultraviolet,
                genreContent = Color.White,
                genreIcon = Color.White,
            ),
            toolbar = BrandedToolbarStyle(
                subscribeContainer = c.accent,
                subscribeContent = c.onAccent,
                subscribedContainer = c.control,
                subscribedContent = c.text,
                toggleContainer = c.control,
                toggleContent = c.text,
                toggleCheckedContainer = c.accent,
                toggleCheckedContent = c.onAccent,
                actionContainer = c.control,
                actionContent = c.text,
            ),
            episodeTimeColor = c.accentOnPage,
        )
    }

    override fun headerToButtons(description: BrandedDescription) = if (description.isVisible) 16.dp else 14.dp

    @Composable
    override fun Header(podcast: Podcast, description: BrandedDescription, chips: @Composable () -> Unit, modifier: Modifier) {
        Column(modifier) {
            VergecastIdentity(podcast, Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            chips()
            if (description.isVisible) VergecastDescription(description, Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        }
    }

    @Composable
    override fun Tag(modifier: Modifier) = VergecastTag(modifier)

    @Composable
    override fun Spotlight(spotlight: BrandedSpotlight, modifier: Modifier) = VergecastSpotlight(spotlight, modifier)
}
