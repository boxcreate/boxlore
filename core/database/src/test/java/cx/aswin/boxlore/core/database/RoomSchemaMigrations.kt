package cx.aswin.boxlore.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Runs the production migrations from [from] to [to] on [db] directly, the way Room chains them. */
internal fun RoomSchemaFixture.runMigrations(db: SupportSQLiteDatabase, from: Int, to: Int,) {
    var version = from
    while (version < to) {
        val step: Migration = BoxLoreDatabase.ALL_MIGRATIONS
            .filter { it.startVersion == version && it.endVersion <= to }
            .maxBy { it.endVersion }
        step.migrate(db)
        version = step.endVersion
    }
    db.version = to
}
