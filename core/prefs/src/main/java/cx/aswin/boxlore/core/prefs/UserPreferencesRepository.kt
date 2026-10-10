package cx.aswin.boxlore.core.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import cx.aswin.boxlore.core.model.ContentRegions
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

internal fun sanitizeNavigationStyle(value: String?): String = if (value?.trim()?.lowercase() == "classic") "classic" else "floating"

/** Cold-start landing: `home` (default), `subscriptions`, or `downloads`. */
object OpenAppTo {
    const val HOME = "home"
    const val SUBSCRIPTIONS = "subscriptions"
    const val DOWNLOADS = "downloads"
}

data class PendingPodcastIdRepair(val oldPodcastId: String, val newPodcastId: String,)

internal fun sanitizeOpenAppTo(value: String?): String = when (value?.trim()?.lowercase()) {
    OpenAppTo.SUBSCRIPTIONS -> OpenAppTo.SUBSCRIPTIONS
    OpenAppTo.DOWNLOADS -> OpenAppTo.DOWNLOADS
    else -> OpenAppTo.HOME
}

class UserPreferencesRepository(context: Context,) {
    private val autoDownloadBackground = AutoDownloadBackgroundPreferences(context)
    val autoDownloadBackgroundSettingsStream = autoDownloadBackground.settings

    suspend fun setAutoDownloadBackgroundChecksEnabled(enabled: Boolean) = autoDownloadBackground.setEnabled(enabled)
    suspend fun setAutoDownloadBackgroundWifiOnly(wifiOnly: Boolean) = autoDownloadBackground.setWifiOnly(wifiOnly)
    suspend fun setAutoDownloadBackgroundChargingOnly(chargingOnly: Boolean) = autoDownloadBackground.setChargingOnly(chargingOnly)

    private val dataStore = context.userPreferencesDataStore
    private val appearance = AppearancePreferences(
        dataStore,
        PrefsFileMigrator.open(
            context,
            newName = PrefsFileMigrator.Files.THEME_FAST_CACHE,
            oldName = PrefsFileMigrator.LegacyFiles.THEME_FAST_CACHE,
        ),
    )
    private val library = LibraryPreferences(dataStore)
    private val engagement = EngagementPreferences(dataStore)
    private val tips = TooltipPreferences(dataStore)
    private val reviews = ReviewPromptPreferences(dataStore)
    private val announcements = AnnouncementPreferences(dataStore)
    val cachedThemeConfig: String
        get() = appearance.cachedThemeConfig

    val cachedSurfaceStyle: String
        get() = appearance.cachedSurfaceStyle

    val cachedFontRoundness: String
        get() = appearance.cachedFontRoundness

    val cachedNavigationStyle: String
        get() = appearance.cachedNavigationStyle

    val cachedOpenAppTo: String
        get() = appearance.cachedOpenAppTo

    val cachedExploreDefaultTab: String
        get() = appearance.cachedExploreDefaultTab

    val cachedSubscriptionsDefaultTab: String
        get() = appearance.cachedSubscriptionsDefaultTab

    val cachedSubscriptionsTabStyle: String
        get() = appearance.cachedSubscriptionsTabStyle

    val cachedThemeBrand: String
        get() = appearance.cachedThemeBrand

    val cachedUseDynamicColor: Boolean
        get() = appearance.cachedUseDynamicColor

    val cachedArtworkColorsEnabled: Boolean
        get() = appearance.cachedArtworkColorsEnabled

    val cachedCustomTheme: ThemeSelection?
        get() = appearance.cachedCustomTheme

    val artworkColorsEnabledStream: Flow<Boolean> = appearance.artworkColorsEnabledStream

    suspend fun setArtworkColorsEnabled(enabled: Boolean) = appearance.setArtworkColorsEnabled(enabled)

    val customThemeStream: Flow<ThemeSelection?> = appearance.customThemeStream

    suspend fun setThemeSelection(selection: ThemeSelection, customThemeToRemember: ThemeSelection? = null) = appearance.setThemeSelection(selection, customThemeToRemember)

    val cachedWidgetAppearance: String
        get() = appearance.cachedWidgetAppearance

    private fun normalizeRegionCode(region: String): String = ContentRegions.canonicalize(region)

    val regionStream: Flow<String> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }.map { preferences ->
                val stored = preferences[Keys.REGION]
                if (stored != null) {
                    normalizeRegionCode(stored)
                } else {
                    val localeCountry =
                        java.util.Locale
                            .getDefault()
                            .country
                            .lowercase()
                    ContentRegions.localeDefaultRegion(localeCountry)
                }
            }.distinctUntilChanged()

    val contentLanguagesStream: Flow<List<String>> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }.map { preferences ->
                val region =
                    preferences[Keys.REGION]?.let { normalizeRegionCode(it) }
                        ?: ContentRegions.localeDefaultRegion(
                            java.util.Locale
                                .getDefault()
                                .country
                                .lowercase(),
                        )
                val stored = ContentRegions.decodeLanguages(preferences[Keys.CONTENT_LANGUAGES])
                ContentRegions.normalizeLanguages(stored, region)
            }.distinctUntilChanged()

    suspend fun setRegion(region: String) {
        val normalized = normalizeRegionCode(region)
        val recommended = ContentRegions.recommendedLanguages(normalized)
        dataStore.edit { preferences ->
            preferences[Keys.REGION] = normalized
            preferences[Keys.CONTENT_LANGUAGES] = ContentRegions.encodeLanguages(recommended)
            preferences[Keys.HAS_DISMISSED_REGION_NUDGE] = true
        }
    }

    suspend fun setContentLanguages(languages: List<String>) {
        val region = regionStream.first()
        val normalized = ContentRegions.normalizeLanguages(languages, region)
        dataStore.edit { preferences ->
            preferences[Keys.CONTENT_LANGUAGES] = ContentRegions.encodeLanguages(normalized)
        }
    }

    val hasDismissedRegionNudgeStream: Flow<Boolean> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }.map { preferences ->
                preferences[Keys.HAS_DISMISSED_REGION_NUDGE] ?: false
            }.distinctUntilChanged()

    suspend fun dismissRegionNudge() {
        dataStore.edit { preferences ->
            preferences[Keys.HAS_DISMISSED_REGION_NUDGE] = true
        }
    }

    val hasDismissedExploreRegionNudgeStream: Flow<Boolean> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }.map { preferences ->
                preferences[Keys.HAS_DISMISSED_EXPLORE_REGION_NUDGE] ?: false
            }.distinctUntilChanged()

    suspend fun dismissExploreRegionNudge() {
        dataStore.edit { preferences ->
            preferences[Keys.HAS_DISMISSED_EXPLORE_REGION_NUDGE] = true
        }
    }

    val hasDismissedHomeImportBannerStream: Flow<Boolean> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }.map { preferences ->
                preferences[Keys.HAS_DISMISSED_HOME_IMPORT_BANNER] ?: false
            }.distinctUntilChanged()

    suspend fun dismissHomeImportBanner() {
        dataStore.edit { preferences ->
            preferences[Keys.HAS_DISMISSED_HOME_IMPORT_BANNER] = true
        }
    }

    val briefingDismissedDate: Flow<String> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }.map { preferences ->
                preferences[Keys.BRIEFING_DISMISSED_DATE] ?: ""
            }.distinctUntilChanged()

    suspend fun dismissBriefing(date: String) {
        dataStore.edit { preferences ->
            preferences[Keys.BRIEFING_DISMISSED_DATE] = date
        }
    }

    val briefingDismissedForever: Flow<Boolean> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }.map { preferences ->
                preferences[Keys.BRIEFING_DISMISSED_FOREVER] ?: false
            }.distinctUntilChanged()

    suspend fun dismissBriefingForever() {
        dataStore.edit { preferences ->
            preferences[Keys.BRIEFING_DISMISSED_FOREVER] = true
        }
    }

    val wasInitialRegionMatchStream: Flow<Boolean?> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }.map { preferences ->
                preferences[Keys.WAS_INITIAL_REGION_MATCH]
            }.distinctUntilChanged()

    suspend fun setWasInitialRegionMatch(match: Boolean) {
        dataStore.edit { preferences ->
            if (preferences[Keys.WAS_INITIAL_REGION_MATCH] == null) {
                preferences[Keys.WAS_INITIAL_REGION_MATCH] = match
            }
        }
    }

    val themeConfigStream: Flow<String> = appearance.themeConfigStream

    suspend fun setThemeConfig(themeConfig: String) = appearance.setThemeConfig(themeConfig)

    val useDynamicColorStream: Flow<Boolean> = appearance.useDynamicColorStream

    suspend fun setUseDynamicColor(useDynamicColor: Boolean) = appearance.setUseDynamicColor(useDynamicColor)

    val themeBrandStream: Flow<String> = appearance.themeBrandStream

    suspend fun setThemeBrand(themeBrand: String) = appearance.setThemeBrand(themeBrand)

    suspend fun setThemePreset(presetKey: String) = appearance.setThemeSelection(ThemeSelection(presetKey, presetKey, false))

    val surfaceStyleStream: Flow<String> = appearance.surfaceStyleStream

    suspend fun setSurfaceStyle(surfaceStyle: String) = appearance.setSurfaceStyle(surfaceStyle)

    val fontRoundnessStream: Flow<String> = appearance.fontRoundnessStream

    suspend fun setFontRoundness(fontRoundness: String) = appearance.setFontRoundness(fontRoundness)

    val navigationStyleStream: Flow<String> = appearance.navigationStyleStream

    suspend fun setNavigationStyle(navigationStyle: String) = appearance.setNavigationStyle(navigationStyle)

    val openAppToStream: Flow<String> = appearance.openAppToStream

    suspend fun setOpenAppTo(openAppTo: String) = appearance.setOpenAppTo(openAppTo)

    val exploreDefaultTabStream: Flow<String> = appearance.exploreDefaultTabStream

    suspend fun setExploreDefaultTab(tab: String) = appearance.setExploreDefaultTab(tab)

    val subscriptionsDefaultTabStream: Flow<String> = appearance.subscriptionsDefaultTabStream

    suspend fun setSubscriptionsDefaultTab(tab: String) = appearance.setSubscriptionsDefaultTab(tab)

    val subscriptionsTabStyleStream: Flow<String> = appearance.subscriptionsTabStyleStream

    suspend fun setSubscriptionsTabStyle(style: String) = appearance.setSubscriptionsTabStyle(style)

    suspend fun hydrateMissingDataStoreFromFastCache() = appearance.hydrateMissingDataStoreFromFastCache()

    val subscriptionSortStream: Flow<String> = library.subscriptionSortStream

    suspend fun setSubscriptionSort(sort: String) = library.setSort(Keys.SUBSCRIPTION_SORT, sort)

    val subscriptionFolderSortStream: Flow<String> = library.subscriptionFolderSortStream

    suspend fun setSubscriptionFolderSort(sort: String) = library.setSort(Keys.SUBSCRIPTION_FOLDER_SORT, sort)

    val subscriptionIntraFolderSortStream: Flow<String> = library.subscriptionIntraFolderSortStream

    suspend fun setSubscriptionIntraFolderSort(sort: String) = library.setSort(Keys.SUBSCRIPTION_INTRA_FOLDER_SORT, sort)

    val subscriptionManualOrderStream: Flow<List<String>> = library.subscriptionManualOrderStream

    suspend fun setSubscriptionManualOrder(ids: List<String>) = library.setSubscriptionManualOrder(ids)

    val subscriptionFolderManualOrderStream: Flow<List<String>> = library.subscriptionFolderManualOrderStream

    suspend fun setSubscriptionFolderManualOrder(ids: List<String>) = library.setSubscriptionFolderManualOrder(ids)

    val homePinnedPodcastIdsStream: Flow<List<String>> = library.homePinnedPodcastIdsStream

    suspend fun setHomePinnedPodcastIds(ids: List<String>) = library.setHomePinnedPodcastIds(ids)

    suspend fun toggleHomePinnedPodcastId(podcastId: String): HomePinnedShows.ToggleResult = library.toggleHomePinnedPodcastId(podcastId)

    suspend fun removePodcastIdFromManualOrderAndPins(podcastId: String) = library.removePodcastIdFromManualOrderAndPins(podcastId)

    suspend fun legacyRssRepairVersion(): Int = library.legacyRssRepairVersion()

    suspend fun markLegacyRssRepairVersion(version: Int) = library.markLegacyRssRepairVersion(version)

    suspend fun beginPodcastIdRepair(oldPodcastId: String, newPodcastId: String,) = library.beginPodcastIdRepair(oldPodcastId, newPodcastId)

    suspend fun pendingPodcastIdRepair(): PendingPodcastIdRepair? = library.pendingPodcastIdRepair()

    suspend fun finishPodcastIdRepair(oldPodcastId: String, newPodcastId: String,) = library.finishPodcastIdRepair(oldPodcastId, newPodcastId)

    suspend fun cancelPodcastIdRepair(oldPodcastId: String, newPodcastId: String,) = library.cancelPodcastIdRepair(oldPodcastId, newPodcastId)

    val latestEpisodesSortUseSmartStream: Flow<Boolean> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }.map { preferences ->
                preferences[Keys.LATEST_EPISODES_SORT_USE_SMART] ?: true
            }.distinctUntilChanged()

    suspend fun setLatestEpisodesSortUseSmart(useSmart: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.LATEST_EPISODES_SORT_USE_SMART] = useSmart
        }
    }

    /** Persisted playback speed — restored across app restarts. */
    val playbackSpeedStream: Flow<Float> =
        dataStore.data
            .map { preferences ->
                preferences[Keys.PLAYBACK_SPEED] ?: 1.0f
            }.catch { exception ->
                if (exception is IOException) emit(1.0f) else throw exception
            }.distinctUntilChanged()

    suspend fun setPlaybackSpeed(speed: Float) {
        dataStore.edit { preferences ->
            preferences[Keys.PLAYBACK_SPEED] = speed
        }
    }

    val skipBeginningMsStream: Flow<Long> =
        playbackDurationStream(
            Keys.SKIP_BEGINNING_MS,
            PlaybackSkipBounds.DEFAULT_SKIP_BEGINNING_MS,
        ) { PlaybackSkipBounds.sanitizeTrim(it) }

    val skipEndingMsStream: Flow<Long> =
        playbackDurationStream(
            Keys.SKIP_ENDING_MS,
            PlaybackSkipBounds.DEFAULT_SKIP_ENDING_MS,
        ) { PlaybackSkipBounds.sanitizeTrim(it) }

    val seekBackwardMsStream: Flow<Long> =
        playbackDurationStream(
            Keys.SEEK_BACKWARD_MS,
            PlaybackSkipBounds.DEFAULT_SEEK_BACKWARD_MS,
        ) { PlaybackSkipBounds.sanitizeSeekBackward(it) }

    val seekForwardMsStream: Flow<Long> =
        playbackDurationStream(
            Keys.SEEK_FORWARD_MS,
            PlaybackSkipBounds.DEFAULT_SEEK_FORWARD_MS,
        ) { PlaybackSkipBounds.sanitizeSeekForward(it) }

    /** Extra mini-player transport controls are opt-in; full-player seeking is always available. */
    val miniPlayerSeekButtonsEnabledStream: Flow<Boolean> = dataStore.data
        .map { preferences -> preferences[Keys.MINI_PLAYER_SEEK_BUTTONS_ENABLED] ?: false }
        .catch { exception ->
            if (exception is IOException) emit(false) else throw exception
        }.distinctUntilChanged()

    suspend fun setMiniPlayerSeekButtonsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.MINI_PLAYER_SEEK_BUTTONS_ENABLED] = enabled
        }
    }

    suspend fun setSkipBeginningMs(valueMs: Long) {
        setPlaybackDuration(Keys.SKIP_BEGINNING_MS, valueMs) {
            PlaybackSkipBounds.sanitizeTrim(it)
        }
    }

    suspend fun setSkipEndingMs(valueMs: Long) {
        setPlaybackDuration(Keys.SKIP_ENDING_MS, valueMs) {
            PlaybackSkipBounds.sanitizeTrim(it)
        }
    }

    suspend fun setSeekBackwardMs(valueMs: Long) {
        setPlaybackDuration(Keys.SEEK_BACKWARD_MS, valueMs) {
            PlaybackSkipBounds.sanitizeSeekBackward(it)
        }
    }

    suspend fun setSeekForwardMs(valueMs: Long) {
        setPlaybackDuration(Keys.SEEK_FORWARD_MS, valueMs) {
            PlaybackSkipBounds.sanitizeSeekForward(it)
        }
    }

    private fun playbackDurationStream(key: Preferences.Key<Long>, defaultValue: Long, sanitize: (Long) -> Long,): Flow<Long> = dataStore.data
        .map { preferences -> sanitize(preferences[key] ?: defaultValue) }
        .catch { exception ->
            if (exception is IOException) emit(defaultValue) else throw exception
        }.distinctUntilChanged()

    private suspend fun setPlaybackDuration(key: Preferences.Key<Long>, valueMs: Long, sanitize: (Long) -> Long,) {
        dataStore.edit { preferences ->
            preferences[key] = sanitize(valueMs)
        }
    }

    val skipBehaviorStream: Flow<String> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }.map { preferences ->
                preferences[Keys.SKIP_BEHAVIOR] ?: "just_skip"
            }.distinctUntilChanged()

    suspend fun setSkipBehavior(behavior: String) {
        dataStore.edit { preferences ->
            preferences[Keys.SKIP_BEHAVIOR] = behavior
        }
    }

    val hasSeenSwipeDismissTip: Flow<Boolean> = tips.hasSeenSwipeDismissTip

    val hasSeenTitleTapTip: Flow<Boolean> = tips.hasSeenTitleTapTip

    val hasSeenSwipeMinimizeTip: Flow<Boolean> = tips.hasSeenSwipeMinimizeTip

    suspend fun markSwipeDismissTipSeen() = tips.markSwipeDismissTipSeen()

    suspend fun markTitleTapTipSeen() = tips.markTitleTapTipSeen()

    suspend fun markSwipeMinimizeTipSeen() = tips.markSwipeMinimizeTipSeen()

    val hasSeenMarkPlayedTip: Flow<Boolean> = tips.hasSeenMarkPlayedTip

    suspend fun markMarkPlayedTipSeen() = tips.markMarkPlayedTipSeen()

    val hasSeenListeningHistoryTrackingNotice: Flow<Boolean> = tips.hasSeenListeningHistoryTrackingNotice

    suspend fun markListeningHistoryTrackingNoticeSeen() = tips.markListeningHistoryTrackingNoticeSeen()

    val hasLoggedFirstPlay: Flow<Boolean> = engagement.hasLoggedFirstPlay

    suspend fun markFirstPlayLogged() = engagement.markFirstPlayLogged()

    val dismissedFeatureVersion: Flow<String> = engagement.dismissedFeatureVersion

    suspend fun dismissFeatureAnnouncement(version: String) = engagement.dismissFeatureAnnouncement(version)

    data class Announcement(
        val title: String,
        val body: String,
        val route: String?,
        val imageUrl: String?,
        val actionLabel: String?,
        val showActionInApp: Boolean,
        val timestamp: Long,
        val category: String,
        val presentation: String = "compact",
        val tone: String = "primary",
        val imageStyle: String = "banner",
        val releaseAlert: Boolean? = null,
        val includePlay: Boolean = false,
        val testOnly: Boolean = false,
        val releaseVersionCode: Long = 0,
        val releaseUrl: String? = null,
    )

    val activeAnnouncementStream: Flow<Announcement?> = announcements.activeAnnouncementStream

    suspend fun setAnnouncement(announcement: Announcement) = announcements.setAnnouncement(announcement)

    suspend fun clearAnnouncement() = announcements.clearAnnouncement()

    val reviewHasReviewed: Flow<Boolean> = reviews.reviewHasReviewed

    suspend fun markReviewed() = reviews.markReviewed()

    suspend fun markReviewPromptShown() = reviews.markReviewPromptShown()

    suspend fun shouldShowReviewPrompt(isPlaying: Boolean): Boolean = reviews.shouldShowReviewPrompt(isPlaying)

    suspend fun syncReviewMilestonePending(completedCount: Int) = reviews.syncReviewMilestonePending(completedCount)

    suspend fun reviewMilestonePending(): Int? = reviews.reviewMilestonePending()

    suspend fun clearReviewMilestonePending() = reviews.clearReviewMilestonePending()

    suspend fun hasReviewedSync(): Boolean = reviews.hasReviewedSync()

    suspend fun recordEngagementPromptShown() = engagement.recordEngagementPromptShown()

    suspend fun isEngagementCooldownElapsed(): Boolean = engagement.isEngagementCooldownElapsed()

    suspend fun setNpsLastScore(score: Int) = engagement.setNpsLastScore(score)

    suspend fun npsLastScore(): Int? = engagement.npsLastScore()

    suspend fun setPromoterReviewPending(pending: Boolean) = engagement.setPromoterReviewPending(pending)

    suspend fun isPromoterReviewPending(): Boolean = engagement.isPromoterReviewPending()

    suspend fun markNpsSurveyPending(completedCount: Int) = engagement.markNpsSurveyPending(completedCount)

    suspend fun isNpsSurveyPending(): Boolean = engagement.isNpsSurveyPending()

    suspend fun hasNpsSurveyFired(): Boolean = engagement.hasNpsSurveyFired()

    suspend fun npsSurveyCompletedCount(): Int? = engagement.npsSurveyCompletedCount()

    suspend fun markNpsSurveyFired() = engagement.markNpsSurveyFired()

    val hideCompletedInFeedsStream: Flow<Boolean> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }.map { preferences ->
                preferences[Keys.HIDE_COMPLETED_IN_FEEDS] ?: true
            }.distinctUntilChanged()

    suspend fun setHideCompletedInFeeds(hide: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.HIDE_COMPLETED_IN_FEEDS] = hide
        }
    }

    val hideCompletedInShowDetailsStream: Flow<Boolean> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }.map { preferences ->
                preferences[Keys.HIDE_COMPLETED_IN_SHOW_DETAILS] ?: false
            }.distinctUntilChanged()

    suspend fun setHideCompletedInShowDetails(hide: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.HIDE_COMPLETED_IN_SHOW_DETAILS] = hide
        }
    }

    val hideCompletedInHomeStream: Flow<Boolean> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }.map { preferences ->
                preferences[Keys.HIDE_COMPLETED_IN_HOME] ?: true
            }.distinctUntilChanged()

    suspend fun setHideCompletedInHome(hide: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.HIDE_COMPLETED_IN_HOME] = hide
        }
    }

    val hideCompletedInSubsStream: Flow<Boolean> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }.map { preferences ->
                preferences[Keys.HIDE_COMPLETED_IN_SUBS] ?: true
            }.distinctUntilChanged()

    suspend fun setHideCompletedInSubs(hide: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.HIDE_COMPLETED_IN_SUBS] = hide
        }
    }

    val autoOrganizeFoldersStream: Flow<Boolean> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }.map { preferences ->
                preferences[Keys.AUTO_ORGANIZE_FOLDERS_ENABLED] ?: false
            }.distinctUntilChanged()

    suspend fun setAutoOrganizeFolders(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.AUTO_ORGANIZE_FOLDERS_ENABLED] = enabled
        }
    }

    /**
     * When true (default), implicit plays (queue / mixtape / Smart Queue / casual play) soft-expire
     * mid-episode seek after 7 days without playing. Explicit resume surfaces always seek.
     */
    val restartForgottenEpisodesStream: Flow<Boolean> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }.map { preferences ->
                preferences[Keys.RESTART_FORGOTTEN_EPISODES] ?: true
            }.distinctUntilChanged()

    suspend fun setRestartForgottenEpisodes(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.RESTART_FORGOTTEN_EPISODES] = enabled
        }
    }

    /**
     * When true, Smart Queue only continues the current show (newer/next episodes).
     * Other-show resume, subscription, rec, and trending fill stay off. Default false.
     */
    val sameShowQueueOnlyStream: Flow<Boolean> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }.map { preferences ->
                preferences[Keys.SAME_SHOW_QUEUE_ONLY] ?: false
            }.distinctUntilChanged()

    suspend fun setSameShowQueueOnly(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.SAME_SHOW_QUEUE_ONLY] = enabled
        }
    }

    /**
     * When true, Home hides Settings and Feedback; those shortcuts sit on Library instead.
     * Default false so Home keeps the current top-bar icons.
     */
    val homeShortcutsInLibraryStream: Flow<Boolean> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }.map { preferences ->
                preferences[Keys.HOME_SHORTCUTS_IN_LIBRARY] ?: false
            }.distinctUntilChanged()

    suspend fun setHomeShortcutsInLibrary(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.HOME_SHORTCUTS_IN_LIBRARY] = enabled
        }
    }

    val widgetAppearanceStream: Flow<String> = appearance.widgetAppearanceStream

    suspend fun setWidgetAppearance(appearance: String) = this.appearance.setWidgetAppearance(appearance)

    val overriddenRecPodcastIdStream: Flow<String?> = library.overriddenRecPodcastIdStream

    suspend fun setOverriddenRecPodcastId(podcastId: String?) = library.setOverriddenRecPodcastId(podcastId)

    val smartDownloadsEnabledStream: Flow<Boolean> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }.map { preferences ->
                preferences[Keys.SMART_DOWNLOADS_ENABLED] ?: false
            }.distinctUntilChanged()

    suspend fun setSmartDownloadsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.SMART_DOWNLOADS_ENABLED] = enabled
        }
    }

    val smartDownloadsMaxEpisodesStream: Flow<Int> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }.map { preferences ->
                preferences[Keys.SMART_DOWNLOADS_MAX_EPISODES] ?: 10
            }.distinctUntilChanged()

    suspend fun setSmartDownloadsMaxEpisodes(maxEpisodes: Int) {
        dataStore.edit { preferences ->
            preferences[Keys.SMART_DOWNLOADS_MAX_EPISODES] = maxEpisodes
        }
    }

    val smartDownloadsStorageBudgetStream: Flow<Long> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }.map { preferences ->
                preferences[Keys.SMART_DOWNLOADS_STORAGE_BUDGET] ?: 1000L
            }.distinctUntilChanged()

    suspend fun setSmartDownloadsStorageBudget(budgetMb: Long) {
        dataStore.edit { preferences ->
            preferences[Keys.SMART_DOWNLOADS_STORAGE_BUDGET] = budgetMb
        }
    }

    val smartDownloadsWifiOnlyStream: Flow<Boolean> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }.map { preferences ->
                preferences[Keys.SMART_DOWNLOADS_WIFI_ONLY] ?: true
            }.distinctUntilChanged()

    suspend fun setSmartDownloadsWifiOnly(wifiOnly: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.SMART_DOWNLOADS_WIFI_ONLY] = wifiOnly
        }
    }

    val smartDownloadsChargingOnlyStream: Flow<Boolean> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }.map { preferences ->
                preferences[Keys.SMART_DOWNLOADS_CHARGING_ONLY] ?: false
            }.distinctUntilChanged()

    suspend fun setSmartDownloadsChargingOnly(chargingOnly: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.SMART_DOWNLOADS_CHARGING_ONLY] = chargingOnly
        }
    }

    val smartDownloadsCleanupRuleStream: Flow<String> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }.map { preferences ->
                preferences[Keys.SMART_DOWNLOADS_CLEANUP_RULE] ?: "after_24h"
            }.distinctUntilChanged()

    suspend fun setSmartDownloadsCleanupRule(rule: String) {
        dataStore.edit { preferences ->
            preferences[Keys.SMART_DOWNLOADS_CLEANUP_RULE] = rule
        }
    }

    val smartDownloadsLastSyncTimeStream: Flow<Long> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }.map { preferences ->
                preferences[Keys.SMART_DOWNLOADS_LAST_SYNC_TIME] ?: 0L
            }.distinctUntilChanged()

    suspend fun setSmartDownloadsLastSyncTime(lastSyncTime: Long) {
        dataStore.edit { preferences ->
            preferences[Keys.SMART_DOWNLOADS_LAST_SYNC_TIME] = lastSyncTime
        }
    }

    val autoDownloadWifiOnlyStream: Flow<Boolean> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }.map { preferences ->
                preferences[Keys.AUTO_DOWNLOAD_WIFI_ONLY] ?: true
            }.distinctUntilChanged()

    suspend fun setAutoDownloadWifiOnly(wifiOnly: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.AUTO_DOWNLOAD_WIFI_ONLY] = wifiOnly
        }
    }

    val autoDownloadMaxEpisodesStream: Flow<Int> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }.map { preferences ->
                preferences[Keys.AUTO_DOWNLOAD_MAX_EPISODES] ?: 2
            }.distinctUntilChanged()

    suspend fun setAutoDownloadMaxEpisodes(maxEpisodes: Int) {
        dataStore.edit { preferences ->
            preferences[Keys.AUTO_DOWNLOAD_MAX_EPISODES] = maxEpisodes
        }
    }

    val autoDownloadDeleteCompletedStream: Flow<Boolean> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }.map { preferences ->
                preferences[Keys.AUTO_DOWNLOAD_DELETE_COMPLETED] ?: true
            }.distinctUntilChanged()

    suspend fun setAutoDownloadDeleteCompleted(deleteCompleted: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.AUTO_DOWNLOAD_DELETE_COMPLETED] = deleteCompleted
        }
    }

    val lastSeenEpisodesStream: Flow<Map<String, String>> = library.lastSeenEpisodesStream

    suspend fun setLastSeenEpisodeId(podcastId: String, episodeId: String,) = library.setLastSeenEpisodeId(podcastId, episodeId)

    suspend fun removeLastSeenEpisodeId(podcastId: String) = library.setLastSeenEpisodeId(podcastId, null)

    /** Last listener-selected Home mix: `daily` (default) or `offline`. */
    val homeMixModeStream: Flow<String> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }.map { preferences ->
                sanitizeHomeMixMode(preferences[Keys.HOME_MIX_MODE])
            }.distinctUntilChanged()

    suspend fun setHomeMixMode(mode: String) {
        dataStore.edit { preferences ->
            preferences[Keys.HOME_MIX_MODE] = sanitizeHomeMixMode(mode)
        }
    }

    private fun sanitizeHomeMixMode(mode: String?): String = if (mode == HOME_MIX_MODE_OFFLINE) mode else HOME_MIX_MODE_DAILY

    companion object {
        const val FONT_ROUNDNESS_CRISP = FontRoundnessAxis.CRISP
        const val FONT_ROUNDNESS_SOFT = FontRoundnessAxis.SOFT
        const val FONT_ROUNDNESS_ROUND = FontRoundnessAxis.ROUND
        private const val HOME_MIX_MODE_DAILY = "daily"
        private const val HOME_MIX_MODE_OFFLINE = "offline"
    }
}
