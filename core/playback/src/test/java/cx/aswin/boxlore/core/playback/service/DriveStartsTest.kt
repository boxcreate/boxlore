package cx.aswin.boxlore.core.playback.service

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DriveStartsTest {
    @Test
    fun aDriveAlreadyUnderWayWhenThePlayerStartsCounts() {
        assertEquals(true, DriveStarts().started(nowInCar = true))
    }

    @Test
    fun repeatedCarChecksDuringOneDriveStartItOnce() {
        val starts = DriveStarts()
        assertEquals(listOf(true, false, false), listOf(true, true, true).map(starts::started))
    }

    @Test
    fun noCarIsNeverAStart() {
        val starts = DriveStarts()
        assertEquals(listOf(false, false), listOf(false, false).map(starts::started))
    }

    @Test
    fun aNewDriveAfterTheCarWentStartsAgain() {
        val starts = DriveStarts()
        assertEquals(listOf(false, true, false, true), listOf(false, true, false, true).map(starts::started))
    }

    @Test
    fun aSlowInCarCheckCannotLandAfterANewerOne() = runTest {
        // Regression: the lookup ran outside the lock, so a slow "in a car" answer could land after a later
        // "not in a car" one, leave the car connected, and swallow the next drive start.
        val starts = DriveStarts()
        val slowAnswer = CompletableDeferred<Boolean>()
        val first = async { starts.check { slowAnswer.await() } }
        val second = async { starts.check { false } }
        runCurrent()
        slowAnswer.complete(true)

        assertEquals(true, first.await())
        assertEquals(false, second.await())
        // The car left after the slow check, so connecting again is a new drive.
        assertEquals(true, starts.check { true })
    }
}
