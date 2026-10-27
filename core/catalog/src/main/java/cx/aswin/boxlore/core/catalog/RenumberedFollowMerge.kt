package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.model.EpisodeRecipeId
import cx.aswin.boxlore.core.model.Podcast

/**
 * The rows a follow of a show Podcast Index renumbered moves to: the old show's follow ends up under
 * the show it now answers as, and only there (docs/EPISODE_IDS.md §5, renumbered shows). Pure;
 * [RenumberedShowFollows] writes the result.
 *
 * - **The new show already followed:** its row is kept, with the two follows' settings merged:
 *   its own follow time, notifications and auto-download on when either was, and the new
 *   show's own genre, icon, sort and playback trims, else the old show's.
 * - **The new show not followed:** it is followed now with the old show's settings. Its
 *   details come from the new show's answer, else its stored row, else the old show's row. It never
 *   takes the old show's namespace or address bookkeeping: a stored row keeps its own
 *   ([cx.aswin.boxlore.core.database.PodcastDao.upsert]), a new one has none until its catalog is read.
 * - **The old show** is unfollowed as an unfollow does it: notifications, auto-download and its
 *   custom genre cleared, its unfollow time now.
 * Both rows are dirty, so the next sync push carries the follow and the unfollow; a pull of the old
 * show's older follow loses to the newer unfollow ([cx.aswin.boxlore.core.catalog.sync.SubscriptionSyncResolver]).
 *
 * A follow time is never moved earlier: sync compares a follow with an unfollow by their times
 * ([cx.aswin.boxlore.core.catalog.sync.SubscriptionSyncResolver]), so a follow written with the old
 * show's earlier time would lose to any unfollow of the new show made since, on any device. The
 * new show keeps its own follow time, or takes the time of the move, like any new follow.
 *
 * Added for renumbered shows.
 */
object RenumberedFollowMerge {
    /** The two rows to write and what moved. */
    data class Move(
        val kept: PodcastEntity,
        val retired: PodcastEntity,
        /** Notifications are on for [kept] and weren't on its stored row: they need registering. */
        val notificationsMoved: Boolean,
        /** Auto-download is on for [kept] and wasn't on its stored row. */
        val autoDownloadMoved: Boolean,
    )

    fun move(old: PodcastEntity, newId: String, current: PodcastEntity?, answer: Podcast?, nowMs: Long): Move {
        val kept = if (current?.isSubscribed == true) mergedInto(current, old) else followed(newId, old, current, answer, nowMs)
        val retired =
            old.copy(
                isSubscribed = false,
                subscribedAt = 0L,
                unsubscribedAt = maxOf(nowMs, old.subscribedAt + 1),
                isDirty = true,
                notificationsEnabled = false,
                autoDownloadEnabled = false,
                customGenre = null,
                customGenreIcon = null,
            )
        return Move(
            kept = kept,
            retired = retired,
            notificationsMoved = kept.notificationsEnabled && current?.let { it.isSubscribed && it.notificationsEnabled } != true,
            autoDownloadMoved = kept.autoDownloadEnabled && current?.let { it.isSubscribed && it.autoDownloadEnabled } != true,
        )
    }

    /** [current] (followed) with [old]'s follow merged in. */
    private fun mergedInto(current: PodcastEntity, old: PodcastEntity): PodcastEntity {
        val keepsOwnGenre = !current.customGenre.isNullOrBlank()
        val keepsOwnSort = current.preferredSort != null
        return current.copy(
            subscribedAt = current.subscribedAt,
            unsubscribedAt = 0L,
            isDirty = true,
            notificationsEnabled = current.notificationsEnabled || old.notificationsEnabled,
            autoDownloadEnabled = current.autoDownloadEnabled || old.autoDownloadEnabled,
            customGenre = if (keepsOwnGenre) current.customGenre else old.customGenre,
            customGenreIcon = if (keepsOwnGenre) current.customGenreIcon else old.customGenreIcon,
            preferredSort = if (keepsOwnSort) current.preferredSort else old.preferredSort,
            type = if (keepsOwnSort) current.type else old.type,
            skipBeginningOverrideMs = current.skipBeginningOverrideMs ?: old.skipBeginningOverrideMs,
            skipEndingOverrideMs = current.skipEndingOverrideMs ?: old.skipEndingOverrideMs,
            feedUrl = current.feedUrl ?: old.feedUrl,
        )
    }

    /** [newId] followed with [old]'s settings; details from [answer], else [stored], else [old]. */
    private fun followed(newId: String, old: PodcastEntity, stored: PodcastEntity?, answer: Podcast?, nowMs: Long): PodcastEntity {
        val shown = (stored ?: unstoredRowOf(newId, old, nowMs)).withAnswer(answer)
        return shown.copy(
            isSubscribed = true,
            subscribedAt = maxOf(nowMs, (stored?.unsubscribedAt ?: 0L) + 1L),
            unsubscribedAt = 0L,
            isDirty = true,
            type = if (old.preferredSort != null) old.type else shown.type,
            latestEpisode = shown.latestEpisode?.let { it.copy(podcastId = newId, podcastTitle = it.podcastTitle ?: shown.title) },
            preferredSort = old.preferredSort ?: shown.preferredSort,
            notificationsEnabled = old.notificationsEnabled,
            autoDownloadEnabled = old.autoDownloadEnabled,
            skipBeginningOverrideMs = old.skipBeginningOverrideMs ?: shown.skipBeginningOverrideMs,
            skipEndingOverrideMs = old.skipEndingOverrideMs ?: shown.skipEndingOverrideMs,
            sourceType = PodcastEntity.SOURCE_PODCAST_INDEX,
            customGenre = old.customGenre,
            customGenreIcon = old.customGenreIcon,
        )
    }

    /**
     * A new row for [newId] with [old]'s show details only: none of its namespace, address
     * bookkeeping, validators, link, latest episode or sync marks, which belong to [old]'s number.
     */
    private fun unstoredRowOf(newId: String, old: PodcastEntity, nowMs: Long): PodcastEntity = PodcastEntity(
        podcastId = newId,
        title = old.title,
        author = old.author,
        imageUrl = old.imageUrl,
        description = old.description,
        genre = old.genre,
        type = old.type,
        lastRefreshed = nowMs,
        podcastGuid = old.podcastGuid,
        medium = old.medium,
        feedUrl = old.feedUrl,
    )

    /** This row with [answer]'s show details where it has them; the stored address stays. */
    private fun PodcastEntity.withAnswer(answer: Podcast?): PodcastEntity {
        if (answer == null) return this
        return copy(
            title = answer.title.ifBlank { title },
            author = answer.artist.ifBlank { author },
            imageUrl = answer.imageUrl.ifBlank { imageUrl },
            description = answer.description ?: description,
            genre = answer.genre.takeIf(String::isNotBlank) ?: genre,
            podcastGuid = answer.podcastGuid ?: podcastGuid,
            fundingUrl = answer.fundingUrl ?: fundingUrl,
            fundingMessage = answer.fundingMessage ?: fundingMessage,
            medium = answer.medium ?: medium,
            updateFrequency = answer.updateFrequency ?: updateFrequency,
            location = answer.location ?: location,
            license = answer.license ?: license,
        ).withAnswerState(answer)
    }

    /** The answer's flags, address and latest episode where this row has none (part of [withAnswer]). */
    private fun PodcastEntity.withAnswerState(answer: Podcast): PodcastEntity = copy(
        hasValue = answer.hasValue || hasValue,
        isLocked = answer.isLocked || isLocked,
        feedUrl = feedUrl ?: answer.feedUrl?.takeIf(String::isNotBlank),
        latestEpisode = latestEpisode ?: answer.latestEpisode,
    )

    /** [ids] with [oldId] replaced by [newId] in its place, once (a pin list, a manual order). */
    fun replacedId(ids: List<String>, oldId: String, newId: String): List<String> {
        if (oldId !in ids) return ids
        return ids.map { if (it == oldId) newId else it }.distinct()
    }

    /**
     * The followed Podcast Index shows that may be one renumbered show followed twice: two or more
     * follows with the same feed address or podcast GUID. Only canonical numeric show IDs; a show
     * is never matched by its title. Asking each one's details settles which ([RenumberedShowFollows]).
     */
    fun sharedFeedCandidates(followed: List<PodcastEntity>): List<String> {
        val shows = followed.filter { it.isSubscribed && !it.isRss && EpisodeRecipeId.showIdOrNull(it.podcastId) != null }
        val byAddress = shows.mapNotNull { row -> normalizedAddress(row.feedUrl)?.let { it to row.podcastId } }
        val byGuid = shows.mapNotNull { row -> row.podcastGuid?.trim()?.lowercase()?.takeIf(String::isNotEmpty)?.let { "guid:$it" to row.podcastId } }
        return (byAddress + byGuid)
            .groupBy({ it.first }, { it.second })
            .values
            .filter { it.distinct().size > 1 }
            .flatten()
            .distinct()
    }

    /**
     * The [candidates] the foreground pass asks for, at most [maxLookups]: never one [answered]
     * already in this process, shows not asked yet before ones whose lookup [failed] (asked again,
     * so an offline first pass doesn't skip a show until the process ends), each group in
     * [candidates]' order.
     */
    fun lookupOrder(candidates: List<String>, answered: Set<String>, failed: Set<String>, maxLookups: Int): List<String> =
        candidates
            .filterNot { it in answered }
            .sortedBy { it in failed }
            .take(maxLookups)

    /** A feed address compared without its scheme, case, `www.` or trailing slash. */
    internal fun normalizedAddress(url: String?): String? {
        val trimmed = url?.trim()?.lowercase()?.takeIf(String::isNotEmpty) ?: return null
        return trimmed.substringAfter("://").removePrefix("www.").trimEnd('/').takeIf(String::isNotEmpty)
    }
}
