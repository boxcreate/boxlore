package cx.aswin.boxlore.ui.announcement

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository.Announcement
import cx.aswin.boxlore.updates.releaseNotesBlocks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnouncementRenderTest {
    private fun alert() = Announcement("News", "Exact **copy**", null, null, null, false, 1, "ANNOUNCEMENT")

    @Test fun `release download stays in app and GitHub action uses a validated page`() {
        val value = alert().copy(releaseAlert = true, route = "https://github.com/boxcreate/boxlore/releases/download/v29/app.apk", releaseUrl = "https://evil.example/update")
        val items = announcementActions(value)
        assertEquals(listOf(AnnouncementActionKind.UPDATE, AnnouncementActionKind.GITHUB), items.map { it.kind })
        assertEquals("boxlore://updates/download", items[0].route)
        assertEquals("https://github.com/boxcreate/boxlore/releases/tag/v29", items[1].route)
    }

    @Test fun `ordinary announcements honour optional action visibility and exact custom labels`() {
        val value = alert().copy(route = "https://example.org", actionLabel = "Read the story")
        assertTrue(announcementActions(value).isEmpty())
        val action = announcementActions(value.copy(showActionInApp = true)).single()
        assertEquals(AnnouncementActionKind.CUSTOM, action.kind)
        assertEquals(value.route, action.route)
        assertEquals(value.actionLabel, action.label)
        assertTrue(announcementActions(value.copy(showActionInApp = true, route = " ")).isEmpty())
    }

    @Test fun `tone changes action and tag colours without replacing surfaces or text roles`() {
        val base = lightColorScheme(secondary = Color.Blue, secondaryContainer = Color.Cyan)
        val toned = announcementColors(base, "secondary")
        assertEquals(base.secondary, toned.primary)
        assertEquals(base.secondaryContainer, toned.primaryContainer)
        assertEquals(base.surfaceContainer, toned.surfaceContainer)
        assertEquals(base.onSurface, toned.onSurface)
        assertEquals(base, announcementColors(base, "unknown"))
    }

    @Test fun `announcement images retain the HTTPS only policy`() {
        assertEquals("https://example.org/cover.png", announcementImageUrl("https://example.org/cover.png"))
        listOf(null, "file:///storage/cover.png", "content://cover", "http://example.org/cover.png", "https://user:password@example.org/cover.png", "not a url").forEach { assertNull(announcementImageUrl(it)) }
    }

    @Test fun `native announcement Markdown preserves its links and styled callouts`() {
        val blocks = releaseNotesBlocks("## News\n\n> Important **information**\n\n[Discussion](https://github.com/boxcreate/boxlore/pull/1)", stripPrLinks = false)
        assertEquals(2, blocks[0].heading)
        assertTrue(blocks[1].callout)
        assertEquals("Important **information**", blocks[1].text)
        assertTrue(blocks.last().text.contains("/pull/1"))
    }
}
