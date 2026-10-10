package cx.aswin.boxlore.updates

import android.app.Activity
import android.content.Context
import android.os.Build
import cx.aswin.boxlore.BuildConfig
import cx.aswin.boxlore.util.isInstalledFromPlayStore
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Application-scoped checks/downloads survive Activity recreation without retaining it. */
class AppUpdates internal constructor(
    val checker: UpdateChecker,
    val usesPlay: Boolean,
    private val installer: ApkInstaller,
    private val scope: CoroutineScope,
    private val session: UpdateSessionStore = MemoryUpdateSessionStore(),
) {
    private val mutableInstall = MutableStateFlow(ApkInstallState())
    val install = mutableInstall.asStateFlow()
    private val mutableVisible = MutableStateFlow(false)
    val visible = mutableVisible.asStateFlow()
    private var restoring: Job? = null
    private var restoringChecksum: String? = null
    private var transfer: Job? = null
    private var transferChecksum: String? = null
    private var prepared: Pair<UpdateManifest, File>? = null
    private var launchingInstall = false

    init {
        scope.launch {
            checker.state.map { it.offer?.manifest?.apkSha256 }.distinctUntilChanged().collect { checksum ->
                val obsoleteTransfer = transferChecksum != null && transferChecksum != checksum
                val obsoleteRestore = restoringChecksum != null && restoringChecksum != checksum
                val obsoletePrepared = prepared != null && prepared?.first?.apkSha256 != checksum
                if (obsoleteTransfer || obsoleteRestore || obsoletePrepared) {
                    transfer?.cancel()
                    restoring?.cancel()
                    prepared = null
                    session.preparedChecksum = null
                    mutableInstall.value = ApkInstallState()
                }
                restoreSession()
            }
        }
    }

    fun foreground() {
        restoreSession()
        scope.launch { checker.check() }
    }

    private fun restoreSession() {
        if (!session.visible || prepared != null || restoring?.isActive == true) return
        val manifest = checker.state.value.offer?.manifest
        if (manifest == null || session.preparedChecksum != manifest.apkSha256) {
            session.visible = false
            session.preparedChecksum = null
            return
        }
        mutableVisible.value = true
        restoringChecksum = manifest.apkSha256
        restoring = scope.launch {
            mutableInstall.value = ApkInstallState(ApkInstallStage.VERIFYING, 1f)
            try {
                val file = installer.restore(manifest)
                if (checker.state.value.offer?.manifest?.apkSha256 == manifest.apkSha256) {
                    prepared = file?.let { manifest to it }
                    if (file == null) session.preparedChecksum = null
                    mutableInstall.value = if (file != null) ApkInstallState(ApkInstallStage.READY, 1f) else ApkInstallState()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableInstall.value = ApkInstallState(ApkInstallStage.FAILED)
            } finally {
                if (restoringChecksum == manifest.apkSha256) restoringChecksum = null
            }
        }
    }

    fun open(checkNow: Boolean = true) {
        mutableVisible.value = true
        // Persist only a prepared update, not ordinary browsing of this screen.
        session.visible = session.preparedChecksum != null
        if (checkNow) scope.launch { checker.check(force = true) }
    }

    /** An explicit announcement Download tap checks metadata before any transfer. */
    fun openAndDownload() {
        open(checkNow = false)
        scope.launch {
            checker.check(force = true)
            restoring?.join()
            if (!usesPlay && checker.state.value.status == UpdateCheckStatus.AVAILABLE && prepared == null) download()
        }
    }

    fun dismiss() {
        mutableVisible.value = false
        session.visible = false
    }

    fun download() {
        val manifest = checker.state.value.offer?.manifest ?: return
        if (transfer?.isCompleted == false || restoring?.isActive == true) return
        transferChecksum = manifest.apkSha256
        transfer = scope.launch {
            try {
                val file = installer.prepare(manifest) {
                    if (checker.state.value.offer?.manifest?.apkSha256 == manifest.apkSha256) mutableInstall.value = it
                }
                if (checker.state.value.offer?.manifest?.apkSha256 != manifest.apkSha256) {
                    mutableInstall.value = ApkInstallState()
                    return@launch
                }
                prepared = manifest to file
                session.preparedChecksum = manifest.apkSha256
                session.visible = mutableVisible.value
                mutableInstall.value = ApkInstallState(ApkInstallStage.READY, 1f)
            } catch (cancelled: CancellationException) {
                mutableInstall.value = ApkInstallState()
                throw cancelled
            } catch (_: Exception) {
                mutableInstall.value = ApkInstallState(ApkInstallStage.FAILED)
            } finally {
                transferChecksum = null
            }
        }
    }

    fun pauseDownload() {
        transfer?.cancel()
    }

    /** Call only after an explicit Install tap; Android owns installation confirmation. */
    suspend fun install(activity: Activity) {
        val (manifest, file) = prepared ?: return
        if (launchingInstall) return
        session.visible = true
        session.preparedChecksum = manifest.apkSha256
        launchingInstall = true
        try {
            mutableInstall.value = ApkInstallState(ApkInstallStage.VERIFYING, 1f)
            val launched = installer.launch(activity, manifest, file)
            mutableInstall.value = ApkInstallState(if (launched) ApkInstallStage.READY else ApkInstallStage.PERMISSION, 1f)
        } catch (cancelled: CancellationException) {
            mutableInstall.value = if (prepared?.first == manifest) ApkInstallState(ApkInstallStage.READY, 1f) else ApkInstallState()
            throw cancelled
        } catch (_: Exception) {
            mutableInstall.value = ApkInstallState(ApkInstallStage.FAILED)
        } finally {
            launchingInstall = false
        }
    }
}

internal fun createAppUpdates(context: Context, scope: CoroutineScope): AppUpdates {
    val usesPlay = !BuildConfig.BOXLORE_DIRECT_UPDATES || context.isInstalledFromPlayStore()
    val checkStore = AndroidUpdateCheckStore(context, usesPlay)
    val session = AndroidUpdateSessionStore(context)
    val cachedVersion = checkStore.cachedManifest?.versionCode ?: checkStore.cachedPlayVersion
    if (cachedVersion in 1..BuildConfig.VERSION_CODE.toLong()) {
        session.visible = false
        session.preparedChecksum = null
    }
    val source = if (usesPlay) {
        PlayUpdateSource(context)
    } else {
        DirectUpdateSource(updateHttpClient(), BuildConfig.BOXLORE_UPDATE_MANIFEST_URL, BuildConfig.VERSION_CODE.toLong(), Build.VERSION.SDK_INT)
    }
    return AppUpdates(
        checker = UpdateChecker(source, checkStore, BuildConfig.VERSION_CODE.toLong(), Build.VERSION.SDK_INT),
        usesPlay = usesPlay,
        installer = createApkInstaller(context),
        // Serialize UI actions, transfer ownership and offer invalidation on Main.
        // Lookup and file work explicitly move to IO while retaining the process lifetime.
        scope = CoroutineScope(scope.coroutineContext + Dispatchers.Main.immediate),
        session = session,
    )
}

private class AndroidUpdateCheckStore(context: Context, usesPlay: Boolean) : UpdateCheckStore {
    private val preferences = context.getSharedPreferences(
        if (BuildConfig.BOXLORE_ISOLATED_TESTS) {
            "boxlore_test_updates"
        } else if (usesPlay) {
        "boxlore_play_updates"
    } else {
        "boxlore_direct_updates"
    },
        Context.MODE_PRIVATE
    )
    override var checkedAt: Long
        get() = preferences.getLong("checked_at", 0)
        set(value) {
            preferences.edit().putLong("checked_at", value).apply()
        }
    override var attemptedAt: Long
        get() = preferences.getLong("attempted_at", 0)
        set(value) {
            preferences.edit().putLong("attempted_at", value).apply()
        }
    override var cachedManifest: UpdateManifest?
        get() = runCatching { preferences.getString("manifest", null)?.let(UpdateManifest::parse) }.getOrNull()
        set(value) {
            preferences.edit().putString("manifest", value?.encode()).apply()
        }
    override var upcoming: String
        get() = preferences.getString("upcoming", "").orEmpty()
        set(value) {
            preferences.edit().putString("upcoming", value).apply()
        }
    override var cachedPlayVersion: Long
        get() = preferences.getLong("play_offer_version", 0)
        set(value) {
            preferences.edit().putLong("play_offer_version", value).apply()
        }
}

internal interface UpdateSessionStore {
    var visible: Boolean
    var preparedChecksum: String?
}

internal class MemoryUpdateSessionStore : UpdateSessionStore {
    override var visible = false
    override var preparedChecksum: String? = null
}

private class AndroidUpdateSessionStore(context: Context) : UpdateSessionStore {
    private val prefs = context.getSharedPreferences(if (BuildConfig.BOXLORE_ISOLATED_TESTS) "boxlore_test_update_session" else "boxlore_update_session", Context.MODE_PRIVATE)
    override var visible: Boolean
        get() = prefs.getBoolean("visible", false)
        set(value) {
            prefs.edit().putBoolean("visible", value).apply()
        }
    override var preparedChecksum: String?
        get() = prefs.getString("prepared", null)
        set(value) {
            prefs.edit().putString("prepared", value).apply()
        }
}
