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
    if (body.width >= 1_000f && !contextPane) {
        val middle = body.left + (body.width - 16f) / 2f
        return TabletPlayerRegions(
            TabletPlayerArrangement.Columns,
            header,
            null,
            body.copy(right = middle),
            body.copy(left = middle + 16f),
            artworkSize(body, 0.4f),
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
    return TabletPlayerRegions(
        arrangement,
        header,
        art,
        controlRegion.copy(bottom = division),
        controlRegion.copy(top = division),
        artworkSize(art, 0.8f),
    )
}

private fun artworkSize(region: BoxloreLayoutBounds, heightShare: Float): Float {
    val available = min(region.width, region.height).coerceAtLeast(0f)
    return min(360f, min(region.width * 0.6f, region.height * heightShare))
        .coerceIn(min(96f, available), available)
}

internal fun intersectPlayerBounds(first: BoxloreLayoutBounds, second: BoxloreLayoutBounds): BoxloreLayoutBounds? {
    val left = max(first.left, second.left)
    val top = max(first.top, second.top)
    val right = min(first.right, second.right)
    val bottom = min(first.bottom, second.bottom)
    return if (right > left && bottom > top) BoxloreLayoutBounds(left, top, right, bottom) else null
}
