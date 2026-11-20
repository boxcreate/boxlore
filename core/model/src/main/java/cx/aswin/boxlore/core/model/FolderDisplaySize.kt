package cx.aswin.boxlore.core.model

import androidx.annotation.StringRes
import kotlinx.serialization.Serializable

@Serializable
enum class FolderDisplaySize(
    val spanCols: Int,
    val spanRows: Int,
    val dimensionsLabel: String,
    /** English name, kept for callers that still need plain text; show [titleRes] to listeners. */
    val title: String,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
) {
    COMPACT(
        spanCols = 1,
        spanRows = 1,
        dimensionsLabel = "1×1",
        title = "Compact",
        titleRes = R.string.model_folder_size_compact_title,
        subtitleRes = R.string.model_folder_size_compact_subtitle,
    ),
    WIDE(
        spanCols = 2,
        spanRows = 1,
        dimensionsLabel = "2×1",
        title = "Wide",
        titleRes = R.string.model_folder_size_wide_title,
        subtitleRes = R.string.model_folder_size_wide_subtitle,
    ),
    FEATURED(
        spanCols = 2,
        spanRows = 2,
        dimensionsLabel = "2×2",
        title = "Featured",
        titleRes = R.string.model_folder_size_featured_title,
        subtitleRes = R.string.model_folder_size_featured_subtitle,
    ),
    LARGE(
        spanCols = 2,
        spanRows = 3,
        dimensionsLabel = "2×3",
        title = "Tall",
        titleRes = R.string.model_folder_size_tall_title,
        subtitleRes = R.string.model_folder_size_tall_subtitle,
    ),
    SHELF(
        spanCols = 3,
        spanRows = 1,
        dimensionsLabel = "3×1",
        title = "Shelf",
        titleRes = R.string.model_folder_size_shelf_title,
        subtitleRes = R.string.model_folder_size_shelf_subtitle,
    ),
    PANEL(
        spanCols = 3,
        spanRows = 2,
        dimensionsLabel = "3×2",
        title = "Panel",
        titleRes = R.string.model_folder_size_panel_title,
        subtitleRes = R.string.model_folder_size_panel_subtitle,
    ),
    SHOWCASE(
        spanCols = 3,
        spanRows = 3,
        dimensionsLabel = "3×3",
        title = "Showcase",
        titleRes = R.string.model_folder_size_showcase_title,
        subtitleRes = R.string.model_folder_size_showcase_subtitle,
    );

    val isPinnedToTop: Boolean
        get() = this != COMPACT

    /** Where this size sits in the grid, in English; show [placementLabelRes] to listeners. */
    val placementLabel: String
        get() = if (this == COMPACT) {
            "Can be placed anywhere in the grid"
        } else {
            "Pinned to the top of the page"
        }

    /** Where this size sits in the grid: anywhere for [COMPACT], else pinned to the top. */
    @get:StringRes
    val placementLabelRes: Int
        get() = if (this == COMPACT) R.string.model_folder_size_placement_anywhere else R.string.model_folder_size_placement_top
}
