package cx.aswin.boxlore.updates

import android.app.Activity
import android.content.Context
import java.io.File

/** Play builds cannot download or request installation of an APK. */
internal fun createApkInstaller(@Suppress("UNUSED_PARAMETER") context: Context): ApkInstaller = object : ApkInstaller {
    override suspend fun prepare(manifest: UpdateManifest, onState: (ApkInstallState) -> Unit): File = error("Use Google Play updates")
    override suspend fun launch(activity: Activity, manifest: UpdateManifest, file: File): Boolean = false
}
