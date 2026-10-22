package cx.aswin.boxlore.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** `findOwners` through real Room: the check a catalog write runs before it may replace a row. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalEpisodeCatalogDaoOwnersTest {
    private lateinit var database: BoxLoreDatabase
    private lateinit var dao: LocalEpisodeCatalogDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java).allowMainThreadQueries().build()
        dao = database.localEpisodeCatalogDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun findOwnersReportsTheShowAndKeyOfEachRequestedId() = runTest {
        dao.upsertEpisodes(listOf(row("-11", show = "100", key = "guid-a"), row("-12", show = "100", key = "guid-b"), row("77", show = "200", key = "guid-c")))

        val owners = dao.findOwners(listOf("-11", "77", "-99")).sortedBy { it.episodeId }

        assertEquals(
            listOf(LocalEpisodeOwner("-11", "100", "guid-a"), LocalEpisodeOwner("77", "200", "guid-c")),
            owners,
        )
        assertTrue("an unknown ID has no owner", dao.findOwners(listOf("-99")).isEmpty())
    }

    private fun row(id: String, show: String, key: String) = LocalEpisodeEntity(
        episodeId = id,
        podcastId = show,
        guid = key,
        title = "Ep $id",
        description = "",
        audioUrl = "https://cdn.example/$id.mp3",
        imageUrl = null,
        duration = 60,
        publishedDate = 10L,
        chaptersUrl = null,
        transcriptUrl = null,
        transcripts = null,
        persons = null,
        seasonNumber = null,
        episodeNumber = null,
        episodeType = null,
        enclosureType = null,
    )
}
