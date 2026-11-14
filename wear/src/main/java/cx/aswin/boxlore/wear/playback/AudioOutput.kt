package cx.aswin.boxlore.wear.playback

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings

/** Decides whether playback can start now, and what happens when there is nothing to hear it through. */
fun interface AudioOutputGate {
    /** Runs [play] now when something can be heard; otherwise asks for headphones and runs it once they connect. */
    fun start(play: () -> Unit)

    companion object {
        /** No check: for a build without a real player. */
        val Always = AudioOutputGate { play -> play() }
    }
}

/** What counts as something to listen through on a watch. */
internal object AudioOutputRules {
    private val hearable =
        setOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_BLE_BROADCAST,
            AudioDeviceInfo.TYPE_HEARING_AID,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            // Only watches that have a speaker list one.
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
        )

    /**
     * Whether an episode started now would be heard: headphones or a speaker are connected, or the
     * watch has a speaker of its own. [hasAudioOutput] is the watch's own word on playing sound at all.
     */
    fun canBeHeard(outputTypes: Collection<Int>, hasAudioOutput: Boolean): Boolean = hasAudioOutput && outputTypes.any { it in hearable }
}

/** A start that waits for headphones: it runs once when they connect in time, and never twice. */
internal class HeldStart(private val holdMs: Long = HOLD_MS) {
    private var held: (() -> Unit)? = null
    private var until = 0L

    fun hold(start: () -> Unit, now: Long) {
        held = start
        until = now + holdMs
    }

    /** Runs the held start, unless it waited too long: sound starting minutes later would be a surprise. */
    fun release(now: Long) {
        val start = held ?: return
        held = null
        if (now <= until) start()
    }

    private companion object {
        const val HOLD_MS = 2L * 60L * 1_000L
    }
}

/**
 * The real gate. With nothing to listen through it opens the watch's Bluetooth settings, which
 * close again as soon as headphones connect, and playback then starts by itself.
 */
class WatchAudioOutputGate(
    private val context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
) : AudioOutputGate {
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val held = HeldStart()

    init {
        val connected =
            object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                    if (canBeHeard()) held.release(clock())
                }
            }
        audio.registerAudioDeviceCallback(connected, Handler(Looper.getMainLooper()))
    }

    override fun start(play: () -> Unit) {
        if (canBeHeard()) return play()
        held.hold(play, clock())
        // From the background the settings cannot be opened; the start still waits for headphones.
        runCatching { context.startActivity(connectHeadphones()) }
    }

    private fun canBeHeard(): Boolean =
        AudioOutputRules.canBeHeard(
            outputTypes = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.type },
            hasAudioOutput = context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT),
        )

    private fun connectHeadphones(): Intent =
        Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra(EXTRA_CONNECTION_ONLY, true)
            .putExtra(EXTRA_CLOSE_ON_CONNECT, true)
            .putExtra(EXTRA_FILTER_TYPE, FILTER_TYPE_AUDIO)

    private companion object {
        // Wear OS's own extras for a settings page that only connects audio and then closes.
        const val EXTRA_CONNECTION_ONLY = "EXTRA_CONNECTION_ONLY"
        const val EXTRA_CLOSE_ON_CONNECT = "EXTRA_CLOSE_ON_CONNECT"
        const val EXTRA_FILTER_TYPE = "android.bluetooth.devicepicker.extra.FILTER_TYPE"
        const val FILTER_TYPE_AUDIO = 1
    }
}
