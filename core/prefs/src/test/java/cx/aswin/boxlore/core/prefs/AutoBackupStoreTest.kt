package cx.aswin.boxlore.core.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutoBackupStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After fun clear() = runTest {
        context.autoBackupDataStore.edit { it.clear() }
    }

    @Test fun `defaults are off, weekly and keep five`() = runTest {
        val settings = AutoBackupStore(context).current()
        assertEquals(AutoBackupSettings(), settings)
        assertFalse(settings.isActive)
        assertEquals(AutoBackupFrequency.WEEKLY, settings.frequency)
        assertEquals(5, settings.keepCount)
    }

    @Test fun `choosing a folder turns backups on, clears the old problem and survives a new store`() = runTest {
        val store = AutoBackupStore(context)
        store.setFolder("content://tree/a", "Old")
        store.recordProblem(AutoBackupProblem.PERMISSION_LOST, "content://tree/a")
        assertEquals(AutoBackupProblem.PERMISSION_LOST, store.current().lastError)

        store.setFolder("content://tree/b", "Sync")
        store.setFrequency(AutoBackupFrequency.DAILY)
        store.setKeepCount(10)
        val reread = AutoBackupStore(context).current()
        assertTrue(reread.isActive)
        assertEquals("content://tree/b", reread.folderUri)
        assertEquals("Sync", reread.folderName)
        assertEquals(AutoBackupFrequency.DAILY, reread.frequency)
        assertEquals(10, reread.keepCount)
        assertNull(reread.lastError)
    }

    @Test fun `turning off forgets the folder but keeps the schedule and the last backup`() = runTest {
        val store = AutoBackupStore(context)
        store.setFolder("content://tree/a", "Sync")
        store.setFrequency(AutoBackupFrequency.DAILY)
        store.recordSuccess(1_000L, "boxlore_auto_backup_2026-11-20_0900.json", "content://tree/a")
        store.turnOff()
        val settings = store.current()
        assertFalse(settings.enabled)
        assertNull(settings.folderUri)
        assertNull(settings.folderName)
        assertEquals(AutoBackupFrequency.DAILY, settings.frequency)
        assertEquals(1_000L, settings.lastSuccessAt)
        // Picking a folder again starts fresh: the old "Last backup" was saved elsewhere.
        store.setFolder("content://tree/b", "Other")
        assertNull(store.current().lastSuccessAt)
        assertNull(store.current().lastFileName)
    }

    @Test fun `a different folder forgets the last backup and the same folder keeps it`() = runTest {
        val store = AutoBackupStore(context)
        store.setFolder("content://tree/a", "Sync")
        store.recordSuccess(1_000L, "boxlore_auto_backup_2026-11-20_0900.json", "content://tree/a")
        store.setFolder("content://tree/a", "Sync renamed")
        assertEquals(1_000L, store.current().lastSuccessAt)
        store.setFolder("content://tree/b", "Other")
        assertNull(store.current().lastSuccessAt)
        assertNull(store.current().lastFileName)
    }

    @Test fun `a success for an old folder doesn't stamp or clear the new one`() = runTest {
        val store = AutoBackupStore(context)
        store.setFolder("content://tree/new", "Sync")
        store.recordProblem(AutoBackupProblem.IO_ERROR, "content://tree/new")
        store.recordSuccess(9L, "boxlore_auto_backup_2026-11-20_0905.json", "content://tree/old")
        val settings = store.current()
        assertNull(settings.lastSuccessAt)
        assertNull(settings.lastFileName)
        assertEquals(AutoBackupProblem.IO_ERROR, settings.lastError)
    }

    @Test fun `success clears a problem and a problem for an old folder is ignored`() = runTest {
        val store = AutoBackupStore(context)
        store.setFolder("content://tree/new", null)
        store.recordProblem(AutoBackupProblem.FOLDER_MISSING, "content://tree/old")
        assertNull(store.current().lastError)
        store.recordProblem(AutoBackupProblem.IO_ERROR, "content://tree/new")
        store.recordAttempt(5L)
        assertEquals(AutoBackupProblem.IO_ERROR, store.current().lastError)
        store.recordSuccess(9L, "boxlore_auto_backup_2026-11-20_0905.json", "content://tree/new")
        val settings = store.current()
        assertNull(settings.lastError)
        assertEquals(5L, settings.lastAttemptAt)
        assertEquals(9L, settings.lastSuccessAt)
        assertEquals("boxlore_auto_backup_2026-11-20_0905.json", settings.lastFileName)
        assertNull(settings.folderName)
    }

    @Test fun `unknown stored values fall back to the defaults`() = runTest {
        context.autoBackupDataStore.edit {
            it[stringPreferencesKey("frequency")] = "hourly"
            it[intPreferencesKey("keep_count")] = 99
        }
        val settings = AutoBackupStore(context).current()
        assertEquals(AutoBackupFrequency.WEEKLY, settings.frequency)
        assertEquals(5, settings.keepCount)
        AutoBackupStore(context).setKeepCount(7)
        assertEquals(5, AutoBackupStore(context).current().keepCount)
        AutoBackupStore(context).setKeepCount(3)
        assertEquals(3, AutoBackupStore(context).current().keepCount)
    }

    @Test fun `an unreadable file reads as the defaults, while the schedule's flow waits and reads again`() = runTest {
        val broken = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow { throw IOException("disk") }
            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = throw IOException("disk")
        }
        assertEquals(AutoBackupSettings(), AutoBackupStore(broken).settings.first())
        // Never "turned off" while unreadable: the schedule's flow emits nothing...
        assertNull(withTimeoutOrNull(60 * 60 * 1000L) { AutoBackupStore(broken).settingsWhenReadable.first() })
    }

    @Test fun `the schedule's flow picks up settings once the file reads again`() = runTest {
        var reads = 0
        val recovering = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow {
                if (reads++ < 2) throw IOException("disk")
                emit(preferencesOf(booleanPreferencesKey("enabled") to true, stringPreferencesKey("folder_uri") to "content://tree/sync"))
            }
            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = throw IOException("disk")
        }
        val settings = AutoBackupStore(recovering).settingsWhenReadable.first()
        assertTrue(settings.isActive)
        assertEquals(3, reads)
    }

    @Test fun `failed reads wait longer each time, up to ten minutes`() {
        assertEquals(1_000L, AutoBackupStore.readRetryDelayMs(0))
        assertEquals(4_000L, AutoBackupStore.readRetryDelayMs(2))
        assertEquals(AutoBackupStore.MAX_READ_RETRY_MS, AutoBackupStore.readRetryDelayMs(30))
    }

    @Test fun `only folder problems ask for a new folder`() {
        assertTrue(AutoBackupProblem.needsNewFolder(AutoBackupProblem.PERMISSION_LOST))
        assertTrue(AutoBackupProblem.needsNewFolder(AutoBackupProblem.FOLDER_MISSING))
        assertFalse(AutoBackupProblem.needsNewFolder(AutoBackupProblem.IO_ERROR))
        assertFalse(AutoBackupProblem.needsNewFolder(null))
    }
}
