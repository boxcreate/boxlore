package cx.aswin.boxlore.updates

import android.app.Activity
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UpdateSessionTest {
    @Test fun `prepared updater screen and verified file restore across a process restart`() = runTest {
        val manifest = updateManifest()
        val store = MemoryUpdateStore().apply { cachedManifest = manifest }
        val session = MemoryUpdateSessionStore()
        var prepares = 0
        var restores = 0
        val installer = object : ApkInstaller {
            override suspend fun prepare(manifest: UpdateManifest, onState: (ApkInstallState) -> Unit): File {
                prepares++
                return File("private-fixture.apk")
            }
            override suspend fun restore(manifest: UpdateManifest): File {
                restores++
                return File("private-fixture.apk")
            }
            override suspend fun launch(activity: Activity, manifest: UpdateManifest, file: File): Boolean = false
        }
        val first = AppUpdates(UpdateChecker(UpdateSource { UpdateLookup(UpdateOffer(29, "29", manifest)) }, store, 28, 34), false, installer, backgroundScope, session)
        first.open()
        first.download()
        testScheduler.runCurrent()
        assertEquals(manifest.apkSha256, session.preparedChecksum)
        val restored = AppUpdates(UpdateChecker(UpdateSource { UpdateLookup(UpdateOffer(29, "29", manifest)) }, store, 28, 34), false, installer, backgroundScope, session)
        restored.foreground()
        testScheduler.runCurrent()
        assertTrue(restored.visible.value)
        assertEquals(ApkInstallStage.READY, restored.install.value.stage)
        assertEquals(1, prepares)
        assertEquals(1, restores)
        restored.dismiss()
        assertFalse(session.visible)
    }

    @Test fun `a removed cached APK restores the screen without an automatic download`() = runTest {
        val manifest = updateManifest()
        val store = MemoryUpdateStore().apply { cachedManifest = manifest }
        val session = MemoryUpdateSessionStore().apply {
            visible = true
            preparedChecksum = manifest.apkSha256
        }
        var downloads = 0
        val installer = object : ApkInstaller {
            override suspend fun prepare(manifest: UpdateManifest, onState: (ApkInstallState) -> Unit): File {
                downloads++
                return File("fixture")
            }
            override suspend fun restore(manifest: UpdateManifest): File? = null
            override suspend fun launch(activity: Activity, manifest: UpdateManifest, file: File): Boolean = false
        }
        val updates = AppUpdates(UpdateChecker(UpdateSource { UpdateLookup(UpdateOffer(29, "29", manifest)) }, store, 28, 34), false, installer, backgroundScope, session)
        updates.foreground()
        testScheduler.runCurrent()
        assertTrue(updates.visible.value)
        assertEquals(ApkInstallStage.IDLE, updates.install.value.stage)
        assertEquals(null, session.preparedChecksum)
        assertEquals(0, downloads)
    }

    @Test fun `explicit release Download waits for cached file verification before replacing a missing file`() = runTest {
        val manifest = updateManifest()
        val store = MemoryUpdateStore().apply { cachedManifest = manifest }
        val session = MemoryUpdateSessionStore().apply {
            visible = true
            preparedChecksum = manifest.apkSha256
        }
        val restoreDone = CompletableDeferred<Unit>()
        var downloads = 0
        val installer = object : ApkInstaller {
            override suspend fun prepare(manifest: UpdateManifest, onState: (ApkInstallState) -> Unit): File {
                downloads++
                return File("fixture")
            }
            override suspend fun restore(manifest: UpdateManifest): File? {
                restoreDone.await()
                return null
            }
            override suspend fun launch(activity: Activity, manifest: UpdateManifest, file: File): Boolean = false
        }
        val updates = AppUpdates(UpdateChecker(UpdateSource { UpdateLookup(UpdateOffer(29, "29", manifest)) }, store, 28, 34), false, installer, backgroundScope, session)
        updates.openAndDownload()
        testScheduler.runCurrent()
        assertEquals(0, downloads)
        restoreDone.complete(Unit)
        testScheduler.runCurrent()
        assertEquals(1, downloads)
        assertEquals(ApkInstallStage.READY, updates.install.value.stage)
    }

    @Test fun `a changed release cancels stale file restoration instead of leaving verification stuck`() = runTest {
        var manifest = updateManifest()
        val store = MemoryUpdateStore().apply { cachedManifest = manifest }
        val session = MemoryUpdateSessionStore().apply {
            visible = true
            preparedChecksum = manifest.apkSha256
        }
        var cancelled = false
        val installer = object : ApkInstaller {
            override suspend fun prepare(manifest: UpdateManifest, onState: (ApkInstallState) -> Unit): File = error("No automatic download")
            override suspend fun restore(manifest: UpdateManifest): File? {
                try {
                    awaitCancellation()
                } finally {
                    cancelled = true
                }
            }
            override suspend fun launch(activity: Activity, manifest: UpdateManifest, file: File): Boolean = false
        }
        val checker = UpdateChecker(UpdateSource { UpdateLookup(UpdateOffer(manifest.versionCode, manifest.versionName, manifest)) }, store, 28, 34)
        val updates = AppUpdates(checker, false, installer, backgroundScope, session)
        testScheduler.runCurrent()
        assertEquals(ApkInstallStage.VERIFYING, updates.install.value.stage)
        manifest = manifest.copy(versionCode = 30, apkSha256 = "b".repeat(64))
        checker.check(force = true)
        testScheduler.runCurrent()
        assertTrue(cancelled)
        assertEquals(ApkInstallStage.IDLE, updates.install.value.stage)
        assertEquals(null, session.preparedChecksum)
    }

    @Test fun `loopback update is rejected in shipping policy and cannot point outside loopback`() {
        val fixture = updateManifest().copy(testOnly = true, apkUrl = "http://127.0.0.1:8765/candidate.apk", notesUrl = "http://127.0.0.1:8765/notes")
        org.junit.jupiter.api.assertThrows<IllegalArgumentException> { fixture.validate(false) }
        fixture.validate(true)
        org.junit.jupiter.api.assertThrows<IllegalArgumentException> { fixture.copy(apkUrl = "http://example.com:8765/candidate.apk").validate(true) }
    }
}
