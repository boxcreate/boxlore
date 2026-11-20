package cx.aswin.boxlore.backup

import android.app.Application
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import cx.aswin.boxlore.R
import cx.aswin.boxlore.core.prefs.AutoBackupSettings
import cx.aswin.boxlore.core.prefs.AutoBackupStore
import cx.aswin.boxlore.feature.settings.pages.AutoBackupControl
import cx.aswin.boxlore.feature.settings.pages.AutoBackupUiState
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Connects Settings' automatic backup group to the folder grant, the stored settings and the schedule. */
internal object AutoBackupControlFactory {
    /**
     * Grant and store changes outlive the Settings screen: leaving it between releasing a grant and saving
     * the change would otherwise leave a folder stored without access.
     */
    private val processScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Folder changes run one at a time, so turning off (forget the folder, then release its grant) can't
     * interleave with picking that folder again and leave it stored without its grant.
     */
    internal val folderChanges = Mutex()

    private const val TAG = "AutoBackupControl"

    /** One control for the Settings route's life, so its state flow isn't rebuilt on each recomposition. */
    @Composable
    fun rememberControl(application: Application): AutoBackupControl = remember(application) { create(application) }

    /**
     * [settings] follows the store only while it reads, so a failed read never shows backups as off and later
     * changes still arrive (Settings waits for the first read). A change that can't be saved says so.
     */
    fun create(
        application: Application,
        store: AutoBackupStore = AutoBackupStore(application),
        scope: CoroutineScope = processScope,
        settings: Flow<AutoBackupSettings> = store.settingsWhenReadable,
        running: Flow<Boolean> = AutoBackupScheduling.isBackingUp(application),
    ): AutoBackupControl {
        fun change(write: suspend () -> Unit) {
            scope.launch { saveOrSay({ showNotSaved(application) }, write) }
        }
        return AutoBackupControl(
            state = combine(settings, running) { current, isRunning -> AutoBackupUiState(current, isRunning) },
            onFolderPicked = { uri -> change { adoptFolder(application, store, uri) } },
            onTurnOff = { change { turnOff(application, store) } },
            onSetFrequency = { frequency -> change { store.setFrequency(frequency) } },
            onSetKeepCount = { count -> change { store.setKeepCount(count) } },
            onBackUpNow = { AutoBackupScheduling.backUpNow(application) },
        )
    }

    /**
     * Runs one settings change. A store that can't be written ([IOException]) is told to the listener through
     * [onNotSaved] instead of escaping the process scope and closing the app; cancellation still propagates.
     */
    internal suspend fun saveOrSay(onNotSaved: suspend () -> Unit, write: suspend () -> Unit) {
        try {
            write()
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            Log.w(TAG, "An automatic backup setting wasn't saved: ${error.javaClass.simpleName}")
            onNotSaved()
        }
    }

    private suspend fun showNotSaved(application: Application) = withContext(Dispatchers.Main.immediate) {
        Toast.makeText(application, application.getString(R.string.auto_backup_change_not_saved), Toast.LENGTH_LONG).show()
    }

    /**
     * Keeps access to the picked folder, lets go of the previous one, turns backups on and saves the first
     * backup right away, so the listener sees it work.
     */
    internal suspend fun adoptFolder(
        application: Application,
        store: AutoBackupStore,
        uri: Uri,
        takeGrant: (Uri) -> Boolean = { AutoBackupFolderGrant.take(application, it) },
    ): Unit = folderChanges.withLock {
        if (!takeGrant(uri)) {
            withContext(Dispatchers.Main.immediate) {
                Toast.makeText(application, application.getString(R.string.auto_backup_folder_refused), Toast.LENGTH_LONG).show()
            }
            return@withLock
        }
        val previous = store.current().folderUri
        store.setFolder(uri.toString(), AutoBackupFolderGrant.displayName(application, uri))
        if (previous != null && previous != uri.toString()) AutoBackupFolderGrant.release(application, previous)
        AutoBackupScheduling.backUpNow(application, newFolder = true)
    }

    /** Forgets the folder first, then its grant, so a stored folder always still has its grant. */
    internal suspend fun turnOff(application: Application, store: AutoBackupStore): Unit = folderChanges.withLock {
        val folder = store.current().folderUri
        store.turnOff()
        folder?.let { AutoBackupFolderGrant.release(application, it) }
    }
}
