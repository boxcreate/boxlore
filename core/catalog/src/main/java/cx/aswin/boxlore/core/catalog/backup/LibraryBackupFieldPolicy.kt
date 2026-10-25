package cx.aswin.boxlore.core.catalog.backup

import com.google.gson.ExclusionStrategy
import com.google.gson.FieldAttributes
import cx.aswin.boxlore.core.database.PodcastEntity

/**
 * Stored fields the JSON library backup leaves out of its export, so a backup written by this
 * build has the same fields as before Room v39. [BoxLoreBackup.subscriptions] serializes
 * [PodcastEntity] directly, so every new entity field would otherwise join the export.
 *
 * - The v39 feed identity and address columns stay out until the backup format adds identity
 *   data in a version of its own.
 * - [PodcastEntity.transportUrl] is never exported: it is an app-internal fetch address.
 *
 * Import is unaffected: subscriptions are restored through the `Podcast` model, which has none of
 * these fields.
 *
 * Added in P4 of the own-episode-IDs plan (plan section 8, Room v39 podcast columns; backups).
 * COMPAT(own-ids): keeps the backup format and never exports transport addresses. Permanent: never remove (MIGRATION.md §5).
 */
internal object LibraryBackupFieldPolicy : ExclusionStrategy {
    /** [PodcastEntity] fields Room v39 added. */
    val PODCAST_FIELDS_NOT_EXPORTED: Set<String> =
        setOf("epNs", "feedUrlSource", "feedUrlCheckedAt", "transportUrl", "previousFeedUrl")

    override fun shouldSkipField(f: FieldAttributes): Boolean =
        f.declaringClass == PodcastEntity::class.java && f.name in PODCAST_FIELDS_NOT_EXPORTED

    override fun shouldSkipClass(clazz: Class<*>): Boolean = false
}
