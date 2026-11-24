package cx.aswin.boxlore.feature.player.v2.logic

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.min
import cx.aswin.boxlore.feature.player.v2.HeroDimensions

internal data class ResponsiveHeroLayout(
    val isCompact: Boolean,
    val dimensions: HeroDimensions,
    /** Space above the cover: 6dp on short windows growing to 14dp, in step with the cover. */
    val topSpacing: Dp = if (isCompact) 6.dp else 14.dp,
)

internal fun calculateResponsiveHeroLayout(
    maxWidth: Dp,
    maxHeight: Dp,
    isVideo: Boolean
): ResponsiveHeroLayout {
    val isCompact = maxHeight < 620.dp
    // The cover's share of the height grows smoothly between short and tall windows, so windows of
    // almost the same height (Galaxy Z Fold 8 cover) never get covers 40dp apart.
    val growth = ((maxHeight - HERO_GROWTH_START) / (HERO_GROWTH_END - HERO_GROWTH_START)).coerceIn(0f, 1f)
    val availableHeight = maxHeight * (0.28f + 0.06f * growth)
    val topSpacing = lerp(6.dp, 14.dp, growth)
    if (!isVideo) {
        val size = min(maxWidth * 0.68f, availableHeight).coerceAtLeast(138.dp)
        return ResponsiveHeroLayout(isCompact, HeroDimensions(size, size), topSpacing)
    }
    val targetWidth = maxWidth * 0.95f - 48.dp
    val targetHeight = targetWidth * (9f / 16f)
    val dimensions = if (targetHeight > availableHeight) {
        HeroDimensions(availableHeight * (16f / 9f), availableHeight)
    } else {
        HeroDimensions(targetWidth, targetHeight)
    }
    return ResponsiveHeroLayout(isCompact, dimensions, topSpacing)
}

/**
 * Phones held sideways ([shortPane], wider than tall, not a tablet layout) put the cover and title
 * beside the seekbar, play controls and quick actions, so all of them show without scrolling. Windows
 * under [SIDEWAYS_MIN_WIDTH_DP] (sideways split-screen halves) keep the single scrolling column,
 * since two columns there would be too narrow for the play controls and the quick actions.
 */
internal fun usesSidewaysPhoneColumns(widthDp: Float, heightDp: Float, tabletLayout: Boolean, shortPane: Boolean): Boolean =
    !tabletLayout && shortPane && widthDp > heightDp && widthDp >= SIDEWAYS_MIN_WIDTH_DP

/** Sideways phones with less than [SIDEWAYS_TIGHT_HEIGHT_DP] under the system bars use smaller controls and gaps. */
internal fun usesTightSidewaysControls(visibleHeight: Dp): Boolean = visibleHeight < SIDEWAYS_TIGHT_HEIGHT_DP.dp

/** The cover beside the controls: as tall as the column allows after the title, within its width. */
internal fun sidewaysHeroSize(columnWidth: Dp, columnHeight: Dp): Dp =
    min(columnWidth * 0.68f, columnHeight - SIDEWAYS_TITLE_ROOM).coerceIn(96.dp, 280.dp)

private const val SIDEWAYS_MIN_WIDTH_DP = 600f
private const val SIDEWAYS_TIGHT_HEIGHT_DP = 400f
private val SIDEWAYS_TITLE_ROOM = 104.dp
private val HERO_GROWTH_START = 560.dp
private val HERO_GROWTH_END = 680.dp

/**
 * The Cast panel's size on phones: the cover square while it has room for the device row, volume and
 * buttons; otherwise at least that tall and a little wider, never past the column ([maxWidth] less
 * its 24dp sides).
 */
internal fun castHeroDimensions(art: HeroDimensions, maxWidth: Dp, canControlVolume: Boolean): HeroDimensions {
    val minimum = if (canControlVolume) 200.dp else 150.dp
    if (art.height >= minimum) return art
    val width = min(maxOf(art.width, 240.dp), maxWidth - 48.dp)
    return HeroDimensions(width, minimum)
}

internal data class PlayerSheetGeometryValues(
    val expansionFraction: Float,
    val miniPlayerHeight: Dp,
    val sheetHeight: Dp,
    val topCornerRadius: Dp,
    val bottomCornerRadius: Dp,
    val horizontalPadding: Dp,
    val sheetElevation: Dp,
    val miniAlpha: Float,
    val fullAlpha: Float,
    val fullTranslationY: Float,
    val fullScale: Float,
)

internal data class PlayerSheetGeometryInput(
    val sheetOffset: Float,
    val collapsedTargetY: Float,
    val containerHeight: Dp,
    val collapsedHorizontalPadding: Dp,
    val fullEntranceOffsetPx: Float,
    val miniPlayerHeight: Dp = 64.dp,
    val collapsedTopCornerRadius: Dp = 32.dp,
    val collapsedBottomCornerRadius: Dp = 32.dp,
    /** How far the mini player had shrunk into the compact cookie when the sheet opens. */
    val compactness: Float = 0f,
)

internal fun calculatePlayerSheetGeometry(
    input: PlayerSheetGeometryInput,
): PlayerSheetGeometryValues {
    val expansionFraction = if (input.collapsedTargetY <= 0f) {
        0f
    } else {
        (1f - input.sheetOffset / input.collapsedTargetY).coerceIn(0f, 1f)
    }
    val reveal = calculatePlayerSheetReveal(expansionFraction, input.compactness)
    val cornerHold = playerCornerHold(expansionFraction)
    return PlayerSheetGeometryValues(
        expansionFraction = expansionFraction,
        miniPlayerHeight = input.miniPlayerHeight,
        sheetHeight = lerp(input.miniPlayerHeight, input.containerHeight, expansionFraction),
        topCornerRadius = input.collapsedTopCornerRadius * cornerHold,
        bottomCornerRadius = input.collapsedBottomCornerRadius * cornerHold,
        horizontalPadding = lerp(input.collapsedHorizontalPadding, 0.dp, expansionFraction),
        sheetElevation = lerp(3.dp, 16.dp, expansionFraction),
        miniAlpha = reveal.miniAlpha,
        fullAlpha = reveal.fullAlpha,
        fullTranslationY = input.fullEntranceOffsetPx * reveal.entranceRemaining,
        fullScale = reveal.fullScale,
    )
}
