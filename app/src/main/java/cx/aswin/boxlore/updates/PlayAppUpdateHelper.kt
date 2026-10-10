package cx.aswin.boxlore.updates

import android.app.Activity
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import cx.aswin.boxlore.R

/**
 * Google Play In-App Updates wiring owned by the Activity shell (not [AppContainer]).
 */
class PlayAppUpdateHelper(private val activity: Activity, private val updateLauncher: ActivityResultLauncher<IntentSenderRequest>,) {
    private val appUpdateManager: AppUpdateManager by lazy {
        AppUpdateManagerFactory.create(activity)
    }

    /** Fresh Play info for each explicit update tap; no prompt during availability checks. */
    fun startUpdate() {
        try {
            appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
                if (appUpdateInfo.installStatus() == InstallStatus.DOWNLOADED) {
                    appUpdateManager.completeUpdate().addOnFailureListener(::showUpdateError)
                    return@addOnSuccessListener
                }
                val isUpdateAvailable =
                    appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                val updatePriority = appUpdateInfo.updatePriority()
                val daysStale = appUpdateInfo.clientVersionStalenessDays() ?: 0
                val updateType = choosePlayUpdateType(
                    highPriority = updatePriority >= 4 || daysStale >= 7,
                    immediateAllowed = appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE),
                    flexibleAllowed = appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE),
                )

                if (isUpdateAvailable && updateType != null) {
                    try {
                        val started = appUpdateManager.startUpdateFlowForResult(
                            appUpdateInfo,
                            updateLauncher,
                            AppUpdateOptions.newBuilder(updateType).build(),
                        )
                        if (!started) showUpdateError(IllegalStateException("Play did not start an eligible flow"))
                    } catch (e: Exception) {
                        showUpdateError(e)
                    }
                } else {
                    Toast.makeText(activity, R.string.updates_play_unavailable, Toast.LENGTH_LONG).show()
                }
            }.addOnFailureListener {
                showUpdateError(it)
            }
        } catch (e: Exception) {
            showUpdateError(e)
        }
    }

    /** Resume an in-progress immediate update after the Activity returns to foreground. */
    fun resumeInProgressUpdate() {
        try {
            appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
                if (appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                    runCatching {
                        appUpdateManager.startUpdateFlowForResult(
                        appUpdateInfo,
                        updateLauncher,
                        AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build(),
                    )
                    }.onFailure { Log.e(TAG, "Could not resume Play update", it) }
                }
            }.addOnFailureListener { Log.e(TAG, "Could not resume Play update", it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking update status", e)
        }
    }

    private fun showUpdateError(error: Exception) {
        Log.e(TAG, "Could not start Play update", error)
        Toast.makeText(activity, R.string.updates_play_failed, Toast.LENGTH_LONG).show()
    }

    companion object {
        private const val TAG = "AppUpdate"
    }
}

/** Prefer flexible updates, but never advertise an allowed update with no usable flow. */
internal fun choosePlayUpdateType(highPriority: Boolean, immediateAllowed: Boolean, flexibleAllowed: Boolean): Int? = when {
    highPriority && immediateAllowed -> AppUpdateType.IMMEDIATE
    flexibleAllowed -> AppUpdateType.FLEXIBLE
    immediateAllowed -> AppUpdateType.IMMEDIATE
    else -> null
}
