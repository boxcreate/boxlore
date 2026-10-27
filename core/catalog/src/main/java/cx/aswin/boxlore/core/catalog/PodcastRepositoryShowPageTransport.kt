package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.domain.ports.ShowPageFeedPort
import cx.aswin.boxlore.core.rss.FeedTooLargeException
import cx.aswin.boxlore.core.rss.RssFeedClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.awaitResponse

/**
 * One read of an http-only show's feed through boxlore's transport (`/v2/feed/<id>`), with the
 * copy's ETag ([ifNoneMatch]): the document, read at most [RssFeedClient.readFeedBody]'s cap; a
 * 304; a `404 not_proxied` naming an HTTPS address to read directly; or what failed, as
 * `/v2/feed-report` words it. Network errors propagate to the ladder, which words them.
 *
 * Added in P4 PR 16 of the own-episode-IDs plan (plan section 11, feed-first show page; `V2-ROUTES.md` `/v2/feed/<id>`).
 */
internal suspend fun PodcastRepository.v2Transport(showId: Long, ifNoneMatch: String?): ShowPageFeedPort.Transported {
    val response = apiV2.getFeed(showId, ifNoneMatch).awaitResponse()
    return when {
        response.code() == HTTP_NOT_MODIFIED -> ShowPageFeedPort.Transported.NotModified
        response.isSuccessful -> {
            val body = response.body() ?: return ShowPageFeedPort.Transported.Failed(OTHER_STATUS)
            try {
                ShowPageFeedPort.Transported.Document(
                    bytes = body.use { RssFeedClient.readFeedBody(it.byteStream()) },
                    etag = response.headers()["ETag"],
                )
            } catch (_: FeedTooLargeException) {
                ShowPageFeedPort.Transported.Failed(OTHER_STATUS)
            }
        }
        else -> transportFailure(response.code(), response.errorBody()?.use { it.string() })
    }
}

/** A failed transport answer: a `404 not_proxied` with an HTTPS `fetchUrl` is read directly; anything else is `http_<code>`. */
internal fun transportFailure(code: Int, errorBody: String?): ShowPageFeedPort.Transported {
    if (code == HTTP_NOT_FOUND && errorBody != null) {
        val answer = runCatching { Json.parseToJsonElement(errorBody).jsonObject }.getOrNull()
        val error = answer?.get("error")?.jsonPrimitive?.contentOrNull
        val fetchUrl = TrackedPodcastRtdbLogic.httpsFeedUrl(answer?.get("fetchUrl")?.jsonPrimitive?.contentOrNull)
        if (error == "not_proxied" && fetchUrl != null) return ShowPageFeedPort.Transported.ReadDirect(fetchUrl)
    }
    return ShowPageFeedPort.Transported.Failed(if (code in HTTP_CODES) "http_$code" else OTHER_STATUS)
}

private const val HTTP_NOT_MODIFIED = 304
private const val HTTP_NOT_FOUND = 404
private val HTTP_CODES = 100..599
private const val OTHER_STATUS = "other"
