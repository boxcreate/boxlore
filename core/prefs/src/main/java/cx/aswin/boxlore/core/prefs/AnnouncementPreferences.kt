package cx.aswin.boxlore.core.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository.Announcement
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Internal storage helper; the repository remains the public entry point. */
internal class AnnouncementPreferences(
    private val dataStore: DataStore<Preferences>,
) {
    // --- ANNOUNCEMENT PREFERENCES ---
    private object AnnouncementKeys {
        val TITLE = stringPreferencesKey("announcement_title")
        val BODY = stringPreferencesKey("announcement_body")
        val ROUTE = stringPreferencesKey("announcement_route")
        val IMAGE_URL = stringPreferencesKey("announcement_image_url")
        val ACTION_LABEL = stringPreferencesKey("announcement_action_label")
        val SHOW_ACTION_IN_APP =
            androidx.datastore.preferences.core
                .booleanPreferencesKey("announcement_show_action_in_app")
        val TIMESTAMP =
            androidx.datastore.preferences.core
                .longPreferencesKey("announcement_timestamp")
        val CATEGORY = stringPreferencesKey("announcement_category")
        val PRESENTATION = stringPreferencesKey("announcement_presentation")
        val TONE = stringPreferencesKey("announcement_tone")
        val IMAGE_STYLE = stringPreferencesKey("announcement_image_style")
        val RELEASE = booleanPreferencesKey("announcement_release")
        val INCLUDE_PLAY = booleanPreferencesKey("announcement_include_play")
        val TEST_ONLY = booleanPreferencesKey("announcement_test_only")
        val RELEASE_CODE = longPreferencesKey("announcement_release_code")
        val RELEASE_URL = stringPreferencesKey("announcement_release_url")
    }

    val activeAnnouncementStream: Flow<Announcement?> =
        dataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map { pref ->
                val title = pref[AnnouncementKeys.TITLE]
                val body = pref[AnnouncementKeys.BODY]
                if (!title.isNullOrBlank() && !body.isNullOrBlank()) {
                    Announcement(
                        title = title,
                        body = body,
                        route = pref[AnnouncementKeys.ROUTE],
                        imageUrl = pref[AnnouncementKeys.IMAGE_URL],
                        actionLabel = pref[AnnouncementKeys.ACTION_LABEL],
                        showActionInApp = pref[AnnouncementKeys.SHOW_ACTION_IN_APP] ?: true,
                        timestamp = pref[AnnouncementKeys.TIMESTAMP] ?: 0L,
                        category = pref[AnnouncementKeys.CATEGORY] ?: "WHAT'S NEW",
                        presentation = pref[AnnouncementKeys.PRESENTATION] ?: "compact",
                        tone = pref[AnnouncementKeys.TONE] ?: "primary",
                        imageStyle = pref[AnnouncementKeys.IMAGE_STYLE] ?: "banner",
                        releaseAlert = pref[AnnouncementKeys.RELEASE],
                        includePlay = pref[AnnouncementKeys.INCLUDE_PLAY] ?: false,
                        testOnly = pref[AnnouncementKeys.TEST_ONLY] ?: false,
                        releaseVersionCode = pref[AnnouncementKeys.RELEASE_CODE] ?: 0,
                        releaseUrl = pref[AnnouncementKeys.RELEASE_URL],
                    )
                } else {
                    null
                }
            }.distinctUntilChanged()

    suspend fun setAnnouncement(announcement: Announcement) {
        dataStore.edit {
            it[AnnouncementKeys.TITLE] = announcement.title
            it[AnnouncementKeys.BODY] = announcement.body
            if (announcement.route != null) it[AnnouncementKeys.ROUTE] = announcement.route else it.remove(AnnouncementKeys.ROUTE)
            if (announcement.imageUrl !=
                null
            ) {
                it[AnnouncementKeys.IMAGE_URL] = announcement.imageUrl
            } else {
                it.remove(AnnouncementKeys.IMAGE_URL)
            }
            if (announcement.actionLabel !=
                null
            ) {
                it[AnnouncementKeys.ACTION_LABEL] = announcement.actionLabel
            } else {
                it.remove(AnnouncementKeys.ACTION_LABEL)
            }
            it[AnnouncementKeys.SHOW_ACTION_IN_APP] = announcement.showActionInApp
            it[AnnouncementKeys.CATEGORY] = announcement.category
            it[AnnouncementKeys.TIMESTAMP] = announcement.timestamp
            it[AnnouncementKeys.PRESENTATION] = announcement.presentation
            it[AnnouncementKeys.TONE] = announcement.tone
            it[AnnouncementKeys.IMAGE_STYLE] = announcement.imageStyle
            announcement.releaseAlert?.let { value -> it[AnnouncementKeys.RELEASE] = value } ?: it.remove(AnnouncementKeys.RELEASE)
            it[AnnouncementKeys.INCLUDE_PLAY] = announcement.includePlay
            it[AnnouncementKeys.TEST_ONLY] = announcement.testOnly
            it[AnnouncementKeys.RELEASE_CODE] = announcement.releaseVersionCode
            announcement.releaseUrl?.let { url -> it[AnnouncementKeys.RELEASE_URL] = url } ?: it.remove(AnnouncementKeys.RELEASE_URL)
        }
    }

    suspend fun clearAnnouncement() {
        dataStore.edit { pref ->
            pref.remove(AnnouncementKeys.TITLE)
            pref.remove(AnnouncementKeys.BODY)
            pref.remove(AnnouncementKeys.ROUTE)
            pref.remove(AnnouncementKeys.IMAGE_URL)
            pref.remove(AnnouncementKeys.ACTION_LABEL)
            pref.remove(AnnouncementKeys.SHOW_ACTION_IN_APP)
            pref.remove(AnnouncementKeys.CATEGORY)
            pref.remove(AnnouncementKeys.TIMESTAMP)
            pref.remove(AnnouncementKeys.PRESENTATION)
            pref.remove(AnnouncementKeys.TONE)
            pref.remove(AnnouncementKeys.IMAGE_STYLE)
            pref.remove(AnnouncementKeys.RELEASE)
            pref.remove(AnnouncementKeys.INCLUDE_PLAY)
            pref.remove(AnnouncementKeys.TEST_ONLY)
            pref.remove(AnnouncementKeys.RELEASE_CODE)
            pref.remove(AnnouncementKeys.RELEASE_URL)
        }
    }
}
