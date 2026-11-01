package cx.aswin.boxlore.core.playback.service

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.media3.common.DeviceInfo
import androidx.media3.common.Player
import cx.aswin.boxlore.core.analytics.PlaybackListenValues
import cx.aswin.boxlore.core.database.ListeningSessionValues

/**
 * Where a play is heard, as a timed play stores it ([ListeningSessionValues] `OUTPUT_*`): Cast, the
 * car, Bluetooth, wired or USB headphones, or the phone's speaker. Read on the main thread when a
 * play ends; null when it can't be told.
 */
internal object ListeningOutputRoute {
    fun current(context: Context, player: Player?,): String? = runCatching {
        if (player?.deviceInfo?.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE) return@runCatching ListeningSessionValues.OUTPUT_CAST
        val uiMode = context.getSystemService(UiModeManager::class.java)
        if (uiMode?.currentModeType == Configuration.UI_MODE_TYPE_CAR) return@runCatching ListeningSessionValues.OUTPUT_CAR
        val audio = context.getSystemService(AudioManager::class.java) ?: return@runCatching null
        classify(activeOutputTypes(audio))
    }.getOrNull()

    /**
     * Where the sound goes for a listen's `output`: Cast, Bluetooth, wired (or USB) headphones, the
     * speaker, or other. The car counts as a controller, not an output, so car mode reads the audio
     * route like anywhere else.
     */
    fun forAnalytics(context: Context, player: Player?,): String = runCatching {
        if (player?.deviceInfo?.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE) return@runCatching PlaybackListenValues.OUTPUT_CAST
        val audio = context.getSystemService(AudioManager::class.java) ?: return@runCatching PlaybackListenValues.OUTPUT_OTHER
        analyticsOutputOf(activeOutputTypes(audio))
    }.getOrDefault(PlaybackListenValues.OUTPUT_OTHER)

    /**
     * A listen's `output` among [deviceTypes], in [classify]'s order. Hearing aids connect over
     * Bluetooth, so they count as Bluetooth here, not as headphones.
     */
    fun analyticsOutputOf(deviceTypes: Collection<Int>): String = when {
        deviceTypes.any { it in BLUETOOTH || it == AudioDeviceInfo.TYPE_HEARING_AID } -> PlaybackListenValues.OUTPUT_BLUETOOTH
        deviceTypes.any { it in HEADPHONES } -> PlaybackListenValues.OUTPUT_WIRED
        deviceTypes.any { it in SPEAKER } -> PlaybackListenValues.OUTPUT_SPEAKER
        else -> PlaybackListenValues.OUTPUT_OTHER
    }

    /**
     * The route among [deviceTypes]: Bluetooth before wired headphones before the speaker, which is
     * also the order Android routes media in when several are connected.
     */
    fun classify(deviceTypes: Collection<Int>): String? = when {
        deviceTypes.any { it in BLUETOOTH } -> ListeningSessionValues.OUTPUT_BLUETOOTH
        deviceTypes.any { it in HEADPHONES } -> ListeningSessionValues.OUTPUT_HEADPHONES
        deviceTypes.any { it in SPEAKER } -> ListeningSessionValues.OUTPUT_SPEAKER
        else -> null
    }

    /** The devices media plays to now (Android 13+), else every connected output. */
    private fun activeOutputTypes(audio: AudioManager): List<Int> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val media = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()
        audio.getAudioDevicesForAttributes(media).map { it.type }
    } else {
        audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.type }
    }

    private val BLUETOOTH =
        setOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
        )
    private val HEADPHONES =
        setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_HEARING_AID,
        )
    private val SPEAKER = setOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE)
}
