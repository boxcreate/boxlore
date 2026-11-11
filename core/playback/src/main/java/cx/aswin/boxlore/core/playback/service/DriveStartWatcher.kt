package cx.aswin.boxlore.core.playback.service

import android.content.Context
import cx.aswin.boxlore.core.playback.CarSignals
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Says when a drive starts while the player runs, by Android Auto's own announcement rather than its
 * connection to the player: Android Auto never connects to an app it keeps off the car's screen,
 * but it announces the car to every app. Reports [CarSignals.onDriveStarted].
 */
internal class DriveStartWatcher(
    private val context: Context,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val isInCar: (Context) -> Boolean = CarConnectionState::isInCar,
) {
    private val starts = DriveStarts()
    private val watcher = CarConnectionWatcher(context) { check() }

    /** Starts watching, and reports a drive already under way when the player starts. */
    fun start() {
        watcher.start()
        check()
    }

    fun stop() = watcher.stop()

    private fun check() {
        scope.launch {
            val inCar = runCatching { withContext(io) { isInCar(context) } }.getOrDefault(false)
            if (starts.started(inCar)) {
                withContext(io) { runCatching { CarSignals.onDriveStarted?.invoke(context.applicationContext) } }
            }
        }
    }
}

/** Turns car checks into drive starts: the first check in a car, or a change from not in a car to in one. */
internal class DriveStarts {
    private var inCar = false

    @Synchronized
    fun started(nowInCar: Boolean): Boolean {
        val started = nowInCar && !inCar
        inCar = nowInCar
        return started
    }
}
