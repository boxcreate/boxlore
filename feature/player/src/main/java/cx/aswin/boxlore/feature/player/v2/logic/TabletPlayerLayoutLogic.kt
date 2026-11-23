package cx.aswin.boxlore.feature.player.v2.logic

import cx.aswin.boxlore.core.designsystem.layout.BoxloreFold
import cx.aswin.boxlore.core.designsystem.layout.BoxloreFoldOrientation
import cx.aswin.boxlore.core.designsystem.layout.BoxloreLayoutBounds
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.foldSafeRegions
import kotlin.math.max
import kotlin.math.min

internal enum class TabletPlayerArrangement { Stacked, Columns, HorizontalFold, VerticalFold }

internal data class TabletPlayerRegions(
    val arrangement: TabletPlayerArrangement,
    val header: BoxloreLayoutBounds,
    val artwork: BoxloreLayoutBounds?,
    val controls: BoxloreLayoutBounds,
    val support: BoxloreLayoutBounds,
    val artworkSizeDp: Float,
    /** Where the controls and the supporting panel stack in one scrolling column, when they share a region. */
    val stack: BoxloreLayoutBounds = controls.copy(bottom = support.bottom),
)

/** Regions remain in window coordinates so a context pane never mistakes a hinge for local dp. */
internal fun tabletPlayerRegions(
    window: BoxloreWindowLayout,
    viewport: BoxloreLayoutBounds,
    contextPane: Boolean,
): TabletPlayerRegions? {
    if (!window.usesTabletLayout || viewport.width <= 0f || viewport.height <= 0f) return null
    val safe = window.foldSafeRegions().mapNotNull { intersectPlayerBounds(it, viewport) }
    val horizontal = playerHorizontalFold(window, viewport, safe)
    if (horizontal != null) {
        val upper = safe.filter { it.bottom <= horizontal.bounds.top }.maxBy { it.width * it.height }
        val lower = safe.filter { it.top >= horizontal.bounds.bottom }.maxBy { it.width * it.height }
        return separatedPlayerRegions(TabletPlayerArrangement.HorizontalFold, upper, lower)
    }
    val vertical = playerVerticalFold(window, viewport, safe)
    if (vertical != null) {
        val left = safe.filter { it.right <= vertical.bounds.left }.maxBy { it.width * it.height }
        val right = safe.filter { it.left >= vertical.bounds.right }.maxBy { it.width * it.height }
        return separatedPlayerRegions(TabletPlayerArrangement.VerticalFold, left, right)
    }
    val region = safe.maxByOrNull { it.width * it.height } ?: return null
    val header = region.copy(bottom = min(region.bottom, region.top + 56f))
    val body = region.copy(top = header.bottom)
    if (usesPlayerColumns(body, contextPane)) {
        val middle = body.left + (body.width - 16f) / 2f
        return TabletPlayerRegions(
            TabletPlayerArrangement.Columns,
            header,
            null,
            // Both columns end the same distance above the window's bottom edge.
            body.copy(right = middle, bottom = body.bottom - SUPPORT_EDGE_DP),
            insetSupport(body.copy(left = middle + 16f)),
            artworkSize(body, 0.4f, maximum = if (body.width >= LARGE_PLAYER_WIDTH_DP) LARGE_ARTWORK_DP else ARTWORK_DP),
        )
    }
    val controlHeight = min(784f, min(body.height * 0.74f, (body.height - 150f).coerceAtLeast(0f)))
    val division = body.top + controlHeight
    return TabletPlayerRegions(
        TabletPlayerArrangement.Stacked,
        header,
        null,
        body.copy(bottom = division),
        body.copy(top = division),
        artworkSize(body, if (contextPane) 0.19f else 0.28f),
    )
}

/**
 * Side by side from 1000dp, and on landscape windows from 840dp (inner foldable screens held flat,
 * split-screen halves of big tablets); a player beside a page stays stacked.
 */
internal fun usesPlayerColumns(body: BoxloreLayoutBounds, contextPane: Boolean): Boolean =
    !contextPane && (body.width >= 1_000f || (body.width >= COLUMNS_MIN_WIDTH_DP && body.width > body.height))

/** The supporting panel keeps an even margin from the window's end and bottom edges. */
private fun insetSupport(support: BoxloreLayoutBounds): BoxloreLayoutBounds =
    support.copy(right = support.right - SUPPORT_EDGE_DP, bottom = support.bottom - SUPPORT_EDGE_DP)

/** Big screens beside the supporting panel: a larger title, and a cover up to [LARGE_ARTWORK_DP]. */
internal fun usesLargePlayerTitle(regions: TabletPlayerRegions): Boolean =
    regions.arrangement == TabletPlayerArrangement.Columns && regions.support.right - regions.controls.left >= LARGE_PLAYER_WIDTH_DP

private const val COLUMNS_MIN_WIDTH_DP = 840f
private const val LARGE_PLAYER_WIDTH_DP = 1_200f
private const val ARTWORK_DP = 360f
private const val LARGE_ARTWORK_DP = 420f
private const val SUPPORT_EDGE_DP = 16f

private fun playerHorizontalFold(window: BoxloreWindowLayout, viewport: BoxloreLayoutBounds, safe: List<BoxloreLayoutBounds>): BoxloreFold? =
    window.folds.firstOrNull {
        it.orientation == BoxloreFoldOrientation.Horizontal &&
            it.bounds.top > viewport.top &&
            it.bounds.bottom < viewport.bottom &&
            safe.any { region -> region.bottom <= it.bounds.top } &&
            safe.any { region -> region.top >= it.bounds.bottom }
    }

private fun playerVerticalFold(window: BoxloreWindowLayout, viewport: BoxloreLayoutBounds, safe: List<BoxloreLayoutBounds>): BoxloreFold? =
    window.folds.firstOrNull {
        it.orientation == BoxloreFoldOrientation.Vertical &&
            it.bounds.left > viewport.left &&
            it.bounds.right < viewport.right &&
            safe.any { region -> region.right <= it.bounds.left } &&
            safe.any { region -> region.left >= it.bounds.right }
    }

private fun separatedPlayerRegions(
    arrangement: TabletPlayerArrangement,
    artRegion: BoxloreLayoutBounds,
    controlRegion: BoxloreLayoutBounds,
): TabletPlayerRegions {
    val header = artRegion.copy(bottom = min(artRegion.bottom, artRegion.top + 56f))
    val art = artRegion.copy(top = header.bottom)
    val division = controlRegion.top + controlRegion.height * 0.64f
    val support = controlRegion.copy(top = division)
    return TabletPlayerRegions(
        arrangement,
        header,
        art,
        controlRegion.copy(bottom = division),
        if (arrangement == TabletPlayerArrangement.VerticalFold) insetSupport(support) else support,
        artworkSize(art, 0.8f),
        // Book posture: the panel sits under the measured controls on the right-hand screen.
        stack = controlRegion,
    )
}

/** What the player's hero shows; it decides how a height budget turns into a cover size. */
internal enum class TabletHeroKind { Audio, Video, Remote }

/** The cover size for [TabletHeroKind], or [compact] when an audio cover would be too small to read well. */
internal data class TabletArtworkPlan(val sizeDp: Float, val compact: Boolean = false)

/**
 * Turns the height left above the controls into a hero size. Audio covers are square; video is 16:9,
 * so its width may be 16/9 of the height left (within the column and [maximumDp]); the Cast panel has
 * a fixed height and keeps [maximumDp]. An audio cover under [COMPACT_COVER_BELOW_DP] switches to the
 * compact heading (a small cover beside the title) when [compactAvailable].
 */
internal fun planTabletArtwork(
    kind: TabletHeroKind,
    remainingHeightDp: Float,
    maximumDp: Float,
    columnWidthDp: Float,
    compactAvailable: Boolean,
): TabletArtworkPlan = when (kind) {
    TabletHeroKind.Remote -> TabletArtworkPlan(maximumDp)
    TabletHeroKind.Video -> TabletArtworkPlan(
        min(maximumDp, min(remainingHeightDp.coerceAtLeast(MIN_HERO_DP * 9f / 16f) * 16f / 9f, columnWidthDp)).coerceAtLeast(MIN_HERO_DP),
    )
    TabletHeroKind.Audio ->
        if (compactAvailable && remainingHeightDp < COMPACT_COVER_BELOW_DP) {
            TabletArtworkPlan(COMPACT_COVER_DP, compact = true)
        } else {
            TabletArtworkPlan(min(maximumDp, remainingHeightDp.coerceAtLeast(MIN_HERO_DP)))
        }
}

private const val MIN_HERO_DP = 96f
private const val COMPACT_COVER_BELOW_DP = 140f
private const val COMPACT_COVER_DP = 112f

private fun artworkSize(region: BoxloreLayoutBounds, heightShare: Float, maximum: Float = ARTWORK_DP): Float {
    val available = min(region.width, region.height).coerceAtLeast(0f)
    return min(maximum, min(region.width * 0.6f, region.height * heightShare))
        .coerceIn(min(96f, available), available)
}

internal fun intersectPlayerBounds(first: BoxloreLayoutBounds, second: BoxloreLayoutBounds): BoxloreLayoutBounds? {
    val left = max(first.left, second.left)
    val top = max(first.top, second.top)
    val right = min(first.right, second.right)
    val bottom = min(first.bottom, second.bottom)
    return if (right > left && bottom > top) BoxloreLayoutBounds(left, top, right, bottom) else null
}
