package cx.aswin.boxlore.core.support

/** What the support API allows right now; every value comes from the server. */
data class SupportMeta(
    val canStartNew: Boolean,
    val notice: String?,
    val photosEnabled: Boolean,
    val maxPhotos: Int,
    val maxPhotoBytes: Long,
    val maxBody: Int,
    /** Topics to subscribe to for reply pushes (this install's, and the account's when signed in). */
    val pushTopics: List<String>,
    /** What a message can be about, from the server; empty means the app's own list. */
    val categories: List<SupportCategory> = emptyList(),
) {
    companion object {
        /**
         * Before the first answer, when the API is unreachable, or when support isn't available
         * (messages then go through the old form): text only, nothing switched on.
         */
        val Offline = SupportMeta(canStartNew = true, notice = null, photosEnabled = false, maxPhotos = 0, maxPhotoBytes = 0, maxBody = 2000, pushTopics = emptyList())
    }
}

/** One choice of what a message is about; [label] is shown as is. */
data class SupportCategory(val id: String, val label: String)

sealed interface PhotoUpload {
    data class Uploaded(val id: String) : PhotoUpload

    /** Support isn't available (or photos are off): the message can still go, without photos. */
    data object Unavailable : PhotoUpload

    data object Failed : PhotoUpload
}

/** One message to start a conversation. [photoIds] come from [SupportRepository.uploadPhoto]. */
data class OutgoingFeedback(
    val category: String,
    val body: String,
    val appVersion: String?,
    val device: String?,
    val diagnostics: String?,
    val logs: String?,
    val photoIds: List<String> = emptyList(),
    /** The same key for a retry of the same message, so the API saves it once. */
    val requestId: String? = null,
)

sealed interface SendResult {
    /** Saved as a conversation; replies will show up in the app. */
    data class Sent(val threadId: String) : SendResult

    /** The support API isn't available (not live yet, or this phone can't prove its device key): sent through the old form. */
    data object SentByEmailForm : SendResult

    /** New conversations are paused, or this phone hit today's limit; [message] comes from the server. */
    data class Refused(val message: String) : SendResult

    data object Failed : SendResult
}
