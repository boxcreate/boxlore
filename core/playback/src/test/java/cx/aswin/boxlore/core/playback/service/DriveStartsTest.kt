package cx.aswin.boxlore.core.playback.service

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
}
