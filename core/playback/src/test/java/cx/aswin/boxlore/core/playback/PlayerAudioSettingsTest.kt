package cx.aswin.boxlore.core.playback

import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The player's speed and trim silence changes go to the playing show while it has its own value, else
 * to the app settings, and the audio state follows the playing show's stored row.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerAudioSettingsTest {
    private val rows = mutableMapOf<String, MutableStateFlow<ShowPlaybackOverrides>>()
    private val showSpeedWrites = mutableListOf<Pair<String, Float?>>()
    private val showTrimWrites = mutableListOf<Pair<String, Boolean?>>()
    private val store = object : ShowPlaybackSettingsStore {
        override fun observe(podcastId: String): Flow<ShowPlaybackOverrides> = row(podcastId)

        override suspend fun setSpeed(podcastId: String, speed: Float?) {
            showSpeedWrites += podcastId to speed
            row(podcastId).value = row(podcastId).value.copy(speed = speed)
        }

        override suspend fun setTrimSilence(podcastId: String, enabled: Boolean?) {
            showTrimWrites += podcastId to enabled
            row(podcastId).value = row(podcastId).value.copy(trimSilence = enabled)
        }
    }
    private val appSpeedWrites = mutableListOf<Float>()
    private val appTrimWrites = mutableListOf<Boolean>()
    private val app = object : AppAudioSettings {
        override suspend fun speed(): Float = 1.2f

        override suspend fun setSpeed(speed: Float) {
            appSpeedWrites += speed
        }

        override suspend fun setTrimSilence(enabled: Boolean) {
            appTrimWrites += enabled
        }
    }
    private val events = mutableListOf<Pair<String, Map<String, Any>>>()
    private lateinit var restore: () -> Unit

    private fun row(podcastId: String) = rows.getOrPut(podcastId) { MutableStateFlow(ShowPlaybackOverrides(podcastId)) }

    private fun settingsEvents() = events.filter { it.first == "settings_interaction" }.map { it.second["action"] to it.second["value"] }

    @BeforeEach fun setUp() {
        restore = AnalyticsHelper.installRecordingSink(events)
    }

    @AfterEach fun tearDown() = restore()

    @Test
    fun `a speed picked while the show has its own speed is saved for that show, once per change`() = runTest {
        val settings = PlayerAudioSettings(store, app)
        val playing = PlaybackAudioState(podcastId = "p1", showSpeed = 1.5f)

        assertEquals(2f, settings.audioAfterSpeed(playing, 2f).showSpeed)
        settings.saveSpeed(playing, 2f)
        settings.saveSpeed(playing.copy(showSpeed = 2f), 2f)

        assertEquals(listOf("p1" to 2f), showSpeedWrites)
        assertEquals(emptyList<Float>(), appSpeedWrites, "the app speed stays as it was")
        assertEquals(listOf<Pair<Any?, Any?>>("podcast_speed_override_changed" to "p1:2.0"), settingsEvents())
    }

    @Test
    fun `a speed picked for a show without its own speed changes the app speed`() = runTest {
        val settings = PlayerAudioSettings(store, app)
        val playing = PlaybackAudioState(podcastId = "p1")

        assertEquals(playing, settings.audioAfterSpeed(playing, 2f))
        settings.saveSpeed(playing, 2f)
        PlayerAudioSettings(showStore = null, app = app).saveSpeed(playing.copy(showSpeed = 1.5f), 1.75f)

        assertEquals(listOf(2f, 1.75f), appSpeedWrites)
        assertEquals(emptyList<Pair<String, Float?>>(), showSpeedWrites)
        assertEquals(emptyList<Pair<Any?, Any?>>(), settingsEvents())
    }

    @Test
    fun `trim silence goes to the show's own setting while it has one, else to the app's`() = runTest {
        val settings = PlayerAudioSettings(store, app)
        val ownSetting = PlaybackAudioState(podcastId = "p1", showTrimSilence = false, appTrimSilence = true)
        val appSetting = PlaybackAudioState(podcastId = "p2", appTrimSilence = false)

        assertEquals(true, settings.audioAfterTrimSilence(ownSetting, true).showTrimSilence)
        assertEquals(true, settings.audioAfterTrimSilence(ownSetting, true).appTrimSilence)
        assertEquals(true, settings.audioAfterTrimSilence(appSetting, true).appTrimSilence)
        settings.saveTrimSilence(ownSetting, true)
        settings.saveTrimSilence(appSetting, true)

        assertEquals(listOf("p1" to true), showTrimWrites)
        assertEquals(listOf(true), appTrimWrites)
        assertEquals(
            listOf<Pair<Any?, Any?>>(
                "podcast_trim_silence_override_changed" to "p1:on",
                "trim_silence_toggled" to "true",
            ),
            settingsEvents(),
        )
    }

    @Test
    fun `use app speed clears the show's speed and plays at the app speed`() = runTest {
        val settings = PlayerAudioSettings(store, app)

        assertEquals(1.2f, settings.useAppSpeed(PlaybackAudioState(podcastId = "p1", showSpeed = 2f)))
        assertNull(settings.useAppSpeed(PlaybackAudioState()), "nothing is playing")
        assertNull(PlayerAudioSettings(null, app).useAppSpeed(PlaybackAudioState(podcastId = "p1", showSpeed = 2f)))

        assertEquals(listOf<Pair<String, Float?>>("p1" to null), showSpeedWrites)
        assertEquals(listOf<Pair<Any?, Any?>>("podcast_speed_override_changed" to "p1:default"), settingsEvents())
    }

    @Test
    fun `the audio state follows the playing show's stored row and the app's trim silence`() = runTest {
        val playing = MutableStateFlow<String?>(null)
        val appTrim = MutableStateFlow(false)
        val seen = mutableListOf<PlaybackAudioState>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            playingShowAudio(playing, store, appTrim).toList(seen)
        }

        playing.value = "p1"
        store.setSpeed("p1", 1.5f)
        appTrim.value = true
        playing.value = ""
        job.cancel()

        assertEquals(
            listOf(
                PlaybackAudioState(),
                PlaybackAudioState(podcastId = "p1"),
                PlaybackAudioState(podcastId = "p1", showSpeed = 1.5f),
                PlaybackAudioState(podcastId = "p1", showSpeed = 1.5f, appTrimSilence = true),
                PlaybackAudioState(appTrimSilence = true),
            ),
            seen,
        )
    }

    @Test
    fun `without a store the playing show has no settings of its own`() = runTest {
        val seen = mutableListOf<PlaybackAudioState>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            playingShowAudio(MutableStateFlow<String?>("p1").map { it }, null, MutableStateFlow(true)).toList(seen)
        }
        job.cancel()

        assertEquals(listOf(PlaybackAudioState(podcastId = "p1", appTrimSilence = true)), seen)
    }
}
