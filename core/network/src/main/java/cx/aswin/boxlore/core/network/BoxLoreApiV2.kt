package cx.aswin.boxlore.core.network

import cx.aswin.boxlore.core.model.Briefing
import cx.aswin.boxlore.core.network.model.BecauseYouLikeRequest
import cx.aswin.boxlore.core.network.model.BecauseYouLikeResponse
import cx.aswin.boxlore.core.network.model.BootstrapRequest
import cx.aswin.boxlore.core.network.model.BootstrapResponse
import cx.aswin.boxlore.core.network.model.CuratedCuriosityResponseDto
import cx.aswin.boxlore.core.network.model.EpisodesResponse
import cx.aswin.boxlore.core.network.model.RecommendationsV2Request
import cx.aswin.boxlore.core.network.model.RecommendationsV2Response
import cx.aswin.boxlore.core.network.model.SearchResponse
import cx.aswin.boxlore.core.network.model.SimilarEpisodesRequest
import cx.aswin.boxlore.core.network.model.TrendingResponse
import cx.aswin.boxlore.core.network.model.V2EpisodeRegisterRequest
import cx.aswin.boxlore.core.network.model.V2EpisodeRegisterResponse
import cx.aswin.boxlore.core.network.model.V2EpisodeReply
import cx.aswin.boxlore.core.network.model.V2EpisodeSort
import cx.aswin.boxlore.core.network.model.V2EpisodesPageResponse
import cx.aswin.boxlore.core.network.model.V2EpisodesPaging
import cx.aswin.boxlore.core.network.model.V2FeedReportRequest
import cx.aswin.boxlore.core.network.model.V2FeedReportResponse
import cx.aswin.boxlore.core.network.model.V2PodcastResponse
import cx.aswin.boxlore.core.network.model.V2ShowsFeedsRequest
import cx.aswin.boxlore.core.network.model.V2ShowsFeedsResponse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/**
 * The `/v2` routes of the boxlore API, for builds that use boxlore's own episode IDs.
 *
 * Episodes come back under their recipe ID (`id`) with their show (`feedId`), and, where the route
 * sends them, the catalog key and the ID older builds were given (`servedId`); see
 * [cx.aswin.boxlore.core.network.model.v2Identity]. Show IDs are the same numbers as on v1.
 * Discovery routes answer in the v1 response shapes.
 *
 * Paths are relative (`v2/...`), so they resolve under the configured base URL. Requests carry the
 * API session from the shared client; there is no app-key parameter. Build it with
 * [NetworkModule.createBoxLoreApiV2] on the same cached client as [BoxLoreApi].
 *
 * Added in P4 of the own-episode-IDs plan (plan section 9, API contract).
 */
@Suppress("TooManyFunctions") // One Retrofit surface per API version, mirroring BoxLoreApi.
interface BoxLoreApiV2 {

    // --- Shows and their feeds ---

    /** A show's details and the address to read (no Podcast Index call). 404 when the show is unknown. */
    @GET("v2/podcast")
    fun getPodcast(@Query("id") showId: Long): Call<V2PodcastResponse>

    /** The daily address check: each show's current address and episode namespace. */
    @POST("v2/shows/feeds")
    fun getShowFeeds(@Body request: V2ShowsFeedsRequest): Call<V2ShowsFeedsResponse>

    /**
     * The feed document of a show whose address is http-only, read for the phone. This is the only
     * way to read a [cx.aswin.boxlore.core.network.model.V2FeedReadTarget.ViaApi] show: its
     * `transportUrl` needs the API session, so the RSS client never fetches it. Pass the last `ETag`
     * as [ifNoneMatch] to get a 304, which is not a successful Retrofit response: check
     * `code() == 304`. The app keeps its own copy, so the HTTP cache never stores the document. A 404
     * body may carry `fetchUrl` (`not_proxied`).
     */
    @Streaming
    @Headers("Cache-Control: no-store")
    @GET("v2/feed/{id}")
    fun getFeed(
        @Path("id") showId: Long,
        @Header("If-None-Match") ifNoneMatch: String? = null,
    ): Call<ResponseBody>

    /** The phone couldn't read a show's feed: answers with the address the API has now and queues a check. */
    @POST("v2/feed-report")
    fun reportFeed(@Body request: V2FeedReportRequest): Call<V2FeedReportResponse>

    // --- Episodes ---

    /**
     * One episode for an ID-only link. [episodeId] is a stored ID as a decimal string (recipe, served
     * or Podcast Index ID; never a negative `rss:` ID): check it with
     * [cx.aswin.boxlore.core.network.model.V2EpisodeIds.isLookupEpisodeId]. [showId] is its show when
     * known, and must pass [cx.aswin.boxlore.core.network.model.V2EpisodeIds.isEpisodeShowId] (below
     * 2^52); otherwise leave it out. The 200 answer is the episode or [V2EpisodeReply.NeedsFeed]; 404
     * when nothing matches, 400 (permanent) for an ID outside those rules.
     */
    @GET("v2/episode")
    fun getEpisode(
        @Query("id") episodeId: String,
        @Query("show") showId: Long? = null,
    ): Call<V2EpisodeReply>

    /**
     * The last-resort episode list of a show, keyed by recipe ID. The route answers 400 (permanent)
     * unless [showId] is below 2^52, [limit] is 1–1000 and [offset] is 0–10000: check with
     * [cx.aswin.boxlore.core.network.model.V2EpisodesPaging.isValid] first.
     */
    @GET("v2/episodes")
    fun getEpisodes(
        @Query("id") showId: Long,
        @Query("limit") limit: Int = V2EpisodesPaging.DEFAULT_LIMIT,
        @Query("offset") offset: Int = 0,
        @Query("sort") sort: String = V2EpisodeSort.NEWEST,
    ): Call<V2EpisodesPageResponse>

    /** Records an episode before it is shared, so its link resolves for others. Needs an API session. */
    @POST("v2/episodes/register")
    fun registerEpisode(@Body request: V2EpisodeRegisterRequest): Call<V2EpisodeRegisterResponse>

    /** Episodes similar to one episode. On `/v2`, [SimilarEpisodesRequest.id] is that episode's recipe ID as a decimal string. */
    @POST("v2/episodes/similar")
    fun getSimilarEpisodes(@Body request: SimilarEpisodesRequest): Call<EpisodesResponse>

    // --- Search ---

    /** Show search from boxlore's own index ([limit] 1–50). */
    @GET("v2/search")
    fun search(
        @Query("q") query: String,
        @Query("limit") limit: Int? = null,
    ): Call<SearchResponse>

    @GET("v2/search/semantic")
    fun searchSemantic(
        @Query("q") query: String,
        @Query("country") country: String? = "us",
    ): Call<EpisodesResponse>

    // --- Discovery ---

    @GET("v2/trending")
    fun getTrending(
        @Query("country") country: String? = "us",
        @Query("limit") limit: Int? = 50,
        @Query("cat") category: String? = null,
        @Query("offset") offset: Int? = 0,
    ): Call<TrendingResponse>

    @Streaming
    @GET("v2/trending")
    fun getTrendingStream(
        @Query("country") country: String? = "us",
        @Query("limit") limit: Int? = 50,
        @Query("cat") category: String? = null,
        @Query("offset") offset: Int? = 0,
    ): Call<ResponseBody>

    @POST("v2/home/bootstrap")
    fun getHomeBootstrap(
        @Header("X-Device-UUID") deviceUuid: String,
        @Body request: BootstrapRequest,
    ): Call<BootstrapResponse>

    @GET("v2/home/bootstrap")
    fun getHomeBootstrapGet(@Query("country") country: String): Call<BootstrapResponse>

    /** Personalized episodes (the current recommendation algorithm). */
    @POST("v2/recommendations")
    fun getRecommendations(
        @Header("X-Device-UUID") deviceUuid: String,
        @Body request: RecommendationsV2Request,
    ): Call<RecommendationsV2Response>

    @POST("v2/recommendations/because-you-like")
    fun getBecauseYouLike(@Body request: BecauseYouLikeRequest): Call<BecauseYouLikeResponse>

    @GET("v2/curated/vibe")
    fun getCuratedVibe(
        @Query("id") vibeId: String,
        @Query("country") country: String? = null,
        @Query("languages") languages: String? = null,
    ): Call<TrendingResponse>

    @GET("v2/curated/curiosity-v3")
    fun getCuratedCuriosity(
        @Query("page") page: Int? = 1,
        @Query("cb") cacheBuster: String? = null,
    ): Call<CuratedCuriosityResponseDto>

    // --- Briefings ---

    /** A region's briefing; its `chaptersUrl` points at the signed `/v2` chapters link. */
    @GET("v2/briefings/metadata/{region}")
    fun getBriefingMetadata(@Path("region") region: String): Call<Briefing>

    /**
     * One briefing resource ([V2BriefingRoute.path]: chapters, transcript, audio, image), streamed.
     * Prefer the signed links a briefing answer carries; [V2BriefingRoute.url] builds the same path.
     */
    @Streaming
    @GET("v2/briefings/{route}/{region}")
    fun getBriefingResource(
        @Path("route") route: String,
        @Path("region") region: String,
        @Query("d") date: String? = null,
        @Query("v") version: String? = null,
    ): Call<ResponseBody>
}

/**
 * The `/v2/briefings/<route>/<region>` routes. Briefing IDs stay `briefing_*`; only the path moves.
 * Added in P4 of the own-episode-IDs plan (plan section 9).
 */
enum class V2BriefingRoute(val path: String) {
    METADATA("metadata"),
    CHAPTERS("chapters"),
    TRANSCRIPT("transcript"),
    AUDIO("audio"),
    IMAGE("image"),
    ;

    /**
     * Absolute URL of this route for [region] under [baseUrl], with `d` ([date]) and `v` ([version])
     * when given. Segments and parameters are percent-encoded.
     */
    fun url(baseUrl: String, region: String, date: String? = null, version: String? = null): String {
        val builder =
            baseUrl
                .toHttpUrl()
                .newBuilder()
                .addPathSegment("v2")
                .addPathSegment("briefings")
                .addPathSegment(path)
                .addPathSegment(region)
        date?.let { builder.addQueryParameter("d", it) }
        version?.let { builder.addQueryParameter("v", it) }
        return builder.build().toString()
    }
}
