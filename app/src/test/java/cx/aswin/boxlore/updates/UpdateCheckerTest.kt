package cx.aswin.boxlore.updates

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal fun updateManifest(code: Long = 29): UpdateManifest = UpdateManifest(
    schemaVersion = 1,
    packageName = "cx.aswin.boxlore",
    versionCode = code,
    versionName = "0.0.$code",
    minSdk = 31,
    apkUrl = "https://github.com/boxcreate/boxlore/releases/download/v0.0.$code/boxlore.apk",
    apkSha256 = "a".repeat(64),
    apkBytes = 12,
    notesUrl = "https://github.com/boxcreate/boxlore/releases/tag/v0.0.$code",
    notes = "Improved playback",
)

internal class MemoryUpdateStore : UpdateCheckStore {
    override var checkedAt = 0L
    override var attemptedAt = 0L
    override var cachedManifest: UpdateManifest? = null
    override var cachedPlayVersion = 0L
    override var upcoming = ""
}

class UpdateCheckerTest {
    @Test fun `restored timestamp without a restored result cannot leave checker idle forever`() = runTest {
        val store = MemoryUpdateStore().apply {
            checkedAt = 100_000_000L
            attemptedAt = checkedAt
        }
        var lookups = 0
        val checker = UpdateChecker(
            UpdateSource {
            lookups++
            UpdateLookup()
        },
            store,
            29,
            34
        ) { 100_000_001L }
        assertEquals(UpdateCheckStatus.IDLE, checker.state.value.status)
        checker.check()
        assertEquals(1, lookups)
        assertEquals(UpdateCheckStatus.CURRENT, checker.state.value.status)
        checker.check()
        assertEquals(1, lookups)
    }

    @Test fun `unreleased preview is retained without offering a download and survives failed checks`() = runTest {
        val store = MemoryUpdateStore()
        var fail = false
        val checker = UpdateChecker(
            UpdateSource {
            if (fail) error("offline")
            UpdateLookup(upcoming = "- Clearer library controls")
        },
            store,
            29,
            34
        )
        checker.check(force = true)
        assertEquals(UpdateCheckStatus.CURRENT, checker.state.value.status)
        assertFalse(checker.state.value.updateAvailable)
        assertEquals("- Clearer library controls", checker.state.value.upcoming)
        fail = true
        checker.check(force = true)
        assertEquals("- Clearer library controls", checker.state.value.upcoming)
        assertEquals("- Clearer library controls", UpdateChecker(UpdateSource { UpdateLookup() }, store, 29, 34).state.value.upcoming)
    }

    private var time = 100_000_000L
    private val store = MemoryUpdateStore()
    private fun checker(source: UpdateSource) = UpdateChecker(source, store, 28, 34) { time }

    @Test fun `same version refresh and older builds never show an update`() = runTest {
        for (code in listOf(27L, 28L)) {
            val checker = checker { UpdateLookup(UpdateOffer(code, "0.0.$code", updateManifest(code))) }
            checker.check(force = true)
            assertEquals(UpdateCheckStatus.CURRENT, checker.state.value.status)
            assertFalse(checker.state.value.updateAvailable)
        }
    }

    @Test fun `compatible newer manifest shows an offer and survives process recreation`() = runTest {
        val manifest = updateManifest()
        val checker = checker { UpdateLookup(UpdateOffer(29, "0.0.29", manifest)) }
        assertFalse(checker.state.value.updateAvailable)
        checker.check()
        assertTrue(checker.state.value.updateAvailable)
        assertEquals(manifest, store.cachedManifest)
        assertTrue(checker { error("No network before foreground") }.state.value.updateAvailable)
        assertFalse(UpdateChecker(UpdateSource { error("unused") }, store, 29, 34).state.value.updateAvailable)
    }

    @Test fun `Play availability survives process recreation during the check interval and clears after installation`() = runTest {
        val checker = checker { UpdateLookup(UpdateOffer(29, "29")) }
        checker.check()
        assertTrue(checker.state.value.updateAvailable)
        val recreated = checker { error("No duplicate Play lookup within the interval") }
        recreated.check()
        assertTrue(recreated.state.value.updateAvailable)
        assertFalse(UpdateChecker(UpdateSource { error("unused") }, store, 29, 34).state.value.updateAvailable)
        val noLongerAvailable = checker { UpdateLookup() }
        noLongerAvailable.check(force = true)
        assertFalse(checker { error("unused") }.state.value.updateAvailable)
    }

    @Test fun `successful checks throttle six hours and manual checks bypass the interval`() = runTest {
        var requests = 0
        val checker = checker {
            requests++
            UpdateLookup()
        }
        checker.check()
        repeat(4) {
            time += 60_000
            checker.check()
        }
        assertEquals(1, requests)
        checker.check(force = true)
        assertEquals(2, requests)
        time += UpdateChecker.CHECK_INTERVAL
        checker.check()
        assertEquals(3, requests)
    }

    @Test fun `failure never claims up to date and backs off automatic retries`() = runTest {
        var requests = 0
        val checker = checker {
            requests++
            throw IOException("offline")
        }
        checker.check()
        assertEquals(UpdateCheckStatus.FAILED, checker.state.value.status)
        assertFalse(checker.state.value.updateAvailable)
        checker.check()
        assertEquals(1, requests)
        time += UpdateChecker.RETRY_INTERVAL
        checker.check()
        assertEquals(2, requests)
        checker.check(force = true)
        assertEquals(3, requests)
    }

    @Test fun `offline refresh preserves a previously confirmed update`() = runTest {
        store.cachedManifest = updateManifest()
        val checker = checker { throw IOException("offline") }
        checker.check()
        assertEquals(UpdateCheckStatus.FAILED, checker.state.value.status)
        assertTrue(checker.state.value.updateAvailable)
        assertEquals(updateManifest(), store.cachedManifest)
    }

    @Test fun `incompatible release is distinct from up to date`() = runTest {
        val checker = checker { UpdateLookup(incompatible = true) }
        checker.check()
        assertEquals(UpdateCheckStatus.INCOMPATIBLE, checker.state.value.status)
        assertFalse(checker.state.value.updateAvailable)
    }

    @Test fun `foreground and manual request join an in flight lookup`() = runTest {
        val entered = CompletableDeferred<Unit>()
        val complete = CompletableDeferred<Unit>()
        var requests = 0
        val checker = checker {
            requests++
            entered.complete(Unit)
            complete.await()
            UpdateLookup()
        }
        val first = async { checker.check() }
        entered.await()
        val second = async { checker.check(force = true) }
        testScheduler.runCurrent()
        complete.complete(Unit)
        first.await()
        second.await()
        assertEquals(1, requests)
    }

    @Test fun `clock reset does not suppress checks for a day`() = runTest {
        store.checkedAt = time + 86_400_000
        var requests = 0
        checker {
            requests++
            UpdateLookup()
        }.check()
        assertEquals(1, requests)
    }
}
