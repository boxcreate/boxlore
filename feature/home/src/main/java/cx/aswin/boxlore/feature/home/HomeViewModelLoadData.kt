package cx.aswin.boxlore.feature.home

import androidx.lifecycle.viewModelScope
import cx.aswin.boxlore.core.database.toScorable
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.playback.MixtapeEngine
import cx.aswin.boxlore.core.playback.completedEpisodeIds
import cx.aswin.boxlore.core.playback.episodeAliasGroups
import cx.aswin.boxlore.core.playback.resumeSessions
import cx.aswin.boxlore.core.ranking.RankingObjective
import cx.aswin.boxlore.core.ranking.RankingSurface
import cx.aswin.boxlore.feature.home.logic.HomeUiAssemblyLogic
import cx.aswin.boxlore.feature.home.logic.discoverPodcastsExcluding
import cx.aswin.boxlore.feature.home.logic.withCurrentCategoryDiscovery
import cx.aswin.boxlore.feature.home.logic.withSelectedShowFrom
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// from private fun loadData
@OptIn(FlowPreview::class)
internal fun HomeViewModel.loadData() {
    // Curated rows and Picked for you load on their own so their switches never refetch the rest.
    loadEditorialRows()
    loadRecommendations()
    viewModelScope.launch(Dispatchers.Default) {
        homeCacheRestoreGate.awaitRestore()
        // --- BASE DATA FLOW (Restarts when Region or dismissal changes) ---
        combine(
            userPrefs.regionStream,
            clockContextFlow.map { clock -> clock.daypart }.distinctUntilChanged(),
        ) { region, daypart ->
            region to daypart
        }.distinctUntilChanged()
            .collectLatest { (region, daypart) ->
                val regionChanged = cachedRegion != region
                if (regionChanged) {
                    cachedRegion = region
                    cachedForYouTrending = emptyList()
                    cachedHeroItems = emptyList()
                    cachedLatestEpisodes = emptyList()

                    _uiState.update {
                        it.copy(
                            discoverPodcasts = emptyList(),
                            isFilterLoading = true,
                        )
                    }
                }
                activeRegion = region

                val trendingState = MutableStateFlow(cachedForYouTrending)

                // 1. Fast Bootstrap Call (Briefing & Trending); Picked for you waits for it.
                launch {
                    _isTrendingLoaded.value = false
                    try {
                        val bootstrapData =
                            podcastRepository.getHomeBootstrapDataFast(
                                country = region,
                            )

                        // Brief editions load independently of the discovery country. A failed
                        // request with nothing to fall back on keeps the trending Home shows.
                        if (!bootstrapData.failed) trendingState.value = bootstrapData.trending
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        android.util.Log.e("BoxCastTiming", "VM: Fast Bootstrap API load failed", e)
                    } finally {
                        _isTrendingLoaded.value = true
                    }
                }

                val rankingCache = HomeDiscoveryRankingCache()
                val coreSlice =
                    combine(
                        trendingState, // Hot StateFlow — never completes
                        playbackRepository.resumeSessions,
                        subscriptionRepository.subscribedPodcasts,
                        // History keeps an episode stored under several IDs under its owner's ID.
                        combine(playbackRepository.getAllHistory(), playbackRepository.episodeAliasGroups, ::Pair),
                        _resolvedSerialEpisodes,
                    ) { trending, resume, subs, (history, episodeAliases), resolvedSerial ->
                        HomeCoreSlice(trending, resume, subs, history.map { it.toHomeListeningHistoryItem() }, resolvedSerial, history, episodeAliases)
                    }
                val recsSlice =
                    combine(
                        combine(
                            _recommendations,
                            playbackRepository.completedEpisodeIds,
                            _isTrendingLoaded,
                            _isRecommendationsLoaded,
                            userPrefs.hasDismissedHomeImportBannerStream,
                        ) { recommendations, completedEpisodeIds, isTrendingLoaded, isRecommendationsLoaded, hasDismissedImportBanner ->
                            HomeRecsSlice(
                                recommendations,
                                completedEpisodeIds,
                                isTrendingLoaded,
                                isRecommendationsLoaded,
                                hasDismissedImportBanner,
                            )
                        },
                        userPrefs.restartForgottenEpisodesStream,
                    ) { slice, staleRestartEnabled ->
                        slice.copy(staleRestartEnabled = staleRestartEnabled)
                    }
                val briefingSlice =
                    combine(
                        _briefingState,
                        _briefingDismissedDate,
                        _briefingChaptersState,
                        _briefingDismissedForever,
                    ) { briefing, dismissedDate, briefingChapters, dismissedForever ->
                        HomeBriefingSlice(briefing, dismissedDate, briefingChapters, dismissedForever)
                    }
                val becauseYouLikeSlice =
                    combine(
                        _seemsToLikePodcast,
                        _becauseYouLikeRecommendations,
                        _becauseYouLikePodcasts,
                        _isBecauseYouLikeLoading,
                        _isRecommendationsFallback,
                    ) {
                            seemsToLikePodcast,
                            becauseYouLikeRecommendations,
                            becauseYouLikePodcasts,
                            isBecauseYouLikeLoading,
                            isRecommendationsFallback,
                        ->
                        HomeBecauseYouLikeSlice(
                            seemsToLikePodcast,
                            becauseYouLikeRecommendations,
                            becauseYouLikePodcasts,
                            isBecauseYouLikeLoading,
                            isRecommendationsFallback,
                        )
                    }
                combine(
                    combine(coreSlice, recsSlice, briefingSlice, becauseYouLikeSlice) { core, recs, briefing, becauseYouLike ->
                        HomeDataWrapper(
                            trending = core.trending,
                            resume = core.resume,
                            subs = core.subs,
                            history = core.history,
                            scoringHistory = core.scoringHistory,
                            episodeAliases = core.episodeAliases,
                            resolvedSerial = core.resolvedSerial,
                            recommendations = recs.recommendations,
                            completedEpisodeIds = recs.completedEpisodeIds,
                            isTrendingLoaded = recs.isTrendingLoaded,
                            isRecommendationsLoaded = recs.isRecommendationsLoaded,
                            hasDismissedImportBanner = recs.hasDismissedImportBanner,
                            staleRestartEnabled = recs.staleRestartEnabled,
                            briefing = briefing.briefing,
                            briefingChapters = briefing.briefingChapters,
                            briefingDismissedDate = briefing.briefingDismissedDate,
                            briefingDismissedForever = briefing.briefingDismissedForever,
                            seemsToLikePodcast = becauseYouLike.seemsToLikePodcast,
                            becauseYouLikeRecommendations = becauseYouLike.becauseYouLikeRecommendations,
                            becauseYouLikePodcasts = becauseYouLike.becauseYouLikePodcasts,
                            isBecauseYouLikeLoading = becauseYouLike.isBecauseYouLikeLoading,
                            isRecommendationsFallback = becauseYouLike.isRecommendationsFallback,
                        )
                    },
                    _editorialRows,
                    _isEditorialRowsLoading,
                    userPrefs.homePinnedPodcastIdsStream,
                    showsOrderRefreshGeneration,
                ) { wrapper, editorialRows, isEditorialRowsLoading, pinnedIds, refreshGeneration ->
                    wrapper.copy(
                        editorialRows = editorialRows,
                        isEditorialRowsLoading = isEditorialRowsLoading,
                        pinnedPodcastIds = pinnedIds,
                        showsOrderRefreshGeneration = refreshGeneration,
                    )
                }.debounce(100L).collect { wrapper ->
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                        val rankingNowMs = System.currentTimeMillis()
                        val allHistory = wrapper.history
                        val scoringHistory = wrapper.scoringHistory
                        val staleRestartEnabled = wrapper.staleRestartEnabled
                        val subscribedIds = wrapper.subs.map(Podcast::id).toSet()
                        val rankingPlan = yourShowsRanker.plan(
                            subIds = subscribedIds,
                            history = allHistory,
                            staleRestartEnabled = staleRestartEnabled,
                            requestedGeneration = wrapper.showsOrderRefreshGeneration,
                        )
                        val ranked = rankingCache.get(
                            HomeDiscoveryRankingInput(
                                trending = wrapper.trending,
                                recommendations = wrapper.recommendations,
                                rankingPass = rankingPlan.discoveryPass,
                            ),
                        ) { input -> rankDiscovery(input, scoringHistory, subscribedIds) }
                        val trendingList = ranked.trending
                        val rankedRecommendations = ranked.recommendations
                        val resumeList = wrapper.resume
                        val subs = wrapper.subs
                        val resolvedSerial = wrapper.resolvedSerial
                        val completedEpisodeIds = wrapper.completedEpisodeIds

                        // Prompts count only episodes finished here, so a restore or import can't trigger them.
                        val completedCount = allHistory.countFinishedSince(installedAtMs)

                        // Check if review prompt should be shown (promoter handoff or milestone-based)
                        if (!_showReviewPrompt.value && !_showFeedback.value && !_showPostReview.value) {
                            val isPlayingNow = playerState.value.isPlaying
                            if (engagementCoordinator.shouldShowPromoterReview(isPlayingNow, installedAtMs)) {
                                // Recorded once the sheet is on screen (onReviewPromptDisplayed).
                                engagementCoordinator.reserveSessionForPrompt()
                                reviewPromptRecorder.decided(
                                    ReviewPromptRecord(
                                        promptType = "promoter_review",
                                        source = "nps_handoff",
                                        npsScore = userPrefs.npsLastScore(),
                                        isPromoterHandoff = true,
                                    ),
                                )
                                _reviewPromptVariant.value =
                                    cx.aswin.boxlore.feature.home.components.ReviewPromptVariant.PromoterHandoff
                                _showReviewPrompt.value = true
                            } else if (engagementCoordinator.canShowProactivePrompt(isPlayingNow)) {
                                userPrefs.syncReviewMilestonePending(completedCount)
                                val shouldPrompt = userPrefs.shouldShowReviewPrompt(isPlayingNow, installedAtMs)
                                if (shouldPrompt) {
                                    engagementCoordinator.reserveSessionForPrompt()
                                    reviewPromptRecorder.decided(
                                        ReviewPromptRecord(
                                            promptType = "milestone_review",
                                            source = "episode_milestone",
                                            completedEpisodes = completedCount,
                                        ),
                                    )
                                    _reviewPromptVariant.value =
                                        cx.aswin.boxlore.feature.home.components.ReviewPromptVariant.Milestone
                                    _showReviewPrompt.value = true
                                }
                            }
                        }

                        // NPS survey: mark eligible once the user reaches 3+ completed episodes.
                        // The trigger event fires on the next app open (see MainActivity)
                        // so it never interrupts background playback.
                        if (completedCount >= 3 && !userPrefs.hasNpsSurveyFired() && !userPrefs.isNpsSurveyPending()) {
                            userPrefs.markNpsSurveyPending(completedCount)
                        }

                        val podScoresMap =
                            if (rankingPlan.needsScores) {
                                adaptiveScorer.scorePodcasts(
                                    podcasts = wrapper.subs.map { it.toScorable() },
                                    history = scoringHistory,
                                    objective = RankingObjective.YOUR_SHOWS,
                                    surface = RankingSurface.HOME,
                                    nowMs = rankingNowMs,
                                )
                            } else {
                                emptyMap()
                            }

                        val assembled =
                            HomeUiAssemblyLogic.assemble(
                                trendingList = trendingList,
                                rankedRecommendations = rankedRecommendations,
                                resumeList = resumeList,
                                subs = subs,
                                allHistory = allHistory,
                                episodeAliases = wrapper.episodeAliases,
                                installedAtMs = installedAtMs,
                                resolvedSerial = resolvedSerial,
                                completedEpisodeIds = completedEpisodeIds,
                                region = region,
                                editorialRows = wrapper.editorialRows,
                                previousStableOrder = rankingPlan.previousOrder,
                                podcastScores = podScoresMap,
                                previousMixtape = rankingPlan.previousMixtape,
                                buildMixtape = { scores, recommendations ->
                                    MixtapeEngine.build(
                                        subscriptions = subs,
                                        history = scoringHistory,
                                        resolvedSerialEpisodes = _resolvedSerialEpisodes.value,
                                        recommendations = recommendations,
                                        podcastScores = scores,
                                        adaptiveRanking =
                                        MixtapeEngine.AdaptiveRanking(
                                            scorer = adaptiveScorer,
                                            surface = RankingSurface.HOME,
                                        ),
                                        staleRestartEnabled = staleRestartEnabled,
                                    )
                                },
                                isTrendingLoaded = wrapper.isTrendingLoaded,
                                hasDismissedImportBanner = wrapper.hasDismissedImportBanner,
                                rawBriefing = wrapper.briefing,
                                rawBriefingChapters = wrapper.briefingChapters,
                                briefingDismissedDate = wrapper.briefingDismissedDate,
                                briefingDismissedForever = wrapper.briefingDismissedForever,
                                staleRestartEnabled = staleRestartEnabled,
                                nowMs = rankingNowMs,
                                pinnedPodcastIds = wrapper.pinnedPodcastIds,
                                refreshShowsOrderFromScores = rankingPlan.refreshOrder,
                            )

                        yourShowsRanker.commit(rankingPlan, assembled.stablePodcastOrder, assembled.mixtapeCache, rankingNowMs)
                        assembled.mixtapeCache?.let { currentUnplayedEpisodes = it.episodes }

                        if (assembled.shouldUpdateForYouCache) {
                            cachedRegion = region
                            cachedForYouTrending = trendingList
                            cachedHeroItems = assembled.heroItems
                            cachedLatestEpisodes = assembled.latestEpisodes
                        }

                        val nextState =
                            HomeUiState(
                                heroItems = assembled.heroItems,
                                latestEpisodes = assembled.latestEpisodes,
                                unplayedEpisodeCount = assembled.unplayedEpisodeCount,
                                completedEpisodeCount = assembled.completedEpisodeCount,
                                subscribedPodcasts = assembled.subscribedPodcasts,
                                selectedCategory = _selectedCategory.value,
                                discoveryGreeting =
                                discoveryGreetingFor(
                                    daypart = daypart,
                                    date = clockContextFlow.value.date,
                                ),
                                discoverPodcasts = assembled.discoverPodcasts,
                                recommendations = assembled.recommendations,
                                isLoading = assembled.isLoading,
                                isFilterLoading = assembled.isFilterLoading,
                                isError = false,
                                episodePlaybackState = assembled.episodePlaybackState,
                                softExpireProgressEpisodeIds = assembled.softExpireProgressEpisodeIds,
                                showImportBanner = assembled.showImportBanner,
                                briefing = assembled.briefing,
                                briefingChapters = assembled.briefingChapters,
                                isRecommendationsLoading = !wrapper.isRecommendationsLoaded,
                                seemsToLikePodcast = wrapper.seemsToLikePodcast,
                                becauseYouLikeRecommendations = wrapper.becauseYouLikeRecommendations,
                                becauseYouLikePodcasts = wrapper.becauseYouLikePodcasts,
                                isBecauseYouLikeLoading = wrapper.isBecauseYouLikeLoading,
                                isRecommendationsFallback = wrapper.isRecommendationsFallback,
                                editorialRows = wrapper.editorialRows,
                                isEditorialRowsLoading = wrapper.isEditorialRowsLoading,
                                pinnedPodcastIds = wrapper.pinnedPodcastIds.toSet(),
                            )
                        _uiState.update { previous -> nextState.withCurrentCategoryDiscovery(previous).withSelectedShowFrom(previous) }
                    }
                }
            }
    }

    // --- CATEGORY OBSERVER (Considers Region) ---
    viewModelScope.launch {
        combine(_selectedCategory, userPrefs.regionStream) { category, region ->
            category to region
        }.collectLatest { (category, region) ->
            if (category == null) {
                // "For You" - use cached data instantly if it matches current region
                if (cachedRegion == region && cachedHeroItems.isNotEmpty()) {
                    val discover =
                        discoverPodcastsExcluding(
                            trending = cachedForYouTrending,
                            heroItems = cachedHeroItems,
                            editorialRows = _editorialRows.value,
                        ).orEmpty()

                    _uiState.update {
                        it.copy(
                            selectedCategory = null,
                            discoverPodcasts = discover,
                            isFilterLoading = false,
                        )
                    }
                } else {
                    // Region has changed or cache is empty / stale, so clear discover list and wait for load
                    _uiState.update {
                        it.copy(
                            selectedCategory = null,
                            discoverPodcasts = emptyList(),
                            isFilterLoading = true,
                        )
                    }
                }
            } else {
                // Category selected
                _uiState.update {
                    it.copy(
                        isFilterLoading = true,
                        selectedCategory = category,
                        discoverPodcasts = emptyList(),
                    )
                }

                try {
                    android.util.Log.d("HomeViewModel", "Category: Fetching '$category' for region '$region'...")
                    var finalList: List<Podcast> = emptyList()
                    podcastRepository
                        .getTrendingPodcastsStream(region, 50, category.lowercase())
                        .collect { items ->
                            finalList = items
                            _uiState.update {
                                it.copy(
                                    discoverPodcasts = items,
                                    isFilterLoading = items.size < 10,
                                )
                            }
                        }
                    _uiState.update {
                        it.copy(
                            discoverPodcasts = finalList,
                            isFilterLoading = false,
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.e("HomeViewModel", "Category stream error", e)
                    _uiState.update { it.copy(isFilterLoading = false) }
                }
            }
        }
    }
}
