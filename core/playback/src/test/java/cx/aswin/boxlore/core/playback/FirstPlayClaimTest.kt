package cx.aswin.boxlore.core.playback

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** `first_episode_played` is claimed once per install, from the player. */
class FirstPlayClaimTest {
    private class MemoryStore(var claimed: Boolean = false, var updated: Boolean? = null) : FirstPlayStore {
        override fun isClaimed() = claimed

        override fun markClaimed() {
            claimed = true
        }

        override fun updatedFromOlderBuild() = updated

        override fun setUpdatedFromOlderBuild(updated: Boolean) {
            this.updated = updated
        }
    }

    @Test
    fun `the first play claims once and later plays never do`() = runTest {
        val store = MemoryStore()
        val claim = FirstPlayClaim(store, isUpdatedInstall = { false }) { false }

        assertTrue(claim.claim())
        assertFalse(claim.claim())
        assertTrue(store.claimed)
    }

    @Test
    fun `plays starting together still claim once`() = runTest {
        val claim = FirstPlayClaim(MemoryStore(), isUpdatedInstall = { false }) { false }

        val results = List(5) { async { claim.claim() } }.awaitAll()

        assertEquals(1, results.count { it })
    }

    @Test
    fun `an updated install that already sent it from the screen stays quiet`() = runTest {
        val store = MemoryStore()
        val claim = FirstPlayClaim(store, isUpdatedInstall = { true }) { true }

        assertFalse(claim.claim())
        assertTrue(store.claimed)
    }

    @Test
    fun `a restored phone updated before its first listen still sends it`() = runTest {
        // Regression: the restored older-build flag and the later update together hid the new phone's first play.
        val store = MemoryStore()
        FirstPlayClaim.noteInstallOrigin(store) { false }
        val claim = FirstPlayClaim(store, isUpdatedInstall = { true }) { true }

        assertTrue(claim.claim())
    }

    @Test
    fun `the install origin is noted once`() {
        val store = MemoryStore()
        FirstPlayClaim.noteInstallOrigin(store) { true }
        FirstPlayClaim.noteInstallOrigin(store) { false }

        assertEquals(true, store.updated)
    }

    @Test
    fun `a failed legacy read still claims`() = runTest {
        val claim = FirstPlayClaim(MemoryStore(), isUpdatedInstall = { true }) { error("datastore unavailable") }

        assertTrue(claim.claim())
    }
}
