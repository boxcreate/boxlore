package cx.aswin.boxlore.core.database

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import cx.aswin.boxlore.core.model.BriefingArtwork

/** Repairs installed history and later cloud/backup writes before any reader sees stale resource IDs. */
internal class BriefingArtworkCompatibility(applicationId: String) : RoomDatabase.Callback() {
    private val authority = applicationId.replace("'", "''")

    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        db.beginTransaction()
        try {
            tables.forEach { table ->
                installTriggers(db, table)
                db.execSQL("UPDATE ${table.name} SET ${assignments(table)} WHERE ${needsRepair(table)}")
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun installTriggers(db: SupportSQLiteDatabase, table: ArtworkTable) {
        val watchedColumns = (listOf("episodeId", "podcastId") + table.columns).joinToString(", ")
        listOf("insert" to "INSERT", "update" to "UPDATE OF $watchedColumns").forEach { (suffix, event) ->
            db.execSQL("DROP TRIGGER IF EXISTS brief_artwork_${table.name}_$suffix")
            db.execSQL(
                """
                CREATE TRIGGER brief_artwork_${table.name}_$suffix
                AFTER $event ON ${table.name}
                WHEN ${needsRepair(table, "NEW.")}
                BEGIN
                    UPDATE ${table.name} SET ${assignments(table)} WHERE rowid = NEW.rowid;
                END
                """.trimIndent(),
            )
        }
    }

    private fun assignments(table: ArtworkTable): String = table.columns.joinToString(", ") { column ->
        "$column = CASE WHEN ${isNumericResource(column)} THEN ${canonicalUri()} ELSE $column END"
    }

    private fun needsRepair(table: ArtworkTable, prefix: String = ""): String {
        val numericArtwork = table.columns.joinToString(" OR ") { isNumericResource("$prefix$it") }
        return "(${canonicalUri(prefix)}) IS NOT NULL AND ($numericArtwork)"
    }

    // Exact episode-region prefix takes precedence over show metadata. Unknown identities are untouched.
    private fun canonicalUri(prefix: String = ""): String {
        val episodeCases = BriefingArtwork.entries.flatMap { artwork ->
            artwork.regionAliases.map { alias ->
                val idPrefix = "briefing_${alias}_"
                "WHEN substr(${prefix}episodeId, 1, ${idPrefix.length}) = '$idPrefix' " +
                    "THEN '${artwork.uri(authority)}'"
            }
        }
        val podcastCases = BriefingArtwork.entries.flatMap { artwork ->
            artwork.regionAliases.map { alias ->
                "WHEN ${prefix}podcastId = 'briefing_$alias' THEN '${artwork.uri(authority)}'"
            }
        }
        return "CASE ${episodeCases.joinToString(" ")} ${podcastCases.joinToString(" ")} ELSE NULL END"
    }

    private fun isNumericResource(column: String): String =
        "($column GLOB 'android.resource://*/[0-9]*' AND $column NOT GLOB 'android.resource://*/*[^0-9]*')"

    private data class ArtworkTable(val name: String, val columns: List<String>)

    private companion object {
        val tables = listOf(
            ArtworkTable("listening_history", listOf("episodeImageUrl", "podcastImageUrl")),
            ArtworkTable("queue_items", listOf("imageUrl", "podcastImageUrl")),
            ArtworkTable("downloaded_episodes", listOf("episodeImageUrl", "podcastImageUrl")),
        )
    }
}
