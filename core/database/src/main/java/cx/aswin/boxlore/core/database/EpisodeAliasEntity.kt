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
     * Recorded by a catalog read for a new episode that took its recipe ID.
     *
     * Added in P4 PR 9 of the own-episode-IDs plan (plan section 4, recipe IDs).
     */
    const val FEED_RECIPE = "feed_recipe"

    /**
     * Recorded by a catalog read for a new episode that took a reserved local ID, because another
     * episode already held the ID it would have taken (docs/EPISODE_IDS.md §4).
     *
     * Added in P4 PR 9 of the own-episode-IDs plan (plan section 4, collisions).
     */
    const val FEED_RESERVED = "feed_reserved"

    /**
     * Recorded by a catalog read for a stored catalog row that had no alias yet (a row written
     * after the v39 upgrade by a build without aliases).
     *
     * Added in P4 PR 9 of the own-episode-IDs plan (plan section 4, aliases).
     */
    const val FEED_STORED_ROW = "feed_stored_row"

    /**
     * Recorded by a catalog read when a stored row whose alias was unresolved (a phone-minted row
     * keyed by its own audio address) is found under exactly the key the feed gives its item: the
     * key is confirmed and the alias becomes trusted.
     *
     * Added in P4 PR 9 of the own-episode-IDs plan (plan section 5, unresolved aliases are retried when the feed is read).
     */
    const val FEED_CONFIRMED_KEY = "feed_confirmed_key"

    /**
     * Recorded by a catalog read for a new episode that kept an ID this show's history, queue,
     * downloads or unresolved alias already held, because the item's own phone-minted ID equals
     * it (the same hash input, so the same episode).
     *
     * Added in P4 PR 9 of the own-episode-IDs plan (plan section 4, nothing is re-keyed; section 5, unresolved aliases).
     */
    const val FEED_OWN_MINT = "feed_own_mint"

    /**
     * Recorded by a catalog read for a new episode that kept an ID only this show's history, queue
     * or downloads held (a Podcast Index ID, for example), matched by a unique audio address, else
     * a unique title and date, as the orphan rematch matches them.
     *
     * Added in P4 PR 9 of the own-episode-IDs plan (plan section 4, nothing is re-keyed; section 5, unresolved aliases).
     */
    const val FEED_LISTENER_MATCH = "feed_listener_match"

    /**
     * Recorded when a show added by its feed address moves to its Podcast Index show and keeps
     * its episodes' phone-minted IDs (the legacy RSS subscription upgrade).
     *
     * Added in P4 PR 9 of the own-episode-IDs plan (plan section 5, every catalog ID of a Podcast Index show has an alias).
     */
    const val RSS_FOLLOW_UPGRADE = "rss_follow_upgrade"

    /**
     * A catalog read corrected a stored key that differed only by characters SQLite `TRIM()`
     * keeps; the row kept its ID.
     *
     * Added in P4 PR 9 of the own-episode-IDs plan (plan section 4, legacy keys are re-trimmed).
     */
    const val HEAL_RETRIMMED_KEY = "heal_retrimmed_key"

    /**
     * A catalog read healed a row keyed by the synthetic `id:<its own ID>` to the feed's key: the
     * item's own minted ID equals the row's ID, so it is the same episode.
     *
     * Added in P4 PR 9 of the own-episode-IDs plan (plan section 4, synthetic keys resolve when the feed is read).
     */
    const val HEAL_SYNTHETIC_KEY = "heal_synthetic_key"

    /**
     * A catalog read healed a phone-minted row keyed by its own audio address to the item's GUID:
     * the item's own minted ID equals the row's ID, so it is the same episode.
     *
     * Added in P4 PR 9 of the own-episode-IDs plan (plan section 4, legacy keys).
     */
    const val HEAL_AUDIO_URL_KEY = "heal_audio_url_key"
}
