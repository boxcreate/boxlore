@file:Suppress("LargeClass", "LongParameterList", "TooManyFunctions", "kotlin:S107")

package cx.aswin.boxlore.core.analytics

import android.content.Context
import android.content.SharedPreferences
import com.posthog.PostHog
import com.posthog.PostHogOnFeatureFlags
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.FeedFetchReport
import cx.aswin.boxlore.core.model.FeedUrlCorrection
import cx.aswin.boxlore.core.model.RankingAggregateTelemetry
import cx.aswin.boxlore.core.model.UpgradeRepairReport
import cx.aswin.boxlore.core.prefs.PrefsFileMigrator
import java.time.Instant

object AnalyticsHelper : Analytics {
    @Volatile private var activeSeekSource: String = "scrubber"

    @Volatile private var activePauseReason: String = "user_voluntary"

    /** Wiring tests see every event whatever its level; pass [applyLevels] to test the levels. */
    @androidx.annotation.VisibleForTesting
    fun installRecordingSink(recorder: MutableList<Pair<String, Map<String, Any>>>, applyLevels: Boolean = false,): () -> Unit =
        AnalyticsEmit.installRecordingSink(recorder, applyLevels)

    fun setSeekSource(source: String) {
        activeSeekSource = source
    }

    fun consumeSeekSource(): String {
        val src = activeSeekSource
        activeSeekSource = "scrubber" // Reset to default
        return src
    }

    fun setPauseReason(reason: String) {
        activePauseReason = reason
    }

    fun consumePauseReason(): String {
        val reason = activePauseReason
        activePauseReason = "user_voluntary" // Reset to default
        return reason
    }

    private const val PREFS_NAME = PrefsFileMigrator.Files.ANALYTICS
    private const val KEY_FIRST_LAUNCH = "is_first_launch"
    private const val KEY_SAMPLE_BUCKET = "analytics_sample_bucket"
    private const val KEY_POLICY = "analytics_policy"

    fun deriveGenrePersona(selectedGenres: Set<String>): Map<String, String> = GenrePersonaLogic.deriveGenrePersona(selectedGenres)

    // ── 0. Event levels ────────────────────────────────────────────

    /**
     * Loads this install's sample bucket (picked once, kept across PostHog resets) and the switch
     * remembered from the last launch for this [appVersion]. Call before PostHog setup so the first
     * events already follow it.
     */
    fun initPolicy(context: Context, appVersion: String,) = startPolicy(PrefsPolicyStore(analyticsPrefs(context)), appVersion)

    internal fun startPolicy(store: AnalyticsPolicy.Store, appVersion: String,) {
        policyHome = PolicyHome(store, appVersion)
        AnalyticsPolicy.start(store, appVersion)
    }

    /**
     * Applies the `analytics-policy-v1` flag PostHog holds now and remembers it for the next launch.
     * Runs whenever flags load: PostHog's own callback (startup, `identify`, `reset`) and
     * [reloadFeatureFlags]. Does nothing before [initPolicy].
     */
    fun refreshAnalyticsPolicy() {
        val home = policyHome ?: return
        AnalyticsPolicy.refresh(policyPayload(), home.appVersion, home.store)
    }

    /**
     * `PostHog.reloadFeatureFlags` that also applies a changed analytics switch, then runs [onLoaded].
     * Use it for every explicit reload: PostHog doesn't run its configured flags callback for them.
     * Call once PostHog is set up.
     */
    fun reloadFeatureFlags(onLoaded: () -> Unit = {}) = PostHog.reloadFeatureFlags(flagsReloaded(onLoaded))

    internal fun flagsReloaded(onLoaded: () -> Unit): PostHogOnFeatureFlags =
        PostHogOnFeatureFlags {
            try {
                refreshAnalyticsPolicy()
            } finally {
                onLoaded()
            }
        }

    /** Test hook: the payload PostHog holds for the switch. */
    @Volatile
    internal var policyPayload: () -> Any? = { PostHog.getFeatureFlagPayload(AnalyticsPolicy.FLAG_KEY, null) }

    /** Test hook: no store yet, payload read from PostHog. */
    internal fun resetPolicyForTest() {
        policyHome = null
        policyPayload = { PostHog.getFeatureFlagPayload(AnalyticsPolicy.FLAG_KEY, null) }
    }

    /** Where [initPolicy] keeps the bucket and switch, and the app version they belong to. */
    private class PolicyHome(val store: AnalyticsPolicy.Store, val appVersion: String)

    @Volatile
    private var policyHome: PolicyHome? = null

    private class PrefsPolicyStore(private val prefs: SharedPreferences) : AnalyticsPolicy.Store {
        override fun bucket(): Int? = prefs.getInt(KEY_SAMPLE_BUCKET, -1).takeIf { it >= 0 }

        override fun saveBucket(bucket: Int) = prefs.edit().putInt(KEY_SAMPLE_BUCKET, bucket).apply()

        override fun rules(): String? = prefs.getString(KEY_POLICY, null)

        override fun saveRules(encoded: String) = prefs.edit().putString(KEY_POLICY, encoded).apply()
    }

    private fun analyticsPrefs(context: Context) =
        PrefsFileMigrator.open(
            context,
            newName = PREFS_NAME,
            oldName = PrefsFileMigrator.LegacyFiles.ANALYTICS,
        )

    // ── 1. App Launch ──────────────────────────────────────────────

    override fun trackFirstLaunchIfNecessary(context: Context) {
        val prefs =
            PrefsFileMigrator.open(
                context,
                newName = PREFS_NAME,
                oldName = PrefsFileMigrator.LegacyFiles.ANALYTICS,
            )
        val isFirstLaunch = prefs.getBoolean(KEY_FIRST_LAUNCH, true)

        if (isFirstLaunch) {
            AnalyticsEmit.personSet(
                mapOf(
                    "first_seen_at" to Instant.now().toString(),
                    "onboarding_status" to "pending",
                ),
            )
            prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
        }
        // Open/background volume is SDK-backed (Application Opened / Backgrounded).
        // Do not emit glossary app_open / app_background here.
    }

    /** Person `$set_once` install_channel only — never emit install_attributed volume event. */
    fun trackInstallChannelAttributed(
        installChannel: String,
        referrerRaw: String? = null,
        utmSource: String? = null,
        shareToken: String? = null,
    ) = GrowthSessionAnalyticsTracks.trackInstallChannelAttributed(
        installChannel,
        referrerRaw,
        utmSource,
        shareToken,
    )

    fun trackDeepLinkOpened(
        linkScheme: String,
        isFirstOpen: Boolean,
        linkHost: String? = null,
        contentType: String? = null,
        podcastId: String? = null,
        episodeId: String? = null,
        coldStart: Boolean? = null,
    ) = GrowthSessionAnalyticsTracks.trackDeepLinkOpened(
        linkScheme,
        isFirstOpen,
        linkHost,
        contentType,
        podcastId,
        episodeId,
        coldStart,
    )

    fun trackOnboardingAbandoned(lastStep: String, flowType: String, timeSpentSeconds: Int? = null, subscribedCount: Int? = null,) = GrowthSessionAnalyticsTracks.trackOnboardingAbandoned(
        lastStep,
        flowType,
        timeSpentSeconds,
        subscribedCount,
    )

    fun trackSessionRestorePrompt(action: String, episodeId: String? = null, podcastId: String? = null, positionSeconds: Float? = null,) = GrowthSessionAnalyticsTracks.trackSessionRestorePrompt(
        action,
        episodeId,
        podcastId,
        positionSeconds,
    )

    /**
     * App Check health/adoption. Captured once per launch on builds that ship
     * App Check. `token_obtained` = whether the SDK produced an attestation
     * token; `provider` distinguishes debug vs Play Integrity. App version is
     * attached automatically by PostHog, so adoption can be sliced by build.
     */
    override fun trackAppCheckStatus(tokenObtained: Boolean, provider: String, reason: String?,) {
        AnalyticsEmit.event(
            "app_check_status",
            buildMap<String, Any> {
                put("token_obtained", tokenObtained)
                put("provider", provider)
                reason?.let { put("reason", it.take(60)) }
            },
        )
    }

    /** See [ApiSessionAnalyticsTracks]: session exchanges, renewals and step-ups. */
    fun trackApiSessionStatus(status: ApiSessionStatus) = ApiSessionAnalyticsTracks.trackApiSessionStatus(status)

    override fun trackFirstEpisodePlayed(episodeId: String?, podcastId: String?) {
        AnalyticsEmit.event(
            "first_episode_played",
            buildMap {
                put(
                    "\$set_once",
                    mapOf(
                        "first_episode_played_logged" to true,
                        "first_play_at" to Instant.now().toString(),
                    ),
                )
                episodeId?.takeIf(String::isNotBlank)?.let { put("episode_id", it) }
                podcastId?.takeIf(String::isNotBlank)?.let { put("podcast_id", it) }
            },
        )
    }

    // ── Episode identity (recipe_id beside episode_id) ─────────────

    /**
     * Installs the background lookup of trusted alias recipe IDs ([EpisodeRecipeIdLookup]); `:app`
     * calls it once at startup. Without one, events still carry recipe IDs that are the episode ID
     * itself or that callers [rememberEpisodeRecipeIds]. Phase P4 PR 18.
     */
    fun installEpisodeRecipeIdLookup(lookup: EpisodeRecipeIdLookup?) = EpisodeRecipeIds.install(lookup)

    /** Records recipe IDs a caller already knows (stored episode ID to recipe ID), for later events. Phase P4 PR 18. */
    fun rememberEpisodeRecipeIds(recipeIds: Map<String, Long?>) = EpisodeRecipeIds.remember(recipeIds)

    /** Records the [Episode.recipeId] of each of [episodes] that has one, for later events. Phase P4 PR 18. */
    fun rememberEpisodeRecipeIds(episodes: Iterable<Episode>) = EpisodeRecipeIds.remember(episodes.filter { it.recipeId != null }.associate { it.id to it.recipeId })

    // ── Feedback / NPS ──────────────────────────────────────────────
    // feedback_submitted is only the NPS survey's trigger (its display condition keys off it); prompts
    // that appear are engagement_prompt_shown, hand-offs to Play store_review_opened, and a sent
    // feedback form feedback_sent.

    /** Deferred automatic trigger, fired on app open once the user hits the eligibility milestone. */
    override fun trackSurveyNpsEligible(completedEpisodes: Int?, triggerContext: String,) {
        AnalyticsEmit.event(
            "feedback_submitted",
            buildMap {
                put("feedback_type", "nps_eligible")
                put("source", triggerContext)
                put("trigger_type", "automatic")
                put("trigger_context", triggerContext)
                completedEpisodes?.let { put("completed_episodes", it) }
            },
        )
        deliverSurveyTriggerEvent()
    }

    /** Manual trigger from a long-press or a remote console feature flag. */
    override fun trackSurveyNpsManualTrigger(source: String) {
        AnalyticsEmit.event(
            "feedback_submitted",
            mapOf(
                "feedback_type" to "nps_manual",
                "source" to source,
                "trigger_source" to source,
                "trigger_type" to "manual",
                "trigger_context" to if (source == "remote_flag") "console" else "manual",
            ),
        )
        deliverSurveyTriggerEvent()
    }

    /** Flush and refresh flags so the SDK evaluates survey display conditions immediately. */
    private fun deliverSurveyTriggerEvent() {
        PostHogStartup.runWhenReady {
            PostHog.flush()
            reloadFeatureFlags()
        }
    }

    /**
     * A review prompt appeared (`milestone_review`, or `promoter_review` for an NPS promoter, with
     * [npsScore]). Not `feedback_submitted`, which would also make the person eligible for the survey.
     */
    override fun trackEngagementPromptShown(promptType: String, source: String, completedEpisodes: Int?, npsScore: Int?,) {
        AnalyticsEmit.event(
            "engagement_prompt_shown",
            buildMap {
                put("prompt_type", promptType)
                put("source", source)
                completedEpisodes?.let { put("completed_episodes", it) }
                npsScore?.let { put("nps_score", it) }
            },
        )
    }

    /**
     * The listener was sent to Play to rate boxlore: [method] `in_app_review` (Play's review sheet was
     * requested; Play doesn't say whether it appeared) or `store_page`, from [source].
     */
    fun trackStoreReviewOpened(method: String, source: String,) {
        AnalyticsEmit.event("store_review_opened", mapOf("method" to method, "source" to source))
    }

    /** The in-app feedback form was sent and the server accepted it: categories only, never the text. */
    fun trackFeedbackSent(category: String, withEmail: Boolean, withDiagnostics: Boolean,) {
        AnalyticsEmit.event(
            "feedback_sent",
            mapOf("category" to category, "with_email" to withEmail, "with_diagnostics" to withDiagnostics),
        )
    }

    fun resetIdentity() {
        AnalyticsEmit.event("identity_reset", mapOf("reason" to "user_reset"))
        PostHogStartup.runWhenReady { PostHog.reset() }
        AppearanceAnalyticsTracks.resendHomeHeroStyleAfterIdentityReset()
    }

    /** Empty until PostHog finishes setting up; callers already fall back when blank. */
    fun getDistinctId(): String = if (PostHogStartup.isReady) PostHog.distinctId() else ""

    /** Phase C — Auto + polish (PR9). */
    fun trackLateNightSafeguardDecision(decision: String, durationMinutes: Int? = null,) = PhaseCAnalyticsTracks.trackLateNightSafeguardDecision(decision, durationMinutes)

    fun trackAndroidAutoConnected(sessionId: String? = null) = PhaseCAnalyticsTracks.trackAndroidAutoConnected(sessionId)

    fun trackAndroidAutoDisconnected(sessionId: String? = null, durationSeconds: Int? = null,) = PhaseCAnalyticsTracks.trackAndroidAutoDisconnected(sessionId, durationSeconds)

    fun trackAndroidAutoBrowse(node: String, action: String? = null,) = PhaseCAnalyticsTracks.trackAndroidAutoBrowse(node, action)

    fun trackLearnCaughtUp(cardsRemaining: Int? = null) = PhaseCAnalyticsTracks.trackLearnCaughtUp(cardsRemaining)

    fun trackCatalogMiss(lookupType: String, key: String? = null,) = PhaseCAnalyticsTracks.trackCatalogMiss(lookupType, key)

    fun trackRssRefreshFailed(podcastId: String? = null, errorType: String? = null,) = PhaseCAnalyticsTracks.trackRssRefreshFailed(podcastId, errorType)

    fun trackProgressSyncAnomaly(anomalyType: String, episodeId: String? = null,) = PhaseCAnalyticsTracks.trackProgressSyncAnomaly(anomalyType, episodeId)

    /** `feed_fetch_result`: one show-page read, or one background pass per source (P4, plan "PostHog"). */
    fun trackFeedFetchResult(report: FeedFetchReport) = FeedAnalyticsTracks.trackFeedFetchResult(report)

    /** `feed_url_corrected`: one saved address swap (P4, plan "Upgrade repair"). */
    fun trackFeedUrlCorrected(correction: FeedUrlCorrection) = FeedAnalyticsTracks.trackFeedUrlCorrected(correction)

    /** `own_ids_upgrade_completed`: the one-time upgrade repair finished, once per install (P4 PR 18, plan "Upgrade repair"). */
    fun trackOwnIdsUpgradeCompleted(report: UpgradeRepairReport) = FeedAnalyticsTracks.trackOwnIdsUpgradeCompleted(report)

    data class SmartQueueRefillEvent(
        val triggeringEpisodeId: String,
        val triggeringPodcastGenre: String,
        val refilledCount: Int,
        val recommendationSources: List<String>,
        val refilledEpisodeIds: List<String>,
        val region: String? = null,
        val sourceCounts: Map<String, Int> = emptyMap(),
        val usedServerRecommendations: Boolean = false,
    )

    // Domain track façades (bodies in *AnalyticsTracks)
    fun trackHomeImportBannerImpression() = OnboardingAnalyticsTracks.trackHomeImportBannerImpression()

    fun trackHomeImportBannerClicked(action: String) = OnboardingAnalyticsTracks.trackHomeImportBannerClicked(action)

    fun trackHomeImportBannerDismissed() = OnboardingAnalyticsTracks.trackHomeImportBannerDismissed()

    fun trackOnboardingStarted(entryPoint: String = "welcome_screen") = OnboardingAnalyticsTracks.trackOnboardingStarted(entryPoint)

    fun trackOnboardingFlowSelected(flowType: String, entryPoint: String = "welcome_screen",) = OnboardingAnalyticsTracks.trackOnboardingFlowSelected(flowType, entryPoint)

    fun trackOnboardingSkipped(screen: String, totalOnboardingTimeSeconds: Float,) = OnboardingAnalyticsTracks.trackOnboardingSkipped(screen, totalOnboardingTimeSeconds)

    fun trackOnboardingSimilarChoice(path: String, choice: String, seedCount: Int,) = OnboardingAnalyticsTracks.trackOnboardingSimilarChoice(path, choice, seedCount)

    fun trackOnboardingAiTurnSubmitted(turnNumber: Int, selectedOptions: Set<String>, customInputText: String, timeSpentSeconds: Float,) = OnboardingAnalyticsTracks.trackOnboardingAiTurnSubmitted(turnNumber, selectedOptions, customInputText, timeSpentSeconds)

    fun trackOnboardingAiResponseReceived(
        turnNumber: Int,
        optionsCount: Int,
        optionsList: List<String>,
        durationSeconds: Float,
        detectedIntent: String? = null,
    ) = OnboardingAnalyticsTracks.trackOnboardingAiResponseReceived(
        turnNumber,
        optionsCount,
        optionsList,
        durationSeconds,
        detectedIntent,
    )

    fun trackOnboardingAiSearchRedirect(turnNumber: Int, suggestedQuery: String?,) = OnboardingAnalyticsTracks.trackOnboardingAiSearchRedirect(turnNumber, suggestedQuery)

    fun trackOnboardingAiSynthesisCompleted(rowsCount: Int, podcastsCount: Int, durationSeconds: Float,) = OnboardingAnalyticsTracks.trackOnboardingAiSynthesisCompleted(rowsCount, podcastsCount, durationSeconds)

    fun trackOnboardingAiSynthesisFailed(errorMessage: String) = OnboardingAnalyticsTracks.trackOnboardingAiSynthesisFailed(errorMessage)

    fun trackOnboardingAiDone(
        totalSubscribedCount: Int,
        subscribedPodcastsList: List<String>,
        didScrollSuggestions: Boolean,
        totalOnboardingTimeSeconds: Float,
        favoriteGenres: List<String>,
        entryPoint: String = "welcome_screen",
    ) = OnboardingAnalyticsTracks.trackOnboardingAiDone(
        totalSubscribedCount,
        subscribedPodcastsList,
        didScrollSuggestions,
        totalOnboardingTimeSeconds,
        favoriteGenres,
        entryPoint,
    )

    fun trackSearchPerformed(query: String, resultsCount: Int,) = OnboardingAnalyticsTracks.trackSearchPerformed(query, resultsCount)

    fun trackSearchPodcastSubscribed(podcastName: String, podcastId: String, totalSubscribedCount: Int,) = OnboardingAnalyticsTracks.trackSearchPodcastSubscribed(podcastName, podcastId, totalSubscribedCount)

    fun trackOnboardingSearchDone(
        entryPoint: String,
        totalSubscribedCount: Int,
        subscribedPodcastsList: List<String>,
        searchesPerformed: Int,
        timeSpentOnSearchSeconds: Float,
        totalOnboardingTimeSeconds: Float,
    ) = OnboardingAnalyticsTracks.trackOnboardingSearchDone(
        entryPoint,
        totalSubscribedCount,
        subscribedPodcastsList,
        searchesPerformed,
        timeSpentOnSearchSeconds,
        totalOnboardingTimeSeconds,
    )

    fun trackImportSheetOpened() = OnboardingAnalyticsTracks.trackImportSheetOpened()

    fun trackOnboardingImportCompleted(
        importType: String,
        importedPodcastCount: Int,
        importedPodcastsList: List<String>,
        totalOnboardingTimeSeconds: Float,
        entryPoint: String = "welcome_screen",
    ) = OnboardingAnalyticsTracks.trackOnboardingImportCompleted(
        importType,
        importedPodcastCount,
        importedPodcastsList,
        totalOnboardingTimeSeconds,
        entryPoint,
    )

    fun trackOnboardingImportFailed(importType: String, errorMessage: String?,) = OnboardingAnalyticsTracks.trackOnboardingImportFailed(importType, errorMessage)

    fun trackOnboardingStepViewed(stepName: String, flowType: String, stepIndex: Int? = null,) = OnboardingAnalyticsTracks.trackOnboardingStepViewed(stepName, flowType, stepIndex)

    fun trackOnboardingManualStepCompleted(stepName: String, selectionsCount: Int, selectionsList: List<String>, timeSpentSeconds: Float,) = OnboardingAnalyticsTracks.trackOnboardingManualStepCompleted(stepName, selectionsCount, selectionsList, timeSpentSeconds)

    fun trackOnboardingManualDone(
        totalSubscribedCount: Int,
        subscribedPodcastsList: List<String>,
        totalOnboardingTimeSeconds: Float,
        didSwitchFromAi: Boolean,
        favoriteGenres: Set<String>,
    ) = OnboardingAnalyticsTracks.trackOnboardingManualDone(
        totalSubscribedCount,
        subscribedPodcastsList,
        totalOnboardingTimeSeconds,
        didSwitchFromAi,
        favoriteGenres,
    )

    fun trackFeatureAnnouncementViewed(featureId: String) = DiscoveryAnalyticsTracks.trackFeatureAnnouncementViewed(featureId)

    fun trackFeatureAnnouncementDismissed(featureId: String) = DiscoveryAnalyticsTracks.trackFeatureAnnouncementDismissed(featureId)

    fun trackInAppAnnouncementViewed(category: String, hasImage: Boolean, hasAction: Boolean, announcement: AnnouncementRef? = null,) =
        DiscoveryAnalyticsTracks.trackInAppAnnouncementViewed(category, hasImage, hasAction, announcement)

    fun trackInAppAnnouncementDismissed(category: String, hasImage: Boolean, hasAction: Boolean, announcement: AnnouncementRef? = null,) =
        DiscoveryAnalyticsTracks.trackInAppAnnouncementDismissed(category, hasImage, hasAction, announcement)

    fun trackInAppAnnouncementAction(category: String, hasImage: Boolean, actionLabel: String, announcement: AnnouncementRef? = null,) =
        DiscoveryAnalyticsTracks.trackInAppAnnouncementAction(category, hasImage, actionLabel, announcement)

    /** The system notification prompt was shown, from [surface] (`show_bell`, `library_import`). */
    fun trackNotificationPermissionRequested(surface: String? = null) = DiscoveryAnalyticsTracks.trackNotificationPermissionRequested(surface)

    fun trackNotificationPermissionDecided(isGranted: Boolean, surface: String? = null,) =
        DiscoveryAnalyticsTracks.trackNotificationPermissionDecided(isGranted, surface)

    fun trackHomeHeroCarouselSwiped(maxCardIndexViewed: Int, totalCardsAvailable: Int,) = DiscoveryAnalyticsTracks.trackHomeHeroCarouselSwiped(maxCardIndexViewed, totalCardsAvailable)

    fun trackCuratedBlockImpression(blockTitle: String, vibeIds: List<String>,) = DiscoveryAnalyticsTracks.trackCuratedBlockImpression(blockTitle, vibeIds)

    fun trackVideoSpotlightImpression(itemsCount: Int, podcastIds: List<String>,) = DiscoveryAnalyticsTracks.trackVideoSpotlightImpression(itemsCount, podcastIds)

    fun trackVideoSpotlightPodcastTapped(podcastId: String, podcastName: String, positionIndex: Int, clickTarget: String,) = DiscoveryAnalyticsTracks.trackVideoSpotlightPodcastTapped(
        podcastId,
        podcastName,
        positionIndex,
        clickTarget,
    )

    fun trackVideoSpotlightPlayInitiated(podcastId: String, podcastName: String, episodeId: String, episodeTitle: String?,) = DiscoveryAnalyticsTracks.trackVideoSpotlightPlayInitiated(
        podcastId,
        podcastName,
        episodeId,
        episodeTitle,
    )

    fun trackHomeRecommendationsImpression(recommendationsCount: Int, episodeIds: List<String>, timeBlockTitle: String?,) = DiscoveryAnalyticsTracks.trackHomeRecommendationsImpression(recommendationsCount, episodeIds, timeBlockTitle)

    fun trackHomeRecommendationCardTapped(
        episodeId: String,
        episodeTitle: String?,
        podcastId: String,
        podcastName: String?,
        positionIndex: Int,
        timeBlockTitle: String?,
    ) = DiscoveryAnalyticsTracks.trackHomeRecommendationCardTapped(
        episodeId,
        episodeTitle,
        podcastId,
        podcastName,
        positionIndex,
        timeBlockTitle,
    )

    fun trackExploreRecommendationsImpression(recommendationsCount: Int, episodeIds: List<String>,) = DiscoveryAnalyticsTracks.trackExploreRecommendationsImpression(recommendationsCount, episodeIds)

    fun trackExploreRecommendationCardTapped(
        episodeId: String,
        episodeTitle: String?,
        podcastId: String,
        podcastName: String?,
        positionIndex: Int,
    ) = DiscoveryAnalyticsTracks.trackExploreRecommendationCardTapped(episodeId, episodeTitle, podcastId, podcastName, positionIndex)

    fun trackPodcastInfoScreenViewed(
        podcastId: String,
        podcastName: String? = null,
        entryPoint: String? = null,
        genreFilter: String? = null,
        scrollDepth: Int? = null,
        searchQuery: String? = null,
    ) = DiscoveryAnalyticsTracks.trackPodcastInfoScreenViewed(podcastId, podcastName, entryPoint, genreFilter, scrollDepth, searchQuery)

    fun trackPodcastSubscriptionToggled(podcastId: String, podcastName: String?, isSubscribed: Boolean, entryPoint: String,) = DiscoveryAnalyticsTracks.trackPodcastSubscriptionToggled(podcastId, podcastName, isSubscribed, entryPoint)

    fun trackPodcastInfoScreenSession(
        podcastId: String,
        podcastName: String,
        timeSpentSeconds: Float,
        wasSubscribed: Boolean,
        didSubscribe: Boolean,
        didUnsubscribe: Boolean,
        didSearch: Boolean,
        didSortEpisodes: Boolean,
        episodesPlayedCount: Int,
        episodesClickedCount: Int,
    ) = DiscoveryAnalyticsTracks.trackPodcastInfoScreenSession(
        podcastId,
        podcastName,
        timeSpentSeconds,
        wasSubscribed,
        didSubscribe,
        didUnsubscribe,
        didSearch,
        didSortEpisodes,
        episodesPlayedCount,
        episodesClickedCount,
    )

    fun trackCuratedCardTapped(podcastId: String, podcastName: String?, vibeId: String, positionIndex: Int,) = DiscoveryAnalyticsTracks.trackCuratedCardTapped(podcastId, podcastName, vibeId, positionIndex)

    fun trackEpisodeInfoScreenViewed(properties: Map<String, Any>) = DiscoveryAnalyticsTracks.trackEpisodeInfoScreenViewed(properties)

    fun trackEpisodeInfoScreenSession(properties: Map<String, Any>) = DiscoveryAnalyticsTracks.trackEpisodeInfoScreenSession(properties)

    fun trackProxyFallbackTriggered(imageHost: String, proxyWidth: Int, sampleMultiplier: Int = 10,) = DiscoveryAnalyticsTracks.trackProxyFallbackTriggered(imageHost, proxyWidth, sampleMultiplier)

    fun setOnboardingImportCompletedUserProperties(initialPodcastsSubscribed: Int) = OnboardingAnalyticsTracks.setOnboardingImportCompletedUserProperties(initialPodcastsSubscribed)

    fun trackPlaybackStarted(
        podcastId: String?,
        podcastName: String?,
        podcastGenre: String?,
        episodeId: String,
        episodeTitle: String?,
        startPositionSeconds: Float,
        totalDurationSeconds: Float,
        isRepeating: Boolean,
        isSubscribed: Boolean,
        entryPoint: String? = null,
        entryPointContext: Map<String, Any>? = null,
        playbackMode: String = PlaybackListenValues.MODE_STREAM,
        speed: Float = 1.0f,
        listen: ListenStart? = null,
    ) = PlaybackAnalyticsTracks.trackPlaybackStarted(
        podcastId,
        podcastName,
        podcastGenre,
        episodeId,
        episodeTitle,
        startPositionSeconds,
        totalDurationSeconds,
        isRepeating,
        isSubscribed,
        entryPoint,
        entryPointContext,
        playbackMode = playbackMode,
        clientSurface = PlaybackListenValues.clientSurface(listen?.controlledBy.orEmpty()),
        speed = speed,
        listen = listen,
    )

    /**
     * The page view of a screen, tab, sheet or dialog without a view event of its own, with the
     * screen before it ([ScreenViews]). Screens call it through `TrackScreenView`, once per visit.
     */
    fun trackScreenViewed(screen: String, previousScreen: String?, properties: Map<String, Any> = emptyMap(),) =
        ScreenAnalyticsTracks.trackScreenViewed(screen, previousScreen, properties)

    /** One record per listen, sent when it ends; see [PlaybackListenSummary]. */
    fun trackPlaybackSessionEnded(listen: PlaybackListenSummary) = ListenAnalyticsTracks.trackPlaybackSessionEnded(listen)

    /** A listen the previous run left unfinished because Android ended the app. */
    fun trackPlaybackInterrupted(listen: PlaybackInterruptedListen) = ListenAnalyticsTracks.trackPlaybackInterrupted(listen)

    fun trackPlaybackPaused(
        podcastId: String?,
        podcastName: String?,
        podcastGenre: String?,
        episodeId: String,
        episodeTitle: String?,
        durationPlayedSeconds: Float,
        totalBufferedTimeSeconds: Float,
        totalDurationSeconds: Float,
        isCompleted: Boolean,
        entryPoint: String? = null,
        entryPointContext: Map<String, Any>? = null,
        queueSize: Int? = null,
        pauseReason: String = "user_voluntary",
    ) = PlaybackAnalyticsTracks.trackPlaybackPaused(
        podcastId,
        podcastName,
        podcastGenre,
        episodeId,
        episodeTitle,
        durationPlayedSeconds,
        totalBufferedTimeSeconds,
        totalDurationSeconds,
        isCompleted,
        entryPoint,
        entryPointContext,
        queueSize,
        pauseReason,
    )

    fun trackPlaybackCompleted(
        podcastId: String?,
        podcastName: String?,
        podcastGenre: String?,
        episodeId: String,
        episodeTitle: String?,
        totalDurationSeconds: Float,
        entryPoint: String? = null,
        entryPointContext: Map<String, Any>? = null,
    ) = PlaybackAnalyticsTracks.trackPlaybackCompleted(
        podcastId,
        podcastName,
        podcastGenre,
        episodeId,
        episodeTitle,
        totalDurationSeconds,
        entryPoint,
        entryPointContext,
    )

    fun trackPlaybackHeartbeat(
        podcastId: String?,
        podcastName: String?,
        episodeId: String,
        episodeTitle: String?,
        currentPositionSeconds: Float,
        totalDurationSeconds: Float,
        heartbeatPercentage: Int,
        heartbeatType: String,
        entryPoint: String? = null,
    ) = PlaybackAnalyticsTracks.trackPlaybackHeartbeat(
        podcastId,
        podcastName,
        episodeId,
        episodeTitle,
        currentPositionSeconds,
        totalDurationSeconds,
        heartbeatPercentage,
        heartbeatType,
        entryPoint,
    )

    fun trackPlaybackSeeked(
        podcastId: String?,
        podcastName: String?,
        episodeId: String,
        episodeTitle: String?,
        fromPositionSeconds: Float,
        toPositionSeconds: Float,
        totalDurationSeconds: Float,
        seekSource: String,
        entryPoint: String? = null,
    ) = PlaybackAnalyticsTracks.trackPlaybackSeeked(
        podcastId,
        podcastName,
        episodeId,
        episodeTitle,
        fromPositionSeconds,
        toPositionSeconds,
        totalDurationSeconds,
        seekSource,
        entryPoint,
    )

    fun trackPlaybackBuffering(
        episodeId: String? = null,
        podcastId: String? = null,
        entryPoint: String? = null,
        bufferDurationMs: Long? = null,
    ) = PlaybackAnalyticsTracks.trackPlaybackBuffering(episodeId, podcastId, entryPoint, bufferDurationMs)

    fun trackPlaybackError(
        errorCode: String,
        errorMessage: String,
        podcastId: String?,
        episodeId: String?,
        podcastName: String? = null,
        episodeTitle: String? = null,
        listenId: String? = null,
        cause: Throwable? = null,
    ) = PlaybackAnalyticsTracks.trackPlaybackError(
        errorCode,
        errorMessage,
        podcastId,
        episodeId,
        podcastName,
        episodeTitle,
        listenId = listenId,
        cause = cause,
    )

    fun trackExploreScreenViewed(sourceEntryPoint: String? = null) = PlaybackAnalyticsTracks.trackExploreScreenViewed(sourceEntryPoint)

    fun trackExploreSearchPerformed(query: String, resultsCount: Int, searchMode: String = "show_keyword",) = PlaybackAnalyticsTracks.trackExploreSearchPerformed(query, resultsCount, searchMode)

    fun trackExploreScreenSession(
        timeSpentSeconds: Float,
        categoriesClickedCount: Int,
        vibesClickedCount: Int,
        searchesPerformedCount: Int,
        podcastsClickedCount: Int,
        maxScrollDepth: Int,
        finalCategoryState: String,
        finalVibeState: String?,
        finalSearchQuery: String?,
    ) = PlaybackAnalyticsTracks.trackExploreScreenSession(
        timeSpentSeconds,
        categoriesClickedCount,
        vibesClickedCount,
        searchesPerformedCount,
        podcastsClickedCount,
        maxScrollDepth,
        finalCategoryState,
        finalVibeState,
        finalSearchQuery,
    )

    fun trackLibraryHubViewed(sourceEntryPoint: String) = LibraryAnalyticsTracks.trackLibraryHubViewed(sourceEntryPoint)

    fun trackLibraryHubSession(timeSpentSeconds: Float, navigatedTo: String?,) = LibraryAnalyticsTracks.trackLibraryHubSession(timeSpentSeconds, navigatedTo)

    fun trackLibrarySubscriptionsViewed(sourceEntryPoint: String, initialTab: String,) = LibraryAnalyticsTracks.trackLibrarySubscriptionsViewed(sourceEntryPoint, initialTab)

    fun trackLibrarySubscriptionsSession(
        timeSpentSeconds: Float,
        tabSwitchesCount: Int,
        didSearch: Boolean,
        finalSearchQuery: String?,
        podcastsClickedCount: Int,
        episodesClickedCount: Int,
    ) = LibraryAnalyticsTracks.trackLibrarySubscriptionsSession(
        timeSpentSeconds,
        tabSwitchesCount,
        didSearch,
        finalSearchQuery,
        podcastsClickedCount,
        episodesClickedCount,
    )

    fun trackLibraryLikedViewed(sourceEntryPoint: String) = LibraryAnalyticsTracks.trackLibraryLikedViewed(sourceEntryPoint)

    fun trackLibraryLikedSession(timeSpentSeconds: Float, episodesClickedCount: Int, episodesUnlikedCount: Int,) = LibraryAnalyticsTracks.trackLibraryLikedSession(timeSpentSeconds, episodesClickedCount, episodesUnlikedCount)

    fun trackLibraryDownloadsViewed(sourceEntryPoint: String) = LibraryAnalyticsTracks.trackLibraryDownloadsViewed(sourceEntryPoint)

    fun trackLibraryDownloadsSession(timeSpentSeconds: Float, episodesClickedCount: Int, episodesDeletedCount: Int,) = LibraryAnalyticsTracks.trackLibraryDownloadsSession(timeSpentSeconds, episodesClickedCount, episodesDeletedCount)

    fun trackLibraryHistoryViewed(sourceEntryPoint: String) = LibraryAnalyticsTracks.trackLibraryHistoryViewed(sourceEntryPoint)

    fun trackLibraryHistorySession(timeSpentSeconds: Float, episodesClickedCount: Int, itemsDeletedCount: Int,) = LibraryAnalyticsTracks.trackLibraryHistorySession(timeSpentSeconds, episodesClickedCount, itemsDeletedCount)

    fun trackTopControlbarInteraction(action: String, screen: String,) = LibraryAnalyticsTracks.trackTopControlbarInteraction(action, screen)

    fun trackPlaybackRouteChanged(isRemote: Boolean, volumeControlAvailable: Boolean,) = LibraryAnalyticsTracks.trackPlaybackRouteChanged(isRemote, volumeControlAvailable)

    fun trackSettingsScreenViewed(sourceEntryPoint: String) = LibraryAnalyticsTracks.trackSettingsScreenViewed(sourceEntryPoint)

    fun trackSettingsInteraction(action: String, value: String? = null,) = LibraryAnalyticsTracks.trackSettingsInteraction(action, value)

    fun trackCustomThemeEditorOpened() = LibraryAnalyticsTracks.trackCustomThemeEditorOpened()

    fun trackCustomThemeSaved(surfaceStyle: String) = LibraryAnalyticsTracks.trackCustomThemeSaved(surfaceStyle)

    fun trackHomeHeroStyleSelected(previousStyle: String?, previousShade: String?, style: String, shade: String,) =
        AppearanceAnalyticsTracks.trackHomeHeroStyleSelected(previousStyle, previousShade, style, shade)

    fun syncHomeHeroStylePersonProperties(style: String?, shade: String?) = AppearanceAnalyticsTracks.syncHomeHeroStylePersonProperties(style, shade)

    /** A LeaveMeAlone switch or the Brief card's Always hide; [source] is `settings` or `brief_card`. */
    fun trackRecommendationSectionToggled(
        section: cx.aswin.boxlore.core.prefs.DiscoverySection,
        enabled: Boolean,
        source: String,
        sectionsOff: Int,
    ) = RecommendationsAnalyticsTracks.trackSectionToggled(section, enabled, source, sectionsOff)

    /**
     * The `support-world` variant for this person when it is one of [knownVariants], or null before
     * flags load or for an unknown value. Only a known variant records an exposure. Read once per visit.
     */
    fun supportWorldVariant(knownVariants: Set<String>): String? = SupportAnalyticsTracks.supportWorldVariant(knownVariants)

    fun trackSupportEntryViewed(surface: String) = SupportAnalyticsTracks.trackSupportEntryViewed(surface)

    fun trackSupportPageViewed(
        world: SupportWorldContext? = null,
        entryPoint: String? = null,
        localHour: Int? = null,
        weekday: Int? = null,
    ) = SupportAnalyticsTracks.trackSupportPageViewed(world, entryPoint, localHour, weekday)

    fun trackSupportTierToggled(
        tierTitle: String,
        codename: String,
        amount: String,
        isLoreInspect: Boolean = false,
        world: SupportWorldContext? = null,
        tierIndex: Int? = null,
    ) = SupportAnalyticsTracks.trackSupportTierToggled(tierTitle, codename, amount, isLoreInspect, world, tierIndex)

    fun trackSupportDonateClicked(
        tierTitle: String,
        codename: String,
        amount: String,
        world: SupportWorldContext? = null,
        tierIndex: Int? = null,
        amountUsd: Double? = null,
        paymentsLive: Boolean? = null,
    ) = SupportAnalyticsTracks.trackSupportDonateClicked(tierTitle, codename, amount, world, tierIndex, amountUsd, paymentsLive)

    fun trackSupportPageLeft(
        world: SupportWorldContext,
        dwellMs: Long,
        tiersViewed: Int,
        payTapped: Boolean,
    ) = SupportAnalyticsTracks.trackSupportPageLeft(world, dwellMs, tiersViewed, payTapped)

    fun trackMiniPlayerInteraction(
        action: String,
        podcastId: String?,
        episodeId: String?,
        podcastName: String? = null,
        episodeTitle: String? = null,
    ) = LibraryAnalyticsTracks.trackMiniPlayerInteraction(action, podcastId, episodeId, podcastName, episodeTitle)

    fun trackFullPlayerScreenSession(
        podcastId: String?,
        episodeId: String?,
        metrics: Map<String, Any>,
        podcastName: String? = null,
        episodeTitle: String? = null,
    ) = LibraryAnalyticsTracks.trackFullPlayerScreenSession(podcastId, episodeId, metrics, podcastName, episodeTitle)

    fun trackDownloadCompleted(episodeId: String, podcastId: String, source: String? = null, fileSizeMb: Float? = null,) = LibraryAnalyticsTracks.trackDownloadCompleted(episodeId, podcastId, source, fileSizeMb)

    fun trackDownloadFailed(errorReason: String, episodeId: String? = null, podcastId: String? = null, source: String? = null,) = LibraryAnalyticsTracks.trackDownloadFailed(errorReason, episodeId, podcastId, source)

    fun trackDownloadRequested(episodeId: String, podcastId: String, source: String, wifiOnly: Boolean? = null,) = LibraryAnalyticsTracks.trackDownloadRequested(episodeId, podcastId, source, wifiOnly)

    fun trackSmartDownloadSync(
        requestedCount: Int? = null,
        completedCount: Int? = null,
        failedCount: Int? = null,
        cleanedCount: Int? = null,
        trigger: String? = null,
    ) = LibraryAnalyticsTracks.trackSmartDownloadSync(
        requestedCount,
        completedCount,
        failedCount,
        cleanedCount,
        trigger,
    )

    fun trackShowNotificationToggled(podcastId: String, enabled: Boolean,) = LibraryAnalyticsTracks.trackShowNotificationToggled(podcastId, enabled)

    fun trackShareContent(
        contentType: String,
        podcastId: String? = null,
        episodeId: String? = null,
        channel: String? = null,
        surface: String? = null,
    ) = LibraryAnalyticsTracks.trackShareContent(contentType, podcastId, episodeId, channel, surface)

    fun trackBackupRestoreResult(
        action: String,
        success: Boolean,
        itemCount: Int? = null,
        format: String? = null,
        errorMessage: String? = null,
    ) = LibraryAnalyticsTracks.trackBackupRestoreResult(action, success, itemCount, format, errorMessage)

    fun trackEpisodeLikedToggled(episodeId: String, podcastId: String, isLiked: Boolean, surface: String? = null,) = LibraryAnalyticsTracks.trackEpisodeLikedToggled(episodeId, podcastId, isLiked, surface)

    fun trackEpisodeMarkPlayed(episodeId: String, podcastId: String, isPlayed: Boolean, surface: String? = null,) = LibraryAnalyticsTracks.trackEpisodeMarkPlayed(episodeId, podcastId, isPlayed, surface)

    fun trackSearchResultTapped(
        surface: String,
        resultType: String,
        podcastId: String? = null,
        episodeId: String? = null,
        positionIndex: Int? = null,
        searchQuery: String? = null,
        searchMode: String? = null,
    ) = LibraryAnalyticsTracks.trackSearchResultTapped(
        surface,
        resultType,
        podcastId,
        episodeId,
        positionIndex,
        searchQuery,
        searchMode,
    )

    fun trackNotificationReceived(notificationType: String, podcastId: String? = null, episodeId: String? = null,) = LibraryAnalyticsTracks.trackNotificationReceived(notificationType, podcastId, episodeId)

    fun trackNotificationTapped(
        notificationType: String = "unknown",
        podcastId: String? = null,
        episodeId: String? = null,
        targetRoute: String? = null,
    ) = LibraryAnalyticsTracks.trackNotificationTapped(notificationType, podcastId, episodeId, targetRoute)

    fun trackQueueModified(
        action: String,
        episodeId: String? = null,
        podcastId: String? = null,
        queueSize: Int? = null,
        source: String? = null,
        itemsCount: Int? = null,
    ) = QueueContentAnalyticsTracks.trackQueueModified(action, episodeId, podcastId, queueSize, source, itemsCount)

    fun trackNavTabClicked(tabName: String, previousTab: String? = null,) = QueueContentAnalyticsTracks.trackNavTabClicked(tabName, previousTab)

    fun trackPlayMixClicked(count: Int, mixMode: String = "daily",) = LibraryAnalyticsTracks.trackPlayMixClicked(count, mixMode)

    fun trackHomeMixModeChanged(mixMode: String) = LibraryAnalyticsTracks.trackHomeMixModeChanged(mixMode)

    fun trackHomePodcastFiltered(podcastId: String, title: String,) = LibraryAnalyticsTracks.trackHomePodcastFiltered(podcastId, title)

    fun trackLibrarySubscriptionsLayoutToggled(isGridView: Boolean) = LibraryAnalyticsTracks.trackLibrarySubscriptionsLayoutToggled(isGridView)

    fun trackLibrarySubscriptionsSortChanged(sortMethod: String, tab: String,) = LibraryAnalyticsTracks.trackLibrarySubscriptionsSortChanged(sortMethod, tab)

    fun trackLibrarySubscriptionsGenreFiltered(genreName: String, tab: String,) = LibraryAnalyticsTracks.trackLibrarySubscriptionsGenreFiltered(genreName, tab)

    fun trackSmartQueueRefilled(event: AnalyticsHelper.SmartQueueRefillEvent) = QueueContentAnalyticsTracks.trackSmartQueueRefilled(event)

    fun trackQueueReordered(episodeId: String, fromPosition: Int, toPosition: Int, contextType: String?,) = QueueContentAnalyticsTracks.trackQueueReordered(episodeId, fromPosition, toPosition, contextType)

    fun trackLoreQueueConflictShown(episodeId: String, normalQueueSize: Int,) = QueueContentAnalyticsTracks.trackLoreQueueConflictShown(episodeId, normalQueueSize)

    fun trackLoreQueueConflictResult(episodeId: String, result: String,) = QueueContentAnalyticsTracks.trackLoreQueueConflictResult(episodeId, result)

    fun trackSmartQueueEpisodeSkipped(episodeId: String, recommendationSource: String, positionInQueue: Int,) = QueueContentAnalyticsTracks.trackSmartQueueEpisodeSkipped(episodeId, recommendationSource, positionInQueue)

    fun trackOfflineModeEntered() = QueueContentAnalyticsTracks.trackOfflineModeEntered()

    fun trackDiscoverCategoryFiltered(categoryName: String) = QueueContentAnalyticsTracks.trackDiscoverCategoryFiltered(categoryName)

    fun trackDailyBriefingBannerTapped(region: String, date: String,) = QueueContentAnalyticsTracks.trackDailyBriefingBannerTapped(region, date)

    fun trackDailyBriefingPlayClicked(region: String, date: String, source: String,) = QueueContentAnalyticsTracks.trackDailyBriefingPlayClicked(region, date, source)

    fun trackDailyBriefingPauseClicked(region: String, date: String, source: String,) = QueueContentAnalyticsTracks.trackDailyBriefingPauseClicked(region, date, source)

    fun trackDailyBriefingInteraction(action: String, region: String, date: String, extraProps: Map<String, Any> = emptyMap(),) = QueueContentAnalyticsTracks.trackDailyBriefingInteraction(action, region, date, extraProps)

    fun trackDailyBriefingRegionChanged(previousRegion: String, newRegion: String, date: String,) = QueueContentAnalyticsTracks.trackDailyBriefingRegionChanged(previousRegion, newRegion, date)

    fun trackDailyBriefingRelatedEpisodeClicked(
        region: String,
        date: String,
        chapterIndex: Int,
        episodeId: String,
        episodeTitle: String,
        podcastId: String,
        podcastTitle: String,
    ) = QueueContentAnalyticsTracks.trackDailyBriefingRelatedEpisodeClicked(
        region,
        date,
        chapterIndex,
        episodeId,
        episodeTitle,
        podcastId,
        podcastTitle,
    )

    fun trackDailyBriefingCardImpression(region: String, date: String, playbackStatus: String,) = QueueContentAnalyticsTracks.trackDailyBriefingCardImpression(region, date, playbackStatus)

    fun trackDailyBriefingScreenViewed(region: String, date: String, source: String? = null,) = QueueContentAnalyticsTracks.trackDailyBriefingScreenViewed(region, date, source)

    fun trackLearnScreenViewed() = QueueContentAnalyticsTracks.trackLearnScreenViewed()

    fun trackLearnCardDismissed(episodeId: String, episodeTitle: String?, podcastId: String?, podcastTitle: String?,) = QueueContentAnalyticsTracks.trackLearnCardDismissed(episodeId, episodeTitle, podcastId, podcastTitle)

    fun trackLearnCardQueued(episodeId: String, episodeTitle: String?, podcastId: String?, podcastTitle: String?,) = QueueContentAnalyticsTracks.trackLearnCardQueued(episodeId, episodeTitle, podcastId, podcastTitle)

    fun trackLearnCardInfoClicked(episodeId: String, episodeTitle: String?, podcastId: String?, podcastTitle: String?,) = QueueContentAnalyticsTracks.trackLearnCardInfoClicked(episodeId, episodeTitle, podcastId, podcastTitle)

    fun trackLearnCardPodcastClicked(podcastId: String?, podcastTitle: String?,) = QueueContentAnalyticsTracks.trackLearnCardPodcastClicked(podcastId, podcastTitle)

    fun trackLearnCardPlayClicked(episodeId: String, episodeTitle: String?, podcastId: String?, podcastTitle: String?,) = QueueContentAnalyticsTracks.trackLearnCardPlayClicked(episodeId, episodeTitle, podcastId, podcastTitle)

    fun trackLearnScreenSession(
        timeSpentSeconds: Float,
        cardsDismissedCount: Int,
        cardsQueuedCount: Int,
        playsCount: Int,
        podcastsClickedCount: Int,
        infosClickedCount: Int,
    ) = QueueContentAnalyticsTracks.trackLearnScreenSession(
        timeSpentSeconds,
        cardsDismissedCount,
        cardsQueuedCount,
        playsCount,
        podcastsClickedCount,
        infosClickedCount,
    )

    /** Phase C — `adaptive_ranking_status` (PR9). */
    override fun trackAdaptiveRankingStatus(statuses: List<RankingAggregateTelemetry>) {
        PhaseCAnalyticsTracks.trackAdaptiveRankingStatus(statuses)
    }

    override fun flush() {
        PostHogStartup.runWhenReady { PostHog.flush() }
    }

    override fun capture(event: String, properties: Map<String, Any>,) {
        AnalyticsEmit.event(event, properties)
    }
}
