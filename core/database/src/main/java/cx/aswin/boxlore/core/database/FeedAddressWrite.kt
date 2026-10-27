package cx.aswin.boxlore.core.database

/**
 * One saved feed address replacement for [PodcastDao.replaceFeedAddress].
 *
 * - [expectedFeedUrl]: the address the caller read; nothing is written when the row holds another
 *   one by then. It also becomes `previousFeedUrl`, so the change can be undone.
 * - [transportUrl]: the address to fetch through when the publisher's can't be read directly; null
 *   clears an old one.
 * - [source]: where [feedUrl] came from (`feedUrlSource`).
 * - [checkedAt]: when the address registry confirmed [feedUrl] (`feedUrlCheckedAt`); 0 when it didn't,
 *   which keeps the stored stamp so the daily check still runs at most once a day.
 * - [markDirty]: true for an address found on this phone for a followed show, so the next sync
 *   push sends it; false for one that came from the cloud, or a show that isn't followed.
 *
 * Added in P4 PR 14 of the own-episode-IDs plan (plan section 11, followed shows: one durable address update).
 * COMPAT(own-ids): the one address writer. Permanent: never remove (MIGRATION.md §5).
 */
data class FeedAddressWrite(
    val podcastId: String,
    val expectedFeedUrl: String?,
    val feedUrl: String,
    val transportUrl: String?,
    val source: String?,
    val checkedAt: Long,
    val markDirty: Boolean,
)
