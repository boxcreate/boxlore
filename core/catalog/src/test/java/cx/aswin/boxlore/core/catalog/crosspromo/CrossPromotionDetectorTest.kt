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
    private fun episode(title: String, duration: Int = 1800, type: String? = "full", description: String = "Discussion notes.", artwork: Boolean = false) =
        Episode(
            id = "ep",
            title = title,
            audioUrl = "https://example.org/audio.mp3",
            duration = duration,
            episodeType = type,
            description = description,
            imageUrl = if (artwork) "https://art.example/episode.jpg" else null,
            podcastImageUrl = "https://art.example/show.jpg",
        )

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
    fun `delimited introductions are possible promotions while presenters and plain introductions need evidence`() {
        listOf("Introducing: The Daily", "Listen Now: The Daily", "Special Preview: The Daily").forEach { title ->
            val result = detector.detect(episode(title), "Host")
            assertTrue(result.isCrossPromotion, title)
            assertEquals("The Daily", result.extractedShowName)
            assertEquals(CrossPromotionConfidence.MEDIUM, result.confidence)
        }
        listOf("Introducing The Daily", "NPR Presents: The Daily", "Presented by NPR: The Daily", "From the creators of Serial: The Daily").forEach { title ->
            assertFalse(detector.detect(episode(title), "Host").isCrossPromotion, title)
        }
        listOf("Introducing: The Daily", "Listen Now: The Daily", "Special Preview: The Daily", "Introducing The Daily", "NPR Presents: The Daily", "Presented by NPR: The Daily", "From the creators of Serial: The Daily").forEach { title ->
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
            assertFalse(detector.detect(episode("Discover: Serial", description = description), "Host").isCrossPromotion, description)
            val introduced = detector.detect(episode("Introducing: Serial", description = description), "Host")
            assertEquals(CrossPromotionConfidence.MEDIUM, introduced.confidence, description)
            assertFalse(CrossPromotionIndicator.DESCRIPTION_PROMO_LANGUAGE in introduced.matchedIndicators, description)
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

    @Test
    fun `announced show keeps its name without season decoration`() {
        // Blood Trails was announced in other hunting shows' feeds and never reached the episode page.
        val description = "This season on Blood Trails, host Jordan Sillars follows eight brand-new cases. Follow Blood Trails on Apple, Spotify, YouTube or wherever you listen."
        listOf("Announcing: Blood Trails, Season Two", "Announcing: Blood Trails, Season Three").forEach { title ->
            val result = detector.detect(episode(title, description = description), "Wired To Hunt Podcast")
            assertTrue(result.isCrossPromotion, title)
            assertEquals("Blood Trails", result.extractedShowName)
            assertEquals(CrossPromotionConfidence.HIGH, result.confidence)
        }
        val trailer = detector.detect(episode("Announcing: Blood Trails, Season Three", duration = 114, artwork = true, description = "Eight brand-new cases."), "The American West")
        assertEquals("Blood Trails", trailer.extractedShowName)
        assertTrue(CrossPromotionIndicator.DISTINCT_ARTWORK in trailer.matchedIndicators)
    }

    @Test
    fun `episode artwork of its own corroborates a presented show`() {
        assertTrue(detector.detect(episode("Wondery Presents: Bunga Bunga", artwork = true), "Detective Trapp").isCrossPromotion)
        assertFalse(detector.detect(episode("Wondery Presents: Bunga Bunga"), "Detective Trapp").isCrossPromotion)
    }

    @Test
    fun `own series premium previews topics events and mixes are not promotions`() {
        mapOf(
            "Introducing: Nightingale Halloween" to "Nightingale",
            "Introducing Gangster Presents: The Story of Ronnie Biggs" to "Gangster",
            "Monster Presents: Insomniac [trailer]" to "Monster Presents: Insomniac",
            "Introducing ‘New York Times Narrated’" to "The New York Times Narrated",
            "Bonus #200 - Alex Pavone [PATREON PREVIEW]" to "Stavvy's World",
            "INTRODUCING the RAMPAGE ROOKIES! Janelle Soukup & Hannah Bergemann on Red Bull Rampage" to "Just Ride",
            "Meet the 26-Year-Old Saving L.A. Cinemas" to "Screen Talk",
            "Deep Ghosh presents United We Rise Podcast Nr. 197" to "United We Stream Podcast",
            "Announcing ROOTCON 2023!" to "Woodland War Machine",
            "Beyond the Garden: new season trailer" to "Why Women Grow",
        ).forEach { (title, host) ->
            assertFalse(detector.detect(episode(title, duration = 120, artwork = true, description = "A short update for listeners."), host).isCrossPromotion, title)
        }
        val season = episode("Introducing: The Old Man Is Still Alive", artwork = true, description = "A preview of the new season of You Must Remember This, about directors late in their careers.")
        assertFalse(detector.detect(season, "You Must Remember This").isCrossPromotion)
    }

    @Test
    fun `new show wording and notes name the promoted show`() {
        listOf(
            episode("We Made a New Podcast Called Conversations with Ghosts! You Can Listen NOW!") to "Conversations with Ghosts",
            episode("I think you might like Multispective Podcast") to "Multispective Podcast",
            episode("Special Bonus - Introducing: Civilian or Spy") to "Civilian or Spy",
            episode("Comfort Eating with Grace Dent: episode one of a new podcast") to "Comfort Eating with Grace Dent",
            episode("Coming Soon: Evan’s new show, “Shell Game”", duration = 206) to "Shell Game",
            episode("Burned by a Paper Sun - Feed Drop") to "Burned by a Paper Sun",
            episode("Feed Drop: Why Only 10% of Companies Succeed With AI", description = "Enjoy a special episode from our friends at the Modern CTO podcast. Read the transcript here.") to "Modern CTO",
            episode("My New Podcast", description = "And here’s my new show, Help Wanted: https://example.org/help-wanted") to "Help Wanted",
            episode("Backyardigans Guess & Go", duration = 180, artwork = true, description = "Get ready for a brand new podcast called Backyardigans Guess & Go! New episodes weekly.") to "Backyardigans Guess & Go",
        ).forEach { (episode, name) ->
            val result = detector.detect(episode, "Host")
            assertTrue(result.isCrossPromotion, episode.title)
            assertEquals(name, result.extractedShowName, episode.title)
        }
    }
}
