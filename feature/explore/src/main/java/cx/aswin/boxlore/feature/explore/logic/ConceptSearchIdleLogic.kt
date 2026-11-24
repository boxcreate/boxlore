package cx.aswin.boxlore.feature.explore.logic

import androidx.annotation.StringRes
import cx.aswin.boxlore.feature.explore.R

/**
 * Tappable prompt on By-concept idle / no-results.
 * [query] is the exact string sent to GET /search/semantic (kept English: the examples were tuned on it);
 * [label] is the question shown.
 */
internal data class ConceptSearchExample(
    val query: String,
    @StringRes val label: Int,
)

/**
 * Example questions for concept search. Queries were checked live against
 * `/search/semantic?country=us` so the top hits match the intent (history,
 * space, conspiracies, money) instead of keyword-stuffed sleep/audiobook mix.
 */
internal object ConceptSearchIdleLogic {
    val examples: List<ConceptSearchExample> =
        listOf(
            ConceptSearchExample(
                query = "why did the roman empire fall",
                label = R.string.explore_concept_example_rome,
            ),
            ConceptSearchExample(
                query = "how do black holes actually work",
                label = R.string.explore_concept_example_black_holes,
            ),
            ConceptSearchExample(
                query = "why people believe conspiracy theories",
                label = R.string.explore_concept_example_conspiracies,
            ),
            ConceptSearchExample(
                query = "how money actually works",
                label = R.string.explore_concept_example_money,
            ),
            ConceptSearchExample(
                query = "why do we dream",
                label = R.string.explore_concept_example_dreams,
            ),
            ConceptSearchExample(
                query = "how were the egyptian pyramids built",
                label = R.string.explore_concept_example_pyramids,
            ),
        )

    /** Four examples on short phones and the Fold cover; all six on tall phones and tablets. */
    fun visibleExampleCount(contentHeightDp: Float, usesTabletLayout: Boolean): Int =
        if (usesTabletLayout || contentHeightDp >= TALL_HEIGHT_DP) examples.size else SHORT_COUNT

    private const val TALL_HEIGHT_DP = 800f
    private const val SHORT_COUNT = 4
}
