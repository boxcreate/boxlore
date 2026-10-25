package cx.aswin.boxlore.core.database.entities

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import cx.aswin.boxlore.core.model.EpisodeRecipeId

/**
 * One recent queue removal with the episode's identity, stored in
 * [QueueMetadataEntity.recentRemovedEpisodes] (a JSON list beside the plain ID list). With the show
 * and the recipe ID or catalog key, a removal also reaches devices that store the episode under
 * another ID.
 *
 * @property id the removed queue row's episode ID
 * @property recipeId the episode's recipe ID, when known and in the recipe range
 * @property show the episode's show ID, when the episode has an identity
 * @property catalogKey the episode's catalog key, when known
 *
 * Added in P4 of the own-episode-IDs plan (plan section 8, rich queue tombstones).
 * COMPAT(own-ids): queue removals across an episode's IDs. Permanent: never remove (MIGRATION.md §5).
 */
data class QueueRemovalIdentity(
    val id: String,
    val recipeId: Long? = null,
    val show: String? = null,
    val catalogKey: String? = null,
) {
    /** True when [other] names the same episode: the same ID, or the same show and catalog key. */
    fun sameEpisodeAs(other: QueueRemovalIdentity): Boolean = id == other.id ||
        (show != null && catalogKey != null && show == other.show && catalogKey == other.catalogKey)

    companion object {
        private const val ID = "id"
        private const val RECIPE_ID = "recipeId"
        private const val SHOW = "show"
        private const val CATALOG_KEY = "catalogKey"

        /**
         * The stored list, oldest first. Field names are written by hand (the sync wire shape
         * `{id, recipeId, show, catalogKey}`), so code shrinking can't rename them. Unreadable
         * input and entries without an ID are dropped; a recipe ID outside the recipe range is dropped.
         */
        fun parseList(raw: String?): List<QueueRemovalIdentity> {
            if (raw.isNullOrBlank()) return emptyList()
            val array = runCatching { JsonParser.parseString(raw).asJsonArray }.getOrNull() ?: return emptyList()
            return array.mapNotNull { element ->
                val entry = runCatching { element.asJsonObject }.getOrNull() ?: return@mapNotNull null
                val id = entry.stringOrNull(ID) ?: return@mapNotNull null
                QueueRemovalIdentity(
                    id = id,
                    recipeId = runCatching { entry[RECIPE_ID]?.takeIf { !it.isJsonNull }?.asLong }.getOrNull()
                        ?.takeIf(EpisodeRecipeId::isRecipeId),
                    show = entry.stringOrNull(SHOW),
                    catalogKey = entry.stringOrNull(CATALOG_KEY),
                )
            }
        }

        /** The JSON list for [entries], or null when there are none. Unknown fields are left out. */
        fun serializeList(entries: List<QueueRemovalIdentity>): String? {
            if (entries.isEmpty()) return null
            val array = JsonArray()
            for (entry in entries) {
                val json = JsonObject()
                json.addProperty(ID, entry.id)
                entry.recipeId?.takeIf(EpisodeRecipeId::isRecipeId)?.let { json.addProperty(RECIPE_ID, it) }
                entry.show?.let { json.addProperty(SHOW, it) }
                entry.catalogKey?.let { json.addProperty(CATALOG_KEY, it) }
                array.add(json)
            }
            return array.toString()
        }

        private fun JsonObject.stringOrNull(name: String): String? =
            runCatching { get(name)?.takeIf { it.isJsonPrimitive }?.asString }.getOrNull()?.takeIf { it.isNotEmpty() }
    }
}
