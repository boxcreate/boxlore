package cx.aswin.boxlore.core.ranking

import cx.aswin.boxlore.core.ranking.database.PreferenceFacetEntity

internal object ShowFacetMigrationLogic {
    fun merge(old: PreferenceFacetEntity, existingTarget: PreferenceFacetEntity?, newPodcastId: String,): PreferenceFacetEntity = old.copy(
        facetKey = newPodcastId,
        positiveEvidence = old.positiveEvidence + (existingTarget?.positiveEvidence ?: 0.0),
        negativeEvidence = old.negativeEvidence + (existingTarget?.negativeEvidence ?: 0.0),
        updatedAt = maxOf(old.updatedAt, existingTarget?.updatedAt ?: 0L),
    )
}
