package cx.aswin.boxlore.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** [knownRecipeIds] for analytics (P4 PR 18): an ID's own recipe ID or a trusted, keyed alias's, never a guess. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EpisodeRecipeIdReadsTest {
    private lateinit var database: BoxLoreDatabase
    private lateinit var dao: EpisodeIdentityDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java).allowMainThreadQueries().build()
        dao = database.episodeIdentityDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun onlyRecipeRangeIdsAndTrustedKeyedAliasesAnswer() = runTest {
        dao.upsertAlias(alias("101", key = "guid-a", recipe = RECIPE, state = EpisodeAliasState.TRUSTED))
        dao.upsertAlias(alias("102", key = "guid-b", recipe = RECIPE + 1, state = EpisodeAliasState.AMBIGUOUS))
        dao.upsertAlias(alias("103", key = null, recipe = RECIPE + 2, state = EpisodeAliasState.UNRESOLVED))
        dao.upsertAlias(alias("104", key = "guid-d", recipe = 77L, state = EpisodeAliasState.TRUSTED))

        val found = dao.knownRecipeIds(listOf("101", "102", "103", "104", "105", " $RECIPE ", ""))

        assertEquals(mapOf("101" to RECIPE, RECIPE.toString() to RECIPE), found)
    }

    private fun alias(id: String, key: String?, recipe: Long?, state: String) = EpisodeAliasEntity(
        aliasId = id,
        podcastId = "920666",
        catalogKey = key,
        recipeId = recipe,
        state = state,
        provenance = EpisodeAliasProvenance.UPGRADE_CATALOG,
    )

    private companion object {
        const val RECIPE = 4_503_599_627_370_500L
    }
}
