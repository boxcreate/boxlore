package cx.aswin.boxlore.core.catalog

import android.util.Log
import cx.aswin.boxlore.core.database.AutoDownloadDao
import cx.aswin.boxlore.core.database.PodcastDao
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import cx.aswin.boxlore.core.rss.LocalEpisodeCatalogRepository
import cx.aswin.boxlore.core.rss.RenumberedShowIds
import cx.aswin.boxlore.core.rss.RenumberedShows
import java.util.Collections
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * What [RenumberedShowFollows] needs besides the show rows: the app's other per-follow state.
 * [SubscriptionRepository] provides it.
 */
internal interface RenumberedFollowEffects {
    /** Saves a pending notification registration for [podcastId] before its row turns them on; false when it couldn't be saved. */
    fun markRegistrationPending(podcastId: String): Boolean

    /** Asks for [podcastId]'s pending registration once its row is saved. */
    fun requestRegistration(podcastId: String)

    /** Turns the old show's notifications off with Firebase (its node's address and its topic), as an unfollow does. */
    suspend fun unregisterNotifications(row: PodcastEntity)

    /** Records an auto-download setting change through the auto-download records. */
    suspend fun autoDownloadChanged(podcastId: String, enabled: Boolean)

    /** Moves folder memberships, Home pins and the manual order from [oldId] to [newId]. */
    suspend fun moveLibraryPlacement(oldId: String, newId: String)

    /** Starts the old show's unfollowed catalog countdown and, when [newId] is given, clears the new show's. */
    suspend fun catalogFollowChanged(oldId: String, newId: String?)

    /** Saves a missing feed address of the followed [podcastId] through the one address writer. */
    suspend fun fillMissingAddress(podcastId: String, feedUrl: String?)

    /** Links [oldId]'s listener rows to [newId]'s stored episodes now (each catalog read links again). */
    suspend fun linkEpisodes(oldId: String, newId: String)
}

/**
 * [RenumberedFollowEffects] on the app's stores: the address writer's pending registrations,
 * Firebase through [unregister], auto-download records, folders, Home pins and the manual order,
 * the catalog's unfollowed countdown and the episode links ([link]).
 */
internal class RenumberedFollowWrites(
    private val podcastDao: PodcastDao,
    private val feedAddresses: FeedAddressUpdater,
    private val unregister: suspend (PodcastEntity) -> Unit,
    private val library: Library,
    private val link: suspend (oldId: String, newId: String) -> Unit,
) : RenumberedFollowEffects {
    /** The optional stores a follow's placement and settings live in; null where a caller doesn't wire them. */
    data class Library(
        val folders: FolderRepository?,
        val prefs: UserPreferencesRepository?,
        val autoDownload: AutoDownloadDao?,
        val catalog: LocalEpisodeCatalogPort?,
    )

    override fun markRegistrationPending(podcastId: String): Boolean = feedAddresses.markRegistrationPending(podcastId)

    override fun requestRegistration(podcastId: String) = feedAddresses.requestPendingRegistration(podcastId)

    override suspend fun unregisterNotifications(row: PodcastEntity) = unregister(row)

    override suspend fun autoDownloadChanged(podcastId: String, enabled: Boolean) {
        library.autoDownload?.changeSetting(podcastId, enabled, System.currentTimeMillis() / MILLIS_PER_SECOND)
    }

    override suspend fun moveLibraryPlacement(oldId: String, newId: String) {
        library.folders?.let { folders ->
            folders.getFolders().filter { oldId in it.podcastIds && newId !in it.podcastIds }.forEach { folders.addPodcastToFolder(newId, it.id) }
            folders.removePodcastFromAllFolders(oldId)
            folders.syncLinkedGenres()
        }
        library.prefs?.let { prefs ->
            val pins = prefs.homePinnedPodcastIdsStream.first()
            RenumberedFollowMerge.replacedId(pins, oldId, newId).takeIf { it != pins }?.let { prefs.setHomePinnedPodcastIds(it) }
            val order = prefs.subscriptionManualOrderStream.first()
            RenumberedFollowMerge.replacedId(order, oldId, newId).takeIf { it != order }?.let { prefs.setSubscriptionManualOrder(it) }
        }
    }

    override suspend fun catalogFollowChanged(oldId: String, newId: String?) {
        val catalog = library.catalog ?: return
        catalog.setUnsubscribedTtl(oldId, System.currentTimeMillis() + LocalEpisodeCatalogRepository.UNSUBSCRIBE_TTL_MS)
        if (newId != null) catalog.setUnsubscribedTtl(newId, null)
    }

    override suspend fun fillMissingAddress(podcastId: String, feedUrl: String?) {
        if (TrackedPodcastRtdbLogic.httpsFeedUrl(podcastDao.getPodcast(podcastId)?.feedUrl) != null) return
        val https = TrackedPodcastRtdbLogic.httpsFeedUrl(feedUrl) ?: return
        feedAddresses.update(FeedAddressChange(podcastId = podcastId, feedUrl = https, source = FeedUrlSources.PODCAST_LOOKUP))
    }

    override suspend fun linkEpisodes(oldId: String, newId: String) = link(oldId, newId)

    private companion object {
        const val MILLIS_PER_SECOND = 1000L
    }
}

/**
 * Keeps one follow for a show Podcast Index renumbered (docs/EPISODE_IDS.md §5, renumbered shows).
 * When show details asked for an old show ID answer with another one ([adoptAnswer]), the renumbering
 * is recorded ([RenumberedShows]) and a follow of the old show moves to the show it now answers as
 * ([RenumberedFollowMerge]): both rows read, merged and written in one transaction and marked for
 * the next sync push, notifications registered for the new show through the pending-registration
 * journal before the row turns them on (never dropped), the old show's notifications turned off,
 * folder memberships, Home pins and the manual order moved, and the old show's listener rows
 * linked to the new show's episodes. `rss:` shows are never touched. A new show followed through
 * its RSS feed keeps that follow and only the old follow ends, its library places moving to the
 * feed's show; when the old follow has notifications and the feed's doesn't (which needs the
 * listener's consent), both follows stay, so notifications are never dropped.
 *
 * Every step is idempotent, so the same answer arriving from several callers (the show page, a
 * cloud pull, the foreground pass) moves the follow once. Moves are serialized.
 *
 * Added for renumbered shows.
 */
class RenumberedShowFollows internal constructor(
    private val podcastDao: PodcastDao,
    val shows: RenumberedShows,
    private val effects: RenumberedFollowEffects,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val moves = Mutex()

    /** Shows whose details the foreground pass got an answer for in this process, so each is asked once. */
    private val checkedThisProcess: MutableSet<String> = Collections.synchronizedSet(HashSet())

    /** Shows whose details lookup failed in this process: asked again, after shows not asked yet. */
    private val failedThisProcess: MutableSet<String> = Collections.synchronizedSet(HashSet())

    /** The show [podcastId] now answers as, when it was renumbered; null otherwise. */
    fun currentId(podcastId: String): String? = shows.currentId(podcastId)

    /**
     * Records an answer for [oldId] that named another show ([answer]) and moves a follow of [oldId]
     * to it. Returns true when a follow moved; false when [answer] isn't a renumbering of [oldId],
     * nothing was followed, or the renumbering couldn't be saved (the follow then stays, so it is
     * never under a number the phone no longer links to the old one).
     */
    suspend fun adoptAnswer(oldId: String, answer: Podcast): Boolean {
        if (!RenumberedShowIds.isRenumbering(oldId, answer.id)) return false
        if (!shows.recordOrWarn(oldId, answer.id)) return false
        return moveFollow(oldId, answer.id, answer)
    }

    /**
     * Moves a follow of [oldId] to the show it was recorded as renumbered to, if any. With
     * [confirmedNewId] (a merge the server confirmed with Podcast Index, from the daily address
     * check) that renumbering is recorded first, and nothing moves unless it was saved. Returns true
     * when a follow moved.
     */
    suspend fun adoptKnown(oldId: String, confirmedNewId: String? = null): Boolean {
        if (confirmedNewId != null) {
            if (!RenumberedShowIds.isRenumbering(oldId, confirmedNewId)) return false
            if (!shows.recordOrWarn(oldId, confirmedNewId)) return false
        }
        val newId = shows.currentId(oldId) ?: return false
        return moveFollow(oldId, newId, answer = null)
    }

    /** Moves every follow of a show renumbered into [podcastId] onto it (a show page about to toggle its follow). */
    suspend fun adoptFormerFollows(podcastId: String): Boolean {
        var moved = false
        for (oldId in shows.formerIds(podcastId)) moved = moveFollow(oldId, podcastId, answer = null) || moved
        return moved
    }

    /**
     * Before a toggle of [podcast]'s follow: settles follows of shows renumbered into it, by what the
     * caller showed ([shownFollowed]; null means [podcast]'s own follow or a followed `rss:` feed linked
     * to it, as follow buttons other than the show page show it). The direction is decided before
     * anything moves:
     * - **Shown as not followed** (the listener asked to follow): such follows move to [podcast]
     *   with their settings ([adoptFormerFollows]). True when [podcast] is now followed: the toggle
     *   is done. Otherwise false, and the toggle follows [podcast] as usual.
     * - **Shown as followed** (the listener asked to unfollow): the follows move first, then any
     *   that couldn't move end with [unfollow]. False while [podcast] (or its linked feed) is
     *   followed, so the toggle ends that follow too; true when only former follows were ended.
     * No former follows, or an `rss:` show: false, and the toggle goes ahead as usual.
     */
    suspend fun endedFormerFollows(podcast: Podcast, shownFollowed: Boolean? = null, unfollow: suspend (PodcastEntity) -> Unit): Boolean {
        if (podcast.isRss || shows.formerIds(podcast.id).isEmpty()) return false
        val askedToUnfollow = shownFollowed ?: podcastDao.isFollowedAsShown(podcast.id)
        adoptFormerFollows(podcast.id)
        val followed = podcastDao.isFollowedAsShown(podcast.id)
        if (!askedToUnfollow) return followed
        val ended = endFormerFollowsOf(podcast.id, unfollow)
        return !followed && ended
    }

    /**
     * Ends follows still under a show renumbered into [podcastId] (one kept beside a follow of
     * [podcastId]'s `rss:` feed for its notifications), when the listener unfollowed that show or
     * its feed. True when one ended.
     */
    suspend fun endFormerFollowsOf(podcastId: String, unfollow: suspend (PodcastEntity) -> Unit): Boolean {
        val left = shows.formerIds(podcastId).mapNotNull { id -> podcastDao.getPodcast(id)?.takeIf { it.isSubscribed && !it.isRss } }
        left.forEach { unfollow(it) }
        return left.isNotEmpty()
    }

    /** True when a show recorded as renumbered into [podcastId] is still followed under its old number. */
    suspend fun isFollowedUnderFormerId(podcastId: String): Boolean =
        shows.formerIds(podcastId).any { id -> podcastDao.getPodcast(id)?.let { it.isSubscribed && !it.isRss } == true }

    /**
     * The foreground pass's step: follows of shows recorded as renumbered move, then followed shows
     * that share a feed address or podcast GUID with another follow are asked for their details
     * once per process ([lookupDetails], at most [maxLookups]), which settles any renumbering among them.
     */
    suspend fun collapseFollows(lookupDetails: suspend (String) -> Podcast?, maxLookups: Int = MAX_LOOKUPS_PER_PASS) {
        val followed = podcastDao.getSubscribedPodcastIndexPodcasts()
        for (row in followed) {
            if (shows.currentId(row.podcastId) != null) adoptKnown(row.podcastId)
        }
        val candidates =
            RenumberedFollowMerge.lookupOrder(
                candidates = RenumberedFollowMerge.sharedFeedCandidates(podcastDao.getSubscribedPodcastIndexPodcasts()),
                answered = checkedThisProcess.toSet(),
                failed = failedThisProcess.toSet(),
                maxLookups = maxLookups,
            )
        for (id in candidates) {
            val answer =
                try {
                    lookupDetails(id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Details lookup for $id failed", e)
                    null
                }
            if (answer == null) {
                failedThisProcess += id
                continue
            }
            checkedThisProcess += id
            failedThisProcess -= id
            if (podcastDao.getPodcast(id)?.isSubscribed == true) adoptAnswer(id, answer)
        }
    }

    /** [collapseFollows] for the foreground pass, run before it reads feeds: a failure is logged and the pass goes on. */
    suspend fun collapseFollowsQuietly(lookupDetails: suspend (String) -> Podcast?) = runStep("collapse renumbered follows") { collapseFollows(lookupDetails) }

    private suspend fun moveFollow(oldId: String, newId: String, answer: Podcast?): Boolean {
        if (!RenumberedShowIds.isRenumbering(oldId, newId)) return false
        val moved = moves.withLock { moveFollowLocked(oldId, newId, answer) }
        // Linking is local and idempotent: run it for every answer, so rows that arrived since are linked.
        runStep("link episodes of $oldId to $newId") { effects.linkEpisodes(oldId, newId) }
        return moved
    }

    /** What one move wrote, read and decided in its transaction. */
    private sealed interface MoveOutcome {
        /** Nothing written. */
        data object None : MoveOutcome

        /** The follow moved to the new show. */
        data class Moved(val old: PodcastEntity, val move: RenumberedFollowMerge.Move) : MoveOutcome

        /** The new show is followed through its own feed ([feedShow]): only the old follow ended. */
        data class EndedForFeed(val old: PodcastEntity, val feedShow: PodcastEntity) : MoveOutcome
    }

    private suspend fun moveFollowLocked(oldId: String, newId: String, answer: Podcast?): Boolean {
        var outcome: MoveOutcome = MoveOutcome.None
        // Read, merge and write in one transaction, so no other writer's change to either row is overwritten.
        podcastDao.moveRenumberedFollow { outcome = movedRows(oldId, newId, answer) }
        when (val done = outcome) {
            MoveOutcome.None -> return false
            is MoveOutcome.EndedForFeed -> {
                Log.i(TAG, "Follow of renumbered show $oldId ended: $newId is followed through ${done.feedShow.podcastId}")
                finishOldShow(done.old, placementId = done.feedShow.podcastId, followedCatalogId = null)
            }
            is MoveOutcome.Moved -> {
                Log.i(TAG, "Follow of renumbered show $oldId moved to $newId")
                if (done.move.notificationsMoved) effects.requestRegistration(newId)
                if (done.move.autoDownloadMoved) runStep("auto-download of $newId") { effects.autoDownloadChanged(newId, true) }
                runStep("address of $newId") { effects.fillMissingAddress(newId, answer?.feedUrl ?: done.old.feedUrl) }
                finishOldShow(done.old, placementId = newId, followedCatalogId = newId)
            }
        }
        return true
    }

    /** The rows of one move, read and written inside [PodcastDao.moveRenumberedFollow]'s transaction. */
    private suspend fun movedRows(oldId: String, newId: String, answer: Podcast?): MoveOutcome {
        val old = podcastDao.getPodcast(oldId)?.takeIf { it.isSubscribed && !it.isRss } ?: return MoveOutcome.None
        val current = podcastDao.getPodcast(newId)
        if (current?.isRss == true) return MoveOutcome.None
        val move = RenumberedFollowMerge.move(old, newId, current, answer, nowMillis())
        val feedShow = podcastDao.getSubscribedRssPodcastLinkedTo(newId)
        if (feedShow != null) {
            // The new show is followed through its own feed: that follow stays as it is and the old one
            // ends, unless that would drop notifications the feed's follow can't take without the
            // listener's consent: then both stay until the listener turns one of them off or on.
            if (old.notificationsEnabled && !feedShow.notificationsEnabled) {
                Log.w(TAG, "Follow of $oldId kept: $newId is followed through ${feedShow.podcastId}, whose notifications are off")
                return MoveOutcome.None
            }
            podcastDao.upsert(move.retired)
            return MoveOutcome.EndedForFeed(old, feedShow)
        }
        // The registration is saved before the row turns notifications on, so it is never dropped.
        if (move.notificationsMoved && !effects.markRegistrationPending(newId)) {
            Log.w(TAG, "Follow of $oldId not moved to $newId: its notification registration couldn't be saved")
            return MoveOutcome.None
        }
        podcastDao.upsert(move.kept)
        podcastDao.upsert(move.retired)
        return MoveOutcome.Moved(old, move)
    }

    /**
     * The old show's leftovers once its follow ended: notifications, auto-download, its folder,
     * Home pin and manual-order places (moved to [placementId], the follow that remains), and its
     * catalog countdown (and [followedCatalogId]'s cleared, when that follow is a Podcast Index show).
     */
    private suspend fun finishOldShow(old: PodcastEntity, placementId: String, followedCatalogId: String?) {
        if (old.notificationsEnabled) runStep("notifications of ${old.podcastId}") { effects.unregisterNotifications(old) }
        if (old.autoDownloadEnabled) runStep("auto-download of ${old.podcastId}") { effects.autoDownloadChanged(old.podcastId, false) }
        runStep("library placement of ${old.podcastId}") { effects.moveLibraryPlacement(old.podcastId, placementId) }
        runStep("catalog of ${old.podcastId}") { effects.catalogFollowChanged(old.podcastId, followedCatalogId) }
    }

    /** One follow-up step; a failure is logged and leaves the moved follow in place. */
    private suspend fun runStep(name: String, step: suspend () -> Unit) {
        try {
            step()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Renumbered show step failed: $name", e)
        }
    }

    private companion object {
        const val TAG = "RenumberedShows"

        /** Details asked for per foreground pass at most: only shows followed twice under one feed qualify. */
        const val MAX_LOOKUPS_PER_PASS = 6
    }
}

/** Records [oldId] → [newId]; false (and logged) when it couldn't be saved, so no follow moves. */
private fun RenumberedShows.recordOrWarn(oldId: String, newId: String): Boolean = record(oldId, newId).also { saved ->
    if (!saved) Log.w("RenumberedShows", "Renumbering $oldId → $newId not saved; the follow stays")
}

/** [podcastId] followed itself or through a followed `rss:` feed linked to it, as follow buttons show it. */
private suspend fun PodcastDao.isFollowedAsShown(podcastId: String): Boolean =
    getPodcast(podcastId)?.isSubscribed == true ||
        getSubscribedRssPodcastLinkedTo(podcastId) != null
