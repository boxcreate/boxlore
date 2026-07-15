package cx.aswin.boxcast.core.data.ranking

import android.content.Context
import cx.aswin.boxcast.core.data.ranking.database.AdaptiveModelEntity
import cx.aswin.boxcast.core.data.ranking.database.AdaptiveRankingDatabase
import cx.aswin.boxcast.core.data.ranking.database.PreferenceFacetEntity
import cx.aswin.boxcast.core.data.ranking.database.RankingExposureEntity
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class RankingExposure(
    val episodeId: String,
    val podcastId: String,
    val objective: RankingObjective,
    val surface: RankingSurface,
    val source: CandidateSource,
    val features: RankingFeatures,
    val entryPoint: String? = null,
    val online: Boolean,
    val shownAt: Long = System.currentTimeMillis(),
)

data class RankingDebugSnapshot(
    val objective: RankingObjective,
    val updateCount: Long,
    val learnedBlend: Double,
    val explorationEnabled: Boolean,
    val featureSchemaVersion: Int,
)

data class RankingAggregateTelemetry(
    val objective: String,
    val rankerVersion: Int,
    val learningStage: String,
    val outcomeCountBucket: String,
    val explorationEligible: Boolean,
)

data class RankingScoreInput(
    val features: RankingFeatures,
    val priorScore: Double,
)

data class PreferenceFacetKey(
    val type: PreferenceFacetType,
    val key: String,
)

data class AdaptiveRankingBackup(
    val version: Int = 1,
    val models: List<AdaptiveModelEntity>? = emptyList(),
    val facets: List<PreferenceFacetEntity>? = emptyList(),
    val exposures: List<RankingExposureEntity>? = emptyList(),
)

class AdaptiveRankingRepository private constructor(
    private val database: AdaptiveRankingDatabase,
    private val model: AdaptiveLinearModel = AdaptiveLinearModel(),
) {
    private val dao = database.adaptiveRankingDao()
    private val objectiveLocks = ConcurrentHashMap<RankingObjective, Mutex>()
    private val exposureInsertCount = AtomicLong()

    suspend fun score(
        objective: RankingObjective,
        features: RankingFeatures,
        priorScore: Double,
    ): RankingScore {
        return objectiveLock(objective).withLock {
            model.score(
                objective = objective,
                features = features,
                priorScore = priorScore,
                state = loadState(objective),
            )
        }
    }

    suspend fun scoreBatch(
        objective: RankingObjective,
        inputs: List<RankingScoreInput>,
    ): List<RankingScore> {
        if (inputs.isEmpty()) return emptyList()
        return objectiveLock(objective).withLock {
            val state = loadState(objective)
            inputs.map { input ->
                model.score(
                    objective = objective,
                    features = input.features,
                    priorScore = input.priorScore,
                    state = state,
                )
            }
        }
    }

    suspend fun recordExposure(exposure: RankingExposure): String {
        val exposureId = UUID.randomUUID().toString()
        dao.insertExposure(
            RankingExposureEntity(
                exposureId = exposureId,
                episodeId = exposure.episodeId,
                podcastId = exposure.podcastId,
                objective = exposure.objective.name,
                surface = exposure.surface.name,
                source = exposure.source.name,
                featureSchemaVersion = exposure.features.schemaVersion,
                featureVector = RankingSerialization.encode(exposure.features.values),
                shownAt = exposure.shownAt,
                resolvedAt = null,
                reward = null,
                listenSeconds = 0,
                entryPoint = exposure.entryPoint,
                online = exposure.online,
            ),
        )
        val insertCount = exposureInsertCount.incrementAndGet()
        if (insertCount == 1L || insertCount % EXPOSURE_PRUNE_INTERVAL == 0L) {
            pruneExposures(exposure.shownAt)
        }
        return exposureId
    }

    suspend fun resolveExposure(
        exposureId: String,
        reward: Double,
        listenSeconds: Long = 0,
        resolvedAt: Long = System.currentTimeMillis(),
    ): Boolean {
        val exposure = dao.getExposure(exposureId) ?: return false
        if (exposure.resolvedAt != null) return false
        val objective = runCatching { RankingObjective.valueOf(exposure.objective) }.getOrNull()
            ?: return false
        if (exposure.featureSchemaVersion != RankingFeatureSchema.VERSION) return false
        val features = runCatching {
            RankingFeatures(
                schemaVersion = exposure.featureSchemaVersion,
                values = RankingSerialization.decode(
                    exposure.featureVector,
                    RankingFeatureSchema.dimension,
                ),
            )
        }.getOrNull() ?: return false
        return objectiveLock(objective).withLock {
            val changed = dao.resolveExposure(
                exposureId = exposureId,
                resolvedAt = resolvedAt,
                reward = reward.coerceIn(-1.0, 1.0),
                listenSeconds = listenSeconds.coerceAtLeast(0),
            )
            if (changed == 0) return@withLock false
            val updated = model.update(features, reward, loadState(objective))
            dao.upsertModel(updated.toEntity(objective, resolvedAt))
            true
        }
    }

    suspend fun resolveLatestExposure(
        episodeId: String,
        reward: Double,
        listenSeconds: Long = 0,
        resolvedAt: Long = System.currentTimeMillis(),
    ): Boolean {
        val exposure = dao.getLatestUnresolvedExposure(episodeId) ?: return false
        return resolveExposure(exposure.exposureId, reward, listenSeconds, resolvedAt)
    }

    suspend fun facetAffinity(
        type: PreferenceFacetType,
        key: String,
        now: Long = System.currentTimeMillis(),
    ): Double {
        val normalizedKey = key.normalizedFacetKey()
        if (normalizedKey.isEmpty()) return 0.0
        return dao.getFacet(type.name, normalizedKey)
            ?.toFacet()
            ?.affinity(now)
            ?: 0.0
    }

    suspend fun facetAffinities(
        keys: Set<PreferenceFacetKey>,
        now: Long = System.currentTimeMillis(),
    ): Map<PreferenceFacetKey, Double> {
        if (keys.isEmpty()) return emptyMap()
        val stored = dao.getAllFacets().associateBy { entity ->
            entity.facetType to entity.facetKey
        }
        return keys.associateWith { request ->
            stored[request.type.name to request.key.normalizedFacetKey()]
                ?.toFacet()
                ?.affinity(now)
                ?: 0.0
        }
    }

    suspend fun updateFacet(
        type: PreferenceFacetType,
        key: String,
        reward: Double,
        now: Long = System.currentTimeMillis(),
    ) {
        val normalizedKey = key.normalizedFacetKey()
        if (normalizedKey.isEmpty()) return
        val existing = dao.getFacet(type.name, normalizedKey)?.toFacet()
            ?: BayesianPreferenceFacet(updatedAt = now)
        val updated = existing.update(reward, now)
        dao.upsertFacet(
            PreferenceFacetEntity(
                facetType = type.name,
                facetKey = normalizedKey,
                positiveEvidence = updated.positiveEvidence,
                negativeEvidence = updated.negativeEvidence,
                updatedAt = updated.updatedAt,
            ),
        )
    }

    suspend fun debugSnapshot(objective: RankingObjective): RankingDebugSnapshot {
        val state = loadState(objective)
        val score = model.score(
            objective = objective,
            features = CandidateFeatureBuilder.build(CandidateSignals()),
            priorScore = 0.0,
            state = state,
        )
        return RankingDebugSnapshot(
            objective = objective,
            updateCount = state.updateCount,
            learnedBlend = score.learnedBlend,
            explorationEnabled = score.explorationBonus > 0.0 ||
                (objective.allowsExploration && state.updateCount >= 50),
            featureSchemaVersion = state.featureSchemaVersion,
        )
    }

    suspend fun aggregateTelemetry(): List<RankingAggregateTelemetry> {
        return RankingObjective.entries.map { objective ->
            val state = loadState(objective)
            RankingAggregateTelemetry(
                objective = objective.name,
                rankerVersion = RankingFeatureSchema.VERSION,
                learningStage = when {
                    state.updateCount == 0L -> "cold_start"
                    state.updateCount < 50L -> "learning"
                    else -> "adaptive"
                },
                outcomeCountBucket = state.updateCount.toOutcomeCountBucket(),
                explorationEligible = objective.allowsExploration && state.updateCount >= 50L,
            )
        }
    }

    suspend fun exportBackup(): AdaptiveRankingBackup {
        return AdaptiveRankingBackup(
            models = dao.getAllModels(),
            facets = dao.getAllFacets(),
            exposures = dao.getAllExposures(),
        )
    }

    suspend fun restoreBackup(backup: AdaptiveRankingBackup) {
        require(backup.version == ADAPTIVE_BACKUP_VERSION) {
            "Unsupported adaptive ranking backup version ${backup.version}"
        }
        val models = requireNotNull(backup.models) { "Adaptive model backup section is missing" }
        val facets = requireNotNull(backup.facets) { "Preference facet backup section is missing" }
        val exposures = requireNotNull(backup.exposures) { "Ranking exposure backup section is missing" }
        require(models.all(::isValidBackupModel)) { "Invalid adaptive model backup" }
        require(facets.all(::isValidBackupFacet)) { "Invalid preference facet backup" }
        require(exposures.size <= MAX_EXPOSURES && exposures.all(::isValidBackupExposure)) {
            "Invalid ranking exposure backup"
        }
        dao.replaceAll(models, facets, exposures)
        RankingShadowDiagnostics.clear()
    }

    suspend fun reset() {
        dao.clearAll()
    }

    private suspend fun loadState(objective: RankingObjective): AdaptiveModelState {
        val entity = dao.getModel(objective.name) ?: return AdaptiveModelState()
        if (entity.featureSchemaVersion != RankingFeatureSchema.VERSION ||
            entity.dimension != RankingFeatureSchema.dimension
        ) {
            return AdaptiveModelState()
        }
        return runCatching {
            AdaptiveModelState(
                featureSchemaVersion = entity.featureSchemaVersion,
                dimension = entity.dimension,
                covariance = RankingSerialization.decode(
                    entity.covariance,
                    entity.dimension * entity.dimension,
                ),
                inverseCovariance = RankingSerialization.decode(
                    entity.inverseCovariance,
                    entity.dimension * entity.dimension,
                ),
                rewardVector = RankingSerialization.decode(
                    entity.rewardVector,
                    entity.dimension,
                ),
                updateCount = entity.updateCount,
            )
        }.getOrElse { AdaptiveModelState() }
    }

    private suspend fun pruneExposures(now: Long) {
        dao.pruneExposuresBefore(now - EXPOSURE_RETENTION_MILLIS)
        dao.pruneExposuresToCount(MAX_EXPOSURES)
    }

    private fun objectiveLock(objective: RankingObjective): Mutex {
        return objectiveLocks.getOrPut(objective) { Mutex() }
    }

    companion object {
        private const val ADAPTIVE_BACKUP_VERSION = 1
        private const val MAX_EXPOSURES = 1_000
        private const val EXPOSURE_PRUNE_INTERVAL = 25L
        private const val EXPOSURE_RETENTION_MILLIS = 30L * 24L * 60L * 60L * 1_000L

        @Volatile
        private var instance: AdaptiveRankingRepository? = null

        fun getInstance(context: Context): AdaptiveRankingRepository {
            return instance ?: synchronized(this) {
                instance ?: AdaptiveRankingRepository(
                    AdaptiveRankingDatabase.getDatabase(context.applicationContext),
                ).also { instance = it }
            }
        }
    }
}

private fun isValidBackupModel(model: AdaptiveModelEntity): Boolean {
    return runCatching {
        RankingObjective.valueOf(model.objective)
        model.featureSchemaVersion == RankingFeatureSchema.VERSION &&
            model.dimension == RankingFeatureSchema.dimension &&
            model.updateCount >= 0L &&
            RankingSerialization.decode(
                model.covariance,
                model.dimension * model.dimension,
            ).all(Double::isFinite) &&
            RankingSerialization.decode(
                model.inverseCovariance,
                model.dimension * model.dimension,
            ).all(Double::isFinite) &&
            RankingSerialization.decode(model.rewardVector, model.dimension).all(Double::isFinite)
    }.getOrDefault(false)
}

private fun isValidBackupFacet(facet: PreferenceFacetEntity): Boolean {
    return runCatching {
        PreferenceFacetType.valueOf(facet.facetType)
        facet.facetKey.isNotBlank() &&
            facet.facetKey.length <= 200 &&
            facet.positiveEvidence.isFinite() &&
            facet.positiveEvidence >= 0.0 &&
            facet.negativeEvidence.isFinite() &&
            facet.negativeEvidence >= 0.0
    }.getOrDefault(false)
}

private fun isValidBackupExposure(exposure: RankingExposureEntity): Boolean {
    return runCatching {
        RankingObjective.valueOf(exposure.objective)
        RankingSurface.valueOf(exposure.surface)
        CandidateSource.valueOf(exposure.source)
        exposure.exposureId.isNotBlank() &&
            exposure.episodeId.isNotBlank() &&
            exposure.featureSchemaVersion == RankingFeatureSchema.VERSION &&
            exposure.listenSeconds >= 0L &&
            (exposure.reward == null || exposure.reward.isFinite()) &&
            RankingSerialization.decode(
                exposure.featureVector,
                RankingFeatureSchema.dimension,
            ).all(Double::isFinite)
    }.getOrDefault(false)
}

private fun AdaptiveModelState.toEntity(
    objective: RankingObjective,
    now: Long,
): AdaptiveModelEntity {
    return AdaptiveModelEntity(
        objective = objective.name,
        featureSchemaVersion = featureSchemaVersion,
        dimension = dimension,
        covariance = RankingSerialization.encode(covariance),
        inverseCovariance = RankingSerialization.encode(inverseCovariance),
        rewardVector = RankingSerialization.encode(rewardVector),
        updateCount = updateCount,
        updatedAt = now,
    )
}

private fun PreferenceFacetEntity.toFacet(): BayesianPreferenceFacet {
    return BayesianPreferenceFacet(
        positiveEvidence = positiveEvidence,
        negativeEvidence = negativeEvidence,
        updatedAt = updatedAt,
    )
}

private fun String.normalizedFacetKey(): String {
    return trim().lowercase().replace(Regex("\\s+"), " ").take(200)
}

private fun Long.toOutcomeCountBucket(): String = when {
    this == 0L -> "0"
    this < 10L -> "1_9"
    this < 50L -> "10_49"
    this < 200L -> "50_199"
    else -> "200_plus"
}
