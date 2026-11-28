package cx.aswin.boxlore.core.catalog.logic

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast

/**
 * Result of GET /search/semantic (podcast vectors + episode vectors, one embed). [needsCheck] when
 * the search was refused until this phone passes the quick check, with no last good answer: the
 * lists are empty for that reason, not because nothing matched.
 */
data class SemanticSearchGroupedResult(val podcasts: List<Podcast>, val episodes: List<Episode>, val needsCheck: Boolean = false,)
