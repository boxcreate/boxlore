package cx.aswin.boxlore.core.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Subscriptions layout and auto-organize storage. Internal storage helper; the repository remains
 * the public entry point.
 */
internal class SubscriptionsLayoutPreferences(
    private val dataStore: DataStore<Preferences>,
) {
    private fun <T> read(value: (Preferences) -> T): Flow<T> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }.map(value)
            .distinctUntilChanged()

    /** Shows tab layout: grid (default) or list, kept after the app is closed. */
    val gridViewStream: Flow<Boolean> = read { it[Keys.SUBSCRIPTIONS_GRID_VIEW] ?: true }

    suspend fun setGridView(gridView: Boolean) {
        dataStore.edit { it[Keys.SUBSCRIPTIONS_GRID_VIEW] = gridView }
    }

    val autoOrganizeStream: Flow<Boolean> = read { it[Keys.AUTO_ORGANIZE_FOLDERS_ENABLED] ?: false }

    suspend fun setAutoOrganize(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_ORGANIZE_FOLDERS_ENABLED] = enabled }
    }

    /** Look auto-organize gives genre folders: a `FolderDisplaySize` name, or null to choose by size. */
    val autoOrganizeFolderSizeStream: Flow<String?> = read { it[Keys.AUTO_ORGANIZE_FOLDER_SIZE] }

    /** Whether auto-organize tile folders show four covers (default) or their icon. */
    val autoOrganizeTileCoversStream: Flow<Boolean> = read { it[Keys.AUTO_ORGANIZE_TILE_COVERS] ?: true }

    suspend fun setAutoOrganizeLook(folderSizeName: String?, tileCovers: Boolean) {
        dataStore.edit { preferences ->
            if (folderSizeName == null) {
                preferences.remove(Keys.AUTO_ORGANIZE_FOLDER_SIZE)
            } else {
                preferences[Keys.AUTO_ORGANIZE_FOLDER_SIZE] = folderSizeName
            }
            preferences[Keys.AUTO_ORGANIZE_TILE_COVERS] = tileCovers
        }
    }
}
