package cx.aswin.boxlore.core.database

import android.content.Context
import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import java.io.File

/**
 * Builds on-disk databases exactly as an installed build left them, for upgrade tests that then
 * open the file through [BoxLoreDatabase.builder] (the production migrations and checks).
 *
 * Shared with `:core:catalog`'s end-to-end upgrade gate (`src/upgradeFixtures`), so it uses only
 * the database module's public API; `runMigrations` stays in `:core:database`'s own tests.
 */
internal object RoomSchemaFixture {
    private const val SCHEMA_DIR = "schemas/cx.aswin.boxlore.core.database.BoxLoreDatabase"

    /** Creates [name] from the exported schema of [version] (tables, indices and Room's identity row), then runs [fill]. */
    fun createFromSchema(context: Context, name: String, version: Int, fill: (SupportSQLiteDatabase) -> Unit,) {
        context.deleteDatabase(name)
        val schema = JsonParser.parseString(schemaFile(version).readText()).asJsonObject.getAsJsonObject("database")
        open(context, name, version) { db ->
            for (entity in schema.getAsJsonArray("entities")) {
                val table = entity.asJsonObject
                val tableName = table.get("tableName").asString
                db.execSQL(table.get("createSql").asString.replace("\${TABLE_NAME}", tableName))
                for (index in table.getAsJsonArray("indices") ?: JsonArray()) {
                    db.execSQL(index.asJsonObject.get("createSql").asString.replace("\${TABLE_NAME}", tableName))
                }
            }
            for (query in schema.getAsJsonArray("setupQueries")) db.execSQL(query.asString)
        }.use { helper -> fill(helper.writableDatabase) }
    }

    /** Opens [name] without Room at [version]; [onCreate] runs only when the file is new. */
    fun open(context: Context, name: String, version: Int, onCreate: (SupportSQLiteDatabase) -> Unit = {},): SupportSQLiteOpenHelper =
        FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(version) {
                        override fun onCreate(db: SupportSQLiteDatabase) = onCreate(db)

                        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int,) = Unit

                        override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int,) = Unit
                    },
                ).build(),
        )

    /** Inserts one row, keeping only the [values] whose column exists in [table]; false when the table doesn't exist. */
    fun insert(db: SupportSQLiteDatabase, table: String, values: Map<String, Any?>,): Boolean {
        val columns = columns(db, table)
        if (columns.isEmpty()) return false
        val kept = values.filterKeys { it in columns }
        db.execSQL(
            "INSERT INTO $table (${kept.keys.joinToString()}) VALUES (${kept.keys.joinToString { "?" }})",
            kept.values.toTypedArray(),
        )
        return true
    }

    fun columns(db: SupportSQLiteDatabase, table: String): List<String> =
        db.query("PRAGMA table_info($table)").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
        }

    /** User tables of [db] (not Room's or SQLite's own). */
    fun tables(db: SupportSQLiteDatabase): List<String> =
        db.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' " +
                "AND name NOT IN ('android_metadata', 'room_master_table') ORDER BY name",
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }

    /** Every row of every user table, column by column, in rowid order. */
    fun snapshot(db: SupportSQLiteDatabase): Map<String, TableRows> =
        tables(db).associateWith { table -> rows(db, table, columns(db, table)) }

    /** The rows of [table] restricted to [columns], in rowid order. */
    fun rows(db: SupportSQLiteDatabase, table: String, columns: List<String>,): TableRows =
        db.query("SELECT ${columns.joinToString()} FROM $table ORDER BY rowid").use { cursor ->
            TableRows(columns, buildList { while (cursor.moveToNext()) add(columns.indices.map { cursor.value(it) }) })
        }

    /** `sqlite_master` SQL for every table, index and trigger, by name. */
    fun schemaSql(db: SupportSQLiteDatabase): Map<String, String?> =
        db.query("SELECT name, sql FROM sqlite_master WHERE name NOT LIKE 'sqlite_%'").use { cursor ->
            buildMap { while (cursor.moveToNext()) put(cursor.getString(0), cursor.getString(1)) }
        }

    /** One query's single Long, or null. */
    fun long(db: SupportSQLiteDatabase, sql: String, vararg args: Any?,): Long? =
        db.query(sql, args).use { cursor -> if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null }

    /** One query's single String, or null. */
    fun string(db: SupportSQLiteDatabase, sql: String, vararg args: Any?,): String? =
        db.query(sql, args).use { cursor -> if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null }

    private fun Cursor.value(index: Int): Any? = when (getType(index)) {
        Cursor.FIELD_TYPE_NULL -> null
        Cursor.FIELD_TYPE_INTEGER -> getLong(index)
        Cursor.FIELD_TYPE_FLOAT -> getDouble(index)
        Cursor.FIELD_TYPE_BLOB -> getBlob(index).toList()
        else -> getString(index)
    }

    private fun schemaFile(version: Int): File {
        val relative = "$SCHEMA_DIR/$version.json"
        return listOf(File(relative), File("../database/$relative"), File("core/database/$relative")).first { it.exists() }
    }

    data class TableRows(val columns: List<String>, val rows: List<List<Any?>>)
}
