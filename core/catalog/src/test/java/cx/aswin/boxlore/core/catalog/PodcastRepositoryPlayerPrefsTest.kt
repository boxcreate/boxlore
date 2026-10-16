package cx.aswin.boxlore.core.catalog

import android.content.Context
import android.content.SharedPreferences
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.PodcastDao
import cx.aswin.boxlore.core.database.RssEpisodeDao
import cx.aswin.boxlore.core.network.NetworkModule
import cx.aswin.boxlore.core.prefs.PrefsFileMigrator
import cx.aswin.boxlore.core.rss.RssPodcastRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.nullable
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * Regression (startup ANR): the player preferences, and their migration from the legacy file, were
 * opened while [PodcastRepository] was constructed on the main thread. They now open on first use.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PodcastRepositoryPlayerPrefsTest {
    private val opened = mutableListOf<String>()

    @AfterEach
    fun tearDown() = RssPodcastRepository.clearInstanceForTests()

    @Test
    fun `construction does not open the player preferences, first use does`() {
        val context = recordingContext()
        val repository =
            PodcastRepository(
                baseUrl = "http://localhost/",
                publicKey = "test-app-key",
                context = context,
                rssRepository = RssPodcastRepository.createForTests(context = context, database = fakeDatabase()),
                ioDispatcher = UnconfinedTestDispatcher(),
                boxLoreApi = NetworkModule.createBoxLoreApi("http://localhost/", OkHttpClient()),
            )
        val playerFiles = setOf(PrefsFileMigrator.Files.PLAYER, PrefsFileMigrator.LegacyFiles.PLAYER)
        assertFalse(opened.any { it in playerFiles }, "Opened at construction: $opened")

        repository.getOrCreateDeviceUuid()

        assertTrue(PrefsFileMigrator.Files.PLAYER in opened, "Opened on first use: $opened")
    }

    /** A context whose preference files record their names as they are opened. */
    private fun recordingContext(): Context {
        val prefs = mock(SharedPreferences::class.java)
        val editor = mock(SharedPreferences.Editor::class.java)
        `when`(prefs.getString(anyString(), nullable(String::class.java))).thenReturn(null)
        `when`(prefs.getAll()).thenReturn(emptyMap())
        `when`(prefs.contains(anyString())).thenReturn(false)
        `when`(prefs.edit()).thenReturn(editor)
        `when`(editor.putString(anyString(), anyString())).thenReturn(editor)
        `when`(editor.commit()).thenReturn(true)
        `when`(editor.apply()).then { }
        val appContext = mock(Context::class.java)
        val context = mock(Context::class.java)
        listOf(appContext, context).forEach { target ->
            `when`(target.getSharedPreferences(anyString(), anyInt())).thenAnswer { call ->
                opened += call.getArgument<String>(0)
                prefs
            }
        }
        `when`(context.applicationContext).thenReturn(appContext)
        return context
    }

    private fun fakeDatabase(): BoxLoreDatabase {
        val database = mock(BoxLoreDatabase::class.java)
        `when`(database.podcastDao()).thenReturn(mock(PodcastDao::class.java))
        `when`(database.rssEpisodeDao()).thenReturn(mock(RssEpisodeDao::class.java))
        return database
    }
}
