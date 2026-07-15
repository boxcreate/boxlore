package cx.aswin.boxcast.core.data.ranking

import android.content.Context
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

data class RankingShadowSnapshot(
    val objective: RankingObjective,
    val candidateCount: Int,
    val topFiveOverlap: Int,
    val meanAbsoluteRankShift: Double,
    val recordedAt: Long,
)

class RankingRuntimeControls private constructor(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun isAdaptiveEnabled(objective: RankingObjective): Boolean {
        return preferences.getBoolean(ADAPTIVE_ENABLED, true) &&
            preferences.getBoolean(objectiveKey(objective), true)
    }

    fun isShadowDiagnosticsEnabled(): Boolean {
        return preferences.getBoolean(SHADOW_DIAGNOSTICS_ENABLED, true)
    }

    fun setAdaptiveEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(ADAPTIVE_ENABLED, enabled).apply()
    }

    fun setObjectiveEnabled(objective: RankingObjective, enabled: Boolean) {
        preferences.edit().putBoolean(objectiveKey(objective), enabled).apply()
    }

    fun setShadowDiagnosticsEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(SHADOW_DIAGNOSTICS_ENABLED, enabled).apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "adaptive_ranking_runtime"
        private const val ADAPTIVE_ENABLED = "adaptive_enabled"
        private const val SHADOW_DIAGNOSTICS_ENABLED = "shadow_diagnostics_enabled"

        @Volatile
        private var instance: RankingRuntimeControls? = null

        fun getInstance(context: Context): RankingRuntimeControls {
            return instance ?: synchronized(this) {
                instance ?: RankingRuntimeControls(context).also { instance = it }
            }
        }

        private fun objectiveKey(objective: RankingObjective): String {
            return "objective_${objective.name.lowercase()}_enabled"
        }
    }
}

object RankingShadowDiagnostics {
    private const val TOP_K = 5
    private val latest = ConcurrentHashMap<RankingObjective, RankingShadowSnapshot>()

    fun record(
        objective: RankingObjective,
        priorOrder: List<String>,
        adaptiveOrder: List<String>,
        now: Long = System.currentTimeMillis(),
    ) {
        if (priorOrder.isEmpty() || adaptiveOrder.isEmpty()) return
        val priorPositions = priorOrder.withIndex().associate { it.value to it.index }
        val comparable = adaptiveOrder.mapIndexedNotNull { index, id ->
            priorPositions[id]?.let { priorIndex -> abs(priorIndex - index).toDouble() }
        }
        val topFiveOverlap = priorOrder.take(TOP_K).toSet()
            .intersect(adaptiveOrder.take(TOP_K).toSet())
            .size
        latest[objective] = RankingShadowSnapshot(
            objective = objective,
            candidateCount = minOf(priorOrder.size, adaptiveOrder.size),
            topFiveOverlap = topFiveOverlap,
            meanAbsoluteRankShift = comparable.averageOrZero(),
            recordedAt = now,
        )
    }

    fun snapshots(): List<RankingShadowSnapshot> {
        return latest.values.sortedBy(RankingShadowSnapshot::objective)
    }

    fun clear() {
        latest.clear()
    }
}

private fun List<Double>.averageOrZero(): Double = if (isEmpty()) 0.0 else average()
