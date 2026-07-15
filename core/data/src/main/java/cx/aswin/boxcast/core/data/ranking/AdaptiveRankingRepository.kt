package cx.aswin.boxcast.core.data.ranking

import android.content.Context
import cx.aswin.boxcast.core.data.ranking.database.AdaptiveModelEntity
import cx.aswin.boxcast.core.data.ranking.database.AdaptiveRankingDatabase
import cx.aswin.boxcast.core.data.ranking.database.PreferenceFacetEntity
import cx.aswin.boxcast.core.data.ranking.database.RankingExposureEntity
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
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

class AdaptiveRankingRepository private constructor(
    private val database: AdaptiveRankingDatabase,
    private val model: AdaptiveLinearModel = AdaptiveLinearModel(),
) {
    private val dao = database.adaptiveRankingDao()
    private val objectiveLocks = ConcurrentHashMap<RankingObjective, Mutex>()

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
        pruneExposures(exposure.shownAt)
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
        private const val MAX_EXPOSURES = 1_000
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
