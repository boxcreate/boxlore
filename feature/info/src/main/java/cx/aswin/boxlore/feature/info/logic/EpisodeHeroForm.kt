package cx.aswin.boxlore.feature.info.logic

/** How the episode page lays out its cover, title and Play row for the space it has. */
internal enum class EpisodeHeroForm {
    /** The phone layout: the cover centred above the title, then the Play row. */
    Stacked,

    /** A short space (a sideways phone, a split-screen half, a tabletop half): a small cover beside the title. */
    Row,

    /** A page 600dp or wider: the cover at the start, the title and the Play row beside it. */
    TwoColumn,
}

private const val TWO_COLUMN_MIN_WIDTH_DP = 600f

/**
 * The hero form for a page [paneWidthDp] wide (the page's own width, not the window's, so a detail
 * pane beside a list keeps the phone layout). A short page always gets [EpisodeHeroForm.Row].
 */
internal fun episodeHeroForm(paneWidthDp: Float, isShortPane: Boolean): EpisodeHeroForm = when {
    isShortPane -> EpisodeHeroForm.Row
    paneWidthDp >= TWO_COLUMN_MIN_WIDTH_DP -> EpisodeHeroForm.TwoColumn
    else -> EpisodeHeroForm.Stacked
}

/** The cover's side for [form] on a page [paneWidthDp] wide, in dp. */
internal fun episodeHeroArtSizeDp(form: EpisodeHeroForm, paneWidthDp: Float): Float = when (form) {
    EpisodeHeroForm.Stacked -> 200f
    EpisodeHeroForm.Row -> 120f
    EpisodeHeroForm.TwoColumn -> (paneWidthDp * 0.36f).coerceIn(240f, 280f)
}
