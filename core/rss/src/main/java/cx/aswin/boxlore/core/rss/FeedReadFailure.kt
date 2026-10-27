package cx.aswin.boxlore.core.rss

import java.io.IOException
import java.io.InterruptedIOException
import java.net.UnknownHostException
import java.security.cert.CertificateException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

/** A publisher feed answered with an HTTP error status ([RssFeedClient.fetch]). */
class FeedHttpException(val code: Int) : IllegalArgumentException("Feed returned HTTP $code")

/**
 * A feed document over the size cap ([RssFeedClient.readFeedBody]). An [IllegalArgumentException],
 * as the cap's `require` threw before P4 PR 16, so callers that caught that still do.
 */
class FeedTooLargeException(maxBytes: Long) : IllegalArgumentException("Feed is larger than ${maxBytes / (1024 * 1024)} MB")

/**
 * Whether a failed publisher-feed read says the saved address no longer works, so it's worth asking
 * the server for a newer one: gone or refused (401, 403, 404, 410, 451), no such host, a certificate
 * that fails, or a body that isn't a feed. Timeouts, server errors, 429 and dropped connections are
 * passing trouble and keep the address.
 */
internal object FeedReadFailure {
    private val GONE_STATUSES = setOf(401, 403, 404, 410, 451)

    fun isAddressGone(error: Throwable): Boolean {
        var cause: Throwable? = error
        var depth = 0
        while (cause != null && depth < MAX_CAUSE_DEPTH) {
            when (cause) {
                is FeedHttpException -> return cause.code in GONE_STATUSES
                is UnknownHostException, is SSLPeerUnverifiedException, is SSLHandshakeException, is CertificateException -> return true
                is NotAFeedException -> return true
            }
            cause = cause.cause
            depth++
        }
        return false
    }

    /**
     * What a failed read saw, as `/v2/feed-report` takes it: `http_<status>`, `timeout`, `dns`,
     * `tls`, `parse`, `network` (the connection failed or dropped) or `other`. The first cause that
     * says something wins.
     *
     * Added in P4 PR 14 of the own-episode-IDs plan (plan section 11, followed shows: reports).
     */
    fun reportStatus(error: Throwable): String {
        var cause: Throwable? = error
        var depth = 0
        while (cause != null && depth < MAX_CAUSE_DEPTH) {
            statusOf(cause)?.let { return it }
            cause = cause.cause
            depth++
        }
        return OTHER
    }

    private fun statusOf(cause: Throwable): String? = when (cause) {
        is FeedHttpException -> if (cause.code in HTTP_CODES) "http_${cause.code}" else OTHER
        is UnknownHostException -> "dns"
        is InterruptedIOException -> "timeout"
        is SSLException, is CertificateException -> "tls"
        is NotAFeedException -> PARSE
        is IOException -> "network"
        else -> null
    }

    /** The `/v2/feed-report` status of a read whose answer held nothing readable as episodes. */
    const val PARSE = "parse"

    private const val OTHER = "other"
    private val HTTP_CODES = 100..599
    private const val MAX_CAUSE_DEPTH = 6
}

/** The address answered, but not with an RSS or Atom feed (wrong content type, nothing parseable). */
class NotAFeedException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)
