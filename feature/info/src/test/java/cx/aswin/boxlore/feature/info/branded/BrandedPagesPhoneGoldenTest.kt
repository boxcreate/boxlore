package cx.aswin.boxlore.feature.info.branded

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import coil.Coil
import coil.ImageLoader
import coil.decode.DataSource
import coil.request.ErrorResult
import coil.request.SuccessResult
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.info.PodcastInfoScreen
import cx.aswin.boxlore.feature.info.PodcastInfoUiState
import cx.aswin.boxlore.feature.info.PodcastInfoViewModel
import cx.aswin.boxlore.feature.info.logic.PodcastInfoPageShow
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Local design check for the branded show pages: renders each at the design boards' size (369 by
 * 821dp, 2x) with the boards' sample episodes, in both themes, and its subscribe moment every 16ms,
 * for side-by-side comparison. Like the phone goldens it runs only on request:
 * `-PtabletCaptures=after --tests '*BrandedPagesPhoneGoldenTest'`. Copy each show's cover to
 * `build/branded-fixtures/<show>-art.jpg` first.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BrandedPagesPhoneGoldenTest(private val show: String, private val dark: Boolean) {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun page() {
        val model = setUp(subscribed = true)
        capture("$show-${theme()}", model.first)
    }

    /** A tablet-width window, to check the header and spotlight adapt. */
    @Test fun tablet() {
        assumeTrue("Dark is enough at tablet width", dark)
        assumeTrue("The card variants share the main page's layout", show in Moments)
        val model = setUp(subscribed = true, window = "w840dp-h1200dp")
        capture("$show-tablet-${theme()}", model.first)
    }

    /** A header's opening, from the page's first frame, every 50ms: lights coming on, art arriving. */
    @Test fun headerOpening() {
        assumeTrue("Only headers with an opening", show in Openings)
        val (model, _) = setUp(subscribed = true)
        render(model, settle = 0)
        var elapsed = 0L
        for (frame in 0L..3_000L step 50L) {
            compose.mainClock.advanceTimeBy(frame - elapsed)
            elapsed = frame
            save("$show-opening-${theme()}-$frame")
        }
    }

    /** The subscribe moment from the tap, every frame, so a one-frame flash cannot hide. */
    @Test fun subscribeMoment() {
        assumeTrue("The card variants share the main page's moment", show in Moments)
        val (model, state) = setUp(subscribed = false)
        render(model)
        save("$show-subscribe-${theme()}-before")
        state.value = (state.value as PodcastInfoUiState.Success).copy(isSubscribed = true)
        var elapsed = 0L
        for (frame in 0L..1_408L step 16L) {
            compose.mainClock.advanceTimeBy(frame - elapsed)
            elapsed = frame
            save("$show-subscribe-${theme()}-$frame")
        }
    }

    private fun theme() = if (dark) "dark" else "light"

    private fun fixture(): Pair<Podcast, List<Episode>> = cardVariant() ?: when (show) {
        "conspiracy" -> conspiracy()
        "history" -> history()
        "darknet" -> darknet()
        "serial" -> serial()
        "waveform" -> waveform()
        "crimejunkie" -> crimeJunkie()
        "jre" -> jre(withPicture = true)
        "jre-nopic" -> jre(withPicture = false)
        "rottenmango" -> rottenMango(longTitle = false)
        "rottenmango-long" -> rottenMango(longTitle = true)
        "daily" -> daily(ownPhoto = true)
        else -> vergecast()
    }

    /** The latest-episode card's other states: partway, a picture that fails, two guests, no photo of its own. */
    private fun cardVariant(): Pair<Podcast, List<Episode>>? = when (show) {
        "crimejunkie-partway" -> crimeJunkie()
        "jre-partway", "jre-fail" -> jre(withPicture = true)
        "jre-twoguests" -> jre(withPicture = false, title = "#2574 - Francis Foster & Konstantin Kisin")
        "rottenmango-partway" -> rottenMango(longTitle = false)
        "daily-partway" -> daily(ownPhoto = true)
        "daily-nophoto" -> daily(ownPhoto = false)
        else -> null
    }

    private lateinit var root: String
    private lateinit var podcastId: String

    private fun setUp(subscribed: Boolean, window: String = "w369dp-h821dp"): Pair<PodcastInfoViewModel, MutableStateFlow<PodcastInfoUiState>> {
        root = System.getProperty("boxlore.projectRoot") ?: ""
        assumeTrue("Local design capture only", root.isNotEmpty())
        RuntimeEnvironment.setQualifiers("en-rUS-$window-port-${if (dark) "night" else "notnight"}-xhdpi")
        val activity = compose.activity
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        shadowOf(activity.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(true)
        shadowOf(activity.application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val art = File(root, "build/branded-fixtures/$show-art.jpg")
        assumeTrue("Copy the cover to $art first", art.isFile)
        val artwork = BitmapFactory.decodeFile(art.absolutePath)
        Coil.setImageLoader(
            ImageLoader.Builder(activity).components {
                add(
                    coil.intercept.Interceptor { chain ->
                        // "jre-fail": the latest episode's picture never arrives.
                        if (show == "jre-fail" && chain.request.data.toString().contains("jre-0")) {
                            ErrorResult(null, chain.request, java.io.IOException("Picture unavailable"))
                        } else {
                            // An episode's own picture when one is copied in as build/branded-fixtures/<episode id>.jpg.
                            val address = java.net.URLDecoder.decode(chain.request.data.toString(), "UTF-8")
                            val name = Regex("""[A-Za-z0-9-]+\.jpg""").findAll(address).lastOrNull()?.value.orEmpty()
                            val own = File(root, "build/branded-fixtures/$name")
                            val picture = if (name.isNotEmpty() && own.isFile && own.name != art.name) BitmapFactory.decodeFile(own.absolutePath) else artwork
                            SuccessResult(BitmapDrawable(activity.resources, picture), chain.request, DataSource.MEMORY_CACHE)
                        }
                    },
                )
            }.build(),
        )
        val (podcast, episodes) = fixture()
        podcastId = podcast.id
        val model = mock(PodcastInfoViewModel::class.java)
        val state = MutableStateFlow<PodcastInfoUiState>(PodcastInfoUiState.Success(podcast, episodes, subscribed))
        `when`(model.uiState).thenReturn(state)
        `when`(model.queuedEpisodeIds).thenReturn(MutableStateFlow(emptySet()))
        `when`(model.downloadedEpisodeIds).thenReturn(MutableStateFlow(emptySet()))
        `when`(model.downloadingEpisodeIds).thenReturn(MutableStateFlow(emptySet()))
        `when`(model.episodePendingDownloadRemoval).thenReturn(MutableStateFlow(null))
        `when`(model.hideCompletedInShowDetails).thenReturn(MutableStateFlow(false))
        `when`(model.isPinnedToHome).thenReturn(MutableStateFlow(false))
        `when`(model.pageShow).thenReturn(MutableStateFlow(PodcastInfoPageShow()))
        `when`(model.globalSkipBeginningMs).thenReturn(MutableStateFlow(0L))
        `when`(model.globalSkipEndingMs).thenReturn(MutableStateFlow(0L))
        `when`(model.autoDownloadBackgroundChecksEnabled).thenReturn(MutableStateFlow(false))
        `when`(model.completedEpisodesState).thenReturn(MutableStateFlow(emptySet()))
        `when`(model.likedEpisodesState).thenReturn(MutableStateFlow(emptySet()))
        // "-partway": the latest episode is 38% heard.
        val partway = if (show.endsWith("-partway")) {
            mapOf(episodes.first().id to PodcastInfoViewModel.EpisodePlaybackState(isResume = true, progress = 0.38f, timeLeft = "28m left"))
        } else {
            emptyMap()
        }
        `when`(model.episodePlaybackState).thenReturn(MutableStateFlow(partway))
        return model to state
    }

    private fun vergecast(): Pair<Podcast, List<Episode>> {
        val now = System.currentTimeMillis() / 1000
        val podcast = TestFixtures.podcast(id = "415434", title = "The Vergecast", artist = "The Verge", imageUrl = ART, genre = "Technology")
            .copy(
                updateFrequency = "daily",
                notificationsEnabled = true,
                autoDownloadEnabled = true,
                description = "The Vergecast is the flagship daily podcast from The Verge about small gadgets, Big Tech, and everything in " +
                    "between. Every Friday, Nilay Patel and David Pierce hang out and make sense of the week’s most important technology news.",
            )
        return podcast to listOf(
            episode(podcast, "vergecast-latest", "The delightfully messy state of Apple gadgets", 93 * 60, now - 55 * 60, "Nilay is going to buy the new touchscreen MacBook. But not for the reason you think."),
            episode(podcast, "vergecast-previous", "The touchscreen MacBook Apple said it wouldn't make", 40 * 60, now - 20 * 3600, "David and Nilay on Apple's surprise, and what it means for every other laptop."),
        )
    }

    /** Weekly on Wednesdays, with a trailer, like the board. */
    private fun conspiracy(): Pair<Podcast, List<Episode>> {
        val podcast = TestFixtures.podcast(id = "9926", title = "Conspiracy Theories", artist = "Spotify Studios", imageUrl = ART, genre = "True Crime")
            .copy(
                notificationsEnabled = true,
                autoDownloadEnabled = true,
                description = "The world’s most controversial events, and the complex beliefs behind them. From Bigfoot sightings to bitcoin " +
                    "takeovers, alien landings to assassinations, who’s shaping the narrative — and why?",
            )
        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 9)
            while (get(java.util.Calendar.DAY_OF_WEEK) != java.util.Calendar.WEDNESDAY) add(java.util.Calendar.DAY_OF_MONTH, -1)
        }
        val latestWednesday = calendar.timeInMillis / 1000
        // The real feed's latest twelve; Spotify marks two other shows' episodes as trailers.
        val titles = listOf(
            "The Donner Party Villain: How Did Lewis Keseberg Survive?" to "It’s been over 175 years since the last few surviving members of the Donner Party were rescued.",
            "Was Jack the Ripper a Freemason?" to "In 1888, Jack the Ripper stalked the streets of Whitechapel, England. Officially, he killed five women.",
            "Winchester Mystery House: Ghosts, Legends, and Hoaxes" to "Sarah Winchester built a house with stairs to nowhere.",
            "The Possessed Nuns of Loudun" to "trailer",
            "Panic in the Streets: The War of the Worlds Radio Broadcast" to "A radio play, and a country that believed it.",
            "Did a Psych Experiment Turn Ted Kaczynski into the Unabomber?" to "Harvard, 1959.",
            "Did Andy Kaufman Fake His Own Death?" to "trailer",
            "Missing Heiress: The Disappearance of Juanita Nielsen" to "Sydney, 1975.",
            "The Chupacabra: Puerto Rico’s Vampire Cryptid" to "Livestock drained of blood.",
            "Chuck Barris: Game Show Host and CIA Assassin?" to "He said so himself.",
        )
        val episodes = titles.mapIndexed { i, (title, notes) ->
            val episode = episode(podcast, "conspiracy-$i", title, (55 - i * 3) * 60, latestWednesday - i * 7L * 86_400, notes)
            if (notes == "trailer") episode.copy(episodeType = "trailer") else episode
        }
        return podcast to episodes
    }

    /** Twice a week, like the show: the real feed's latest titles, numbered, each with its own picture. */
    private fun history(): Pair<Podcast, List<Episode>> {
        val podcast = TestFixtures.podcast(id = "1365765", title = "The Rest Is History", artist = "Goalhanger", imageUrl = ART, genre = "History")
            .copy(
                notificationsEnabled = true,
                autoDownloadEnabled = true,
                description = "Take a deep dive into History’s biggest moments with Tom Holland & Dominic Sandbrook. Explore the stories of " +
                    "History’s most brutal rulers, deadly battles, and world-changing events.",
            )
        val now = System.currentTimeMillis() / 1000
        val titles = listOf(
            "720. Revolution in the Soviet Empire: Revenge of the Red Army (Part 2) ",
            "719. Revolution in the Soviet Empire: The Hungarian Uprising (Part 1)",
            "718. The Suez Crisis: Britain's Shattered Empire (Part 2)",
            "717. The Suez Crisis: Anarchy in the Middle East (Part 1)",
            "716. King Arthur: The Truth Behind the Legend (Part 4)",
            "715. King Arthur: The Mysteries of Merlin (Part 3)",
            "714. King Arthur: Lancelot and the Round Table (Part 2)",
            "713. King Arthur: The Once & Future King (Part 1)",
            "712. The Terror: The Fall of Robespierre (Part 6)",
            "711. The Terror: Killing God (Part 5)",
        )
        val episodes = titles.mapIndexed { i, title ->
            episode(podcast, "trih-$i", title, (71 - i) * 60, now - 86_400L - i * 3L * 86_400, "Tom and Dominic on the story.")
                .copy(episodeNumber = 720 - i)
        }
        return podcast to episodes
    }

    /** Every other Tuesday, like the show: the real feed's latest, each with its own illustration. */
    private fun darknet(): Pair<Podcast, List<Episode>> {
        val podcast = TestFixtures.podcast(id = "577105", title = "Darknet Diaries", artist = "Jack Rhysider", imageUrl = ART, genre = "Technology")
            .copy(
                notificationsEnabled = true,
                autoDownloadEnabled = true,
                description = "Explore true stories of the dark side of the Internet with host Jack Rhysider as he takes you on a journey " +
                    "through the chilling world of hacking, data breaches, and cyber crime.",
            )
        val now = System.currentTimeMillis() / 1000
        val titles = listOf(
            "182: Blackshades" to "What if you got infected by a friend who wanted to spy on you? This is the story of how BlackShades " +
                "spread to more than half a million machines, and 97 people got arrested.",
            "181: Pegasus" to "Spyware that needs no click.",
            "180: The Hunt for the Darkweb" to "Inside the takedown of a market.",
        )
        val episodes = titles.mapIndexed { i, (title, notes) ->
            episode(podcast, "darknet-$i", title, (39 + i * 7) * 60, now - 4 * 86_400L - i * 14L * 86_400, notes).copy(episodeNumber = 182 - i)
        }
        return podcast to episodes
    }

    /** Serial's feed as it stands: METRO's three episodes, then The Last 12 Weeks. */
    private fun serial(): Pair<Podcast, List<Episode>> {
        val podcast = TestFixtures.podcast(id = "745392", title = "Serial", artist = "Serial Productions & The New York Times", imageUrl = ART, genre = "News")
            .copy(
                notificationsEnabled = true,
                autoDownloadEnabled = true,
                description = "Serial Productions makes narrative podcasts that have transformed the medium. Sign up for our newsletter at " +
                    "nytimes.com/serialnewsletter to find out about new shows, get behind the scenes stories, and see photos and videos.",
            )
        val now = System.currentTimeMillis() / 1000
        val titles = listOf(
            Triple("METRO - Heavy is the Head that Refuses to Share the Crown", 18, 3),
            Triple("METRO - Karat and Schtick", 18, 2),
            Triple("Special Preview: METRO with Sarah Koenig - Ep. 1", 18, 1),
            Triple("The Last 12 Weeks - Ep. 5", 17, 5),
        )
        val episodes = titles.mapIndexed { i, (title, season, number) ->
            episode(podcast, "serial-$i", title, (49 - i * 3) * 60, now - 2 * 86_400L - i * 7L * 86_400, "In New York, the competition is famously brutal.")
                .copy(seasonNumber = season, episodeNumber = number)
        }
        return podcast to episodes
    }

    /** Weekly on Fridays, like the board: the latest from this morning, then last week's. */
    private fun waveform(): Pair<Podcast, List<Episode>> {
        val podcast = TestFixtures.podcast(id = "947138", title = "Waveform: The MKBHD Podcast", artist = "MKBHD", imageUrl = ART, genre = "Technology")
            .copy(
                notificationsEnabled = true,
                autoDownloadEnabled = true,
                description = "A tech podcast for the gadget lovers and tech heads among us from the mind of Marques Brownlee, better known " +
                    "as MKBHD. MKBHD has made a name for himself on YouTube reviewing everything from the newest smartphones to cameras " +
                    "to electric cars.",
            )
        val calendar = java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.HOUR_OF_DAY, -9)
            while (get(java.util.Calendar.DAY_OF_WEEK) != java.util.Calendar.FRIDAY) add(java.util.Calendar.DAY_OF_MONTH, -1)
        }
        val latestFriday = calendar.timeInMillis / 1000
        val titles = listOf(
            "Is The \"PROLED\" Macbook Any Good?" to "David and Marques try the new MacBook and its display.",
            "Introducing Tesla Roadster... Again" to "The Roadster is back, again. Plus a phone that folds three ways.",
            "The Best Phones of the Year" to "The team picks their favourite phones so far.",
            "We Tried Every Smart Ring" to "Rings that track your sleep, steps and heart.",
        )
        val episodes = titles.mapIndexed { i, (title, notes) ->
            episode(podcast, "waveform-$i", title, (122 - i) * 60, latestFriday - i * 7L * 86_400, notes)
        }
        return podcast to episodes
    }

    /** Weekly on Mondays, like the board: the latest from last Monday, then the weeks before. */
    private fun crimeJunkie(): Pair<Podcast, List<Episode>> {
        val podcast = TestFixtures.podcast(id = "743411", title = "Crime Junkie", artist = "Audiochuck", imageUrl = ART, genre = "True Crime")
            .copy(
                notificationsEnabled = true,
                autoDownloadEnabled = true,
                description = "Does hearing about a true crime case always leave you scouring the internet for the truth behind the story? " +
                    "Dive into your next mystery with Crime Junkie. Every Monday, join your host Ashley Flowers as she unravels all the " +
                    "details of infamous and underreported true crime cases with her best friend Brit Prawat.",
            )
        val calendar = java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.DAY_OF_MONTH, -1)
            while (get(java.util.Calendar.DAY_OF_WEEK) != java.util.Calendar.MONDAY) add(java.util.Calendar.DAY_OF_MONTH, -1)
        }
        val latestMonday = calendar.timeInMillis / 1000
        val titles = listOf(
            "MYSTERIOUS DEATH OF: Heather Turner" to "In the early hours of May 4, 2017, responding deputies arrived at a home in Dallas, GA after receiving " +
                "a report of a woman with a gunshot wound to the head. While it first appeared to be death by suicide, questions started to swirl.",
            "MISSING: Kristin Smart" to "A college freshman walks home from a party and is never seen again.",
            "MURDERED: Kathleen Peterson" to "A staircase, a novelist and a question that took years to answer.",
            "ATTACKED: The Golden State Killer" to "Decades of fear across California, and the DNA that finally named him.",
        )
        val episodes = titles.mapIndexed { i, (title, notes) ->
            episode(podcast, "crimejunkie-$i", title, (45 + i * 4) * 60, latestMonday - i * 7L * 86_400, notes)
        }
        return podcast to episodes
    }

    /**
     * Six days a week at about the same time, like the show; the latest of this week played, the one
     * before not. Without its own photo, the latest episode reuses the show's cover.
     */
    private fun daily(ownPhoto: Boolean): Pair<Podcast, List<Episode>> {
        val podcast = TestFixtures.podcast(id = "743229", title = "The Daily", artist = "The New York Times", imageUrl = ART, genre = "News")
            .copy(
                notificationsEnabled = true,
                autoDownloadEnabled = true,
                description = "This is what the news should sound like. The biggest stories of our time, told by the best journalists in the " +
                    "world. Hosted by Michael Barbaro, Rachel Abrams and Natalie Kitroeff. Twenty minutes a day, six days a week, ready by 6 a.m.",
            )
        val zone = java.time.ZoneId.systemDefault()
        // Six release days a week, Sunday to Friday, at 6:00, going back three weeks from yesterday.
        val dates = generateSequence(java.time.LocalDate.now(zone).minusDays(1)) { it.minusDays(1) }
            .filter { it.dayOfWeek != java.time.DayOfWeek.SATURDAY }
            .take(18)
            .toList()
        val titles = listOf(
            "A Plan for Two Dans: How Democrats Manipulated Alaska’s Ballot" to "An investigation from The New York Times has uncovered how two " +
                "candidates named Dan Sullivan ended up on the ballot for U.S. Senate in Alaska.",
            "Is Running From Trump Enough to Save Republicans on Tuesday?" to "With days to go, Republicans in tight races are keeping their distance.",
            "The Art of the Pardon" to "How clemency became a business.",
        )
        val episodes = dates.mapIndexed { i, date ->
            val (title, notes) = titles[i % titles.size]
            episode(podcast, "daily-$i", title, (26 + i % 9) * 60, date.atTime(6, 0).atZone(zone).toEpochSecond(), notes)
                .let { if (i == 0 && !ownPhoto) it.copy(imageUrl = ART) else it }
        }
        return podcast.copy(fallbackImageUrl = episodes.first().imageUrl) to episodes
    }

    /** Two a week, like the board; the long title fills the frame's four lines, and an unnumbered episode gets "Bonus". */
    private fun rottenMango(longTitle: Boolean): Pair<Podcast, List<Episode>> {
        val podcast = TestFixtures.podcast(id = "490022", title = "Rotten Mango", artist = "Stephanie Soo", imageUrl = ART, genre = "True Crime")
            .copy(
                notificationsEnabled = true,
                autoDownloadEnabled = true,
                description = "Rotten but still a little sweet! Rotten Mango is a true crime + all things spooky podcast. We love doing deep " +
                    "dives into the darkest crimes and we tend to not leave out any details - which can get a little rotten at times.",
            )
        val now = System.currentTimeMillis() / 1000
        val titles = listOf(
            if (longTitle) {
                "Mackenzie's Mom Calls Police For 'Mean Post' on Social Media- “There Are 2 Girls Bullying Mackenzie”"
            } else {
                "Ex-‘American Idol’ Singer Texts Mistress “I Want Her Dead”, Then His Wife Is Found Shot"
            },
            "The Girl Who Wasn't There",
            "Cannibal Cop Plotted to Kidnap and Cook Women",
            "The Unsolved Murder at the Motel",
        )
        val episodes = titles.mapIndexed { i, title ->
            episode(podcast, "rottenmango-$i", title, (48 + i * 6) * 60, now - (2 + i * 3.5).toLong() * 86_400L, "A case from the archive.")
                .copy(episodeNumber = if (longTitle && i == 0) null else 556 - i)
        }
        return podcast to episodes
    }

    /** About four a week, like the board; the latest has its own picture, or none yet (today's #2573). */
    private fun jre(withPicture: Boolean, title: String? = null): Pair<Podcast, List<Episode>> {
        val podcast = TestFixtures.podcast(id = "550168", title = "The Joe Rogan Experience", artist = "Joe Rogan", imageUrl = ART, genre = "Comedy")
            .copy(notificationsEnabled = true, autoDownloadEnabled = true, description = "The official podcast of comedian Joe Rogan.")
        val now = System.currentTimeMillis() / 1000
        val titles = listOf(
            title ?: if (withPicture) "#2572 - Colum Tyrrell" else "#2573 - Legion of Skanks",
            "#2571 - Jimmy Carr",
            "#2570 - Mystery Boys",
            "JRE MMA Show #187 with Robert Whittaker",
        )
        val episodes = titles.mapIndexed { i, title ->
            episode(podcast, "jre-$i", title, (184 - i * 9) * 60, now - (i + 2) * 86_400L, "A conversation.")
        }.mapIndexed { i, episode -> if (i == 0 && !withPicture) episode.copy(imageUrl = null) else episode }
        // As on the real page, the show's fallback image is its latest episode's picture.
        return podcast.copy(fallbackImageUrl = episodes.first().imageUrl) to episodes
    }

    private fun episode(podcast: Podcast, id: String, title: String, seconds: Int, published: Long, notes: String) = TestFixtures.episode(
        id = id,
        title = title,
        podcastId = podcast.id,
        podcastTitle = podcast.title,
        duration = seconds,
        publishedDate = published,
        // Each episode has its own picture, as most do; one that is the show's cover counts as none.
        imageUrl = "https://images.example.invalid/$id.jpg",
        description = notes,
    )

    private fun render(model: PodcastInfoViewModel, settle: Long = 3_500) {
        val activity = compose.activity
        compose.mainClock.autoAdvance = false
        compose.setContent {
            BoxLoreTheme(darkTheme = dark, dynamicColor = false) {
                Surface(Modifier.fillMaxSize()) { PodcastInfoScreen(podcastId, model, {}, { _, _, _ -> }, {}, {}) }
            }
        }
        // The phone's status bar: 140px at 3.25x is 43dp.
        val insets = WindowInsetsCompat.Builder().setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, 86, 0, 0)).build()
        compose.runOnUiThread { ViewCompat.dispatchApplyWindowInsets(activity.window.decorView, insets) }
        // Long enough for a header's lights or intro, which wait for the page to settle, to finish.
        if (settle > 0) {
            compose.mainClock.advanceTimeBy(settle)
            compose.waitForIdle()
        }
    }

    private fun capture(name: String, model: PodcastInfoViewModel) {
        render(model)
        save(name)
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun save(name: String) {
        val out = File(root, "build/reports/branded-captures/$name.png")
        requireNotNull(out.parentFile).mkdirs()
        compose.onRoot().captureRoboImage(out.absolutePath, roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
    }

    companion object {
        private const val ART = "https://images.example.invalid/art.jpg"

        private val Openings = setOf("history", "darknet", "conspiracy", "serial")

        private val Moments = setOf("vergecast", "conspiracy", "history", "darknet", "serial", "waveform", "crimejunkie", "jre", "jre-nopic", "rottenmango", "rottenmango-long", "daily")

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-dark={1}")
        fun pages(): List<Array<Any>> = (Moments + listOf("crimejunkie-partway", "jre-partway", "jre-fail", "jre-twoguests", "rottenmango-partway", "daily-nophoto", "daily-partway")).flatMap { show -> listOf(arrayOf<Any>(show, true), arrayOf<Any>(show, false)) }
    }
}
