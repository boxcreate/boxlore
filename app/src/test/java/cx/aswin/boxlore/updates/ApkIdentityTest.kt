package cx.aswin.boxlore.updates

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ApkIdentityTest {
    private val current = ApkIdentity("cx.aswin.boxlore", 28, 31, setOf("trusted"))
    private val candidate = current.copy(versionCode = 29)

    @Test fun `same signing identity and verified single signer rotation accepted`() {
        validateApkIdentity(current, candidate, updateManifest())
        validateApkIdentity(current, candidate.copy(signers = setOf("new"), signingHistory = setOf("trusted", "new")), updateManifest())
    }

    @Test fun `wrong package downgrade unexpected version or missing signer rejected`() {
        val invalid = listOf(
            candidate.copy(packageName = "other.app"),
            candidate.copy(versionCode = 28),
            candidate.copy(versionCode = 30),
            candidate.copy(minSdk = 35),
            candidate.copy(signers = emptySet()),
            candidate.copy(signers = setOf("untrusted")),
            candidate.copy(signers = setOf("untrusted", "trusted"), signingHistory = setOf("trusted")),
        )
        invalid.forEach { apk ->
            assertThrows(IllegalArgumentException::class.java) { validateApkIdentity(current, apk, updateManifest()) }
        }
    }
}
