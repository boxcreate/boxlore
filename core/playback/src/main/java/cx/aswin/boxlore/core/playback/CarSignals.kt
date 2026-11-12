package cx.aswin.boxlore.core.playback

import android.content.Context
import cx.aswin.boxlore.core.playback.service.CarConnectionState

/**
 * Car moments `:app` can act on; playback shows nothing itself. `:app` sets the hooks at start-up.
 *
 * - [onDriveStarted]: the player was running when the phone started showing on a car's screen.
 *   Android Auto announces its car connection to every app listening, including apps it keeps off
 *   the car's screen (installs that didn't come from a trusted store such as Google Play).
 * - [onCarScreensOpened]: Android Auto asked for boxlore's lists during a drive, so boxlore is on
 *   the car's screen. Sent once per player service.
 *
 * Both run off the main thread.
 */
object CarSignals {
    @Volatile var onDriveStarted: ((Context) -> Unit)? = null

    @Volatile var onCarScreensOpened: ((Context) -> Unit)? = null

    /** Whether the phone is in a car right now: Android Auto on a car's screen, car mode, or Automotive. Reads a provider, so off the main thread. */
    fun isInCar(context: Context): Boolean = CarConnectionState.isInCar(context)
}
