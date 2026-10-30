package cx.aswin.boxlore.core.playback

internal class CastQueueSnapshotPolicy {
    private var candidateIds: List<String>? = null
    private var candidateFirstSeenAtMs: Long = 0L

    fun shouldPreserveLocalQueue(
        remoteIds: List<String>,
        localIds: List<String>,
        nowMs: Long,
    ): Boolean {
        val isStrictSubset =
            remoteIds.isNotEmpty() &&
                remoteIds.size < localIds.size &&
                remoteIds.all(localIds::contains)
        if (!isStrictSubset) {
            candidateIds = null
            return false
        }
        if (remoteIds != candidateIds) {
            candidateIds = remoteIds
            candidateFirstSeenAtMs = nowMs
            return true
        }
        return nowMs - candidateFirstSeenAtMs < STABILITY_WINDOW_MS
    }

    companion object {
        const val STABILITY_WINDOW_MS = 1_000L
    }
}
