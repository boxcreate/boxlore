package cx.aswin.boxlore.feature.info.branded.conspiracy

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

/** Conspiracy Theories as a case file: mustard paper, typewriter type, a stamped wordmark. */
internal object ConspiracyPage : BrandedShowPage {
    override val ids = BrandedShowIds(
        podcastIndexId = "9926",
        podcastGuid = "b20e2ba1-cace-5f92-aa6a-335343b8ba0a",
        feedUrl = "https://feeds.megaphone.fm/END5064265457",
    )
    override val headerTop = 73.dp
    override val subscribeMoment: BrandedSubscribeMoment = ConspiracyTypedAndFiled

    override fun headerToButtons(description: BrandedDescription) = 14.dp

    override fun style(base: ColorScheme, dark: Boolean): BrandedPageStyle {
        val c = if (dark) ConspiracyColors.Dark else ConspiracyColors.Light
        val colors = base.copy(
            primary = c.accent,
            onPrimary = c.onAccent,
            primaryContainer = c.accent,
            onPrimaryContainer = c.onAccent,
            secondary = c.genreChip,
            onSecondary = c.onGenreChip,
            secondaryContainer = c.genreChip,
            onSecondaryContainer = c.onGenreChip,
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
            outline = c.textMuted,
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
                // Folder-tab chips: rounded on top, nearly square below.
                shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomEnd = 3.dp, bottomStart = 3.dp),
                height = 34.dp,
                padding = PaddingValues(start = 9.dp, end = 12.dp),
                iconSize = 18.dp,
                gap = 6.dp,
                fontSize = 14.sp,
                fontWeight = 500,
                container = c.chip,
                content = c.chipText,
                icon = c.accentOnPage,
                genreContainer = c.genreChip,
                genreContent = c.onGenreChip,
                genreIcon = c.onGenreChip,
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

    @Composable
    override fun Header(podcast: Podcast, description: BrandedDescription, chips: @Composable () -> Unit, modifier: Modifier) =
        ConspiracyHeader(podcast, description, chips, modifier)

    @Composable
    override fun Tag(modifier: Modifier) = ConspiracyTag(modifier)

    @Composable
    override fun Spotlight(spotlight: BrandedSpotlight, modifier: Modifier) = ConspiracySpotlight(spotlight, modifier)
}
