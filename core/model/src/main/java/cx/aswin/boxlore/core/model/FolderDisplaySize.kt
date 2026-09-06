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
        subtitle = "1 cell",
    ),
    WIDE(
        spanCols = 2,
        spanRows = 1,
        dimensionsLabel = "2×1",
        title = "Wide",
        subtitle = "2 cols",
    ),
    FEATURED(
        spanCols = 2,
        spanRows = 2,
        dimensionsLabel = "2×2",
        title = "Featured",
        subtitle = "2×2 square",
    ),
    LARGE(
        spanCols = 2,
        spanRows = 3,
        dimensionsLabel = "2×3",
        title = "Large",
        subtitle = "Max 2×3",
    ),
    SHELF(
        spanCols = 3,
        spanRows = 1,
        dimensionsLabel = "3×1",
        title = "Shelf",
        subtitle = "Full row",
    ),
}
