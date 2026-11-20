package cx.aswin.boxlore.feature.info

import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.catalog.SubscriptionRepository
import cx.aswin.boxlore.core.database.PodcastDao
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.model.text.UiText
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.info.components.PodcastPlaybackSettingsState
import cx.aswin.boxlore.feature.info.components.appDefaultsSummary
import cx.aswin.boxlore.feature.info.components.appSpeedChoiceLabel
import cx.aswin.boxlore.feature.info.components.appTrimSilenceChoiceLabel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

/** "Playback for this show" saves the show's own speed and trim silence, shows them, and reports real changes once. */
@OptIn(ExperimentalCoroutinesApi::class)
class PodcastShowPlaybackWriterTest {
    private val speedWrites = mutableListOf<Pair<String, Float?>>()
    private val trimWrites = mutableListOf<Pair<String, Boolean?>>()
    private val events = mutableListOf<Pair<String, Map<String, Any>>>()
    private lateinit var restore: () -> Unit
    private var storedRow: PodcastEntity? = null
    private val podcasts = object : PodcastDao by mock(PodcastDao::class.java) {
        override suspend fun getPodcast(id: String): PodcastEntity? = storedRow?.takeIf { it.podcastId == id }

        override suspend fun setPlaybackSpeedOverride(id: String, speed: Float?) {
            speedWrites += id to speed
        }

        override suspend fun setSkipSilenceOverride(id: String, enabled: Boolean?) {
            trimWrites += id to enabled
        }
    }
    private val page = PodcastInfoUiState.Success(TestFixtures.podcast(id = "p1"), emptyList(), isSubscribed = true)
    private val uiState = MutableStateFlow<PodcastInfoUiState>(page)

    @BeforeEach fun setUp() {
        restore = AnalyticsHelper.installRecordingSink(events)
    }

    @AfterEach fun tearDown() = restore()

    private fun settings() = events.filter { it.first == "settings_interaction" }.map { it.second["action"] to it.second["value"] }

    private fun current() = (uiState.value as PodcastInfoUiState.Success).podcast

    @Test
    fun `a show's own speed is saved, shown and reported, and the app speed again clears it`() = runTest {
        val writer = PodcastShowPlaybackWriter(this, SubscriptionRepository(podcasts), uiState)

        writer.setSpeed(uiState.value as PodcastInfoUiState.Success, 1.5f)
        advanceUntilIdle()
        writer.setSpeed(uiState.value as PodcastInfoUiState.Success, 1.5f)
        advanceUntilIdle()
        assertEquals(1.5f, current().playbackSpeedOverride)

        writer.setSpeed(uiState.value as PodcastInfoUiState.Success, null)
        advanceUntilIdle()

        assertEquals(listOf("p1" to 1.5f, "p1" to null), speedWrites, "picking the same speed again saves nothing")
        assertEquals(null, current().playbackSpeedOverride)
        assertEquals(
            listOf<Pair<Any?, Any?>>("podcast_speed_override_changed" to "p1:1.5", "podcast_speed_override_changed" to "p1:default"),
            settings(),
        )
    }

    @Test
    fun `trim silence has three states, app setting, on and off`() = runTest {
        val writer = PodcastShowPlaybackWriter(this, SubscriptionRepository(podcasts), uiState)

        for (value in listOf(true, false, null)) {
            writer.setTrimSilence(uiState.value as PodcastInfoUiState.Success, value)
            advanceUntilIdle()
            assertEquals(value, current().skipSilenceOverride)
        }

        assertEquals(listOf("p1" to true, "p1" to false, "p1" to null), trimWrites)
        assertEquals(listOf("p1:on", "p1:off", "p1:default"), settings().map { it.second })
    }

    @Test
    fun `a save that lands after leaving the show never changes the next show's page`() = runTest {
        val writer = PodcastShowPlaybackWriter(this, SubscriptionRepository(podcasts), uiState)
        writer.setSpeed(page, 2f)
        uiState.value = page.copy(podcast = TestFixtures.podcast(id = "p2"))
        advanceUntilIdle()

        assertEquals(listOf("p1" to 2f), speedWrites)
        assertEquals(null, current().playbackSpeedOverride)
    }

    @Test
    fun `a change made in the player shows when the sheet opens, and picking the old speed again saves it`() = runTest {
        val writer = PodcastShowPlaybackWriter(this, SubscriptionRepository(podcasts), uiState)
        writer.setSpeed(uiState.value as PodcastInfoUiState.Success, 1.5f)
        advanceUntilIdle()
        // The player then saves 2x for this show while the page stays open.
        storedRow = row(speed = 2f, trimSilence = true, skipBeginningMs = 30_000L)

        val shown = writer.refresh(current())
        assertEquals(2f, current().playbackSpeedOverride)
        assertEquals(true, current().skipSilenceOverride)
        assertEquals(30_000L, current().skipBeginningOverrideMs)
        assertEquals(current(), shown)

        writer.setSpeed(uiState.value as PodcastInfoUiState.Success, 1.5f)
        advanceUntilIdle()
        assertEquals(listOf("p1" to 1.5f, "p1" to 1.5f), speedWrites, "1.5x is a change again, so it is saved")
    }

    @Test
    fun `a setting cleared in the player isn't written back by following the show again`() = runTest {
        val writer = PodcastShowPlaybackWriter(this, SubscriptionRepository(podcasts), uiState)
        uiState.value = page.copy(podcast = page.podcast.copy(playbackSpeedOverride = 1.5f, skipSilenceOverride = false))
        // "Use app speed" and the player's switch cleared both; the page still shows the old values.
        storedRow = row(speed = null, trimSilence = null)

        val followed = writer.refresh(current())

        assertEquals(null, followed.playbackSpeedOverride)
        assertEquals(null, followed.skipSilenceOverride)
        assertEquals(null, current().playbackSpeedOverride)
    }

    @Test
    fun `a show without a stored row keeps what the page shows`() = runTest {
        val writer = PodcastShowPlaybackWriter(this, SubscriptionRepository(podcasts), uiState)
        uiState.value = page.copy(podcast = page.podcast.copy(playbackSpeedOverride = 1.5f))

        assertEquals(1.5f, writer.refresh(current()).playbackSpeedOverride)
        assertEquals(1.5f, current().playbackSpeedOverride)
    }

    private fun row(speed: Float?, trimSilence: Boolean?, skipBeginningMs: Long? = null) = PodcastEntity(
        podcastId = "p1",
        title = "Serial",
        author = "",
        imageUrl = "",
        description = null,
        isSubscribed = true,
        skipBeginningOverrideMs = skipBeginningMs,
        playbackSpeedOverride = speed,
        skipSilenceOverride = trimSilence,
    )

    @Test
    fun `the app defaults line and the app choices name the app's values`() {
        val state = PodcastPlaybackSettingsState(
            podcastTitle = "Serial",
            isSubscribed = true,
            globalSkipBeginningMs = 30_000L,
            globalSkipEndingMs = 0L,
            skipBeginningOverrideMs = null,
            skipEndingOverrideMs = null,
            globalPlaybackSpeed = 1.2f,
        )
        // The English of these lines is checked in EpisodeDurationFormatterTest, with resources.
        assertEquals(UiText.res(R.string.podcast_playback_app_speed, "1.2×"), appSpeedChoiceLabel(1.2f))
        assertEquals(UiText.res(R.string.podcast_playback_app_setting_off), appTrimSilenceChoiceLabel(false))
        assertEquals(4, appDefaultsSummary(state.copy(globalTrimSilence = true)).size)
        assertTrue(state.usesDefaults)
        assertFalse(state.copy(playbackSpeedOverride = 1.5f).usesDefaults)
        assertFalse(state.copy(trimSilenceOverride = false).usesDefaults)
    }
}
