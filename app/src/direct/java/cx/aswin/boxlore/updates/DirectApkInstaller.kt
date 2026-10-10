package cx.aswin.boxlore.updates

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun createApkInstaller(context: Context): ApkInstaller = DirectApkInstaller(context.applicationContext)

private class DirectApkInstaller(private val context: Context) : ApkInstaller {
    private val downloader = ApkFileDownloader(File(context.cacheDir, "updates"), updateHttpClient())

    override suspend fun restore(manifest: UpdateManifest): File? = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "updates/${manifest.apkSha256.lowercase()}.apk")
        if (!file.isFile) return@withContext null
        verify(manifest, file)
        file
    }

    override suspend fun prepare(manifest: UpdateManifest, onState: (ApkInstallState) -> Unit): File {
        onState(ApkInstallState(ApkInstallStage.DOWNLOADING))
        val file = downloader.download(manifest) { onState(ApkInstallState(ApkInstallStage.DOWNLOADING, it)) }
        onState(ApkInstallState(ApkInstallStage.VERIFYING, 1f))
        verify(manifest, file)
        return file
    }

    @Suppress("DEPRECATION")
    private suspend fun verify(manifest: UpdateManifest, file: File) = withContext(Dispatchers.IO) {
        require(ApkFileDownloader.matches(file, manifest)) { "APK changed after download" }
        val manager = context.packageManager
        val current = manager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val candidate = manager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: error("Unreadable APK")
        val installed = current.signingInfo ?: error("Missing installed signature")
        val downloaded = candidate.signingInfo ?: error("Missing APK signature")
        val currentKeys = installed.apkContentsSigners.map { it.toCharsString() }.toSet()
        val candidateKeys = downloaded.apkContentsSigners.map { it.toCharsString() }.toSet()
        val lineage = downloaded.signingCertificateHistory?.map { it.toCharsString() }.orEmpty()
        validateApkIdentity(
            current = ApkIdentity(current.packageName, current.longVersionCode, current.applicationInfo?.minSdkVersion ?: 0, currentKeys),
            candidate = ApkIdentity(candidate.packageName, candidate.longVersionCode, candidate.applicationInfo?.minSdkVersion ?: 0, candidateKeys, lineage.toSet()),
            manifest = manifest,
        )
    }

    override suspend fun launch(activity: Activity, manifest: UpdateManifest, file: File): Boolean {
        verify(manifest, file)
        return withContext(Dispatchers.Main.immediate) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
                false
            } else {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                activity.startActivity(
                    Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                )
                true
            }
        }
    }
}
