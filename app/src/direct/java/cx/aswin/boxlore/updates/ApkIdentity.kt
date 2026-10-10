package cx.aswin.boxlore.updates

internal data class ApkIdentity(
    val packageName: String,
    val versionCode: Long,
    val minSdk: Int,
    val signers: Set<String>,
    val signingHistory: Set<String> = emptySet(),
)

internal fun validateApkIdentity(current: ApkIdentity, candidate: ApkIdentity, manifest: UpdateManifest) {
    require(candidate.packageName == current.packageName && candidate.packageName == manifest.packageName)
    require(candidate.versionCode == manifest.versionCode && candidate.versionCode > current.versionCode)
    require(candidate.minSdk == manifest.minSdk)
    require(current.signers.isNotEmpty() && candidate.signers.isNotEmpty())
    val matchingKeys = current.signers == candidate.signers
    val verifiedRotation = current.signers.size == 1 && candidate.signers.size == 1 && candidate.signingHistory.containsAll(current.signers)
    require(matchingKeys || verifiedRotation) { "APK signing identity mismatch" }
}
