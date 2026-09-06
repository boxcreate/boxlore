package cx.aswin.boxlore.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class FolderDisplaySize(
    val spanCols: Int,
    val spanRows: Int,
    val dimensionsLabel: String,
    val title: String,
    val subtitle: String,
) {
    COMPACT(
        spanCols = 1,
        spanRows = 1,
        dimensionsLabel = "1×1",
        title = "Compact",
        subtitle = "1 cell • Tap or pods",
    ),
    WIDE(
        spanCols = 2,
        spanRows = 1,
        dimensionsLabel = "2×1",
        title = "Wide",
        subtitle = "2 cols • Clickable",
    ),
    FEATURED(
        spanCols = 2,
        spanRows = 2,
        dimensionsLabel = "2×2",
        title = "Featured",
        subtitle = "2×2 • Clickable + more",
    ),
    LARGE(
        spanCols = 2,
        spanRows = 3,
        dimensionsLabel = "2×3",
        title = "Tall",
        subtitle = "2×3 tall • Clickable",
    ),
    SHELF(
        spanCols = 3,
        spanRows = 1,
        dimensionsLabel = "3×1",
        title = "Shelf",
        subtitle = "Full row • Clickable",
    ),
    PANEL(
        spanCols = 3,
        spanRows = 2,
        dimensionsLabel = "3×2",
        title = "Panel",
        subtitle = "3×2 panel • Clickable",
    ),
    SHOWCASE(
        spanCols = 3,
        spanRows = 3,
        dimensionsLabel = "3×3",
        title = "Showcase",
        subtitle = "Max 3×3 • Full grid",
    ),
}
