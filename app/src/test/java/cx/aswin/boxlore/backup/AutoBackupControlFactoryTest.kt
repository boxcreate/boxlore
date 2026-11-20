package cx.aswin.boxlore.backup

import android.app.Application
import android.net.Uri
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import cx.aswin.boxlore.core.prefs.AutoBackupProblem
import cx.aswin.boxlore.core.prefs.AutoBackupSettings
import cx.aswin.boxlore.core.prefs.AutoBackupStore
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/** The folder grant: which grants are taken and released when a folder is picked or backups are turned off. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AutoBackupControlFactoryTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val store = AutoBackupStore(application)
    private val old = "content://com.android.externalstorage.documents/tree/primary%3AOld".toUri()
    private val sync = "content://com.android.externalstorage.documents/tree/primary%3ASync".toUri()

    @Before fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(application)
    }

    @After fun clear() = runBlocking {
        store.turnOff()
        grants().forEach { AutoBackupFolderGrant.release(application, it.toString()) }
    }

    private fun grants(): List<Uri> = application.contentResolver.persistedUriPermissions.map { it.uri }

    private fun writable(uri: Uri) = application.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission && it.isReadPermission }

    private fun pendingNow() = WorkManager.getInstance(application)
        .getWorkInfosForUniqueWork(AutoBackupScheduling.NOW_WORK_NAME).get()
        .filterNot { it.state.isFinished }

    @Test fun `picking a folder keeps read and write access, turns backups on and starts a backup`() = runBlocking {
        AutoBackupControlFactory.adoptFolder(application, store, sync)
        assertTrue(writable(sync))
        val settings = store.current()
        assertTrue(settings.isActive)
        assertEquals(sync.toString(), settings.folderUri)
        assertEquals(1, pendingNow().size)
    }

    @Test fun `picking another folder releases only the previous folder's grant`() = runBlocking {
        AutoBackupControlFactory.adoptFolder(application, store, old)
        AutoBackupControlFactory.adoptFolder(application, store, sync)
        assertEquals(listOf(sync), grants())
        assertEquals(sync.toString(), store.current().folderUri)
        // Picking the same folder again keeps its grant.
        AutoBackupControlFactory.adoptFolder(application, store, sync)
        assertEquals(listOf(sync), grants())
    }

    @Test fun `a refused grant changes nothing and says why`() = runBlocking {
        AutoBackupControlFactory.adoptFolder(application, store, old)
        store.recordProblem(AutoBackupProblem.FOLDER_MISSING, old.toString())
        AutoBackupControlFactory.adoptFolder(application, store, sync) { false }
        val settings = store.current()
        assertEquals(old.toString(), settings.folderUri)
        assertEquals(AutoBackupProblem.FOLDER_MISSING, settings.lastError)
        assertEquals(listOf(old), grants())
        assertEquals("boxlore can't save to this folder. Choose another one.", ShadowToast.getTextOfLatestToast())
    }

    @Test fun `turning off forgets the folder and releases its grant`() = runBlocking {
        AutoBackupControlFactory.adoptFolder(application, store, sync)
        AutoBackupControlFactory.turnOff(application, store)
        assertTrue(grants().isEmpty())
        val settings = store.current()
        assertFalse(settings.enabled)
        assertNull(settings.folderUri)
    }

    @Test fun `picking a folder waits while another folder change is under way`() = runBlocking {
        var taken = 0
        AutoBackupControlFactory.folderChanges.lock()
        val pick = launch(Dispatchers.IO) { AutoBackupControlFactory.adoptFolder(application, store, sync) { taken++ > -1 } }
        delay(100)
        assertEquals(0, taken)
        AutoBackupControlFactory.folderChanges.unlock()
        pick.join()
        assertEquals(1, taken)
        assertEquals(sync.toString(), store.current().folderUri)
    }

    @Test fun `the control waits for readable settings instead of showing backups as off`() = runBlocking {
        val settings = MutableSharedFlow<AutoBackupSettings>(replay = 1)
        val control = AutoBackupControlFactory.create(application, store, settings = settings, running = flowOf(false))
        assertNull(withTimeoutOrNull(300) { control.state.first() })
        settings.emit(AutoBackupSettings(enabled = true, folderUri = sync.toString()))
        assertTrue(control.state.first().settings.isActive)
    }

    @Test fun `a change the store can't save is told to the listener instead of closing the app`() = runBlocking {
        var told = 0
        AutoBackupControlFactory.saveOrSay({ told++ }) { throw IOException("disk full") }
        assertEquals(1, told)
        AutoBackupControlFactory.saveOrSay({ told++ }) { store.setKeepCount(10) }
        assertEquals(1, told)
        assertEquals(10, store.current().keepCount)
        val cancelled = runCatching { AutoBackupControlFactory.saveOrSay({ told++ }) { throw CancellationException("left") } }
        assertTrue(cancelled.exceptionOrNull() is CancellationException)
        assertEquals(1, told)
    }
}
