package cx.aswin.boxlore.core.catalog

import com.google.gson.Gson
import cx.aswin.boxlore.core.catalog.content.ContentCatalogSnapshot
import cx.aswin.boxlore.core.catalog.logic.GroupedShowSearchResult
import cx.aswin.boxlore.core.catalog.logic.SemanticSearchGroupedResult
import cx.aswin.boxlore.core.catalog.logic.mergeShowSearchResults
import cx.aswin.boxlore.core.domain.ports.EpisodeSupplementPort
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort
import cx.aswin.boxlore.core.model.Briefing
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.network.BoxLoreApi
import cx.aswin.boxlore.core.network.BoxLoreApiV2
import cx.aswin.boxlore.core.network.NetworkModule
import cx.aswin.boxlore.core.network.model.CuratedCuriosityResponseDto
import cx.aswin.boxlore.core.prefs.PrefsFileMigrator
import cx.aswin.boxlore.core.rss.RssPodcastRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import retrofit2.awaitResponse

/**
 * Repository for podcast data via Boxlore API (Cloudflare Worker → Podcast Index).
 */
@Suppress("LongParameterList", "kotlin:S107") // The last parameters are optional test seams and rollout switches.
class PodcastRepository(
    private val baseUrl: String,
    val publicKey: String,
    private val context: android.content.Context,
    internal val rssRepository: RssPodcastRepository,
    /**
     * Optional PI episode supplement store (feed-only extras under a PI podcast id).
     * Null in older tests; production wires [cx.aswin.boxlore.core.rss.EpisodeSupplementRepository].
     */
    val episodeSupplementRepository: EpisodeSupplementPort? = null,
    val localEpisodeCatalog: LocalEpisodeCatalogPort? = null,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO,
    /**
     * Optional override for hermetic JVM/MockWebServer tests. Production leaves this null so [api] is
     * built on the one cached OkHttp client from [NetworkModule.createCachedClient] (shared with [apiV2]).
     */
    boxLoreApi: BoxLoreApi? = null,
    /**
     * Off switch for the 1,000-item match list the feed-only extras compare with
     * ([loadPiEpisodesForSupplement]). The app passes a PostHog kill switch; false means the old
     * 100-item page path.
     *
     * LEGACY(own-ids): the extras of the Podcast Index show page. Remove at sunset step S7
     * (MIGRATION.md §6) once show pages read feeds for every listener.
     */
    private val useEpisodeMatchBaseline: () -> Boolean = { true },
    /**
     * Optional `/v2` client override for hermetic JVM/MockWebServer tests. Production leaves this
     * null so [apiV2] is built on the same cached client as [api].
     */
    boxLoreApiV2: BoxLoreApiV2? = null,
    /**
     * The v2 build's rollout switches (feed-first show pages, v2 discovery, address check, upgrade
     * repair), from the app's PostHog flags. Added in P4 (plan section 11, Rollback). Discovery
     * reads [RolloutSwitches.v2Discovery] per request ([usesV2Discovery]) and
     * [RolloutSwitches.feedFirst] for the show-page source ([showPageSource], P4 PR 16).
     */
    val rolloutSwitches: RolloutSwitches = RolloutSwitches(),
    /** Trusted alias reads for links, pushes, shares and Android Auto (P4 PR 13); none in older tests. */
    val episodeAliases: EpisodeAliasReads = EpisodeAliasReads.NONE,
    /** Test seam for the feed read of an episode only its feed lists ([resolveEpisodeLink]). */
    feedReader: EpisodeFeedReader? = null,
    /**
     * Translates episode IDs between the phone's stored IDs and the recipe IDs of the `/v2`
     * discovery routes. Production passes [RoomDiscoveryEpisodeIds]; the default translates nothing.
     * Added in P4 PR 12 (own-IDs plan section 9, v2 discovery routes).
     */
    internal val discoveryEpisodeIds: DiscoveryEpisodeIds = DiscoveryEpisodeIds.Unmapped,
    /**
     * The feed-first show page's stored copies, connectivity, report limits and telemetry. The
     * default stores nothing, so show pages read the server's list (P4 PR 16, plan section 11).
     */
    feedFirstWiring: FeedFirstWiring = FeedFirstWiring(),
    /**
     * Called, before [getPodcastDetails] returns, with each answer for a numeric show ID that names
     * another show ID: Podcast Index renumbered the show. The app records it and moves a follow of
     * the old show to the new one (`RenumberedShowFollows.adoptAnswer`); the default does
     * nothing. A failure is logged and the answer still returned.
     */
    private val onShowRenumbered: suspend (oldId: String, answer: Podcast) -> Unit = { _, _ -> },
) : cx.aswin.boxlore.core.domain.ports.PodcastCatalogPort {
    internal val episodeFeedReader: EpisodeFeedReader by lazy { feedReader ?: RssEpisodeFeedReader(cx.aswin.boxlore.core.rss.RssFeedClient()) { apiV2 } }

    /** The one cached HTTP client both API versions share; created only when an API needs it. */
    private val cachedClient: okhttp3.OkHttpClient by lazy { NetworkModule.createCachedClient(context) }

    val api: BoxLoreApi = boxLoreApi ?: NetworkModule.createBoxLoreApi(baseUrl, cachedClient)

    /**
     * The `/v2` routes, on the same cached client and base URL as [api] (one HTTP cache per process).
     * Built on first use. Added in P4 of the own-episode-IDs plan (plan section 9).
     */
    val apiV2: BoxLoreApiV2 by lazy { boxLoreApiV2 ?: NetworkModule.createBoxLoreApiV2(baseUrl, cachedClient) }

    /**
     * The feed-first show page: reads a show's own feed into the phone's stored copy, with its
     * failure ladder (P4 PR 16, own-IDs plan section 11).
     */
    val feedFirst: FeedFirstShowPages by lazy {
        FeedFirstShowPages(
            copies = feedFirstWiring.copies,
            registry = { showId -> v2ShowAddress(showId) },
            transport = { showId, etag -> v2Transport(showId, etag) },
            reporter =
            FeedReporter(
                send = { request -> FeedReporter.sendWith(apiV2, request) },
                limiter = feedFirstWiring.reportLimiter,
                isOnline = feedFirstWiring.isOnline,
                enabled = rolloutSwitches.addressCheck,
                keepFree = FeedReportLimiter.FOLLOWED_SHOWS_RESERVE,
            ),
            isOnline = feedFirstWiring.isOnline,
            telemetry = feedFirstWiring.telemetry,
            isFollowed = feedFirstWiring.isFollowed,
        )
    }

    /** Last good `/v2` discovery answers of this process (P4 PR 12, plan section 9). */
    internal val v2LastGood = V2DiscoveryLastGood()

    /** Last good `/v2` personal picks, kept across restarts (P4 PR 12, plan section 9; decision D15). */
    internal val recommendationsLastGood =
        RecommendationsLastGood {
            context.applicationContext.getSharedPreferences(RecommendationsLastGood.FILE, android.content.Context.MODE_PRIVATE)
        }

    /**
     * True while discovery uses the `/v2` routes ([RolloutSwitches.v2Discovery]), read per request.
     *
     * LEGACY(own-ids): the v1 discovery fallback. Remove at sunset step S7 (MIGRATION.md §6) once the
     * v1 API client methods are removed.
     */
    internal fun usesV2Discovery(): Boolean = rolloutSwitches.v2Discovery()
    internal val contentCatalogPreferences =
        context.applicationContext.getSharedPreferences(
            "content_catalog_cache",
            android.content.Context.MODE_PRIVATE,
        )

    suspend fun getTrendingPodcasts(country: String = "us", limit: Int = 50, category: String? = null, offset: Int = 0,): List<Podcast> = withContext(Dispatchers.IO) {
        if (usesV2Discovery()) return@withContext v2TrendingPodcasts(country, limit, category, offset)
        // Fallback or non-streaming implementation
        try {
            val response = api.getTrending(publicKey, country, limit, category, offset).execute()
            if (response.isSuccessful && response.body() != null) {
                mapFeedsToPodcasts(response.body()!!.feeds)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getCuratedPodcasts(vibeId: String, country: String? = null, languages: List<String>? = null,): List<Podcast> = withContext(Dispatchers.IO) {
        try {
            val resolvedCountry =
                country?.let {
                    cx.aswin.boxlore.core.model.ContentRegions
                        .canonicalize(it)
                }
            val queryLanguages =
                resolveContentLanguagesForQuery(languages, resolvedCountry ?: "us")
                    .joinToString(",")
            if (usesV2Discovery()) return@withContext v2CuratedPodcasts(vibeId, resolvedCountry, queryLanguages).orEmpty()
            val response =
                api
                    .getCuratedVibe(
                        publicKey,
                        vibeId,
                        resolvedCountry,
                        queryLanguages,
                    ).execute()
            if (response.isSuccessful && response.body() != null) {
                mapFeedsToPodcasts(response.body()!!.feeds)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            android.util.Log.e("BoxCastRepo", "Curated Vibe Error: $vibeId", e)
            emptyList()
        }
    }

    fun getTrendingPodcastsStream(
        country: String = "us",
        limit: Int = 50,
        category: String? = null,
        offset: Int = 0,
    ): kotlinx.coroutines.flow.Flow<List<Podcast>> = trendingPodcastsStream(country, limit, category, offset)

    /**
     * Show search on v1 `/search` (catalog, Apple and Podcast Index results) whatever the v2
     * discovery switch says: shows carry no episode IDs, and `/v2/search` has no Apple or Podcast
     * Index results, so listeners could no longer find shows outside the catalog.
     */
    suspend fun searchPodcastsWithCorrection(query: String): SearchResult = withContext(Dispatchers.IO) {
        try {
            val response = api.search(publicKey, query).execute()
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val podcasts = body.feeds.mapNotNull { mapSearchFeedToPodcast(it) }
                SearchResult(podcasts, null)
            } else {
                SearchResult(emptyList())
            }
        } catch (e: Exception) {
            SearchResult(emptyList())
        }
    }

    suspend fun searchPodcasts(query: String): List<Podcast> = withContext(Dispatchers.IO) {
        searchPodcastsWithCorrection(query).podcasts
    }

    internal suspend fun searchPodcastIndexForOpml(query: String): PodcastIndexSearchResult = withContext(Dispatchers.IO) {
        try {
            val response = api.search(publicKey, query).execute()
            val body = response.body()
            if (response.isSuccessful && body != null) {
                PodcastIndexSearchResult.Success(body.feeds.mapNotNull(::mapSearchFeedToPodcast))
            } else {
                PodcastIndexSearchResult.Failed
            }
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (_: Exception) {
            PodcastIndexSearchResult.Failed
        }
    }

    /** Meili typeahead only (additive endpoint). */
    suspend fun searchPodcastsTypeahead(query: String, limit: Int = 20,): List<Podcast> = withContext(Dispatchers.IO) {
        try {
            val response = api.searchTypeahead(publicKey, query, limit).execute()
            if (response.isSuccessful && response.body() != null) {
                response.body()!!.feeds.mapNotNull { mapSearchFeedToPodcast(it) }
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Progressive show search: Meili typeahead + hybrid `/search` in parallel,
     * grouped as catalog vs also-found (coverage for Meili episode-count shrink).
     * Legacy [searchPodcasts] remains for older call sites / voice.
     */
    suspend fun searchPodcastsGrouped(query: String): GroupedShowSearchResult = withContext(Dispatchers.IO) {
        val cleaned = query.trim()
        if (cleaned.isEmpty()) {
            return@withContext GroupedShowSearchResult(emptyList(), emptyList())
        }
        val typeaheadDeferred = async { searchPodcastsTypeahead(cleaned) }
        val hybridDeferred = async { searchPodcastsWithCorrection(cleaned) }
        val typeahead = typeaheadDeferred.await()
        val hybrid = hybridDeferred.await()
        mergeShowSearchResults(typeahead, hybrid.podcasts)
    }

    internal fun mapSearchFeedToPodcast(feed: cx.aswin.boxlore.core.network.model.SearchFeed): Podcast? {
        val podcastId =
            when {
                feed.id != 0L -> feed.id.toString()
                feed.itunesId != null && feed.itunesId != 0L -> "itunes:${feed.itunesId}"
                !feed.url.isNullOrEmpty() -> "url:${java.net.URLEncoder.encode(feed.url, "UTF-8")}"
                else -> return null
            }
        return Podcast(
            id = podcastId,
            title = feed.title,
            artist = feed.author ?: "Unknown",
            imageUrl = (feed.artwork ?: feed.image).toHttps(),
            description = feed.description,
            genre = resolvePrimaryGenre(feed.categories),
            medium = feed.medium,
            feedUrl = feed.url,
        )
    }

    suspend fun searchEpisodesSemantic(query: String, country: String,): List<Episode> = withContext(Dispatchers.IO) {
        searchSemanticGrouped(query, country).episodes
    }

    /**
     * Concept search: one CF embed on the proxy → Qdrant `podcasts` + `episodes`.
     * [SemanticSearchGroupedResult.podcasts] may be empty on older Worker builds.
     */
    suspend fun searchSemanticGrouped(query: String, country: String,): SemanticSearchGroupedResult = withContext(Dispatchers.IO) {
        if (usesV2Discovery()) return@withContext v2SemanticGrouped(query, country)
        try {
            val response = api.searchSemantic(publicKey, query, country).execute()
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                SemanticSearchGroupedResult(
                    podcasts = body.feeds.mapNotNull { mapSearchFeedToPodcast(it) },
                    episodes = body.items.mapNotNull { mapToEpisode(it) },
                )
            } else {
                SemanticSearchGroupedResult(emptyList(), emptyList())
            }
        } catch (_: Exception) {
            SemanticSearchGroupedResult(emptyList(), emptyList())
        }
    }

    /**
     * In-show search, always on the phone: the RSS table, a followed show's catalog, the feed-first
     * stored copy (the show page completes its archive first), or else the server's list of the
     * show (`/v2/episodes`). Never v1 `episodes/search`, whose answers carry served IDs that would
     * mix with recipe IDs (P4 PR 16, decision D16).
     */
    suspend fun searchEpisodes(feedId: String, query: String,): List<Episode> = withContext(Dispatchers.IO) {
        if (feedId.startsWith("rss:")) {
            return@withContext searchRssEpisodes(feedId, query)
        }
        if (isLocalCatalogReady(feedId)) {
            return@withContext searchLocalCatalog(feedId, query)
        }
        if (showPageSource(feedId) == ShowPageSource.FEED) {
            searchFeedCopy(feedId, query)?.let { return@withContext it }
        }
        searchServerList(feedId, query)
    }

    /**
     * Where [feedId]'s show page reads its episodes: the RSS table, a followed show's complete
     * catalog, the show's own feed (`feed_first` on, a Podcast Index show whose feed was not found
     * unreadable on this page), or the server's list.
     *
     * Added in P4 PR 16 of the own-episode-IDs plan (plan section 11, feed-first show page; decision D1).
     */
    suspend fun showPageSource(feedId: String): ShowPageSource = withContext(Dispatchers.IO) {
        when {
            feedId.startsWith("rss:") -> ShowPageSource.RSS
            isLocalCatalogReady(feedId) -> ShowPageSource.FOLLOWED_CATALOG
            isFeedFirstShow(feedId) && !feedFirst.isInFallback(feedId) -> ShowPageSource.FEED
            else -> ShowPageSource.SERVER_LIST
        }
    }

    /**
     * What the feed-first show page of [feedId] holds as it opens ([FeedFirstShowPages.open]), or
     * null when the page reads another source (`rss:` shows, a followed show's ready catalog,
     * `feed_first` off). A page that opens tries the feed again after an earlier fallback.
     *
     * Added in P4 PR 16 of the own-episode-IDs plan (plan section 11, feed-first show page).
     */
    suspend fun openFeedShowPage(feedId: String, meta: cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort.PodcastMeta,): ShowPageFeedState? =
        withContext(Dispatchers.IO) {
            if (feedId.startsWith("rss:") || !isFeedFirstShow(feedId) || isLocalCatalogReady(feedId)) return@withContext null
            feedFirst.open(feedId, meta)
        }

    /**
     * Reads [feedId]'s feed into its stored copy, [scope] deep, down the failure ladder
     * ([FeedFirstShowPages.read]), then drops cached pages of the show so the next page reads the
     * new copy, or the server's list after a fallback.
     *
     * Added in P4 PR 16 of the own-episode-IDs plan (plan section 11, feed-first show page).
     */
    suspend fun readShowPageFeed(
        feedId: String,
        meta: cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort.PodcastMeta,
        scope: cx.aswin.boxlore.core.domain.ports.ShowPageFeedPort.Scope,
        savedFeedUrl: String?,
    ): ShowPageFeedRead = withContext(Dispatchers.IO) {
        feedFirst.read(feedId, meta, scope, savedFeedUrl).also { invalidateEpisodesCache(feedId) }
    }

    // Opened on first use: the migration reads the prefs file from disk, and this repository is
    // constructed on the main thread during startup (it caused a startup ANR).
    private val playerPrefs by lazy {
        PrefsFileMigrator.open(
            context,
            newName = PrefsFileMigrator.Files.PLAYER,
            oldName = PrefsFileMigrator.LegacyFiles.PLAYER,
        )
    }

    internal fun getOrCreateDeviceUuid(): String {
        val key = "device_uuid"
        var uuid = playerPrefs.getString(key, null)
        if (uuid == null) {
            uuid =
                java.util.UUID
                    .randomUUID()
                    .toString()
            playerPrefs.edit().putString(key, uuid).apply()
        }
        return uuid
    }

    data class EpisodePage(
        val episodes: List<Episode>,
        val hasMore: Boolean,
        /** PI/RSS items in this fetch before supplement extras. Use for pagination offsets. */
        val sourceCount: Int = episodes.size,
    )

    suspend fun getEpisodesPaginated(
        feedId: String,
        limit: Int = 20,
        offset: Int = 0,
        sort: String = "newest",
        /**
         * When false, skip cached feed extras. Use this for Missing-episodes? matching
         * so extras are not treated as Podcast Index rows and wiped on refresh.
         */
        mergeSupplements: Boolean = true,
    ): EpisodePage = withContext(Dispatchers.IO) {
        val safeLimit = clampPageLimit(limit)
        if (feedId.startsWith("rss:")) {
            return@withContext getRssEpisodesPaginated(feedId, safeLimit, offset, sort)
        }
        if (isLocalCatalogReady(feedId)) {
            return@withContext getLocalCatalogPage(feedId, safeLimit, offset, sort)
        }
        getServerListPage(feedId, safeLimit, offset, sort, mergeSupplements)
    }

    /**
     * [getEpisodesPaginated] for the show page itself: the feed-first stored copy when the page
     * reads the feed ([ShowPageSource.FEED]), else the same sources. Only the show page reads the
     * copy, because only it reads the feed again (on open, pull to refresh and archive actions);
     * Smart Queue, Android Auto, Home and downloads keep reading the server's list
     * ([getEpisodesPaginated]), so a copy that stops being read never goes stale under them.
     *
     * Added in P4 PR 16 of the own-episode-IDs plan (plan section 11, feed-first show page; decision D1).
     */
    suspend fun getShowPageEpisodes(
        feedId: String,
        limit: Int = 20,
        offset: Int = 0,
        sort: String = "newest",
    ): EpisodePage = withContext(Dispatchers.IO) {
        if (showPageSource(feedId) == ShowPageSource.FEED) {
            feedCopyPage(feedId, clampPageLimit(limit), offset, sort)?.let { return@withContext it }
        }
        getEpisodesPaginated(feedId, limit, offset, sort)
    }

    /**
     * True when [feedId]'s show page reads its feed first (`feed_first` on, a Podcast Index show),
     * whether or not this page has fallen back: such a page never offers "Missing episodes?".
     *
     * Added in P4 PR 16 of the own-episode-IDs plan (plan section 11, refresh replaces the chip).
     */
    fun isFeedFirstShowPage(feedId: String): Boolean = !feedId.startsWith("rss:") && isFeedFirstShow(feedId)

    /**
     * Drop cached episode pages for [feedId] so the next read hits the network. Pages are cached
     * under the Podcast Index ID a `url:`, `guid:` or `itunes:` ID resolved to, so that ID's pages
     * go too (before P4 PR 16 only pages under [feedId] itself were dropped).
     */
    fun invalidateEpisodesCache(feedId: String) {
        val prefixes = setOfNotNull(feedId, resolvedFeedIds[feedId]).map { "$it|" }
        episodesCache.invalidateIf { key -> prefixes.any(key::startsWith) }
    }

    /**
     * The Podcast Index episodes a show's feed-only extras ([EpisodeSupplementPort]) are compared
     * with, so an episode Podcast Index lists is never stored again as an extra. Throws on
     * HTTP/network failure so [EpisodeSupplementPort.refreshFromFeed] does not persist extras
     * against an empty list (which would treat every feed item as feed-only).
     *
     * Reads up to [MAX_MATCH_BASELINE] newest episodes from `/episodes/match`, match fields only.
     * The full-page path is capped at 100 for memory and returns the oldest 100, so on a show with
     * more episodes the newer ones would count as feed-only and show twice. Falls back to that page
     * when the route fails or the switch ([useEpisodeMatchBaseline]) is off.
     *
     * Only the extras use it. Followed shows' local catalogs no longer match feed items against
     * Podcast Index: new episodes take recipe IDs (P4 PR 9, own-IDs plan section 4).
     *
     * LEGACY(own-ids): the extras of the Podcast Index show page. Remove at sunset step S7
     * (MIGRATION.md §6) once show pages read feeds for every listener.
     */
    suspend fun loadPiEpisodesForSupplement(feedId: String): List<Episode> = withContext(Dispatchers.IO) {
        val resolvedId = resolvePodcastIndexFeedId(feedId)
        if (useEpisodeMatchBaseline()) {
            loadMatchList(resolvedId)?.let { return@withContext it }
        }
        val response = api.getEpisodesPaginated(publicKey, resolvedId, MAX_SAFE_PAGE_LIMIT, 0, "oldest").execute()
        check(response.isSuccessful && response.body() != null) {
            "PI list HTTP ${response.code()}"
        }
        response.body()!!.items.mapNotNull { mapToEpisode(it) }
    }

    private suspend fun getRssEpisodesPaginated(feedId: String, limit: Int, offset: Int, sort: String,): EpisodePage = try {
        val episodes = rssRepository.getEpisodes(feedId, limit, offset, sort)
        val total = rssRepository.episodeCount(feedId)
        EpisodePage(
            episodes = episodes,
            hasMore = offset + episodes.size < total,
        )
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.e("PodcastRepository", "RSS getEpisodesPaginated failed for $feedId", e)
        EpisodePage(emptyList(), false)
    }

    /**
     * v1 `/episodes` pages of a Podcast Index show, merged with its cached feed extras.
     *
     * LEGACY(own-ids): the last rung of the show page's list, used only when `/v2/episodes` fails.
     * Remove at sunset step S7 (MIGRATION.md §6) once `feed_first` has been at 100% for 30 days.
     */
    internal suspend fun getNetworkEpisodesPaginated(
        feedId: String,
        limit: Int,
        offset: Int,
        sort: String,
        mergeSupplements: Boolean,
    ): EpisodePage {
        val resolvedId = resolvePodcastIndexFeedId(feedId)
        rememberResolvedFeedId(feedId, resolvedId)
        val cacheKey = "$resolvedId|$limit|$offset|$sort"
        val cached = episodesCache[cacheKey]
        if (cached != null) {
            android.util.Log.d("PodcastRepository", "Cache HIT for getEpisodesPaginated: $cacheKey")
            return maybeMergeSupplements(resolvedId, cached, offset, sort, mergeSupplements)
        }
        android.util.Log.d("PodcastRepository", "Cache MISS for getEpisodesPaginated: $cacheKey. Fetching from network.")
        return try {
            val response = api.getEpisodesPaginated(publicKey, resolvedId, limit, offset, sort).execute()
            if (response.isSuccessful && response.body() != null) {
                val piItems = response.body()!!.items.mapNotNull { mapToEpisode(it) }
                val page =
                    EpisodePage(
                        episodes = piItems,
                        hasMore = response.body()!!.hasMore,
                        sourceCount = piItems.size,
                    )
                episodesCache[cacheKey] = page
                maybeMergeSupplements(resolvedId, page, offset, sort, mergeSupplements)
            } else {
                maybeMergeSupplements(
                    resolvedId,
                    EpisodePage(emptyList(), false, sourceCount = 0),
                    offset,
                    sort,
                    mergeSupplements,
                )
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            maybeMergeSupplements(
                resolvedId,
                EpisodePage(emptyList(), false, sourceCount = 0),
                offset,
                sort,
                mergeSupplements,
            )
        }
    }

    internal suspend fun maybeMergeSupplements(
        podcastId: String,
        page: EpisodePage,
        offset: Int,
        sort: String,
        mergeSupplements: Boolean,
    ): EpisodePage {
        if (!mergeSupplements) return page
        return mergeCachedSupplementsIntoPage(podcastId, page, offset, sort)
    }

    override suspend fun getPodcastDetails(feedId: String): Podcast? = withContext(Dispatchers.IO) {
        if (feedId.startsWith("rss:")) {
            return@withContext getRssPodcastDetails(feedId)
        }
        getNetworkPodcastDetails(feedId)?.also { answer -> reportRenumbering(feedId, answer) }
    }

    /** Hands an answer for [feedId] that names another numeric show ID to [onShowRenumbered]. */
    private suspend fun reportRenumbering(feedId: String, answer: Podcast) {
        if (!cx.aswin.boxlore.core.rss.RenumberedShowIds.isRenumbering(feedId, answer.id)) return
        try {
            onShowRenumbered(feedId, answer)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("PodcastRepository", "Renumbered show $feedId → ${answer.id} not recorded", e)
        }
    }

    private suspend fun getRssPodcastDetails(feedId: String): Podcast? = try {
        rssRepository.getPodcast(feedId)?.toPodcast()
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.e("PodcastRepository", "RSS getPodcastDetails failed for $feedId", e)
        null
    }

    /** Picks the right lookup parameter (feed URL/guid/iTunes id/id) and executes the request. */
    private fun executeGetPodcastRequest(feedId: String): retrofit2.Response<cx.aswin.boxlore.core.network.model.PodcastResponse> = when {
        feedId.startsWith(FEED_PREFIX_URL) -> {
            val decodedUrl = java.net.URLDecoder.decode(feedId.substringAfter(FEED_PREFIX_URL), "UTF-8")
            api.getPodcast(publicKey = publicKey, feedUrl = decodedUrl).execute()
        }
        feedId.startsWith(FEED_PREFIX_GUID) -> {
            api.getPodcast(publicKey = publicKey, feedGuid = feedId.substringAfter(FEED_PREFIX_GUID)).execute()
        }
        feedId.startsWith(FEED_PREFIX_ITUNES) -> {
            api.getPodcast(publicKey = publicKey, itunesId = feedId.substringAfter(FEED_PREFIX_ITUNES)).execute()
        }
        else -> api.getPodcast(publicKey = publicKey, feedId = feedId).execute()
    }

    private fun mapPodcastResponseFeed(feed: cx.aswin.boxlore.core.network.model.PodcastFeed): Podcast = Podcast(
        id = feed.id.toString(),
        title = feed.title,
        artist = feed.author ?: "Unknown",
        imageUrl = (feed.artwork ?: feed.image).toHttps(),
        type = feed.type ?: "episodic",
        description = feed.description,
        genre = resolvePrimaryGenre(feed.categories),
        // Podcast 2.0
        fundingUrl = feed.funding?.url,
        fundingMessage = feed.funding?.message,
        podcastGuid = feed.podcastGuid,
        medium = feed.medium,
        ownerName = feed.ownerName,
        hasValue = feed.value != null,
        updateFrequency = feed.updateFrequency,
        location = feed.location,
        license = feed.license,
        isLocked = feed.locked == 1,
        feedUrl = feed.url,
    )

    private suspend fun getNetworkPodcastDetails(feedId: String): Podcast? = try {
        val response = executeGetPodcastRequest(feedId)
        val feed = if (response.isSuccessful) response.body()?.feed else null
        feed?.let { mapPodcastResponseFeed(it) }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    internal suspend fun lookupExactPodcastIndex(key: ExactPodcastLookupKey): ExactPodcastLookupResult = withContext(Dispatchers.IO) {
        try {
            val identifier =
                when (key.type) {
                    ExactPodcastLookupType.FEED_URL -> {
                        val encoded = java.net.URLEncoder.encode(key.value, "UTF-8")
                        "$FEED_PREFIX_URL$encoded"
                    }
                    ExactPodcastLookupType.PODCAST_GUID -> "$FEED_PREFIX_GUID${key.value}"
                }
            val response = executeGetPodcastRequest(identifier)
            when {
                response.isSuccessful -> {
                    val feed = response.body()?.feed
                    if (feed == null) {
                        ExactPodcastLookupResult.NotFound
                    } else {
                        ExactPodcastLookupResult.Found(mapPodcastResponseFeed(feed))
                    }
                }
                response.code() == 404 -> ExactPodcastLookupResult.NotFound
                else -> ExactPodcastLookupResult.Failed
            }
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (_: Exception) {
            ExactPodcastLookupResult.Failed
        }
    }

    suspend fun syncSubscriptions(feedIds: List<String>, runPostPersistCallback: Boolean = true): Map<String, Episode> = withContext(Dispatchers.IO) {
        kotlinx.coroutines.coroutineScope {
            val rssTips = async { loadRssSyncTips(feedIds, runPostPersistCallback) }
            val piTips = try {
                val podcastIndexIds = feedIds.filterNot { it.startsWith("rss:") }
                val readyIds = podcastIndexIds.filter { isLocalCatalogReady(it) }.toSet()
                val optedIn = loadOptedInPodcastIds()
                val piIds = podcastIndexIds.filterNot { it in readyIds || it in optedIn }
                fetchPiSyncTips(piIds) + loadLocalCatalogTips(readyIds.toList()) +
                    loadCachedFeedTips(podcastIndexIds.filter { it in optedIn && it !in readyIds })
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (_: Exception) {
                emptyMap()
            }
            piTips + rssTips.await()
        }
    }

    suspend fun getContentCatalog(forceRefresh: Boolean = false): cx.aswin.boxlore.core.catalog.content.ContentCatalogSnapshot? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cached = readCachedContentCatalog(now)
        if (!forceRefresh && cached?.isSupported(now) == true) return@withContext cached
        try {
            val etag = contentCatalogPreferences.getString(CONTENT_CATALOG_ETAG, null)
            val response = api.getContentCatalog(publicKey, etag).execute()
            if (response.code() == 304) {
                contentCatalogPreferences
                    .edit()
                    .putLong(CONTENT_CATALOG_FETCHED_AT, now)
                    .apply()
                return@withContext readCachedContentCatalog(now)
            }
            val body = response.body()
            if (!response.isSuccessful || body == null) return@withContext cached
            val snapshot = body.toContentCatalogSnapshot(now)
            if (!snapshot.isSupported(now)) return@withContext cached
            contentCatalogPreferences
                .edit()
                .putString(CONTENT_CATALOG_JSON, Gson().toJson(body))
                .putString(CONTENT_CATALOG_ETAG, response.headers()["ETag"])
                .putLong(CONTENT_CATALOG_FETCHED_AT, now)
                .apply()
            snapshot
        } catch (error: Exception) {
            android.util.Log.w("PodcastRepository", "Content catalog refresh failed", error)
            cached
        }
    }

    suspend fun getPersonalizedRecommendations(
        history: List<cx.aswin.boxlore.core.network.model.HistoryItem>,
        interests: List<String> = emptyList(),
        country: String? = null,
        subscribedPodcastIds: List<String> = emptyList(),
        subscribedGenres: List<String> = emptyList(),
        languages: List<String>? = null,
    ): List<Episode> = withContext(Dispatchers.IO) {
        if (usesV2Discovery()) {
            val resolvedCountry = country?.let(cx.aswin.boxlore.core.model.ContentRegions::canonicalize) ?: "us"
            // The `/v2` body takes at most four languages and expands `id` itself.
            val bodyLanguages = cx.aswin.boxlore.core.model.ContentRegions.normalizeLanguages(languages.orEmpty(), resolvedCountry)
            return@withContext v2PersonalizedRecommendations(history, interests, resolvedCountry, subscribedPodcastIds, bodyLanguages)
        }
        v1PersonalizedRecommendations(history, interests, country, subscribedPodcastIds, subscribedGenres, languages)
    }

    suspend fun getBecauseYouLikeRecommendations(
        podcastTitle: String,
        podcastDescription: String,
        excludePodcastId: String? = null,
        country: String? = null,
    ): BecauseYouLikeData = withContext(Dispatchers.IO) {
        // `/v2` answers carry recipe IDs, so they never share a cached answer with v1. The switch is
        // read once, so the key and the route always agree.
        val v2 = usesV2Discovery()
        val cacheKey = (if (v2) "v2|" else "") + "byl|$podcastTitle|$excludePodcastId|$country"
        val cached = becauseYouLikeCache[cacheKey]
        if (cached != null) {
            android.util.Log.d("PodcastRepository", "Cache HIT for getBecauseYouLikeRecommendations: key=$cacheKey")
            return@withContext cached
        }

        try {
            val request =
                cx.aswin.boxlore.core.network.model.BecauseYouLikeRequest(
                    podcastTitle = podcastTitle,
                    podcastDescription = podcastDescription,
                    excludePodcastId = excludePodcastId,
                    country = country,
                )
            if (v2) return@withContext v2BecauseYouLike(request, cacheKey)
            val response = api.getBecauseYouLikeRecommendations(publicKey, request).execute()
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val mappedPodcasts = body.podcasts.map { mapToPodcast(it) }
                val mappedEpisodes = body.episodes.mapNotNull { mapToEpisode(it) }
                val data = BecauseYouLikeData(podcasts = mappedPodcasts, episodes = mappedEpisodes)
                becauseYouLikeCache[cacheKey] = data
                data
            } else {
                BecauseYouLikeData()
            }
        } catch (e: Exception) {
            android.util.Log.e("BoxCastRepo", "Because You Like Recommendations Error", e)
            BecauseYouLikeData()
        }
    }

    suspend fun getSimilarEpisodes(query: SimilarEpisodesQuery): List<Episode> = withContext(Dispatchers.IO) {
        try {
            val resolvedCountry =
                query.country?.let {
                    cx.aswin.boxlore.core.model.ContentRegions
                        .canonicalize(it)
                }
                    ?: "us"
            val queryLanguages =
                resolveContentLanguagesForQuery(query.languages, resolvedCountry)
            if (usesV2Discovery()) return@withContext v2SimilarEpisodes(query, resolvedCountry, queryLanguages)
            val request =
                cx.aswin.boxlore.core.network.model.SimilarEpisodesRequest(
                    id =
                    query.episodeId.takeUnless {
                        it.toLongOrNull()?.let { value -> value < 0L } == true
                    } ?: "0",
                    podcastId = query.podcastId.takeUnless { it.startsWith("rss:") } ?: "0",
                    title = query.title,
                    description = query.description,
                    podcastTitle = query.podcastTitle,
                    categories = query.categories,
                    author = query.author,
                    limit = query.limit,
                    country = resolvedCountry,
                    languages = queryLanguages,
                )
            // Cancellable, so a switched-off Try these episodes or a closed page stops the request.
            val response = api.getSimilarEpisodes(publicKey, request).awaitResponse()
            if (response.isSuccessful && response.body() != null) {
                response.body()!!.items.mapNotNull { mapToEpisode(it) }
            } else {
                emptyList()
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("PodcastRepository", "Error getting similar episodes", e)
            emptyList()
        }
    }

    suspend fun submitFeedback(
        category: String,
        message: String,
        appVersion: String,
        email: String? = null,
        diagnostics: String? = null,
        logs: String? = null,
    ): Boolean = withContext(ioDispatcher) {
        try {
            val request =
                cx.aswin.boxlore.core.network.model.FeedbackRequest(
                    category = category,
                    message = message,
                    appVersion = appVersion,
                    email = email,
                    diagnostics = diagnostics,
                    logs = logs,
                )
            val response = api.submitFeedback(publicKey, request).execute()
            response.isSuccessful && response.body()?.success == true
        } catch (e: Exception) {
            android.util.Log.e("BoxCastRepo", "Failed to submit feedback", e)
            false
        }
    }

    suspend fun getPodcastType(feedId: String): String = withContext(Dispatchers.IO) {
        try {
            val response = getPodcastMeta(feedId)
            response?.type ?: "episodic"
        } catch (e: Exception) {
            "episodic"
        }
    }

    suspend fun getPodcastMeta(feedId: String): cx.aswin.boxlore.core.network.model.PodcastMetaResponse? = withContext(Dispatchers.IO) {
        try {
            val response =
                when {
                    feedId.startsWith(FEED_PREFIX_URL) -> {
                        val decodedUrl =
                            java.net.URLDecoder.decode(
                                feedId.substringAfter(FEED_PREFIX_URL),
                                "UTF-8",
                            )
                        api.getPodcastMeta(publicKey = publicKey, feedUrl = decodedUrl).execute()
                    }
                    feedId.startsWith(FEED_PREFIX_GUID) -> {
                        api
                            .getPodcastMeta(
                                publicKey = publicKey,
                                feedGuid = feedId.substringAfter(FEED_PREFIX_GUID),
                            ).execute()
                    }
                    feedId.startsWith(FEED_PREFIX_ITUNES) -> {
                        api
                            .getPodcastMeta(
                                publicKey = publicKey,
                                itunesId = feedId.substringAfter(FEED_PREFIX_ITUNES),
                            ).execute()
                    }
                    else -> api.getPodcastMeta(publicKey = publicKey, feedId = feedId).execute()
                }
            if (response.isSuccessful) response.body() else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getBriefingMetadata(region: String): Briefing? = withContext(Dispatchers.IO) {
        val mappedRegion = mapRegionForBriefing(region)
        if (usesV2Discovery()) return@withContext v2BriefingMetadata(mappedRegion)
        try {
            val response = api.getBriefingMetadata(publicKey, mappedRegion).execute()
            if (response.isSuccessful) {
                response.body()
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("PodcastRepository", "Failed to fetch briefing for $region (mapped: $mappedRegion)", e)
            null
        }
    }

    suspend fun getHomeBootstrapDataFast(country: String): HomeBootstrapData = withContext(Dispatchers.IO) {
        if (usesV2Discovery()) return@withContext v2HomeBootstrapFast(country)
        try {
            val response = api.getHomeBootstrapGet(publicKey, country).execute()
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val trendingList = body.trending.map { mapToPodcast(it) }
                HomeBootstrapData(
                    briefing = body.briefing,
                    briefingChapters = body.briefingChapters,
                    trending = trendingList,
                    curatedVibes = emptyMap(),
                    recommendations = emptyList(),
                    isRecommendationsFallback = body.isRecommendationsFallback ?: true,
                )
            } else {
                HomeBootstrapData(null, emptyList(), emptyList(), emptyMap(), emptyList())
            }
        } catch (e: Exception) {
            android.util.Log.e("PodcastRepository", "getHomeBootstrapDataFast failed", e)
            HomeBootstrapData(null, emptyList(), emptyList(), emptyMap(), emptyList())
        }
    }

    /**
     * Personal picks for Home's Picked for you and Explore's For you. The bootstrap request asks
     * the server for recommendations only, so it skips the Brief, trending and curated rows that
     * those screens load on their own (or not at all, when the listener switched them off).
     */
    suspend fun getHomeRecommendations(
        country: String,
        history: List<cx.aswin.boxlore.core.network.model.HistoryItem> = emptyList(),
        interests: List<String> = emptyList(),
        subscribedPodcastIds: List<String> = emptyList(),
        subscribedGenres: List<String> = emptyList(),
        languages: List<String>? = null,
    ): HomeRecommendations = withContext(Dispatchers.IO) {
        try {
            val resolvedCountry =
                cx.aswin.boxlore.core.model.ContentRegions
                    .canonicalize(country)
            if (usesV2Discovery()) {
                // The `/v2` recommendation body takes at most four languages and expands `id` itself.
                val bodyLanguages = cx.aswin.boxlore.core.model.ContentRegions.normalizeLanguages(languages.orEmpty(), resolvedCountry)
                return@withContext v2HomeRecommendations(resolvedCountry, history, interests, subscribedPodcastIds, bodyLanguages)
            }
            val queryLanguages = resolveContentLanguagesForQuery(languages, resolvedCountry)
            val (podcastIndexHistory, podcastIndexSubscriptionIds) =
                filterToPodcastIndexScope(history, subscribedPodcastIds)
            val boundedInterests = boundedRecommendationInterests(interests)
            val recsReq =
                if (
                    podcastIndexHistory.isNotEmpty() ||
                    boundedInterests.isNotEmpty() ||
                    podcastIndexSubscriptionIds.isNotEmpty()
                ) {
                    cx.aswin.boxlore.core.network.model.RecommendationsRequest(
                        history = podcastIndexHistory,
                        interests = boundedInterests,
                        country = resolvedCountry,
                        subscribedPodcastIds = podcastIndexSubscriptionIds,
                        subscribedGenres = subscribedGenres,
                    )
                } else {
                    null
                }

            val request =
                cx.aswin.boxlore.core.network.model.BootstrapRequest(
                    country = resolvedCountry,
                    vibeIds = emptyList(),
                    deviceUuid = getOrCreateDeviceUuid(),
                    recommendationsRequest = recsReq,
                    languages = queryLanguages,
                    include = listOf(cx.aswin.boxlore.core.network.model.BootstrapRequest.INCLUDE_RECOMMENDATIONS),
                )

            val response = api.getHomeBootstrap(publicKey, getOrCreateDeviceUuid(), request).execute()
            val body = response.body()
            if (response.isSuccessful && body != null) {
                HomeRecommendations(
                    items = body.recommendations.mapNotNull { mapToEpisode(it) },
                    isFallback = body.isRecommendationsFallback ?: true,
                )
            } else {
                HomeRecommendations()
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("PodcastRepository", "getHomeRecommendations failed", e)
            HomeRecommendations()
        }
    }

    suspend fun getCuratedVibes(vibeIds: List<String>, country: String, languages: List<String>? = null,): Map<String, List<Podcast>> = withContext(Dispatchers.IO) {
        val resolvedCountry =
            cx.aswin.boxlore.core.model.ContentRegions
                .canonicalize(country)
        val queryLanguages = resolveContentLanguagesForQuery(languages, resolvedCountry).joinToString(",")
        val result = java.util.concurrent.ConcurrentHashMap<String, List<Podcast>>()
        val v2 = usesV2Discovery()
        vibeIds
            .map { vibeId ->
                async {
                    if (v2) {
                        v2CuratedPodcasts(vibeId, resolvedCountry, queryLanguages)?.let { result[vibeId] = it }
                        return@async
                    }
                    try {
                        val resp = api.getCuratedVibe(publicKey, vibeId, resolvedCountry, queryLanguages).execute()
                        if (resp.isSuccessful && resp.body() != null) {
                            val pods = resp.body()!!.feeds.map { mapToPodcast(it) }
                            result[vibeId] = pods
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("PodcastRepository", "Failed to fetch vibe $vibeId", e)
                    }
                }
            }.awaitAll()
        result
    }

    suspend fun getCuratedCuriosity(page: Int = 1, bypassCache: Boolean = false,): CuratedCuriosityResponseDto? = withContext(Dispatchers.IO) {
        try {
            val cbVal = if (bypassCache) System.currentTimeMillis().toString() else null
            if (usesV2Discovery()) return@withContext v2CuratedCuriosity(page, cbVal)
            val resp = api.getCuratedCuriosity(publicKey, page, cbVal).execute()
            if (resp.isSuccessful) {
                resp.body()
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("PodcastRepository", "Failed to fetch curated curiosity: ${e.message}", e)
            null
        }
    }

    override suspend fun getEpisodes(feedId: String): List<Episode> = getEpisodesImpl(feedId)

    /** Bounded window for Smart Queue / Auto — never materializes a mega-feed. */
    suspend fun getEpisodeWindow(feedId: String, aroundEpisodeId: String? = null, sort: String = "newest",): List<Episode> = withContext(Dispatchers.IO) {
        if (feedId.startsWith("rss:")) {
            return@withContext getRssEpisodeWindow(feedId, aroundEpisodeId)
        }
        if (isLocalCatalogReady(feedId)) {
            return@withContext getLocalCatalogWindow(feedId, sort, aroundEpisodeId)
        }
        // The show page's IDs (P4 PR 16): the feed-first copy around its episode, else /v2/episodes.
        getShowEpisodeWindow(feedId, aroundEpisodeId, sort)
    }

    override suspend fun getEpisode(episodeId: String): Episode? = getEpisodeImpl(episodeId, showId = null)

    /**
     * [getEpisode] for a caller that knows the episode's show ([showId], a Podcast Index show ID).
     * A recipe-range ID that no local row holds is then looked up on `/v2/episode` with its show,
     * so a recipe ID shared by two shows' episodes resolves to the right one.
     *
     * Added in P4 PR 9 (own-IDs plan section 9: recipe IDs resolve through `/v2/episode`, never PI).
     */
    suspend fun getEpisode(episodeId: String, showId: String?,): Episode? = getEpisodeImpl(episodeId, showId)

    companion object {
        internal const val FEED_PREFIX_URL = "url:"
        internal const val FEED_PREFIX_GUID = "guid:"
        internal const val FEED_PREFIX_ITUNES = "itunes:"
        // Catalog caches are endpoint-versioned so an otherwise-valid v1/v2 response cannot
        // suppress the v3 fetch and then invalidate grouped v3 section responses.

        internal val episodesCache = TimedLruCache<String, EpisodePage>(maxEntries = 30, ttlMillis = DEFAULT_EPISODE_CACHE_TTL_MS)

        /** The Podcast Index ID each `url:`, `guid:` or `itunes:` show ID resolved to, for [invalidateEpisodesCache]. */
        internal val resolvedFeedIds = java.util.concurrent.ConcurrentHashMap<String, String>()

        internal fun rememberResolvedFeedId(feedId: String, resolvedId: String) {
            if (feedId != resolvedId) resolvedFeedIds[feedId] = resolvedId
        }
        internal val recommendationsCache = TimedLruCache<String, List<Episode>>(maxEntries = 20, ttlMillis = DEFAULT_RECOMMENDATIONS_CACHE_TTL_MS)
        internal val becauseYouLikeCache = TimedLruCache<String, BecauseYouLikeData>(maxEntries = 20, ttlMillis = DEFAULT_RECOMMENDATIONS_CACHE_TTL_MS)
    }
}
