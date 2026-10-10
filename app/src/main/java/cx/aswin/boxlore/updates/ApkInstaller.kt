package cx.aswin.boxlore.updates

import android.app.Activity
import java.io.File

enum class ApkInstallStage { IDLE, DOWNLOADING, VERIFYING, READY, PERMISSION, FAILED }

data class ApkInstallState(val stage: ApkInstallStage = ApkInstallStage.IDLE, val progress: Float = 0f)

/** Implemented in direct sources; the Play bundle has only a no-installer implementation. */
internal interface ApkInstaller {
    suspend fun prepare(manifest: UpdateManifest, onState: (ApkInstallState) -> Unit): File

    /** Verify an existing completed file only. Restoration never starts a new network transfer. */
    suspend fun restore(manifest: UpdateManifest): File? = null
    suspend fun launch(activity: Activity, manifest: UpdateManifest, file: File): Boolean
}
