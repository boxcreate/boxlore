package cx.aswin.boxlore.core.network.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/*
 * Wire models for the `/v2` routes ([cx.aswin.boxlore.core.network.BoxLoreApiV2]).
 *
 * Added in P4 of the own-episode-IDs plan (plan section 9, API contract). Episode identity on
 * `/v2` is (show ID, catalog key); `id` on a v2 episode is its recipe ID, and `servedId` is the
 * ID older builds were given for it. Show IDs are the same numbers as on v1.
 *
 * The client JSON has `encodeDefaults` off: a request field equal to its default is not sent.
 * Every request field the server needs is therefore declared without a default.
 */

/**
 * Range checks for identifiers on `/v2` (identity contract: recipe fields and recipe parameters
 * are safe integers in `[2^52, 2^53 − 1]`).
 *
 * Mirrors the range of `EpisodeRecipeId` without depending on it: the network layer only
 * validates numbers and never computes recipe IDs.
 */
object V2EpisodeIds {
    /** Smallest recipe ID, 2^52. */
    const val RECIPE_MIN: Long = 1L shl 52

    /** Largest recipe ID, 2^53 − 1 (`Number.MAX_SAFE_INTEGER`, so the API agrees on it). */
    const val RECIPE_MAX: Long = (1L shl 53) - 1

    /** Largest show ID (and episode namespace) the API accepts: 2^53 − 1. */
    const val SHOW_ID_MAX: Long = (1L shl 53) - 1

    /** True when [id] is in the recipe range. */
    fun isRecipeId(id: Long?): Boolean = id != null && id in RECIPE_MIN..RECIPE_MAX

    /** True when [id] is a positive show ID or episode namespace the API accepts. */
    fun isShowId(id: Long?): Boolean = id != null && id in 1..SHOW_ID_MAX

    /**
     * Strict parse of a stored show ID ("920666") into the number `/v2` routes take. Same rule as the
     * server: canonical decimal digits, no sign, no leading zero, within [SHOW_ID_MAX]. `rss:` shows
     * and anything else return null.
     */
    fun parseShowId(value: String?): Long? {
        if (value == null || !CANONICAL_ID.matches(value)) return null
        return value.toLongOrNull()?.takeIf(::isShowId)
    }

    /**
     * A stored episode ID that `/v2/episode` can look up: a positive decimal string within the safe
     * range (a recipe ID, a served ID or a Podcast Index ID). Negative `rss:` IDs exist only on this
     * phone and are never sent. Legacy IDs stay strings; this only checks them.
     */
    fun isLookupEpisodeId(value: String?): Boolean = value != null && CANONICAL_ID.matches(value) && isShowId(value.toLongOrNull())

    private val CANONICAL_ID = Regex("^[1-9][0-9]{0,15}$")
}

/** `fetchVia` values: read the publisher's address directly, or through `transportUrl`. */
object V2FetchVia {
    const val DIRECT: String = "direct"
    const val PROXY: String = "proxy"
}

/**
 * The address fields every `/v2` show answer carries.
 *
 * - [fetchUrl]: the publisher's address boxlore has for the show (null: keep the saved one).
 * - [fetchVia]: [V2FetchVia.DIRECT] for HTTPS, [V2FetchVia.PROXY] for an http-only address.
 * - [transportUrl]: `/v2/feed/<id>`, set only when [fetchVia] is [V2FetchVia.PROXY].
 * - [feedSource]: where the address came from (`registry:apple`, `catalog`, …).
 */
interface V2FeedAddress {
    val fetchUrl: String?
    val fetchVia: String?
    val transportUrl: String?
    val feedSource: String?

    /**
     * The URL to read the feed document from: [transportUrl] when the address needs the proxy,
     * otherwise [fetchUrl]. Null when the API has no address. Never saved as the show's address.
     */
    fun readUrl(): String? = if (fetchVia == V2FetchVia.PROXY) transportUrl else fetchUrl
}

/** Registry flags for one show. */
@Serializable
data class V2ShowFlags(
    @SerialName("addressRepair")
    val addressRepair: Boolean = false,
    @SerialName("episodeStale")
    val episodeStale: Boolean = false,
)

/**
 * `GET /v2/podcast?id=`: a show's details plus the address to read. [feed] is the v1 `/podcast`
 * shape with `feed.url` set to [fetchUrl]; Podcast Index's address stays in `feed.originalUrl`.
 */
@Serializable
data class V2PodcastResponse(
    @SerialName("status")
    val status: String? = null,
    /** `pidump` or `catalog` (a show added since the weekly dump). */
    @SerialName("source")
    val source: String? = null,
    @SerialName("feed")
    val feed: PodcastFeed? = null,
    @SerialName("fetchUrl")
    override val fetchUrl: String? = null,
    @SerialName("fetchVia")
    override val fetchVia: String? = null,
    @SerialName("transportUrl")
    override val transportUrl: String? = null,
    @SerialName("feedSource")
    override val feedSource: String? = null,
    /** The show's episode namespace; see [validEpNs]. */
    @SerialName("epNs")
    val epNs: Long? = null,
    @SerialName("episodeCount")
    val episodeCount: Long? = null,
    /** Apple's latest episode date for the show, epoch seconds; null when Apple doesn't list it. */
    @SerialName("appleLatestAt")
    val appleLatestAt: Long? = null,
    @SerialName("appleEpisodeCount")
    val appleEpisodeCount: Long? = null,
    @SerialName("flags")
    val flags: V2ShowFlags? = null,
    /** When the registry last confirmed the address, epoch seconds. */
    @SerialName("verifiedAt")
    val verifiedAt: Long? = null,
) : V2FeedAddress {
    /** [epNs] when it is a valid namespace, else null. */
    val validEpNs: Long? get() = epNs?.takeIf(V2EpisodeIds::isShowId)
}

/**
 * `POST /v2/shows/feeds` body: 1 to [MAX_IDS] show IDs. Callers split longer lists.
 */
@Serializable
data class V2ShowsFeedsRequest(
    @SerialName("ids")
    val ids: List<Long>,
) {
    init {
        require(ids.size in 1..MAX_IDS) { "ids must hold 1..$MAX_IDS show IDs, got ${ids.size}" }
        require(ids.all(V2EpisodeIds::isShowId)) { "ids must be show IDs in 1..${V2EpisodeIds.SHOW_ID_MAX}" }
    }

    companion object {
        /** The route's per-request limit. */
        const val MAX_IDS: Int = 500
    }
}

/** `POST /v2/shows/feeds` answer, in request order with duplicates removed. */
@Serializable
data class V2ShowsFeedsResponse(
    /** False when the address registry was unavailable and addresses come from the dump alone. */
    @SerialName("registryAvailable")
    val registryAvailable: Boolean = false,
    @SerialName("results")
    val results: List<V2ShowAddress> = emptyList(),
)

/** One show in [V2ShowsFeedsResponse]. A show the API doesn't know has a null [fetchUrl]: keep the saved address. */
@Serializable
data class V2ShowAddress(
    @SerialName("id")
    val id: Long,
    @SerialName("fetchUrl")
    override val fetchUrl: String? = null,
    @SerialName("fetchVia")
    override val fetchVia: String? = null,
    @SerialName("transportUrl")
    override val transportUrl: String? = null,
    @SerialName("feedSource")
    override val feedSource: String? = null,
    @SerialName("epNs")
    val epNs: Long? = null,
    @SerialName("flags")
    val flags: V2ShowFlags? = null,
    @SerialName("verifiedAt")
    val verifiedAt: Long? = null,
) : V2FeedAddress {
    /** [epNs] when it is a valid namespace, else null. */
    val validEpNs: Long? get() = epNs?.takeIf(V2EpisodeIds::isShowId)
}

/**
 * `POST /v2/feed-report` body: the phone couldn't read show [id]'s feed. [status] is one of the
 * [V2FeedReportStatus] values. No URL is ever sent.
 */
@Serializable
data class V2FeedReportRequest(
    @SerialName("id")
    val id: Long,
    @SerialName("status")
    val status: String,
) {
    init {
        require(V2EpisodeIds.isShowId(id)) { "id must be a show ID" }
        require(V2FeedReportStatus.isValid(status)) { "status must be a V2FeedReportStatus value, got $status" }
    }
}

/** What the phone saw when a feed read failed, as `/v2/feed-report` accepts it. */
object V2FeedReportStatus {
    const val TIMEOUT: String = "timeout"
    const val DNS: String = "dns"
    const val TLS: String = "tls"
    const val PARSE: String = "parse"
    const val NETWORK: String = "network"
    const val OTHER: String = "other"

    /** `http_NNN` for an HTTP status in 100..599. */
    fun http(code: Int): String {
        require(code in HTTP_CODES) { "HTTP status out of range: $code" }
        return "http_$code"
    }

    /** True for a value the route accepts. */
    fun isValid(status: String): Boolean = status in NAMED || HTTP_STATUS.matches(status)

    private val HTTP_CODES = 100..599
    private val NAMED = setOf(TIMEOUT, DNS, TLS, PARSE, NETWORK, OTHER)
    private val HTTP_STATUS = Regex("^http_[1-5][0-9]{2}$")
}

/**
 * `POST /v2/feed-report` answer: whether a re-check was queued, and the address the API has now.
 * When it differs from the one the phone read, read [V2FeedAddress.readUrl] instead.
 */
@Serializable
data class V2FeedReportResponse(
    @SerialName("queued")
    val queued: Boolean = false,
    @SerialName("fetchUrl")
    override val fetchUrl: String? = null,
    @SerialName("fetchVia")
    override val fetchVia: String? = null,
    @SerialName("transportUrl")
    override val transportUrl: String? = null,
    @SerialName("feedSource")
    override val feedSource: String? = null,
) : V2FeedAddress

/**
 * `GET /v2/episode?id=&show=` answer (HTTP 200). A 404 (`not_found`) is an error response and never
 * decodes into this type.
 */
@Serializable(with = V2EpisodeReplySerializer::class)
sealed interface V2EpisodeReply {
    /** The API knows the episode. [episode]'s `id` is its recipe ID; see [EpisodeItem.v2Identity]. */
    @Serializable
    data class Found(
        @SerialName("status")
        val status: String? = null,
        /** `book` or `pi`. */
        @SerialName("source")
        val source: String? = null,
        @SerialName("episode")
        val episode: EpisodeItem,
    ) : V2EpisodeReply

    /**
     * A recipe ID the API can't play: read show [show]'s feed on the phone and match by recipe ID.
     * The address fields are null when the API has no address for the show.
     */
    @Serializable
    data class NeedsFeed(
        @SerialName("show")
        val show: Long,
        @SerialName("fetchUrl")
        override val fetchUrl: String? = null,
        @SerialName("fetchVia")
        override val fetchVia: String? = null,
        @SerialName("transportUrl")
        override val transportUrl: String? = null,
    ) : V2EpisodeReply,
        V2FeedAddress {
        override val feedSource: String? get() = null
    }
}

/** Picks [V2EpisodeReply.NeedsFeed] when the body says `needsFeed: true`, else [V2EpisodeReply.Found]. */
internal object V2EpisodeReplySerializer : JsonContentPolymorphicSerializer<V2EpisodeReply>(V2EpisodeReply::class) {
    override fun selectDeserializer(element: JsonElement): KSerializer<out V2EpisodeReply> {
        val needsFeed = ((element as? JsonObject)?.get("needsFeed") as? JsonPrimitive)?.booleanOrNull == true
        return if (needsFeed) V2EpisodeReply.NeedsFeed.serializer() else V2EpisodeReply.Found.serializer()
    }
}

/**
 * `GET /v2/episodes` answer: the last-resort list when the phone can't read a show's feed.
 * [fallback] is always true; the app shows its short notice whenever it uses this list.
 */
@Serializable
data class V2EpisodesPageResponse(
    @SerialName("status")
    val status: String? = null,
    @SerialName("fallback")
    val fallback: Boolean = false,
    /** `pi`, or `feeds` when Podcast Index failed and boxlore's saved lists answered. */
    @SerialName("source")
    val source: String? = null,
    @SerialName("feedId")
    val feedId: Long? = null,
    @SerialName("items")
    val items: List<EpisodeItem> = emptyList(),
    @SerialName("hasMore")
    val hasMore: Boolean = false,
    @SerialName("offset")
    val offset: Int = 0,
    @SerialName("limit")
    val limit: Int = 0,
)

/** `sort` values for `GET /v2/episodes`. */
object V2EpisodeSort {
    const val NEWEST: String = "newest"
    const val OLDEST: String = "oldest"
}

/**
 * `POST /v2/episodes/register` body: an episode about to be shared, as read from show [show]'s feed.
 * At least one of [guid] and [enclosureUrl] is needed; a null one is left out of the JSON.
 */
@Serializable
data class V2EpisodeRegisterRequest(
    @SerialName("show")
    val show: Long,
    @SerialName("guid")
    val guid: String? = null,
    @SerialName("enclosureUrl")
    val enclosureUrl: String? = null,
) {
    init {
        require(show in 1 until V2EpisodeIds.RECIPE_MIN) { "show must be a show ID below 2^52" }
        require(!guid.isNullOrBlank() || !enclosureUrl.isNullOrBlank()) { "guid or enclosureUrl is required" }
    }
}

/**
 * `POST /v2/episodes/register` answer. [confirmed] is true when the API now serves the episode
 * (under [servedId] when Podcast Index lists it, else [recipeId]); false when it couldn't find the
 * episode in the show's feed or list, so nothing was written.
 */
@Serializable
data class V2EpisodeRegisterResponse(
    @SerialName("recipeId")
    val recipeId: Long? = null,
    @SerialName("servedId")
    val servedId: Long? = null,
    @SerialName("confirmed")
    val confirmed: Boolean = false,
    @SerialName("show")
    val show: Long? = null,
    @SerialName("catalogKey")
    val catalogKey: String? = null,
) {
    /** [recipeId] when it is in the recipe range, else null. */
    val validRecipeId: Long? get() = recipeId?.takeIf(V2EpisodeIds::isRecipeId)
}

/**
 * The identity of an episode from a `/v2` answer.
 *
 * - [recipeId]: the episode's recipe ID (its `id` on `/v2`).
 * - [showId]: its show (`feedId`).
 * - [catalogKey]: the trimmed GUID or enclosure URL, when the route sends it.
 * - [servedId]: the ID older builds were given for it, as a decimal string, when known.
 */
data class V2EpisodeIdentity(
    val recipeId: Long,
    val showId: Long,
    val catalogKey: String?,
    val servedId: String?,
)

/**
 * This item's identity when it came from a `/v2` route, or null when its `id` is not a recipe ID or
 * it has no valid show. Only meaningful for `/v2` answers: on v1, `id` may be a served ID that only
 * looks like a recipe ID.
 */
fun EpisodeItem.v2Identity(): V2EpisodeIdentity? {
    if (!V2EpisodeIds.isRecipeId(id) || !V2EpisodeIds.isShowId(feedId)) return null
    return V2EpisodeIdentity(
        recipeId = id,
        showId = requireNotNull(feedId),
        catalogKey = catalogKey?.takeIf(String::isNotEmpty),
        servedId = servedId?.takeIf { it > 0L }?.toString(),
    )
}
