package cx.aswin.boxlore.core.database

/**
 * Reads one listener state for an episode stored under several IDs (docs/EPISODE_IDS.md §5).
 *
 * Pure functions over an episode's history rows and its `episode_group_state` row:
 * - An episode with one stored ID reads its own row exactly as it is, so every show whose
 *   episodes each have one ID keeps today's state.
 * - Otherwise the owner's row is the base (the most recently played row when the owner has none).
 *   Progress, position, length and play time come from the most recently played row: the owner's,
 *   unless another ID was played later. Every write on this phone goes to the owner, so another ID
 *   is newer only after a cloud pull, a restore or older-build data; reading it keeps that later
 *   listening, and an ID that holds only a like never hides an ID with progress. Liked and
 *   completed are the latest decision among the group settings and the rows: a row's like decision
 *   is its `likedAt`, its completion decision is its `lastPlayedAt`. A group setting wins a tie
 *   with a row, so an older alias row can never bring back a like or completion that was cleared;
 *   rows that tie with each other count as set when any of them is.
 *
 * Added in P4 of the own-episode-IDs plan (plan section 8, projected listener state).
 * COMPAT(own-ids): one listener state per episode across its IDs. Permanent: never remove (MIGRATION.md §5).
 */
object EpisodeListenerProjection {
    /** One like or completion decision and when it was made (epoch ms; 0 when the time is unknown). */
    data class Decision(val at: Long, val value: Boolean)

    /**
     * The episode's projected history row, or null when none of its IDs has a row. With one stored
     * ID it is that ID's row, unchanged; [state] is then ignored.
     *
     * @param rows history rows of [group]'s members; rows of other IDs are ignored
     */
    fun project(group: EpisodeGroup, rows: Collection<ListeningHistoryEntity>, state: EpisodeGroupStateEntity?,): ListeningHistoryEntity? {
        val members = rows.filter { it.episodeId in group.memberIds }
        if (!group.isShared) return members.firstOrNull { it.episodeId == group.requestedId }
        val base = members.firstOrNull { it.episodeId == group.ownerId }
            ?: members.maxWithOrNull(compareBy<ListeningHistoryEntity>({ it.lastPlayedAt }, { it.episodeId }))
            ?: return null
        val played = latestPlayed(members, base)
        val liked = likeDecision(members, state)
        val completed = completionDecision(members, state)
        val completedBy = if (completed.value && !base.isCompleted) {
            // Ties on play time go to the higher ID, as for the base row, so any row order gives the same flags.
            members.filter { it.isCompleted }.maxWithOrNull(compareBy<ListeningHistoryEntity>({ it.lastPlayedAt }, { it.episodeId })) ?: base
        } else {
            base
        }
        return base.copy(
            progressMs = played.progressMs,
            durationMs = played.durationMs.takeIf { it > 0L } ?: base.durationMs,
            lastPlayedAt = played.lastPlayedAt,
            isLiked = liked.value,
            likedAt = liked.at,
            isCompleted = completed.value,
            isManualCompletion = completed.value && completedBy.isManualCompletion,
            isBulkCompletion = completed.value && completedBy.isBulkCompletion,
            // The episode was first played and first finished under whichever ID did it first.
            firstPlayedAt = members.map { it.firstPlayedAt }.reduce(ListeningDates::earliest),
            finishedAt = members.map { it.finishedAt }.reduce(ListeningDates::earliest),
        )
    }

    /** The row whose progress the episode reads: [base] (the owner's) unless another row was played strictly later. */
    private fun latestPlayed(members: List<ListeningHistoryEntity>, base: ListeningHistoryEntity,): ListeningHistoryEntity {
        val latest = members.maxWithOrNull(compareBy<ListeningHistoryEntity>({ it.lastPlayedAt }, { it.episodeId })) ?: return base
        return if (latest.lastPlayedAt > base.lastPlayedAt) latest else base
    }

    /** The episode's current like decision across [rows] and the group settings. */
    fun likeDecision(rows: Collection<ListeningHistoryEntity>, state: EpisodeGroupStateEntity?,): Decision = latest(
        rows = rows.map { Decision(it.likedAt, it.isLiked) },
        group = listOfNotNull(
            state?.likedAt?.takeIf { it > 0L }?.let { Decision(it, true) },
            state?.likeClearedAt?.takeIf { it > 0L }?.let { Decision(it, false) },
        ),
    )

    /** The episode's current completion decision across [rows] and the group settings. */
    fun completionDecision(rows: Collection<ListeningHistoryEntity>, state: EpisodeGroupStateEntity?,): Decision = latest(
        rows = rows.map { Decision(it.lastPlayedAt, it.isCompleted) },
        group = listOfNotNull(
            state?.completedAt?.takeIf { it > 0L }?.let { Decision(it, true) },
            state?.completeClearedAt?.takeIf { it > 0L }?.let { Decision(it, false) },
        ),
    )

    /**
     * The time to stamp a new like decision with: [nowMs], or just after the latest recorded like
     * decision when the clock reads earlier, so the new decision always wins the projection.
     */
    fun likeTime(nowMs: Long, rows: Collection<ListeningHistoryEntity>, state: EpisodeGroupStateEntity?,): Long =
        after(nowMs, listOfNotNull(state?.likedAt, state?.likeClearedAt, rows.maxOfOrNull { it.likedAt }))

    /** As [likeTime], for a completion decision. */
    fun completionTime(nowMs: Long, rows: Collection<ListeningHistoryEntity>, state: EpisodeGroupStateEntity?,): Long =
        after(nowMs, listOfNotNull(state?.completedAt, state?.completeClearedAt, rows.maxOfOrNull { it.lastPlayedAt }))

    private fun after(nowMs: Long, recorded: List<Long>): Long {
        val latest = recorded.maxOrNull() ?: 0L
        return if (nowMs > latest) nowMs else latest + 1L
    }

    /** [state] after a like decision ([liked] at [at]); a new row for [podcastId]/[catalogKey] when [state] is null. */
    fun withLike(state: EpisodeGroupStateEntity?, podcastId: String, catalogKey: String, liked: Boolean, at: Long,): EpisodeGroupStateEntity {
        val current = state ?: EpisodeGroupStateEntity(podcastId = podcastId, catalogKey = catalogKey)
        return if (liked) {
            current.copy(likedAt = at, version = current.version + 1L)
        } else {
            current.copy(likeClearedAt = at, version = current.version + 1L)
        }
    }

    /** [state] after a completion decision ([completed] at [at]); a new row when [state] is null. */
    fun withCompletion(state: EpisodeGroupStateEntity?, podcastId: String, catalogKey: String, completed: Boolean, at: Long,): EpisodeGroupStateEntity {
        val current = state ?: EpisodeGroupStateEntity(podcastId = podcastId, catalogKey = catalogKey)
        return if (completed) {
            current.copy(completedAt = at, version = current.version + 1L)
        } else {
            current.copy(completeClearedAt = at, version = current.version + 1L)
        }
    }

    /**
     * [ids] plus every other ID of their episodes among [shared] (trusted aliases that share their
     * episode with another trusted ID, `EpisodeIdentityDao.sharedTrustedAliasesFlow`).
     */
    fun withSharedAliases(ids: Set<String>, shared: List<EpisodeAliasEntity>,): Set<String> {
        if (shared.isEmpty() || ids.isEmpty()) return ids
        val tokenById = HashMap<String, String>()
        val membersByToken = HashMap<String, MutableList<String>>()
        for (alias in shared) {
            val token = EpisodeGroup.tokenOf(alias.podcastId, alias.catalogKey) ?: continue
            tokenById[alias.aliasId] = token
            membersByToken.getOrPut(token) { ArrayList() } += alias.aliasId
        }
        val tokens = ids.mapNotNullTo(HashSet()) { tokenById[it] }
        if (tokens.isEmpty()) return ids
        return ids + tokens.flatMap { membersByToken.getValue(it) }
    }

    private fun latest(rows: List<Decision>, group: List<Decision>,): Decision {
        val latestRowAt = rows.maxOfOrNull { it.at }
        val latestGroup = group.maxByOrNull { it.at }
        if (latestGroup != null && (latestRowAt == null || latestGroup.at >= latestRowAt)) return latestGroup
        if (latestRowAt == null) return Decision(0L, false)
        return Decision(latestRowAt, rows.any { it.at == latestRowAt && it.value })
    }
}
