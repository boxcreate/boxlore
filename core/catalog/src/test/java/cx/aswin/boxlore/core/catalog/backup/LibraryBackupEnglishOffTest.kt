package cx.aswin.boxlore.core.catalog.backup

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.google.gson.JsonParser
import cx.aswin.boxlore.core.catalog.FolderRepository
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.catalog.SubscriptionRepository
import cx.aswin.boxlore.core.catalog.ports.ListeningHistoryBackupPort
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.PodcastDao
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import cx.aswin.boxlore.core.prefs.userPreferencesDataStore
import cx.aswin.boxlore.core.ranking.AdaptiveRankingRepository
import cx.aswin.boxlore.core.rss.RssPodcastRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The "Include English" choice (D9) through the real backup manager: export, import, and older backups. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryBackupEnglishOffTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var prefs: UserPreferencesRepository
    private lateinit var manager: LibraryBackupManager

    private val noHistory =
        object : ListeningHistoryBackupPort {
            override fun getAllHistory(): Flow<List<ListeningHistoryEntity>> = flowOf(emptyList())

            override suspend fun upsertHistoryEntity(entity: ListeningHistoryEntity) = Unit

            override suspend fun markAllEpisodesCompleted(episodes: List<Episode>, podcastId: String, podcastTitle: String, podcastImageUrl: String?) = Unit
        }

    @Before
    fun setUp() {
        runBlocking { context.userPreferencesDataStore.edit { it.clear() } }
        prefs = UserPreferencesRepository(context)
        val podcastDao = mock(PodcastDao::class.java)
        `when`(podcastDao.getSubscribedPodcasts()).thenReturn(flowOf(emptyList()))
        manager =
            LibraryBackupManager(
                subscriptionRepository = SubscriptionRepository(podcastDao),
                listeningHistory = noHistory,
                podcastRepository = mock(PodcastRepository::class.java),
                userPrefs = prefs,
                context = context,
                adaptiveRankingRepository = mock(AdaptiveRankingRepository::class.java),
                rssPodcastRepository = mock(RssPodcastRepository::class.java),
                folderRepository = mock(FolderRepository::class.java),
            )
    }

    @After
    fun tearDown() {
        runBlocking { context.userPreferencesDataStore.edit { it.clear() } }
    }

    @Test
    fun englishOffAndAFourthPickSurviveExportAndImport() = runTest {
        prefs.setRegion("de")
        prefs.setContentLanguages(listOf("de"))
        prefs.setContentEnglishOff(true)
        prefs.setContentLanguages(listOf("de", "fr", "es", "nl"))
        val json = manager.exportLibraryAsJson()
        val global = JsonParser.parseString(json).asJsonObject.getAsJsonObject("globalPreferences")
        assertTrue(global.get("contentEnglishOff").asBoolean)

        context.userPreferencesDataStore.edit { it.clear() }
        manager.importLibraryFromJson(json)

        assertEquals(listOf("de", "fr", "es", "nl"), prefs.contentLanguagesStream.first())
        assertTrue(prefs.contentEnglishOffStream.first())
    }

    @Test
    fun anOlderBackupKeepsEnglishOn() = runTest {
        manager.importLibraryFromJson("""{"version":6,"subscriptions":[],"history":[],"globalPreferences":{"region":"de","contentLanguages":["en","de"]}}""")
        assertFalse(prefs.contentEnglishOffStream.first())
        assertEquals(listOf("en", "de"), prefs.contentLanguagesStream.first())
    }
}
