package cx.aswin.boxlore.updates

import cx.aswin.boxlore.BuildConfig
import java.net.URI
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val MAX_APK_BYTES = 512L * 1024 * 1024
private val manifestJson = Json { ignoreUnknownKeys = true }

/** Published only after the signed APK has passed release validation. */
@Serializable
data class UpdateManifest(
    val schemaVersion: Int,
    val packageName: String,
    val versionCode: Long,
    val versionName: String,
    val minSdk: Int,
    val apkUrl: String,
    val apkSha256: String,
    val apkBytes: Long,
    val notesUrl: String,
    val notes: String = "",
    val testOnly: Boolean = false,
    val upcoming: String = "",
) {
    fun validate(allowTest: Boolean = BuildConfig.BOXLORE_ISOLATED_TESTS): UpdateManifest {
        require(schemaVersion == 1) { "Unsupported update manifest" }
        require(packageName == "cx.aswin.boxlore" && versionCode > 0 && minSdk >= 31)
        require(versionName.isNotBlank() && versionName.length <= 64)
        require(apkSha256.matches(Regex("[a-fA-F0-9]{64}")))
        require(apkBytes in 1..MAX_APK_BYTES && notes.length <= 24_000 && upcoming.length <= 6_000)
        if (testOnly) {
            require(allowTest) { "Test updates are unavailable in shipping builds" }
            requireTestUpdateUrl(apkUrl, "/candidate.apk")
            requireTestUpdateUrl(notesUrl, "/notes")
        } else {
            requireReleaseUrl(apkUrl, "/download/")
            require(URI(apkUrl).path.endsWith(".apk"))
            requireReleaseUrl(notesUrl, "/tag/")
        }
        return this
    }

    fun encode(): String = manifestJson.encodeToString(serializer(), this)

    companion object {
        fun parse(value: String, allowTest: Boolean = BuildConfig.BOXLORE_ISOLATED_TESTS): UpdateManifest = manifestJson.decodeFromString(serializer(), value).validate(allowTest)
    }
}

private fun requireReleaseUrl(value: String, route: String) {
    val uri = URI(value)
    require(uri.scheme == "https" && uri.host == "github.com" && uri.port == -1)
    require(uri.userInfo == null && uri.query == null && uri.fragment == null)
    require(uri.path.startsWith("/boxcreate/boxlore/releases$route"))
    require(uri.rawPath == uri.path && ".." !in uri.path)
}

data class UpdateOffer(val versionCode: Long, val versionName: String, val manifest: UpdateManifest? = null)

data class UpdateLookup(val offer: UpdateOffer? = null, val incompatible: Boolean = false, val upcoming: String = "")

enum class UpdateCheckStatus { IDLE, CHECKING, CURRENT, AVAILABLE, INCOMPATIBLE, FAILED }

data class UpdateCheckState(val status: UpdateCheckStatus = UpdateCheckStatus.IDLE, val offer: UpdateOffer? = null, val upcoming: String = "") {
    val updateAvailable: Boolean get() = offer != null
}

internal fun requireTestUpdateUrl(value: String, path: String) {
    val uri = URI(value)
    require(uri.scheme == "http" && uri.host == "127.0.0.1" && uri.port == 8765 && uri.path == path)
    require(uri.rawPath == uri.path && uri.query == null && uri.fragment == null && uri.userInfo == null)
}
