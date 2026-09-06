package cx.aswin.boxlore.core.playback.service.auto

import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.model.ListeningCompletionLogic

/**
 * Resumption scenarios supported by Android Auto and media button reconnects.
 */
enum class AutoResumptionCase {
    LivePlayer,
    ActiveMiniPlayer,
    InactiveMiniPlayerWithIncomplete,
    NoResumption,
}

data class AutoResumptionCandidate(
    val episodeId: String,
    val progressMs: Long = 0L,
    val durationMs: Long = 0L,
    val isCompleted: Boolean = false,
) {
    companion object {
        fun from(entity: ListeningHistoryEntity): AutoResumptionCandidate =
            AutoResumptionCandidate(
                episodeId = entity.episodeId,
                progressMs = entity.progressMs,
                durationMs = entity.durationMs,
                isCompleted = entity.isCompleted,
            )
    }
}

data class AutoResumptionDecision(
    val case: AutoResumptionCase,
    val targetEpisodeId: String?,
    val startPositionMs: Long,
    val shouldResume: Boolean,
)

data class AlignedQueue<T>(
    val items: List<T>,
    val startIndex: Int,
)

/**
 * Pure policy determining whether playback should resume on auto reconnect or media button event,
 * what episode to target, what start position to seek to, and how the playlist queue should be aligned.
 */
object AutoPlaybackResumptionPolicy {

    fun resolveCase(
        hasLivePlayerItems: Boolean,
        isPlayerDismissed: Boolean,
        candidate: AutoResumptionCandidate?,
    ): AutoResumptionCase {
        if (hasLivePlayerItems) {
            return AutoResumptionCase.LivePlayer
        }
        if (candidate == null || candidate.episodeId.isBlank()) {
            return AutoResumptionCase.NoResumption
        }
        val isCompleted = ListeningCompletionLogic.isCompleted(
            isCompleted = candidate.isCompleted,
            progressMs = candidate.progressMs,
            durationMs = candidate.durationMs,
        )
        return if (!isPlayerDismissed) {
            AutoResumptionCase.ActiveMiniPlayer
        } else if (!isCompleted) {
            AutoResumptionCase.InactiveMiniPlayerWithIncomplete
        } else {
            AutoResumptionCase.NoResumption
        }
    }

    fun resolveStartPositionMs(
        resumptionCase: AutoResumptionCase,
        candidate: AutoResumptionCandidate?,
        livePositionMs: Long = 0L,
    ): Long {
        return when (resumptionCase) {
            AutoResumptionCase.LivePlayer -> livePositionMs.coerceAtLeast(0L)
            AutoResumptionCase.ActiveMiniPlayer -> {
                if (candidate == null) return 0L
                val isCompleted = ListeningCompletionLogic.isCompleted(
                    isCompleted = candidate.isCompleted,
                    progressMs = candidate.progressMs,
                    durationMs = candidate.durationMs,
                )
                if (isCompleted) 0L else candidate.progressMs.coerceAtLeast(0L)
            }
            AutoResumptionCase.InactiveMiniPlayerWithIncomplete -> {
                candidate?.progressMs?.coerceAtLeast(0L) ?: 0L
            }
            AutoResumptionCase.NoResumption -> 0L
        }
    }

    fun evaluate(
        hasLivePlayerItems: Boolean,
        isPlayerDismissed: Boolean,
        candidate: AutoResumptionCandidate?,
        liveEpisodeId: String? = null,
        livePositionMs: Long = 0L,
    ): AutoResumptionDecision {
        val case = resolveCase(
            hasLivePlayerItems = hasLivePlayerItems,
            isPlayerDismissed = isPlayerDismissed,
            candidate = candidate,
        )
        val targetEpisodeId = when (case) {
            AutoResumptionCase.LivePlayer -> liveEpisodeId ?: candidate?.episodeId
            AutoResumptionCase.ActiveMiniPlayer,
            AutoResumptionCase.InactiveMiniPlayerWithIncomplete -> candidate?.episodeId
            AutoResumptionCase.NoResumption -> null
        }
        val startPositionMs = resolveStartPositionMs(
            resumptionCase = case,
            candidate = candidate,
            livePositionMs = livePositionMs,
        )
        val shouldResume = case != AutoResumptionCase.NoResumption && !targetEpisodeId.isNullOrBlank()
        return AutoResumptionDecision(
            case = case,
            targetEpisodeId = if (shouldResume) targetEpisodeId else null,
            startPositionMs = if (shouldResume) startPositionMs else 0L,
            shouldResume = shouldResume,
        )
    }

    fun <T> alignQueue(
        targetItem: T,
        queue: List<T>,
        idSelector: (T) -> String = { it.toString() },
    ): AlignedQueue<T> {
        val targetId = idSelector(targetItem).stripEpisodePrefix()
        if (targetId.isBlank()) {
            return AlignedQueue(items = queue, startIndex = 0)
        }
        val existingIndex = queue.indexOfFirst { idSelector(it).stripEpisodePrefix() == targetId }
        return if (existingIndex >= 0) {
            AlignedQueue(items = queue, startIndex = existingIndex)
        } else {
            AlignedQueue(items = listOf(targetItem) + queue, startIndex = 0)
        }
    }

    fun alignQueueIds(
        targetEpisodeId: String,
        queueEpisodeIds: List<String>,
    ): AlignedQueue<String> {
        if (targetEpisodeId.isBlank()) {
            return AlignedQueue(items = queueEpisodeIds, startIndex = 0)
        }
        return alignQueue(
            targetItem = targetEpisodeId,
            queue = queueEpisodeIds,
            idSelector = { it },
        )
    }
}
