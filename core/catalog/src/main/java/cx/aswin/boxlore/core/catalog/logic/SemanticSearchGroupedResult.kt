package cx.aswin.boxlore.core.catalog.logic

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast

/** Result of GET /search/semantic (podcast vectors + episode vectors, one embed). */
data class SemanticSearchGroupedResult(val podcasts: List<Podcast>, val episodes: List<Episode>,)
