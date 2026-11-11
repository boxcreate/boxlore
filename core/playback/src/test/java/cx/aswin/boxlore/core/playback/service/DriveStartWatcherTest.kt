package cx.aswin.boxlore.core.playback.service

import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.playback.CarSignals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Drive starts as `:app` receives them, from Android Auto's car-connection broadcast. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DriveStartWatcherTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val reported = mutableListOf<Context>()
    private var previous: ((Context) -> Unit)? = null
    private var inCar = false
    private var lookupFails = false

    @Before
    fun setUp() {
        previous = CarSignals.onDriveStarted
        CarSignals.onDriveStarted = { reported += it }
    }

    @After
    fun tearDown() {
        CarSignals.onDriveStarted = previous
    }

    /** Checks run in place, so each one has finished before the next line. */
    private fun startWatcher() =
        DriveStartWatcher(context, CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined) {
            check(!lookupFails) { "car connection provider unavailable" }
            inCar
        }.also(DriveStartWatcher::start)

    private fun androidAutoAnnouncesAChange() {
        context.sendBroadcast(Intent(CarConnectionWatcher.ACTION_CAR_CONNECTION_UPDATED))
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun aDriveUnderWayWhenThePlayerStartsIsReportedOnce() {
        inCar = true
        val watcher = startWatcher()
        androidAutoAnnouncesAChange()

        assertEquals(listOf(context.applicationContext), reported)
        watcher.stop()
    }

    @Test
    fun eachNewDriveIsReportedAndNoCarNeverIs() {
        val watcher = startWatcher()
        androidAutoAnnouncesAChange()
        assertEquals(0, reported.size)

        inCar = true
        androidAutoAnnouncesAChange()
        inCar = false
        androidAutoAnnouncesAChange()
        inCar = true
        androidAutoAnnouncesAChange()

        assertEquals(2, reported.size)
        watcher.stop()
    }

    @Test
    fun aFailedCarLookupCountsAsNoCar() {
        lookupFails = true
        inCar = true
        val watcher = startWatcher()
        androidAutoAnnouncesAChange()
        assertEquals(0, reported.size)

        lookupFails = false
        androidAutoAnnouncesAChange()
        assertEquals(1, reported.size)
        watcher.stop()
    }

    @Test
    fun nothingIsReportedOnceThePlayerServiceStops() {
        startWatcher().stop()
        inCar = true
        androidAutoAnnouncesAChange()

        assertEquals(0, reported.size)
    }
}
