package cx.aswin.boxlore.core.catalog.crosspromo

import cx.aswin.boxlore.core.model.CrossPromotionConfidence
import cx.aswin.boxlore.core.model.CrossPromotionIndicator
import cx.aswin.boxlore.core.model.Episode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CrossPromotionDetectorTest {
    private val detector = CrossPromotionDetector()
    private fun episode(title: String, duration: Int = 1800, type: String? = "full", description: String = "Discussion notes.") =
        Episode(id = "ep", title = title, audioUrl = "https://example.org/audio.mp3", duration = duration, episodeType = type, description = description)

    @Test
    fun `explicit feed exchanges work independently of duration or numbering`() {
        listOf("Feed Drop: Serial", "Trailer Swap - Serial", "Promo Drop || Serial", "Bonus Drop: Serial", "Feed Share: Serial", "Guest Feed: Serial", "Companion Show: Serial", "Network Premiere: Serial").forEach { title ->
            val result = detector.detect(episode(title), "Host")
            assertTrue(result.isCrossPromotion, title)
            assertEquals("Serial", result.extractedShowName)
            assertEquals(CrossPromotionConfidence.HIGH, result.confidence)
        }
    }

    @Test
    fun `introductions and previews require supporting evidence`() {
        listOf("Introducing: The Daily", "Listen Now: The Daily", "Special Preview: The Daily", "Introducing The Daily", "NPR Presents: The Daily", "Presented by NPR: The Daily", "From the creators of Serial: The Daily").forEach { title ->
            assertFalse(detector.detect(episode(title), "Host").isCrossPromotion, title)
            val result = detector.detect(episode(title, type = "trailer", description = "Subscribe to The Daily wherever you get podcasts."), "Host")
            assertTrue(result.isCrossPromotion, title)
            assertEquals("The Daily", result.extractedShowName)
            assertEquals(CrossPromotionConfidence.HIGH, result.confidence)
        }
    }

    @Test
    fun `short introduction is possible promotion while weak topic titles still need corroboration`() {
        val introduction = detector.detect(episode("Introducing The Daily Show", duration = 90), "Host")
        assertTrue(introduction.isCrossPromotion)
        assertEquals(CrossPromotionConfidence.MEDIUM, introduction.confidence)
        assertTrue(CrossPromotionIndicator.SHORT_DURATION in introduction.matchedIndicators)
        listOf("Discover: The Universe", "Meet: Our Guest", "New Season: Season 4", "Introducing Season 3").forEach {
            assertFalse(detector.detect(episode(it, duration = 90, type = "trailer"), "Host").isCrossPromotion, it)
        }
        val confirmed = detector.detect(episode("Discover: The Daily", description = "Listen to “The Daily” wherever you get podcasts."), "Host")
        assertTrue(confirmed.isCrossPromotion)
    }

    @Test
    fun `bonus unnumbered episodes and evergreen footer do not cause promotion`() {
        listOf("Episode 47: Introducing the New iPhone", "A conversation with Serial", "Bonus interview", "Morning headlines").forEach {
            assertFalse(detector.detect(episode(it, type = "bonus", description = "Listen to “Another Show” wherever you get podcasts."), "Host").isCrossPromotion, it)
        }
        assertFalse(detector.detect(episode("Short update", duration = 90, description = "Follow us on Instagram for more news."), "Host").isCrossPromotion)
    }

    @Test
    fun `short regular episode with another shows evergreen footer is not a preview`() {
        assertFalse(detector.detect(episode("Morning headlines", duration = 90, description = "Subscribe to Another Show wherever you get your podcasts."), "Host").isCrossPromotion)
        assertFalse(detector.detect(episode("Trailer", type = "trailer", description = "<p>Listen on <a href='https://podcasts.apple.com/us/podcast/id123'>Apple Podcasts</a></p>"), "Host").isCrossPromotion)
    }

    @Test
    fun `description supplies a name only with corroboration and retains the article The`() {
        val result = detector.detect(episode("A special preview", duration = 95, type = "trailer", description = "Subscribe to The Daily wherever you get your podcasts."), "Host")
        assertTrue(result.isCrossPromotion)
        assertEquals("The Daily", result.extractedShowName)
        val linked = detector.detect(episode("A special preview", type = "trailer", description = "<p>Listen to <a href='https://podcasts.apple.com/us/podcast/id123'>Échos du monde</a></p>"), "Host")
        assertTrue(linked.isCrossPromotion)
        assertEquals("Échos du monde", linked.extractedShowName)
    }

    @Test
    fun `self promotion uses exact normalized names rather than substring exclusion`() {
        assertFalse(detector.detect(episode("Feed Drop: THE DAILY"), "The Daily Podcast").isCrossPromotion)
        assertFalse(detector.detect(episode("Season trailer", type = "trailer", duration = 90, description = "Subscribe to Host Podcast for more episodes."), "Host").isCrossPromotion)
        assertTrue(detector.detect(episode("Feed Drop: Serial"), "Serial Killers").isCrossPromotion)
    }

    @Test
    fun `nine minute full introduction with a weekday schedule is detected`() {
        val title = "Introducing: CONSPIRACY THEORIES, CULTS, AND CRIMES"
        val description = "Listen to Conspiracy Theories, Cults, and Crimes every Wednesday as we explore the real people at the center of the world's most shocking secrets and nefarious organizations."
        listOf(description, "<p>$description</p>").forEach { notes ->
            val result = detector.detect(episode(title, duration = 558, description = notes), "Infamous America")
            assertTrue(result.isCrossPromotion)
            assertEquals("CONSPIRACY THEORIES, CULTS, AND CRIMES", result.extractedShowName)
            assertEquals(CrossPromotionConfidence.HIGH, result.confidence)
            assertTrue(CrossPromotionIndicator.DESCRIPTION_PROMO_LANGUAGE in result.matchedIndicators)
        }
    }

    @Test
    fun `known promoted names tolerate long sentences punctuation and schedules`() {
        listOf("every Thursday", "weekly", "as we meet people " + "with unexpected stories ".repeat(10), ". New episodes arrive soon.").forEach { continuation ->
            val result = detector.detect(episode("Introducing: Science with Alice", description = "Listen to our new podcast Science with Alice $continuation", duration = 1200), "Host")
            assertTrue(result.isCrossPromotion, continuation)
            assertEquals("Science with Alice", result.extractedShowName)
        }
        assertTrue(detector.detect(episode("Introducing: Cults and Crimes", description = "Listen to Cults & Crimes every Friday."), "Host").isCrossPromotion)
        assertTrue(detector.detect(episode("Introducing: The Podcast Industry", description = "Listen to The Podcast Industry as we meet people " + "with unexpected stories ".repeat(10)), "Host").isCrossPromotion)
        assertFalse(detector.detect(episode("Introducing: Cults & Crimes", description = "Listen to Cults and Crimes every Friday."), "Cults and Crimes").isCrossPromotion)
    }

    @Test
    fun `description cannot corroborate a shorter show prefix or an unrelated mention`() {
        listOf("Listen to Serial Killers every Wednesday.", "We discuss Serial on this episode.", "Follow us on Instagram for more Serial updates.").forEach { description ->
            assertFalse(detector.detect(episode("Introducing: Serial", description = description), "Host").isCrossPromotion, description)
        }
        assertFalse(detector.detect(episode("Morning headlines", duration = 90, description = "Listen to Another Show every Wednesday."), "Host").isCrossPromotion)
    }

    @Test
    fun `wrapped feed exchanges and quoted or scheduled descriptions keep their names`() {
        listOf("[Feed Drop]: Another Show", "(Feed Drop): Another Show", "*Feed Drop*: Another Show").forEach {
            assertEquals("Another Show", detector.detect(episode(it), "Host").extractedShowName)
        }
        listOf("Follow our new podcast Another Show every Monday", "Listen to Another Show on Apple Podcasts", "Check out our show Another Show for more stories", "Introducing our new podcast “Another Show” today").forEach {
            assertEquals("Another Show", detector.detect(episode("A special preview", type = "trailer", description = it), "Host").extractedShowName, it)
        }
    }
}
