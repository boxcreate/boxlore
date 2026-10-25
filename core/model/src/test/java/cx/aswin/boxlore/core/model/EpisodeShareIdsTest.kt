package cx.aswin.boxlore.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Which ID an episode share link carries (own-IDs plan section 9, links). */
class EpisodeShareIdsTest {
    private val recipe = EpisodeRecipeId.recipeId(920666L, "guid-1")

    @Test
    fun `an episode of a Podcast Index show is shared by its recipe ID with its show`() {
        assertEquals(EpisodeShareTarget.Episode(recipe.toString(), "920666"), EpisodeShareIds.choose("5001", "920666", recipe))
        assertEquals(EpisodeShareTarget.Episode(recipe.toString(), "920666"), EpisodeShareIds.choose("-77", "920666", recipe))
    }

    @Test
    fun `without a recipe ID the episode's own recipe-range or Podcast Index ID is shared with its show`() {
        assertEquals(EpisodeShareTarget.Episode(recipe.toString(), "920666"), EpisodeShareIds.choose(recipe.toString(), "920666", null))
        assertEquals(EpisodeShareTarget.Episode("5001", "920666"), EpisodeShareIds.choose("5001", "920666", null))
    }

    @Test
    fun `a phone-only or reserved ID is never shared, the show is`() {
        assertEquals(EpisodeShareTarget.Show("920666"), EpisodeShareIds.choose("-4611686018427387904", "920666", null))
        assertEquals(EpisodeShareTarget.Show("920666"), EpisodeShareIds.choose("-1", "920666", 12L))
    }

    @Test
    fun `a show added by its feed address, or with no usable show, keeps today's link`() {
        assertEquals(EpisodeShareTarget.Episode("-99", null), EpisodeShareIds.choose("-99", "rss:abc", null))
        assertEquals(EpisodeShareTarget.Episode("ep-1", null), EpisodeShareIds.choose("ep-1", null, recipe))
        assertEquals(EpisodeShareTarget.Episode("5001", null), EpisodeShareIds.choose("5001", "0920666", null))
        // A show ID in the recipe range is not a Podcast Index show.
        assertEquals(EpisodeShareTarget.Episode("5001", null), EpisodeShareIds.choose("5001", recipe.toString(), null))
    }
}
