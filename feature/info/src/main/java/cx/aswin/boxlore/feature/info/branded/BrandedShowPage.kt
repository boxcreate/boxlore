package cx.aswin.boxlore.feature.info.branded

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.PodrollItem
import cx.aswin.boxlore.core.model.listenerShowIds
import kotlinx.coroutines.flow.first

/**
 * A show picked by boxlore, drawn in its own style. A pack owns the identity block above the chips,
 * the tag in the top bar and the latest-episode spotlight. The chips, buttons and episode list
 * keep their usual layout and take only the pack's shapes and colours.
 */
internal interface BrandedShowPage {
    val ids: BrandedShowIds

    /** Space from the status bar to the top of [Header]. */
    val headerTop: Dp

    /** Space from the bottom of [Header] to the action buttons. */
    fun headerToButtons(description: BrandedDescription): Dp

    /** The show's own subscribe moment, or null for the usual genre icon pop. */
    val subscribeMoment: BrandedSubscribeMoment? get() = null

    /** A show whose identity is dark only keeps its dark page, status bar and navbar in light mode too. */
    val darkOnly: Boolean get() = false

    /** Page colours, and the shapes and colours of the shared controls, for one theme. */
    fun style(base: ColorScheme, dark: Boolean): BrandedPageStyle

    /**
     * Everything above the action buttons, in the order the show's design puts it: its artwork,
     * logos and copyright button; the usual [chips] row, placed as it comes (any number of chips,
     * scrolling sideways, from the page margin to the screen edge); and the [description] when it
     * [BrandedDescription.isVisible]. Tapping the description shows the full text and, as on every
     * show page, [BrandedDescriptionExtras].
     */
    @Composable
    fun Header(podcast: Podcast, description: BrandedDescription, chips: @Composable () -> Unit, modifier: Modifier)

    /** The "Made for:" tag centred in the top bar ([BrandedTag]). */
    @Composable
    fun Tag(modifier: Modifier)

    /** The latest episode's card, with its heading. */
    @Composable
    fun Spotlight(spotlight: BrandedSpotlight, modifier: Modifier)
}

/** The show description and its expanded state, as the regular show page keeps them. */
@Immutable
internal data class BrandedDescription(
    val text: String,
    val isLocked: Boolean,
    val podroll: List<PodrollItem>,
    val expanded: Boolean,
    val onToggle: () -> Unit,
    val onPodcastClick: (String) -> Unit,
) {
    /** Shown when there is anything to show, like the regular page's description card. */
    val isVisible: Boolean get() = text.isNotEmpty() || isLocked || podroll.isNotEmpty()
}

/**
 * A show's subscribe moment, played over the Subscribe button in place of the genre icon pop. It
 * always ends on the usual Subscribed state; with animations off it never plays.
 */
internal interface BrandedSubscribeMoment {
    /** How long the moment runs before the button settles on Subscribed. */
    val durationMillis: Int

    /** When alerts and auto-download start sliding in, as usual. */
    val revealDelayMillis: Int

    /** The Subscribe button's own nudge at [elapsedMillis]. */
    fun nudge(elapsedMillis: Float): BrandedButtonNudge

    /** Everything drawn over the Subscribe button, in its [shape]; [elapsedMillis] is read while drawing. */
    @Composable
    fun Overlay(elapsedMillis: () -> Float, labels: BrandedSubscribeLabels, style: BrandedToolbarStyle, shape: Shape, modifier: Modifier)
}

/**
 * One haptic as the subscribe moment's own clock passes [atMillis], so it lands on its beat at any
 * system animation speed.
 */
@Composable
internal fun MomentHaptic(elapsedMillis: () -> Float, atMillis: Number, type: HapticFeedbackType = HapticFeedbackType.ContextClick) {
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(Unit) {
        snapshotFlow { elapsedMillis() >= atMillis.toFloat() }.first { it }
        haptics.performHapticFeedback(type)
    }
}

@Immutable
internal data class BrandedButtonNudge(val offsetY: Dp, val scale: Float)

@Immutable
internal data class BrandedSubscribeLabels(val subscribe: String, val subscribed: String, val compact: Boolean)

/** Every id a show can be opened under: its Podcast Index feed, a linked RSS copy, its GUID or its feed URL. */
@Immutable
internal data class BrandedShowIds(val podcastIndexId: String, val podcastGuid: String, val feedUrl: String) {
    fun matches(podcast: Podcast): Boolean = podcastIndexId in podcast.listenerShowIds ||
        podcast.podcastGuid?.equals(podcastGuid, ignoreCase = true) == true ||
        podcast.feedUrl?.let(::normalizedFeedUrl) == normalizedFeedUrl(feedUrl)
}

private fun normalizedFeedUrl(url: String): String = url.trim().lowercase()
    .removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/')

/** The branded page for [podcast], when it has one in this build. */
internal fun brandedShowPageFor(podcast: Podcast?, pages: List<BrandedShowPage> = BrandedShowPages.all): BrandedShowPage? =
    podcast?.let { show -> pages.firstOrNull { it.ids.matches(show) } }

@Immutable
internal data class BrandedPageStyle(
    val colors: ColorScheme,
    /** The bottom bar's colours while this page is open. */
    val navigationColors: ColorScheme,
    val chips: BrandedChipStyle,
    val toolbar: BrandedToolbarStyle,
    /** The release time in each episode card's details line. */
    val episodeTimeColor: Color,
)

@Immutable
internal data class BrandedChipStyle(
    val shape: Shape,
    val height: Dp,
    val padding: PaddingValues,
    val iconSize: Dp,
    val gap: Dp,
    val fontSize: TextUnit,
    val fontWeight: Int,
    val container: Color,
    val content: Color,
    val icon: Color,
    val genreContainer: Color,
    val genreContent: Color,
    val genreIcon: Color,
    /** A thin ring around the chips other than genre, or null for none. */
    val outline: Color? = null,
)

/** The action buttons keep their usual shapes and layout; a branded page sets only their colours. */
@Immutable
internal data class BrandedToolbarStyle(
    val subscribeContainer: Color,
    val subscribeContent: Color,
    val subscribedContainer: Color,
    val subscribedContent: Color,
    val toggleContainer: Color,
    val toggleContent: Color,
    val toggleCheckedContainer: Color,
    val toggleCheckedContent: Color,
    val actionContainer: Color,
    val actionContent: Color,
)

/** The page's active branded show and the style resolved for the current theme. */
@Immutable
internal data class ActiveBrandedPage(val page: BrandedShowPage, val style: BrandedPageStyle)

internal val LocalBrandedPage = staticCompositionLocalOf<ActiveBrandedPage?> { null }

/**
 * What a branded header can move with: one of the show's episodes playing, and the subscribe
 * moment's clock, which the Subscribe button runs and the header may follow.
 */
@Stable
internal class BrandedHeaderMotion {
    var showPlaying by mutableStateOf(false)

    /** Milliseconds into the subscribe moment while it plays, otherwise null. Read it while drawing. */
    var subscribeMomentMillis: (() -> Float)? by mutableStateOf(null)
}

internal val LocalBrandedHeaderMotion = staticCompositionLocalOf<BrandedHeaderMotion?> { null }
