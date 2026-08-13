package cx.aswin.boxlore.feature.explore.logic

/**
 * Tappable prompt on By-concept idle / no-results.
 * [query] is the exact string sent to GET /search/semantic.
 */
internal data class ConceptSearchExample(
    val query: String,
    val label: String,
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
                label = "Why did the Roman empire fall?",
            ),
            ConceptSearchExample(
                query = "how do black holes actually work",
                label = "How do black holes actually work?",
            ),
            ConceptSearchExample(
                query = "why people believe conspiracy theories",
                label = "Why do people believe conspiracies?",
            ),
            ConceptSearchExample(
                query = "how money actually works",
                label = "How does money actually work?",
            ),
        )
}
