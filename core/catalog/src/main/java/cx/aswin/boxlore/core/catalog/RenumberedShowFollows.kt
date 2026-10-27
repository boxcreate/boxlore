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

    /** Starts the old show's unfollowed catalog countdown and clears the new show's. */
    suspend fun catalogFollowChanged(oldId: String, newId: String)

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

    override suspend fun catalogFollowChanged(oldId: String, newId: String) {
        val catalog = library.catalog ?: return
        catalog.setUnsubscribedTtl(oldId, System.currentTimeMillis() + LocalEpisodeCatalogRepository.UNSUBSCRIBE_TTL_MS)
        catalog.setUnsubscribedTtl(newId, null)
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
 * ([RenumberedFollowMerge]): written in one transaction, both rows marked for the next sync push,
 * notifications registered for the new show through the pending-registration journal before the
 * row turns them on (never dropped), the old show's notifications turned off, folder memberships,
 * Home pins and the manual order moved, and the old show's listener rows linked to the new show's
 * episodes. `rss:` shows are never touched; a new show followed through its RSS feed keeps that
 * follow and only the old follow ends.
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

    /** Shows whose details the foreground pass asked for in this process, so each is asked once. */
    private val checkedThisProcess: MutableSet<String> = Collections.synchronizedSet(HashSet())

    /** The show [podcastId] now answers as, when it was renumbered; null otherwise. */
    fun currentId(podcastId: String): String? = shows.currentId(podcastId)

    /**
     * Records an answer for [oldId] that named another show ([answer]) and moves a follow of [oldId]
     * to it. Returns true when a follow moved; false when [answer] isn't a renumbering of [oldId] or
     * nothing was followed.
     */
    suspend fun adoptAnswer(oldId: String, answer: Podcast): Boolean {
        if (!RenumberedShowIds.isRenumbering(oldId, answer.id)) return false
        if (!shows.record(oldId, answer.id)) Log.w(TAG, "Renumbering $oldId → ${answer.id} not saved")
        return moveFollow(oldId, answer.id, answer)
    }

    /** Moves a follow of [oldId] to the show it was recorded as renumbered to, if any. */
    suspend fun adoptKnown(oldId: String): Boolean {
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
     * Before a toggle of [podcast]'s follow: moves follows of shows renumbered into it
     * ([adoptFormerFollows]). When [podcast] still isn't followed but such a follow is (a move that
     * couldn't be saved), the page showed it as [podcast]'s follow, so each one is ended with
     * [unfollow] instead of adding a second follow, and true is returned: the toggle is done.
     * False when the toggle goes ahead as usual (always for an `rss:` show).
     */
    suspend fun endedFormerFollows(podcast: Podcast, unfollow: suspend (PodcastEntity) -> Unit): Boolean {
        if (podcast.isRss) return false
        adoptFormerFollows(podcast.id)
        if (podcastDao.getPodcast(podcast.id)?.isSubscribed == true) return false
        val left = shows.formerIds(podcast.id).mapNotNull { id -> podcastDao.getPodcast(id)?.takeIf { it.isSubscribed && !it.isRss } }
        left.forEach { unfollow(it) }
        return left.isNotEmpty()
    }

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
            RenumberedFollowMerge
                .sharedFeedCandidates(podcastDao.getSubscribedPodcastIndexPodcasts())
                .filter { checkedThisProcess.add(it) }
                .take(maxLookups)
        for (id in candidates) {
            val answer =
                try {
                    lookupDetails(id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Details lookup for $id failed", e)
                    null
                } ?: continue
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

    private suspend fun moveFollowLocked(oldId: String, newId: String, answer: Podcast?): Boolean {
        val old = podcastDao.getPodcast(oldId)?.takeIf { it.isSubscribed && !it.isRss } ?: return false
        val linkedFeed = podcastDao.getRssPodcastLinkedTo(newId)?.takeIf { it.isRss && it.isSubscribed }
        val current = podcastDao.getPodcast(newId)
        if (current?.isRss == true) return false
        val now = nowMillis()
        val move = RenumberedFollowMerge.move(old, newId, current, answer, now)
        if (linkedFeed != null) {
            // The new show is followed through its own feed: that follow stays as it is, the old one ends.
            podcastDao.upsert(move.retired)
            finishOldShow(old, newId)
            return true
        }
        // The registration is saved before the row turns notifications on, so it is never dropped.
        if (move.notificationsMoved && !effects.markRegistrationPending(newId)) {
            Log.w(TAG, "Follow of $oldId not moved to $newId: its notification registration couldn't be saved")
            return false
        }
        podcastDao.moveRenumberedFollow(kept = move.kept, retired = move.retired)
        Log.i(TAG, "Follow of renumbered show $oldId moved to $newId")
        if (move.notificationsMoved) effects.requestRegistration(newId)
        if (move.autoDownloadMoved) runStep("auto-download of $newId") { effects.autoDownloadChanged(newId, true) }
        runStep("address of $newId") { effects.fillMissingAddress(newId, answer?.feedUrl ?: old.feedUrl) }
        finishOldShow(old, newId)
        return true
    }

    /** The old show's leftovers once its follow ended: notifications, auto-download, placement, catalog countdown. */
    private suspend fun finishOldShow(old: PodcastEntity, newId: String) {
        if (old.notificationsEnabled) runStep("notifications of ${old.podcastId}") { effects.unregisterNotifications(old) }
        if (old.autoDownloadEnabled) runStep("auto-download of ${old.podcastId}") { effects.autoDownloadChanged(old.podcastId, false) }
        runStep("library placement of ${old.podcastId}") { effects.moveLibraryPlacement(old.podcastId, newId) }
        runStep("catalog of ${old.podcastId}") { effects.catalogFollowChanged(old.podcastId, newId) }
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
