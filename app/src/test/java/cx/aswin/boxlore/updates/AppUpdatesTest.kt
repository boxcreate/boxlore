package cx.aswin.boxlore.updates

import android.app.Activity
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppUpdatesTest {
    @Test fun `ordinary update browsing does not reopen after process restart`() = runTest {
        val checker = UpdateChecker(UpdateSource { UpdateLookup() }, MemoryUpdateStore(), 29, 34)
        val session = MemoryUpdateSessionStore()
        val updates = AppUpdates(checker, false, fakeInstaller { File("fixture") }, backgroundScope, session)
        updates.open()
        testScheduler.runCurrent()
        assertTrue(updates.visible.value)
        assertFalse(session.visible)
        val restarted = AppUpdates(checker, false, fakeInstaller { File("fixture") }, backgroundScope, session)
        testScheduler.runCurrent()
        assertFalse(restarted.visible.value)
    }

    @Test fun `legacy visible flag without a pending update is cleared`() = runTest {
        val checker = UpdateChecker(UpdateSource { UpdateLookup() }, MemoryUpdateStore(), 29, 34)
        val session = MemoryUpdateSessionStore().apply { visible = true }
        val updates = AppUpdates(checker, false, fakeInstaller { File("fixture") }, backgroundScope, session)
        testScheduler.runCurrent()
        assertFalse(updates.visible.value)
        assertFalse(session.visible)
    }

    @Test fun `installed update does not restore its old install page`() = runTest {
        val manifest = updateManifest(29)
        val store = MemoryUpdateStore().apply { cachedManifest = manifest }
        val checker = UpdateChecker(UpdateSource { UpdateLookup() }, store, 29, 34)
        val session = MemoryUpdateSessionStore().apply {
            visible = true
            preparedChecksum = manifest.apkSha256
        }
        val updates = AppUpdates(checker, false, fakeInstaller { File("fixture") }, backgroundScope, session)
        testScheduler.runCurrent()
        assertFalse(updates.visible.value)
        assertFalse(session.visible)
        assertEquals(null, session.preparedChecksum)
    }

    @Test fun `opening a cached available update refreshes latest metadata without downloading`() = runTest {
        var checks = 0
        var downloads = 0
        var latest = updateManifest().copy(versionCode = 30)
        val store = MemoryUpdateStore().apply {
            cachedManifest = updateManifest()
            checkedAt = System.currentTimeMillis()
        }
        val checker = UpdateChecker(
            UpdateSource {
            checks++
            UpdateLookup(UpdateOffer(latest.versionCode, "latest", latest))
        },
            store,
            28,
            34
        )
        val updates = AppUpdates(
            checker,
            false,
            fakeInstaller {
            downloads++
            File("fixture")
        },
            backgroundScope
        )
        updates.open()
        testScheduler.runCurrent()
        assertEquals(30L, checker.state.value.offer?.versionCode)
        assertEquals(1, checks)
        assertEquals(0, downloads)
        updates.dismiss()
        latest = latest.copy(versionCode = 31)
        updates.open()
        testScheduler.runCurrent()
        assertEquals(31L, checker.state.value.offer?.versionCode)
        assertEquals(2, checks)
    }

    @Test fun `announcement download checks metadata once before starting one transfer`() = runTest {
        var checks = 0
        var downloads = 0
        val checker = UpdateChecker(
            UpdateSource {
            checks++
            UpdateLookup(UpdateOffer(29, "29", updateManifest()))
        },
            MemoryUpdateStore(),
            28,
            34
        )
        val updates = AppUpdates(
            checker,
            false,
            fakeInstaller {
            downloads++
            File("fixture")
        },
            backgroundScope
        )
        updates.openAndDownload()
        testScheduler.runCurrent()
        assertEquals(1, checks)
        assertEquals(1, downloads)
    }

    @Test fun `foreground check does not open a prompt or start a download`() = runTest {
        var downloads = 0
        val checker = UpdateChecker(UpdateSource { UpdateLookup(UpdateOffer(29, "0.0.29", updateManifest())) }, MemoryUpdateStore(), 28, 34)
        val updates = AppUpdates(
            checker,
            false,
            fakeInstaller {
            downloads++
            File("fixture")
        },
            backgroundScope
        )
        updates.foreground()
        testScheduler.runCurrent()
        assertTrue(checker.state.value.updateAvailable)
        assertFalse(updates.visible.value)
        assertEquals(0, downloads)
        updates.open()
        assertTrue(updates.visible.value)
        assertEquals(0, downloads)
    }

    @Test fun `only one explicit transfer runs and dismissing the page preserves it`() = runTest {
        val done = CompletableDeferred<Unit>()
        var downloads = 0
        val checker = UpdateChecker(UpdateSource { UpdateLookup(UpdateOffer(29, "0.0.29", updateManifest())) }, MemoryUpdateStore(), 28, 34)
        checker.check()
        val updates = AppUpdates(
            checker,
            false,
            fakeInstaller {
            downloads++
            done.await()
            File("fixture")
        },
            backgroundScope
        )
        updates.open()
        updates.download()
        testScheduler.runCurrent()
        updates.dismiss()
        updates.download()
        testScheduler.runCurrent()
        assertEquals(1, downloads)
        assertFalse(updates.visible.value)
        done.complete(Unit)
        testScheduler.runCurrent()
        assertEquals(ApkInstallStage.READY, updates.install.value.stage)
    }

    @Test fun `failed transfer is retryable without claiming ready to install`() = runTest {
        var attempts = 0
        val checker = UpdateChecker(UpdateSource { UpdateLookup(UpdateOffer(29, "0.0.29", updateManifest())) }, MemoryUpdateStore(), 28, 34)
        checker.check()
        val updates = AppUpdates(
            checker,
            false,
            fakeInstaller {
            if (++attempts == 1) throw IOException("bad transfer")
            File("fixture")
        },
            backgroundScope
        )
        updates.download()
        testScheduler.runCurrent()
        assertEquals(ApkInstallStage.FAILED, updates.install.value.stage)
        updates.download()
        testScheduler.runCurrent()
        assertEquals(ApkInstallStage.READY, updates.install.value.stage)
        assertEquals(2, attempts)
    }

    @Test fun `Play update action never starts an APK transfer`() = runTest {
        var downloads = 0
        val checker = UpdateChecker(UpdateSource { UpdateLookup(UpdateOffer(29, "29")) }, MemoryUpdateStore(), 28, 34)
        checker.check()
        val updates = AppUpdates(
            checker,
            true,
            fakeInstaller {
            downloads++
            File("fixture")
        },
            backgroundScope
        )
        updates.download()
        testScheduler.runCurrent()
        assertEquals(0, downloads)
    }

    @Test fun `changing an offer cancels its transfer and clears a prepared APK`() = runTest {
        var manifest = updateManifest()
        var downloads = 0
        var cancelled = false
        val checker = UpdateChecker(UpdateSource { UpdateLookup(UpdateOffer(manifest.versionCode, manifest.versionName, manifest)) }, MemoryUpdateStore(), 28, 34)
        checker.check()
        val updates = AppUpdates(
            checker,
            false,
            fakeInstaller {
            if (++downloads == 1) {
                try {
                    awaitCancellation()
                } finally {
                    cancelled = true
                }
            }
            File("fixture")
        },
            backgroundScope
        )
        updates.download()
        testScheduler.runCurrent()
        manifest = manifest.copy(versionCode = 30, versionName = "0.0.30", apkSha256 = "b".repeat(64))
        checker.check(force = true)
        testScheduler.runCurrent()
        assertTrue(cancelled)
        assertEquals(ApkInstallStage.IDLE, updates.install.value.stage)
        updates.download()
        testScheduler.runCurrent()
        assertEquals(ApkInstallStage.READY, updates.install.value.stage)
        manifest = manifest.copy(versionCode = 31, versionName = "0.0.31", apkSha256 = "c".repeat(64))
        checker.check(force = true)
        testScheduler.runCurrent()
        assertEquals(ApkInstallStage.IDLE, updates.install.value.stage)
    }

    @Test fun `release Download tap checks then starts transfer while failed checks do not download`() = runTest {
        var offline = false
        var downloads = 0
        val checker = UpdateChecker(UpdateSource { if (offline) error("offline") else UpdateLookup(UpdateOffer(29, "29", updateManifest())) }, MemoryUpdateStore(), 28, 34)
        val updates = AppUpdates(
            checker,
            false,
            fakeInstaller {
            downloads++
            File("fixture")
        },
            backgroundScope
        )
        updates.openAndDownload()
        testScheduler.runCurrent()
        assertTrue(updates.visible.value)
        assertEquals(1, downloads)
        assertEquals(ApkInstallStage.READY, updates.install.value.stage)
        offline = true
        updates.openAndDownload()
        testScheduler.runCurrent()
        assertEquals(1, downloads)
        assertEquals(UpdateCheckStatus.FAILED, checker.state.value.status)
    }

    private fun fakeInstaller(prepare: suspend () -> File) = object : ApkInstaller {
        override suspend fun prepare(manifest: UpdateManifest, onState: (ApkInstallState) -> Unit): File = prepare()
        override suspend fun launch(activity: Activity, manifest: UpdateManifest, file: File): Boolean = true
    }
}
