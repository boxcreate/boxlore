package cx.aswin.boxlore.core.support

/** What the support API allows right now; every value comes from the server. */
data class SupportMeta(
    val canStartNew: Boolean,
    val notice: String?,
    val photosEnabled: Boolean,
    val maxPhotos: Int,
    val maxPhotoBytes: Long,
    val maxBody: Int,
    val pushTopic: String?,
) {
    companion object {
        /** Before the first answer, or when the API is unreachable: text only, nothing switched on. */
        val Offline = SupportMeta(canStartNew = true, notice = null, photosEnabled = false, maxPhotos = 0, maxPhotoBytes = 0, maxBody = 2000, pushTopic = null)
    }
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
)

sealed interface SendResult {
    /** Saved as a conversation; replies will show up in the app. */
    data class Sent(val threadId: String) : SendResult

    /** The support API isn't available (not live yet, or no session): sent through the old form instead. */
    data object SentByEmailForm : SendResult

    /** New conversations are paused, or this phone hit today's limit; [message] comes from the server. */
    data class Refused(val message: String) : SendResult

    data object Failed : SendResult
}
