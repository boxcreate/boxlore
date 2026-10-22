package cx.aswin.boxlore.core.rss

import java.net.UnknownHostException
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

/** A publisher feed answered with an HTTP error status ([RssFeedClient.fetch]). */
class FeedHttpException(val code: Int) : IllegalArgumentException("Feed returned HTTP $code")

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

    private const val MAX_CAUSE_DEPTH = 6
}

/** The address answered, but not with an RSS or Atom feed (wrong content type, nothing parseable). */
class NotAFeedException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)
