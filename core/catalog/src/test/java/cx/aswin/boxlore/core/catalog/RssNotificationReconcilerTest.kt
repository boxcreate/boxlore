package cx.aswin.boxlore.core.catalog

import android.content.Context
import android.util.AtomicFile
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.database.PodcastEntity
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RssNotificationReconcilerTest {
    private val id = "rss:one"
    private val url = "https://publisher.example/accepted"
    private val record = RssNotificationRegistration.scoped(id, "device-a", url)
    private val consent = object : RssNotificationConsent {
        override val registrationId = "device-a"
        override fun isAccepted(podcastId: String, feedUrl: String) = feedUrl == url
        override fun accept(podcastId: String, feedUrl: String) = Unit
        override fun revoke(podcastId: String) = Unit
    }

    @Test fun disabledOrDeletedShowCleansUpAfterRestartWithoutAnyEnabledRows() = runTest {
        for (show in listOf(null, show().copy(notificationsEnabled = false), show().copy(isSubscribed = false))) {
            val file = newFile()
            try {
                assertTrue(DeviceRssNotificationRegistrations(file).remember(record))
                val restarted = DeviceRssNotificationRegistrations(file)
                val remote = FakeRemote()
                assertTrue(RssNotificationReconciler(restarted, { show }, consent, remote).reconcile())
                assertEquals(listOf(record), remote.removed)
                assertTrue(DeviceRssNotificationRegistrations(file).records().isEmpty())
            } finally {
                AtomicFile(file).delete()
            }
        }
    }

    @Test fun remoteDeletionFailureRetainsTheObligationUntilBothOperationsSucceed() = runTest {
        val store = MemoryRssNotificationRegistrations().apply { remember(record) }
        val remote = FakeRemote().apply { failRemoval = true }
        val reconciler = RssNotificationReconciler(store, { null }, consent, remote)
        assertFalse(reconciler.reconcile())
        assertEquals(listOf(record), store.records())
        remote.failRemoval = false
        assertTrue(reconciler.reconcile())
        assertTrue(store.records().isEmpty())
    }

    @Test fun activePublicationKeepsItsCleanupObligationAndUsesTheSavedUrl() = runTest {
        val store = MemoryRssNotificationRegistrations().apply { remember(record) }
        val remote = FakeRemote()
        assertTrue(RssNotificationReconciler(store, { show() }, consent, remote).reconcile())
        assertEquals(listOf(record), store.records())
        assertEquals(url, remote.payloads.single()["feedUrl"])
        assertTrue(remote.removed.isEmpty())
    }

    @Test fun urlChangeOrRevokedConsentRemovesTheOldScopeInsteadOfPublishingIt() = runTest {
        for (pair in listOf(show().copy(feedUrl = "$url?new") to consent, show() to RssNotificationConsent.NONE)) {
            val store = MemoryRssNotificationRegistrations().apply { remember(record) }
            val remote = FakeRemote()
            assertTrue(RssNotificationReconciler(store, { pair.first }, pair.second, remote).reconcile())
            assertEquals(listOf(record), remote.removed)
            assertTrue(remote.payloads.isEmpty())
        }
    }

    @Test fun disableDuringPublicationImmediatelyRemovesTheRegistration() = runTest {
        val store = MemoryRssNotificationRegistrations().apply { remember(record) }
        var current = show()
        val remote = FakeRemote().apply { onPublish = { current = current.copy(notificationsEnabled = false) } }
        assertTrue(RssNotificationReconciler(store, { current }, consent, remote).reconcile())
        assertEquals(listOf(record), remote.removed)
        assertTrue(store.records().isEmpty())
    }

    @Test fun legacyTopicIsRemovedBeforePublishingTheAcceptedScope() = runTest {
        val legacy = RssNotificationRegistration.legacy(id, "device-a")
        val store = MemoryRssNotificationRegistrations().apply {
            remember(record)
            remember(legacy)
        }
        val remote = FakeRemote()
        assertTrue(RssNotificationReconciler(store, { show() }, consent, remote).reconcile())
        assertEquals(listOf("remove:${legacy.key}", "publish:${record.key}"), remote.calls)
        assertEquals(listOf(record), store.records())
    }

    @Test fun cancellationKeepsTheJournalForTheNextWorker() = runTest {
        val store = MemoryRssNotificationRegistrations().apply { remember(record) }
        val remote = object : RssNotificationRemote {
            override suspend fun publish(record: RssNotificationRegistration, payload: Map<String, String>) = Unit
            override suspend fun remove(record: RssNotificationRegistration): Unit = throw CancellationException("stopped")
        }
        try {
            RssNotificationReconciler(store, { null }, consent, remote).reconcile()
            org.junit.Assert.fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertEquals(listOf(record), store.records())
        }
    }

    @Test fun journalStoresOnlyFingerprintsAndRetainsItsMigrationMarkerAfterReopen() {
        val file = newFile()
        try {
            val store = DeviceRssNotificationRegistrations(file)
            assertTrue(store.remember(record))
            assertTrue(store.completeLegacyMigration())
            assertFalse(file.readText().contains(url))
            val restarted = DeviceRssNotificationRegistrations(file)
            assertEquals(listOf(record), restarted.records())
            assertTrue(restarted.legacyMigrationComplete)
        } finally {
            AtomicFile(file).delete()
        }
    }

    @Test fun failedJournalWriteDoesNotClaimToHaveRememberedAPublication() {
        val file = newFile()
        val storage = object : AtomicFile(file) {
            override fun startWrite(): FileOutputStream = throw IOException("disk full")
        }
        try {
            val store = DeviceRssNotificationRegistrations(storage)
            assertFalse(store.remember(record))
            assertTrue(store.records().isEmpty())
        } finally {
            storage.delete()
        }
    }

    @Test fun publicationTimeoutRetainsTheObligationAndCanRecoverOnTheNextPass() = runTest {
        val store = MemoryRssNotificationRegistrations().apply { remember(record) }
        val stalled = object : RssNotificationRemote {
            override suspend fun publish(record: RssNotificationRegistration, payload: Map<String, String>) = kotlinx.coroutines.delay(31_000)
            override suspend fun remove(record: RssNotificationRegistration) = Unit
        }
        assertFalse(RssNotificationReconciler(store, { show() }, consent, stalled).reconcile())
        assertEquals(listOf(record), store.records())
        val recovered = FakeRemote()
        assertTrue(RssNotificationReconciler(store, { show() }, consent, recovered).reconcile())
        assertEquals(url, recovered.payloads.single()["feedUrl"])
    }

    @Test fun unreadableJournalCannotBeOverwrittenToAuthorizeANewPublication() {
        val file = newFile()
        try {
            file.writeText("broken=\\uZZZZ")
            val before = file.readText()
            assertFalse(DeviceRssNotificationRegistrations(file).remember(record))
            assertEquals(before, file.readText())
        } finally {
            AtomicFile(file).delete()
        }
    }

    @Test fun cleanupKeepsTheTrackedNodeAndDropsOnlyItsFeedUrlBecauseTheRulesRefuseNodeDeletes() = runTest {
        val store = MemoryRssNotificationRegistrations().apply { remember(record) }
        val firebase = RulesFirebase()
        val remote = FirebaseRssNotificationRemote(firebase)
        assertTrue(RssNotificationReconciler(store, { show() }, consent, remote).reconcile())
        assertEquals(url, firebase.nodes.getValue(record.key)["feedUrl"])
        assertEquals(setOf(record.topic), firebase.topics)

        assertTrue(RssNotificationReconciler(store, { show().copy(notificationsEnabled = false) }, consent, remote).reconcile())
        assertEquals(mapOf("title" to "Show", "imageUrl" to ""), firebase.nodes[record.key])
        assertTrue(firebase.topics.isEmpty())
        assertTrue(store.records().isEmpty())
    }

    @Test fun cleanupOfAKeyThatWasNeverPublishedStillLeavesItsTopicAndClearsTheJournal() = runTest {
        val legacy = RssNotificationRegistration.legacy(id, "device-a")
        val store = MemoryRssNotificationRegistrations().apply { remember(legacy) }
        val firebase = RulesFirebase().apply { topics += legacy.topic }
        assertTrue(RssNotificationReconciler(store, { show() }, consent, FirebaseRssNotificationRemote(firebase)).reconcile())
        assertTrue(firebase.nodes.isEmpty())
        assertTrue(firebase.topics.isEmpty())
        assertTrue(store.records().isEmpty())
    }

    @Test fun cleanupFailureOtherThanARulesRefusalKeepsTheTopicAndTheObligation() = runTest {
        val store = MemoryRssNotificationRegistrations().apply { remember(record) }
        val firebase = RulesFirebase().apply {
            topics += record.topic
            deleteFailure = IOException("offline")
        }
        val reconciler = RssNotificationReconciler(store, { null }, consent, FirebaseRssNotificationRemote(firebase))
        assertFalse(reconciler.reconcile())
        assertEquals(setOf(record.topic), firebase.topics)
        assertEquals(listOf(record), store.records())
        firebase.deleteFailure = null
        assertTrue(reconciler.reconcile())
        assertTrue(firebase.topics.isEmpty())
        assertTrue(store.records().isEmpty())
    }

    private fun show() = PodcastEntity(id, "Show", "Author", "", "", isSubscribed = true, notificationsEnabled = true, feedUrl = url)
    private fun newFile() = File(ApplicationProvider.getApplicationContext<Context>().noBackupFilesDir, "rss-journal-test-${System.nanoTime()}")

    private class FakeRemote : RssNotificationRemote {
        val removed = mutableListOf<RssNotificationRegistration>()
        val payloads = mutableListOf<Map<String, String>>()
        val calls = mutableListOf<String>()
        var failRemoval = false
        var onPublish: () -> Unit = {}
        override suspend fun publish(record: RssNotificationRegistration, payload: Map<String, String>) {
            calls += "publish:${record.key}"
            payloads += payload
            onPublish()
        }
        override suspend fun remove(record: RssNotificationRegistration) {
            calls += "remove:${record.key}"
            if (failRemoval) throw IOException("FCM unsubscribe failed after RTDB acknowledgement")
            removed += record
        }
    }

    /** Applies the deployed `tracked_podcasts/$podcastId` write rule: a write must leave the node with at least one child. */
    private class RulesFirebase : TrackedPodcastFirebase {
        val nodes = mutableMapOf<String, Map<String, String>>()
        val topics = mutableSetOf<String>()
        var deleteFailure: Exception? = null

        override suspend fun set(key: String, payload: Map<String, String>) {
            if (payload.isEmpty()) throw TrackedPodcastWriteRefused()
            nodes[key] = payload
        }

        override suspend fun delete(path: String) {
            deleteFailure?.let { throw it }
            val key = path.substringBefore('/')
            val remaining = if ('/' in path) nodes[key].orEmpty() - path.substringAfter('/') else emptyMap()
            if (remaining.isEmpty()) throw TrackedPodcastWriteRefused()
            nodes[key] = remaining
        }

        override suspend fun subscribe(topic: String) {
            topics += topic
        }

        override suspend fun unsubscribe(topic: String) {
            topics -= topic
        }
    }
}
