package cx.aswin.boxlore.feature.widgets

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class LibraryWidgetSnapshotStore(
    context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(): LibraryWidgetSnapshot? {
        val raw = prefs.getString(KEY_SNAPSHOT, null) ?: return null
        return runCatching { json.decodeFromString<LibraryWidgetSnapshot>(raw) }.getOrNull()
    }

    fun write(snapshot: LibraryWidgetSnapshot) {
        prefs.edit().putString(KEY_SNAPSHOT, json.encodeToString(snapshot)).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_SNAPSHOT).apply()
    }

    companion object {
        const val PREFS_NAME = "boxlore_library_widget"
        private const val KEY_SNAPSHOT = "snapshot"
    }
}
