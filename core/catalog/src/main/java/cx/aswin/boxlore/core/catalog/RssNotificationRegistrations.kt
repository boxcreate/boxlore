package cx.aswin.boxlore.core.catalog

import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.util.Properties
import org.json.JSONObject

/** A device-owned publication obligation; only fingerprints, never feed URLs, are persisted. */
data class RssNotificationRegistration(val podcastId: String, val key: String, val topic: String, val feedFingerprint: String) {
    companion object {
        fun scoped(podcastId: String, deviceId: String, feedUrl: String) = RssNotificationRegistration(
            podcastId,
            TrackedPodcastRtdbLogic.registrationKey(podcastId, deviceId, feedUrl),
            TrackedPodcastRtdbLogic.topic(podcastId, feedUrl),
            TrackedPodcastRtdbLogic.feedFingerprint(feedUrl),
        )

        fun legacy(podcastId: String, deviceId: String) = RssNotificationRegistration(
            podcastId,
            TrackedPodcastRtdbLogic.registrationKey(podcastId, deviceId),
            TrackedPodcastRtdbLogic.topic(podcastId),
            "",
        )
    }
}

interface RssNotificationRegistrationStore {
    val legacyMigrationComplete: Boolean
    fun records(): List<RssNotificationRegistration>
    fun remember(record: RssNotificationRegistration): Boolean
    fun forget(record: RssNotificationRegistration): Boolean
    fun completeLegacyMigration(): Boolean
}

/** Production wiring uses the atomic file store; this default keeps repository fakes hermetic. */
class MemoryRssNotificationRegistrations : RssNotificationRegistrationStore {
    private val values = linkedMapOf<String, RssNotificationRegistration>()
    override var legacyMigrationComplete = false
        private set

    @Synchronized override fun records() = values.values.toList()

    @Synchronized override fun remember(record: RssNotificationRegistration): Boolean {
        values[record.key] = record
        return true
    }

    @Synchronized override fun forget(record: RssNotificationRegistration): Boolean {
        values.remove(record.key)
        return true
    }

    @Synchronized override fun completeLegacyMigration(): Boolean {
        legacyMigrationComplete = true
        return true
    }
}

/** Keep in noBackupFilesDir: a restored device must never delete another device's registration. */
class DeviceRssNotificationRegistrations internal constructor(private val storage: AtomicFile) : RssNotificationRegistrationStore {
    constructor(file: File) : this(AtomicFile(file))

    override val legacyMigrationComplete: Boolean
        @Synchronized get() = read().getProperty(MIGRATED) == "true"

    @Synchronized override fun records(): List<RssNotificationRegistration> {
        val values = read()
        return values.stringPropertyNames().filter { it != MIGRATED }.map { key ->
            val data = JSONObject(values.getProperty(key))
            RssNotificationRegistration(data.getString("podcastId"), key, data.getString("topic"), data.getString("fingerprint"))
        }
    }

    @Synchronized override fun remember(record: RssNotificationRegistration): Boolean = change { values ->
        values.setProperty(record.key, JSONObject().put("podcastId", record.podcastId).put("topic", record.topic).put("fingerprint", record.feedFingerprint).toString())
    }

    @Synchronized override fun forget(record: RssNotificationRegistration): Boolean = change { it.remove(record.key) }

    @Synchronized override fun completeLegacyMigration(): Boolean = change { it.setProperty(MIGRATED, "true") }

    private fun read(): Properties = Properties().apply {
        try {
            storage.openRead().use { load(it) }
        } catch (error: FileNotFoundException) {
            if (storage.baseFile.exists()) throw error
        }
    }

    private fun change(edit: (Properties) -> Unit): Boolean = try {
        val values = read()
        edit(values)
        val output = storage.startWrite()
        try {
            values.store(output, "RSS notification registrations v1")
            storage.finishWrite(output)
        } catch (error: Exception) {
            storage.failWrite(output)
            throw error
        }
        true
    } catch (_: Exception) {
        false
    }

    private companion object {
        const val MIGRATED = "__legacy_migrated"
    }
}
