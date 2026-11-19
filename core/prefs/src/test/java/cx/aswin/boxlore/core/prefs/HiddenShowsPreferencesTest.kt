package cx.aswin.boxlore.core.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.model.HiddenShow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The hidden shows' DataStore list: order, aliases, names that hold separators, the cap and older rows. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HiddenShowsPreferencesTest {
    private lateinit var context: Context
    private lateinit var repository: UserPreferencesRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        runBlocking { context.userPreferencesDataStore.edit { it.clear() } }
        repository = UserPreferencesRepository(context)
    }

    @After
    fun tearDown() {
        runBlocking { context.userPreferencesDataStore.edit { it.clear() } }
    }

    @Test
    fun `hiding keeps the newest first, survives a new repository and matches aliases`() = runTest {
        val hidden = repository.hiddenShows
        hidden.hide(HiddenShow("1", "First", "https://art/1"))
        hidden.hide(HiddenShow("rss:https://feed.example/a", "Feed copy", "", aliasIds = setOf("42")))

        val reopened = UserPreferencesRepository(context).hiddenShows
        assertEquals(listOf("rss:https://feed.example/a", "1"), reopened.showsStream.first().map(HiddenShow::id))
        assertEquals(setOf("rss:https://feed.example/a", "42", "1"), reopened.matchIds())
        assertEquals(HiddenShow("1", "First", "https://art/1"), reopened.showsStream.first().last())

        // Hiding again moves it to the front with its newer name.
        hidden.hide(HiddenShow("1", "First, renamed"))
        assertEquals(listOf("1", "rss:https://feed.example/a"), hidden.showsStream.first().map(HiddenShow::id))
        assertEquals("First, renamed", hidden.showsStream.first().first().title)
    }

    @Test
    fun `unhiding by any of a show's IDs removes it and says which shows went`() = runTest {
        val hidden = repository.hiddenShows
        hidden.hide(HiddenShow("rss:https://feed.example/a", aliasIds = setOf("42")))
        hidden.hide(HiddenShow("7"))

        val removed = hidden.unhide(setOf("42"))

        assertEquals(listOf("rss:https://feed.example/a"), removed.map(HiddenShow::id))
        assertEquals(setOf("7"), hidden.matchIds())
        assertTrue(hidden.unhide(setOf("unknown")).isEmpty())
        hidden.unhide(setOf("7"))
        assertTrue(hidden.showsStream.first().isEmpty())
    }

    @Test
    fun `names and IDs with the list separators round-trip without breaking other rows`() = runTest {
        val hidden = repository.hiddenShows
        hidden.setAll(
            listOf(
                HiddenShow("1", "Odd\u001ename\u001d with\u001f marks", "https://art/1?a=1,b=2"),
                HiddenShow("2", "Plain"),
                HiddenShow("  ", "No ID"),
                HiddenShow("2", "Duplicate"),
            ),
        )
        val shows = hidden.showsStream.first()
        assertEquals(listOf("1", "2"), shows.map(HiddenShow::id))
        assertEquals("Oddname with marks", shows[0].title)
        assertEquals("https://art/1?a=1,b=2", shows[0].imageUrl)
        assertEquals("Plain", shows[1].title)
    }

    @Test
    fun `an ID without details is still hidden, and the list keeps the newest 500`() = runTest {
        context.userPreferencesDataStore.edit { it[stringPreferencesKey("hidden_podcast_ids")] = PreferenceIdList.encode(listOf("9")) }
        assertEquals(listOf(HiddenShow("9")), repository.hiddenShows.showsStream.first())

        repository.hiddenShows.setAll(List(HiddenShowsCodec.MAX + 20) { HiddenShow("s$it") })
        val shows = repository.hiddenShows.showsStream.first()
        assertEquals(HiddenShowsCodec.MAX, shows.size)
        assertEquals("s0", shows.first().id)

        repository.hiddenShows.setAll(emptyList())
        assertTrue(repository.hiddenShows.matchIds().isEmpty())
    }
}
