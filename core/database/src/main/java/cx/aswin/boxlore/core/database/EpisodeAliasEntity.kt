package cx.aswin.boxlore.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One stored ID of an episode (docs/EPISODE_IDS.md §5).
 *
 * An episode is (show ID, catalog key). Every local ID that points at one, whether a Podcast
 * Index ID, a phone-minted negative ID or a recipe ID, gets a row here, so old IDs keep
 * resolving after their catalog row is evicted. There is deliberately no foreign key to
 * `local_episodes`: `LocalEpisodeCatalogDao.deleteCatalog` leaves these rows in place.
 *
 * @property aliasId the stored local episode ID, exactly as history, queue and downloads hold it
 * @property podcastId the show the ID belongs to
 * @property catalogKey the episode's recipe-trimmed catalog key; null while the key is unknown
 *   ([EpisodeAliasState.UNRESOLVED])
 * @property recipeId the recipe ID of ([podcastId], [catalogKey]) in the show's namespace; null
 *   while the key is unknown. It is a compact handle, not a unique key, and need not equal [aliasId].
 * @property state [EpisodeAliasState]
 * @property owner true for the one trusted ID per (show, key) that holds listener state
 * @property provenance where this row's evidence came from ([EpisodeAliasProvenance])
 * @property createdAt when the row was recorded (epoch ms); rows seeded by the v39 upgrade carry the upgrade time
 *
 * Added in P4 of the own-episode-IDs plan (plan section 8, Room v39 aliases).
 * COMPAT(own-ids): old local episode IDs keep resolving through these rows. Permanent: never remove (MIGRATION.md §5).
 */
@Entity(
    tableName = "episode_aliases",
    indices = [
        Index(value = ["podcastId", "catalogKey"]),
        Index(value = ["podcastId", "recipeId"]),
    ],
)
data class EpisodeAliasEntity(
    @PrimaryKey
    val aliasId: String,
    val podcastId: String,
    val catalogKey: String?,
    val recipeId: Long?,
    val state: String,
    @ColumnInfo(defaultValue = "0")
    val owner: Boolean = false,
    val provenance: String,
    @ColumnInfo(defaultValue = "0")
    val createdAt: Long = 0L,
)

/**
 * How far an [EpisodeAliasEntity] may be relied on (docs/EPISODE_IDS.md §5).
 *
 * Added in P4 of the own-episode-IDs plan (plan section 8, alias states).
 * COMPAT(own-ids): stored state values. Permanent: never remove or rename (MIGRATION.md §5).
 */
object EpisodeAliasState {
    /** The stored ID is proven to identify this (show, key). Several IDs can be trusted for one episode. */
    const val TRUSTED = "trusted"

    /** Competing identities can't be told apart; never used to resolve and never moves state. */
    const val AMBIGUOUS = "ambiguous"

    /** No usable key yet (synthetic key, history-only, evicted); retried when the show's feed is read. */
    const val UNRESOLVED = "unresolved"
}

/**
 * Where an [EpisodeAliasEntity] came from. Later writers add their own values; stored values
 * are never renamed.
 *
 * Added in P4 of the own-episode-IDs plan (plan section 4, synthetic keys and provenance).
 * COMPAT(own-ids): stored provenance values. Permanent: never remove or rename (MIGRATION.md §5).
 */
object EpisodeAliasProvenance {
    /** Seeded by the v39 upgrade from a `local_episodes` row; its key was re-trimmed with the recipe set. */
    const val UPGRADE_CATALOG = "upgrade_catalog"

    /** Seeded by the v39 upgrade from a row whose key was the synthetic `id:<its own ID>`. */
    const val UPGRADE_SYNTHETIC_KEY = "upgrade_synthetic_key"

    /** Seeded by the v39 upgrade from a phone-minted row keyed by its own audio address, which may hide a GUID. */
    const val UPGRADE_AUDIO_URL_KEY = "upgrade_audio_url_key"

    /** Seeded by the v39 upgrade from an ID found only in history, the queue or downloads. */
    const val UPGRADE_LISTENER_ROW = "upgrade_listener_row"

    /**
     * A legacy phone-minted ID whose listener state the orphan rematch moved onto a catalog row after
     * a feed read; the ID joins that row's episode so a later copy of it (a cloud pull, a restore)
     * counts as the same episode. Added in P4 (plan section 8, re-key paths narrowed).
     */
    const val ORPHAN_REMATCH = "orphan_rematch"
}
