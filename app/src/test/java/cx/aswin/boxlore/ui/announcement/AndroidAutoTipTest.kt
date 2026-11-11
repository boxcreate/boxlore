package cx.aswin.boxlore.ui.announcement

import cx.aswin.boxlore.ui.announcement.AndroidAutoTip.Trigger
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidAutoTipTest {
    private class FakeStore(var shown: Boolean = false, var seenInCar: Boolean = false) : AndroidAutoTip.Store {
        override fun shown() = shown

        override fun markShown() {
            shown = true
        }

        override fun seenInCar() = seenInCar

        override fun markSeenInCar() {
            seenInCar = true
        }
    }

    private class Harness(
        val store: FakeStore = FakeStore(),
        var playInstall: Boolean = false,
        var inCar: Boolean = true,
        var waitingAnnouncement: Boolean = false,
        var notificationsWork: Boolean = true,
    ) {
        var alerts = 0
        var notifications = 0

        val tip =
            AndroidAutoTip(
                store = store,
                isPlayInstall = { playInstall },
                isInCar = { inCar },
                hasWaitingAnnouncement = { waitingAnnouncement },
                saveAlert = { alerts++ },
                postNotification = {
                    if (notificationsWork) notifications++
                    notificationsWork
                },
            )
    }

    @Test
    fun openingBoxloreDuringADriveShowsTheAlertOnly() = runTest {
        val h = Harness()
        h.tip.maybeShow(Trigger.APP_OPENED)
        assertEquals(1, h.alerts)
        assertEquals(0, h.notifications)
        assertTrue(h.store.shown)
    }

    @Test
    fun aDriveStartingWhilePlayingNotifiesAndSavesTheAlert() = runTest {
        val h = Harness()
        h.tip.maybeShow(Trigger.DRIVE_STARTED)
        assertEquals(1, h.alerts)
        assertEquals(1, h.notifications)
        assertTrue(h.store.shown)
    }

    @Test
    fun itShowsOncePerInstall() = runTest {
        val h = Harness()
        h.tip.maybeShow(Trigger.DRIVE_STARTED)
        h.tip.maybeShow(Trigger.APP_OPENED)
        h.tip.maybeShow(Trigger.DRIVE_STARTED)
        assertEquals(1, h.alerts)
        assertEquals(1, h.notifications)
    }

    @Test
    fun aPlayStoreInstallNeverSeesIt() = runTest {
        val h = Harness(playInstall = true)
        h.tip.maybeShow(Trigger.APP_OPENED)
        h.tip.maybeShow(Trigger.DRIVE_STARTED)
        assertEquals(0, h.alerts + h.notifications)
        assertFalse(h.store.shown)
    }

    @Test
    fun outsideACarNothingHappensAndALaterDriveStillGetsIt() = runTest {
        val h = Harness(inCar = false)
        h.tip.maybeShow(Trigger.APP_OPENED)
        assertEquals(0, h.alerts)
        assertFalse(h.store.shown)

        h.inCar = true
        h.tip.maybeShow(Trigger.APP_OPENED)
        assertEquals(1, h.alerts)
    }

    @Test
    fun onceBoxloreIsOnTheCarsScreenItNeverShows() = runTest {
        val h = Harness()
        h.tip.carScreensOpened()
        h.tip.maybeShow(Trigger.DRIVE_STARTED)
        assertEquals(0, h.alerts + h.notifications)
        assertTrue(h.store.seenInCar)
    }

    @Test
    fun anUnseenAnnouncementIsNotReplacedAndTheNotificationStillGoesOut() = runTest {
        val h = Harness(waitingAnnouncement = true)
        h.tip.maybeShow(Trigger.DRIVE_STARTED)
        assertEquals(0, h.alerts)
        assertEquals(1, h.notifications)
        assertTrue(h.store.shown)
    }

    @Test
    fun whenNothingCouldBeShownALaterTryStillCan() = runTest {
        val h = Harness(waitingAnnouncement = true, notificationsWork = false)
        h.tip.maybeShow(Trigger.DRIVE_STARTED)
        h.tip.maybeShow(Trigger.APP_OPENED)
        assertEquals(0, h.alerts + h.notifications)
        assertFalse(h.store.shown)

        h.waitingAnnouncement = false
        h.tip.maybeShow(Trigger.APP_OPENED)
        assertEquals(1, h.alerts)
        assertTrue(h.store.shown)
    }
}
