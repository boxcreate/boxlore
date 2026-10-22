package cx.aswin.boxlore.core.rss

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLPeerUnverifiedException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FeedReadFailureTest {
    @Test
    fun `gone or refused statuses, missing hosts, failing certificates and non-feeds mean the address is gone`() {
        for (code in listOf(401, 403, 404, 410, 451)) assertTrue(FeedReadFailure.isAddressGone(FeedHttpException(code)), "HTTP $code")
        assertTrue(FeedReadFailure.isAddressGone(UnknownHostException("feeds.example")))
        assertTrue(FeedReadFailure.isAddressGone(SSLPeerUnverifiedException("hostname not verified")))
        assertTrue(FeedReadFailure.isAddressGone(NotAFeedException("URL did not return an RSS or Atom feed")))
        assertTrue(FeedReadFailure.isAddressGone(IllegalStateException("wrapped", FeedHttpException(404))), "found through the cause")
    }

    @Test
    fun `passing trouble keeps the address`() {
        for (code in listOf(429, 500, 502, 503)) assertFalse(FeedReadFailure.isAddressGone(FeedHttpException(code)), "HTTP $code")
        assertFalse(FeedReadFailure.isAddressGone(SocketTimeoutException("slow")))
        assertFalse(FeedReadFailure.isAddressGone(ConnectException("refused")))
        assertFalse(FeedReadFailure.isAddressGone(IOException("connection reset")))
        assertFalse(FeedReadFailure.isAddressGone(IllegalArgumentException("Feed is larger than 50 MB")))
    }

    @Test
    fun `typed feed errors keep their messages and IllegalArgumentException type`() {
        assertEquals("Feed returned HTTP 404", FeedHttpException(404).message)
        assertTrue(FeedHttpException(404) is IllegalArgumentException)
        assertTrue(NotAFeedException("x") is IllegalArgumentException)
    }
}
