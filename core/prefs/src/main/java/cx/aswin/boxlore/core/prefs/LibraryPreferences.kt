package cx.aswin.boxlore.core.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Internal storage helper; the repository remains the public entry point. */
internal class LibraryPreferences(
    private val dataStore: DataStore<Preferences>,
) {
    private companion object {
        const val LAST_SEEN_EPISODE_ID_PREFIX = "last_seen_episode_id_"
    }

    // SORTING PREFERENCES
    suspend fun setSort(key: Preferences.Key<String>, sort: String) {
        dataStore.edit { it[key] = sort }
    }

    val subscriptionSortStream: Flow<String> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }.map { preferences ->
                preferences[Keys.SUBSCRIPTION_SORT] ?: "SmartRank"
            }.distinctUntilChanged()

    val subscriptionFolderSortStream: Flow<String> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }.map { preferences ->
                preferences[Keys.SUBSCRIPTION_FOLDER_SORT] ?: "Inherit"
            }.distinctUntilChanged()

    val subscriptionIntraFolderSortStream: Flow<String> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }.map { preferences ->
                preferences[Keys.SUBSCRIPTION_INTRA_FOLDER_SORT] ?: "Inherit"
            }.distinctUntilChanged()

    val subscriptionManualOrderStream: Flow<List<String>> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }.map { preferences ->
                PreferenceIdList.decode(preferences[Keys.SUBSCRIPTION_MANUAL_ORDER])
            }.distinctUntilChanged()

    suspend fun setSubscriptionManualOrder(ids: List<String>) {
        dataStore.edit { preferences ->
            preferences[Keys.SUBSCRIPTION_MANUAL_ORDER] = PreferenceIdList.encode(ids)
        }
    }

    val subscriptionFolderManualOrderStream: Flow<List<String>> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }.map { preferences ->
                PreferenceIdList.decode(preferences[Keys.SUBSCRIPTION_FOLDER_MANUAL_ORDER])
            }.distinctUntilChanged()

    suspend fun setSubscriptionFolderManualOrder(ids: List<String>) {
        dataStore.edit { preferences ->
            preferences[Keys.SUBSCRIPTION_FOLDER_MANUAL_ORDER] = PreferenceIdList.encode(ids)
        }
    }

    val homePinnedPodcastIdsStream: Flow<List<String>> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }.map { preferences ->
                HomePinnedShows.sanitize(PreferenceIdList.decode(preferences[Keys.HOME_PINNED_PODCAST_IDS]))
            }.distinctUntilChanged()

    suspend fun setHomePinnedPodcastIds(ids: List<String>) {
        dataStore.edit { preferences ->
            preferences[Keys.HOME_PINNED_PODCAST_IDS] =
                PreferenceIdList.encode(HomePinnedShows.sanitize(ids))
        }
    }

    /**
     * Sanitizes, toggles, and persists Home pins in one DataStore write.
     * [HomePinnedShows.ToggleResult.AtCapacity] leaves the stored list unchanged.
     */
    suspend fun toggleHomePinnedPodcastId(podcastId: String): HomePinnedShows.ToggleResult {
        var result = HomePinnedShows.ToggleResult.Unpinned
        dataStore.edit { preferences ->
            val current =
                HomePinnedShows.sanitize(
                    PreferenceIdList.decode(preferences[Keys.HOME_PINNED_PODCAST_IDS]),
                )
            val (next, toggleResult) = HomePinnedShows.toggle(current, podcastId)
            result = toggleResult
            if (toggleResult != HomePinnedShows.ToggleResult.AtCapacity) {
                preferences[Keys.HOME_PINNED_PODCAST_IDS] = PreferenceIdList.encode(next)
            }
        }
        return result
    }

    /** Drops an unsubscribed show from Manual order and Home pins without touching other prefs. */
    suspend fun removePodcastIdFromManualOrderAndPins(podcastId: String) {
        val id = podcastId.trim()
        if (id.isEmpty()) return
        dataStore.edit { preferences ->
            val prevOrder = PreferenceIdList.decode(preferences[Keys.SUBSCRIPTION_MANUAL_ORDER])
            if (id in prevOrder) {
                preferences[Keys.SUBSCRIPTION_MANUAL_ORDER] =
                    PreferenceIdList.encode(prevOrder.filter { it != id })
            }
            val prevPins =
                HomePinnedShows.sanitize(PreferenceIdList.decode(preferences[Keys.HOME_PINNED_PODCAST_IDS]))
            if (id in prevPins) {
                preferences[Keys.HOME_PINNED_PODCAST_IDS] =
                    PreferenceIdList.encode(prevPins.filter { it != id })
            }
        }
    }

    /** Versioned one-time repair gate; a failed/incomplete pass deliberately leaves this unchanged. */
    suspend fun legacyRssRepairVersion(): Int = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }.first()[Keys.LEGACY_RSS_REPAIR_VERSION] ?: 0

    suspend fun markLegacyRssRepairVersion(version: Int) {
        dataStore.edit { preferences ->
            val current = preferences[Keys.LEGACY_RSS_REPAIR_VERSION] ?: 0
            if (version > current) preferences[Keys.LEGACY_RSS_REPAIR_VERSION] = version
        }
    }

    /**
     * Journals the cross-store ID rewrite before the Room transaction. If the process stops after
     * Room commits, the next launch can still repair Manual order, pins, and last-seen state.
     */
    suspend fun beginPodcastIdRepair(oldPodcastId: String, newPodcastId: String,) {
        dataStore.edit { preferences ->
            preferences[Keys.LEGACY_RSS_REPAIR_PENDING_OLD_ID] = oldPodcastId
            preferences[Keys.LEGACY_RSS_REPAIR_PENDING_NEW_ID] = newPodcastId
        }
    }

    suspend fun pendingPodcastIdRepair(): PendingPodcastIdRepair? = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }.first()
        .let { preferences ->
            val oldId = preferences[Keys.LEGACY_RSS_REPAIR_PENDING_OLD_ID]
            val newId = preferences[Keys.LEGACY_RSS_REPAIR_PENDING_NEW_ID]
            if (oldId.isNullOrBlank() || newId.isNullOrBlank()) {
                null
            } else {
                PendingPodcastIdRepair(oldId, newId)
            }
        }

    /** Atomically rewrites every DataStore preference keyed by a podcast ID and clears the journal. */
    suspend fun finishPodcastIdRepair(oldPodcastId: String, newPodcastId: String,) {
        dataStore.edit { preferences ->
            if (preferences[Keys.LEGACY_RSS_REPAIR_PENDING_OLD_ID] != oldPodcastId ||
                preferences[Keys.LEGACY_RSS_REPAIR_PENDING_NEW_ID] != newPodcastId
            ) {
                return@edit
            }
            val manualOrder = PreferenceIdList.decode(preferences[Keys.SUBSCRIPTION_MANUAL_ORDER])
            preferences[Keys.SUBSCRIPTION_MANUAL_ORDER] =
                PreferenceIdList.encode(manualOrder.map { if (it == oldPodcastId) newPodcastId else it }.distinct())

            val pins =
                HomePinnedShows.sanitize(
                    PreferenceIdList.decode(preferences[Keys.HOME_PINNED_PODCAST_IDS]),
                )
            preferences[Keys.HOME_PINNED_PODCAST_IDS] =
                PreferenceIdList.encode(
                    HomePinnedShows.sanitize(
                        pins.map { if (it == oldPodcastId) newPodcastId else it },
                    ),
                )
            if (preferences[Keys.OVERRIDDEN_REC_PODCAST_ID] == oldPodcastId) {
                preferences[Keys.OVERRIDDEN_REC_PODCAST_ID] = newPodcastId
            }

            val oldLastSeenKey = stringPreferencesKey("$LAST_SEEN_EPISODE_ID_PREFIX$oldPodcastId")
            val newLastSeenKey = stringPreferencesKey("$LAST_SEEN_EPISODE_ID_PREFIX$newPodcastId")
            val oldLastSeen = preferences[oldLastSeenKey]
            if (oldLastSeen != null && preferences[newLastSeenKey] == null) {
                preferences[newLastSeenKey] = oldLastSeen
            }
            preferences.remove(oldLastSeenKey)
            preferences.remove(Keys.LEGACY_RSS_REPAIR_PENDING_OLD_ID)
            preferences.remove(Keys.LEGACY_RSS_REPAIR_PENDING_NEW_ID)
        }
    }

    suspend fun cancelPodcastIdRepair(oldPodcastId: String, newPodcastId: String,) {
        dataStore.edit { preferences ->
            if (preferences[Keys.LEGACY_RSS_REPAIR_PENDING_OLD_ID] == oldPodcastId &&
                preferences[Keys.LEGACY_RSS_REPAIR_PENDING_NEW_ID] == newPodcastId
            ) {
                preferences.remove(Keys.LEGACY_RSS_REPAIR_PENDING_OLD_ID)
                preferences.remove(Keys.LEGACY_RSS_REPAIR_PENDING_NEW_ID)
            }
        }
    }

    val overriddenRecPodcastIdStream: Flow<String?> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }.map { preferences ->
                preferences[Keys.OVERRIDDEN_REC_PODCAST_ID]
            }.distinctUntilChanged()

    suspend fun setOverriddenRecPodcastId(podcastId: String?) {
        dataStore.edit { preferences ->
            if (podcastId == null) {
                preferences.remove(Keys.OVERRIDDEN_REC_PODCAST_ID)
            } else {
                preferences[Keys.OVERRIDDEN_REC_PODCAST_ID] = podcastId
            }
        }
    }

    val lastSeenEpisodesStream: Flow<Map<String, String>> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }.map { preferences ->
                preferences
                    .asMap()
                    .entries
                    .filter { it.key.name.startsWith(LAST_SEEN_EPISODE_ID_PREFIX) }
                    .mapNotNull { entry ->
                        val value = entry.value as? String
                        if (value != null) {
                            entry.key.name.removePrefix(LAST_SEEN_EPISODE_ID_PREFIX) to value
                        } else {
                            null
                        }
                    }.toMap()
            }.distinctUntilChanged()

    suspend fun setLastSeenEpisodeId(podcastId: String, episodeId: String?) {
        dataStore.edit { preferences ->
            val key = stringPreferencesKey("$LAST_SEEN_EPISODE_ID_PREFIX$podcastId")
            if (episodeId == null) preferences.remove(key) else preferences[key] = episodeId
        }
    }
}
