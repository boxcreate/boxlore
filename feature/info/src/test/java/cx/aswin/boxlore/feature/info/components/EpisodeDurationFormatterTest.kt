package cx.aswin.boxlore.feature.info.components

import cx.aswin.boxlore.core.model.text.UiText
import cx.aswin.boxlore.core.model.text.resolve
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.info.QueueSelectionOutcome
import cx.aswin.boxlore.feature.info.R
import cx.aswin.boxlore.feature.info.branded.brandedDetails
import cx.aswin.boxlore.feature.info.branded.brandedLength
import cx.aswin.boxlore.feature.info.branded.conspiracy.filedDate
import cx.aswin.boxlore.feature.info.branded.conspiracy.typedDetails
import cx.aswin.boxlore.feature.info.branded.crimejunkie.airedDate
import cx.aswin.boxlore.feature.info.formatLicense
import cx.aswin.boxlore.feature.info.logic.PodcastEpisodePlaybackStateLogic
import cx.aswin.boxlore.feature.info.logic.ToolbarWarning
import cx.aswin.boxlore.feature.info.logic.toolbarWarningActionText
import cx.aswin.boxlore.feature.info.logic.toolbarWarningTitle
import cx.aswin.boxlore.feature.info.queueSelectionMessage
import java.util.Locale
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Lengths, ages and dates on episode rows and Showcase cards come from the language's own formats
 * (android.icu), so they read right in every language; English is checked here, with German to show
 * the language is followed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class EpisodeDurationFormatterTest {
    private val en = Locale.ENGLISH
    private val de = Locale.GERMAN
    private val now = 1_700_000_000L
    private lateinit var zone: TimeZone

    @Before
    fun utc() {
        zone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun restore() = TimeZone.setDefault(zone)

    private fun UiText.english() = resolve(RuntimeEnvironment.getApplication().resources)

    @Test
    fun shortLengthsHideUnderAMinute() {
        assertEquals("", formatEpisodeDuration(0, en))
        assertEquals("", formatEpisodeDuration(59, en))
        assertEquals("1m", formatEpisodeDuration(60, en))
        assertEquals("1h 1m", formatEpisodeDuration(3_660, en))
    }

    @Test
    fun listLengthsHideOnlyAnUnknownLength() {
        assertEquals("", formatEpisodeListDuration(-1, en))
        assertEquals("59m", formatEpisodeListDuration(3_599, en))
        assertEquals("1h", formatEpisodeListDuration(3_600, en))
        assertEquals("1h 5m", formatEpisodeListDuration(3_900, en))
    }

    @Test
    fun agesStepFromMinutesToYears() {
        assertEquals("", formatEpisodeRelativeDate(0, en, now))
        // The language's narrow style; this ICU (Robolectric's Android 13) writes days out in English.
        val ages = listOf(55 * 60, 20 * 3600, 3 * 86400, 14 * 86400, 60 * 86400, 365 * 86400).map { formatEpisodeRelativeDate(now - it, en, now) }
        assertEquals(listOf("55 min. ago", "20 hr. ago", "3 days ago", "2 wk. ago", "2 mo. ago", "1 yr. ago"), ages)
        assertEquals("vor 3 Tagen", formatEpisodeRelativeDate(now - 3 * 86400, de, now))
    }

    @Test
    fun rowsAbbreviateSeasonAndEpisodeAsWholePatterns() {
        assertEquals("S2 E5", seasonEpisodeShort(2, 5)?.english())
        assertEquals("S2", seasonEpisodeShort(2, null)?.english())
        assertEquals("E12", seasonEpisodeShort(null, 12)?.english())
        assertNull(seasonEpisodeShort(null, null))
    }

    @Test
    fun titleRowDetailsGiveSeasonAgeAndLengthOrTimeLeft() {
        val episode = TestFixtures.episode(duration = 3_900, publishedDate = now - 2 * 86_400L)
        val age = formatEpisodeRelativeDate(episode.publishedDate, en, now)
        assertEquals(listOf("S2 E5", age, "1h 5m"), episodeTitleRowDetailParts(episode, timeLeft = null, seasonEpisode = "S2 E5", locale = en, nowSeconds = now))
        assertEquals("S2 E5 · $age · 1h 5m", episodeDetailsLine(episodeTitleRowDetailParts(episode, null, "S2 E5", en, now)).text)
        assertEquals(listOf("", age, "12m left"), episodeTitleRowDetailParts(episode, timeLeft = "12m left", seasonEpisode = "", locale = en, nowSeconds = now))
        assertEquals(listOf("", age, "1h 5m"), episodeTitleRowDetailParts(episode, timeLeft = " ", seasonEpisode = "", locale = en, nowSeconds = now))
        val unknown = TestFixtures.episode(duration = 0, publishedDate = 0L)
        assertEquals("E12", episodeDetailsLine(episodeTitleRowDetailParts(unknown, null, "E12", en, now)).text)
    }

    @Test
    fun showcaseCardsTypeTheLengthAndSayPlayedOnceFinished() {
        assertEquals("45 min", brandedLength(45 * 60, en))
        assertEquals("1 hr, 5 min", brandedLength(65 * 60, en))
        assertEquals("", brandedLength(30, en))
        assertEquals("1 Std., 5 Min.", brandedLength(65 * 60, de))
        assertEquals("2d ago · 55 min", brandedDetails("2d ago", 55 * 60, completed = false, played = "Played", locale = en))
        assertEquals("2d ago · Played", brandedDetails("2d ago", 55 * 60, completed = true, played = "Played", locale = en))
        assertEquals("Played", brandedDetails("", 55 * 60, completed = true, played = "Played", locale = en))
    }

    @Test
    fun conspiracyTypesDetailsAndTheFiledDateInCapitals() {
        assertEquals("2D AGO · 55 MIN", typedDetails("2d ago", 55 * 60, en))
        assertEquals("3H AGO · 1 HR, 5 MIN", typedDetails("3h ago", 65 * 60, en))
        assertEquals("1W AGO", typedDetails("1w ago", 30, en))
        assertEquals("", typedDetails("", 0, en))
        assertEquals("28 OCT 2026", filedDate(1_793_188_800L, Locale.UK))
        assertEquals("28. OKT. 2026", filedDate(1_793_188_800L, de))
        assertEquals("", filedDate(0L, Locale.UK))
    }

    @Test
    fun crimeJunkieTagsTheDayItAired() {
        assertEquals("Oct 26", airedDate(1_793_016_000L, Locale.US))
        assertEquals("26. Okt.", airedDate(1_793_016_000L, de))
        assertNull(airedDate(0L, Locale.US))
    }

    @Test
    fun logicCopyResolvesToTodaysEnglish() {
        val timeLeft = PodcastEpisodePlaybackStateLogic::timeLeftLabel
        assertEquals("1h 5m left", timeLeft(3_900L)?.english())
        assertEquals("2h 0m left", timeLeft(7_200L)?.english())
        assertEquals("0m left", timeLeft(59L)?.english())
        assertEquals(
            "Added 42 episodes. Your queue is full, so 178 weren’t added.",
            queueSelectionMessage(QueueSelectionOutcome(added = 42, didNotFit = 178))?.english(),
        )
        assertEquals(
            "Added 1 episode. Your queue is full, so 1 wasn’t added.",
            queueSelectionMessage(QueueSelectionOutcome(added = 1, didNotFit = 1))?.english(),
        )
        assertEquals("Your queue is full. Remove an episode to add more.", queueSelectionMessage(QueueSelectionOutcome(0, 3))?.english())
        assertEquals("Weekly on Wednesdays", UiText.res(R.string.podcast_info_frequency_weekly_wednesday).english())
        assertEquals("Creative Commons BY-SA", formatLicense("cc-by-sa").english())
        assertEquals("Public Domain (CC0)", formatLicense("cc0").english())
        assertEquals("Creative Commons License", formatLicense("creative-commons-").english())
        assertEquals("Custom terms", formatLicense("custom terms").english())
        val app = RuntimeEnvironment.getApplication()
        assertEquals("Auto-download is on", app.getString(toolbarWarningTitle(ToolbarWarning.AUTO_DOWNLOAD_APP_OPEN_ONLY)))
        assertEquals("Turn On", app.getString(toolbarWarningActionText(ToolbarWarning.SYSTEM_PERMISSION_BLOCKED)!!))
        assertEquals("Resume: ", app.getString(resolveJumpPillPrefix(isOngoing = true)))
        assertEquals("Jump to: ", app.getString(resolveJumpPillPrefix(isOngoing = false)))
    }

    @Test
    fun playbackSummaryReadsAsBefore() {
        val state = PodcastPlaybackSettingsState(
            podcastTitle = "Serial",
            isSubscribed = true,
            globalSkipBeginningMs = 30_000L,
            globalSkipEndingMs = 0L,
            skipBeginningOverrideMs = null,
            skipEndingOverrideMs = null,
            globalPlaybackSpeed = 1.2f,
        )
        assertEquals("1.2× · Beginning 30s · Ending Off", appDefaultsSummary(state).joinToString(" · ") { it.english() })
        assertEquals(
            "1.2× · Beginning 30s · Ending Off · Trim silence on",
            appDefaultsSummary(state.copy(globalTrimSilence = true)).joinToString(" · ") { it.english() },
        )
        assertEquals("App speed (1.2×)", appSpeedChoiceLabel(1.2f).english())
        assertEquals("App setting (off)", appTrimSilenceChoiceLabel(false).english())
        assertEquals("App", speedChoiceValue(state).english())
    }
}
