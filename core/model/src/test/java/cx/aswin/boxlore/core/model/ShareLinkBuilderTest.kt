package cx.aswin.boxlore.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ShareLinkBuilderTest {
    @Test
    fun `podcast link encodes id`() {
        assertEquals(
            "https://boxlore.in/share?type=podcast&id=42",
            ShareLinkBuilder.podcast("42"),
        )
    }

    @Test
    fun `episode link without timestamp omits query param`() {
        assertEquals(
            "https://boxlore.in/share?type=episode&id=ep-1",
            ShareLinkBuilder.episode("ep-1"),
        )
    }

    @Test
    fun `episode link converts timestamp to seconds`() {
        assertEquals(
            "https://boxlore.in/share?type=episode&id=ep-1&t=90",
            ShareLinkBuilder.episode("ep-1", timestampMs = 90_500),
        )
    }

    @Test
    fun `episode link prefers clip range over timestamp`() {
        assertEquals(
            "https://boxlore.in/share?type=episode&id=ep-1&start=10&end=20",
            ShareLinkBuilder.episode("ep-1", timestampMs = 5_000, startMs = 10_000, endMs = 20_000),
        )
    }

    @Test
    fun `build dispatches by type`() {
        assertEquals(ShareLinkBuilder.podcast("p"), ShareLinkBuilder.build("podcast", "p"))
        assertEquals(
            ShareLinkBuilder.episode("e", timestampMs = 1_000),
            ShareLinkBuilder.build("episode", "e", timestampMs = 1_000),
        )
    }

    @Test
    fun `episode link names its show before the timestamp or clip`() {
        assertEquals(
            "https://boxlore.in/share?type=episode&id=4503599627370500&show=920666",
            ShareLinkBuilder.episode("4503599627370500", showId = "920666"),
        )
        assertEquals(
            "https://boxlore.in/share?type=episode&id=4503599627370500&show=920666&t=90",
            ShareLinkBuilder.episode("4503599627370500", timestampMs = 90_000, showId = "920666"),
        )
        assertEquals(
            "https://boxlore.in/share?type=episode&id=4503599627370500&show=920666&start=10&end=20",
            ShareLinkBuilder.episode("4503599627370500", startMs = 10_000, endMs = 20_000, showId = "920666"),
        )
        assertEquals(ShareLinkBuilder.episode("ep-1"), ShareLinkBuilder.episode("ep-1", showId = " "))
    }

    @Test
    fun `a share target becomes an episode link with its show, or the show's own link`() {
        assertEquals(
            "https://boxlore.in/share?type=episode&id=4503599627370500&show=920666&t=5",
            ShareLinkBuilder.forTarget(EpisodeShareTarget.Episode("4503599627370500", "920666"), timestampMs = 5_000),
        )
        assertEquals("https://boxlore.in/share?type=episode&id=-42", ShareLinkBuilder.forTarget(EpisodeShareTarget.Episode("-42", null)))
        assertEquals(ShareLinkBuilder.podcast("920666"), ShareLinkBuilder.forTarget(EpisodeShareTarget.Show("920666"), timestampMs = 5_000))
    }
}
