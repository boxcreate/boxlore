package cx.aswin.boxlore.core.playback.service

import android.content.Context
import cx.aswin.boxlore.core.playback.CarSignals
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Says when a drive starts while the player service runs, playing or paused, by Android Auto's own
 * announcement rather than its connection to the player: Android Auto never connects to an app it
 * keeps off the car's screen, but it announces the car to every app. Reports [CarSignals.onDriveStarted].
 * A paused player counts on purpose: boxlore is open and the phone is in a car, which is when the
 * listener looks for boxlore on the car's screen.
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
            val started = starts.check { runCatching { withContext(io) { isInCar(context) } }.getOrDefault(false) }
            if (started) {
                withContext(io) { runCatching { CarSignals.onDriveStarted?.invoke(context.applicationContext) } }
            }
        }
    }
}

/**
 * Turns car checks into drive starts: the first check in a car, or a change from not in a car to in one.
 * [check] looks the car up and applies the answer under one lock, so a slow lookup can't land after a
 * newer one and leave a stale "in a car" that swallows the next drive.
 */
internal class DriveStarts {
    private val lock = Mutex()
    private var inCar = false

    suspend fun check(lookUp: suspend () -> Boolean): Boolean = lock.withLock { started(lookUp()) }

    @Synchronized
    fun started(nowInCar: Boolean): Boolean {
        val started = nowInCar && !inCar
        inCar = nowInCar
        return started
    }
}
