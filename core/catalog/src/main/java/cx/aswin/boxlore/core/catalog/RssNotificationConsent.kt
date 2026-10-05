package cx.aswin.boxlore.core.catalog

import android.util.AtomicFile
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Properties
import java.util.UUID

/** Disclosure acceptance is device-local and tied to the exact saved feed URL. */
interface RssNotificationConsent {
    val registrationId: String? get() = null
    fun isAccepted(podcastId: String, feedUrl: String): Boolean
    fun accept(podcastId: String, feedUrl: String)
    fun revoke(podcastId: String)

    companion object {
        val NONE = object : RssNotificationConsent {
            override fun isAccepted(podcastId: String, feedUrl: String) = false
            override fun accept(podcastId: String, feedUrl: String) = Unit
            override fun revoke(podcastId: String) = Unit
        }
    }
}

/** Store under Context.noBackupFilesDir; neither Android restore nor cloud sync transfers consent. */
class DeviceRssNotificationConsent internal constructor(private val storage: AtomicFile) : RssNotificationConsent {
    constructor(file: File) : this(AtomicFile(file))

    private val rejectedChanges = mutableSetOf<String>()
    override val registrationId: String? get() = deviceRegistrationId()

    @Synchronized
    private fun deviceRegistrationId(): String? {
        val values = read()
        values.getProperty("__device_registration_id")?.let { return it }
        val id = UUID.randomUUID().toString()
        values.setProperty("__device_registration_id", id)
        return if (writeSafely(values)) id else null
    }

    @Synchronized
    override fun isAccepted(podcastId: String, feedUrl: String): Boolean =
        podcastId !in rejectedChanges && read().getProperty(podcastId) == fingerprint(feedUrl)

    @Synchronized
    override fun accept(podcastId: String, feedUrl: String) {
        val values = read()
        values.setProperty(podcastId, fingerprint(feedUrl))
        if (writeSafely(values)) rejectedChanges.remove(podcastId) else rejectedChanges.add(podcastId)
    }

    @Synchronized
    override fun revoke(podcastId: String) {
        rejectedChanges.add(podcastId)
        val values = read()
        values.remove(podcastId)
        writeSafely(values)
    }

    private fun writeSafely(values: Properties): Boolean = try {
        write(values)
        true
    } catch (_: IOException) {
        // A failed change cannot authorize publication or crash the notification action.
        false
    }

    private fun read(): Properties = Properties().apply {
        try {
            storage.openRead().use { load(it) }
        } catch (_: Exception) {
            // Missing or unreadable consent always fails closed.
        }
    }

    private fun write(values: Properties) {
        val output = storage.startWrite()
        try {
            values.store(output, "RSS notification disclosure v1")
            storage.finishWrite(output)
        } catch (error: Exception) {
            storage.failWrite(output)
            throw error
        }
    }

    private fun fingerprint(feedUrl: String): String = "v1:" + MessageDigest.getInstance("SHA-256")
        .digest(feedUrl.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
