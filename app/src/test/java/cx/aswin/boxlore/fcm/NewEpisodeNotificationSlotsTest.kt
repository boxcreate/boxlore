package cx.aswin.boxlore.fcm

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.model.Episode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowNotificationManager

/** One notification per show: tap slots, the group summary, and copies older builds left behind. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NewEpisodeNotificationSlotsTest {
    private var now = 1_000L

    @Before
    fun useATestClock() {
        NewEpisodeNotifications.clearRecentPosts()
        NewEpisodeNotifications.clock = { now }
    }

    @After
    fun restoreTheClock() {
        NewEpisodeNotifications.clearRecentPosts()
        NewEpisodeNotifications.clock = { android.os.SystemClock.elapsedRealtime() }
    }

    /** Lets the time pass in which Android lists a post, so a dismissal after it is read as one. */
    private fun androidHasListedThePosts() {
        now += RecentEpisodePosts.GRACE_MILLIS
    }

    @Test
    fun showsWhoseIdsShareAHashedSlotGetTheirOwnTapSlots() {
        // "Aa" and "BB" have the same hashCode, so the old hashed slots put them on one notification.
        assertEquals(NewEpisodeFcmLogic.episodeSlot("Aa"), NewEpisodeFcmLogic.episodeSlot("BB"))
        val first = NewEpisodeFcmLogic.placeEpisodeNotification("Aa", emptyList(), isUpdate = false)
        val posted = listOf(NewEpisodeFcmLogic.PostedEpisodeNotification("Aa", first.slot, first.order))
        val second = NewEpisodeFcmLogic.placeEpisodeNotification("BB", posted, isUpdate = false)
        assertNotEquals(first.slot, second.slot)
        assertNull(second.replacesPodcastId)
        assertTrue(second.order > first.order)
    }

    @Test
    fun aShowKeepsItsSlotAndAnUpdateKeepsItsPlaceInTheOrder() {
        val posted = listOf(
            NewEpisodeFcmLogic.PostedEpisodeNotification("a", 3, 1),
            NewEpisodeFcmLogic.PostedEpisodeNotification("b", 0, 2),
        )
        assertEquals(NewEpisodeFcmLogic.EpisodeNotificationPlacement(3, 1, null), NewEpisodeFcmLogic.placeEpisodeNotification("a", posted, isUpdate = true))
        assertEquals(NewEpisodeFcmLogic.EpisodeNotificationPlacement(3, 3, null), NewEpisodeFcmLogic.placeEpisodeNotification("a", posted, isUpdate = false))
        // A new show takes the first free slot.
        assertEquals(1, NewEpisodeFcmLogic.placeEpisodeNotification("c", posted, isUpdate = false).slot)
    }

    @Test
    fun whenEverySlotIsTakenTheOldestNotificationMakesRoom() {
        val posted = (0 until NewEpisodeFcmLogic.MAX_EPISODE_NOTIFICATION_SLOTS).map {
            NewEpisodeFcmLogic.PostedEpisodeNotification("show-$it", slot = it, order = (100 - it).toLong())
        }
        val placement = NewEpisodeFcmLogic.placeEpisodeNotification("new", posted, isUpdate = false)
        val oldest = posted.minBy { it.order }
        assertEquals(oldest.slot, placement.slot)
        assertEquals(oldest.podcastId, placement.replacesPodcastId)
        assertEquals(101L, placement.order)
    }

    @Test
    fun twoShowsThatUsedToShareASlotBothStayPostedAndEachOpensItsOwnShow() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val shadow = org.robolectric.Shadows.shadowOf(manager)
        val id = NewEpisodeFcmLogic.EPISODE_NOTIFICATION_ID_BASE
        NewEpisodeNotifications.show(context, "Aa", mapOf("podcastTitle" to "Show A", "guid" to "a-1", "episodeId" to "1"))
        NewEpisodeNotifications.show(context, "BB", mapOf("podcastTitle" to "Show B", "guid" to "b-1", "episodeId" to "2"))

        val a = shadow.getNotification("Aa", id)
        val b = shadow.getNotification("BB", id)
        assertNotNull(a)
        assertNotNull(b)
        val tapA = org.robolectric.Shadows.shadowOf(a.contentIntent)
        val tapB = org.robolectric.Shadows.shadowOf(b.contentIntent)
        assertNotEquals(tapA.requestCode, tapB.requestCode)
        assertEquals("Aa", tapA.savedIntent.getStringExtra("podcast_id"))
        assertEquals("BB", tapB.savedIntent.getStringExtra("podcast_id"))
        assertEquals(NewEpisodeFcmLogic.EPISODE_GROUP_KEY, a.group)
        assertEquals(NewEpisodeFcmLogic.EPISODE_GROUP_KEY, b.group)
    }

    @Test
    fun hydrationUpdatesEachShowsOwnNotificationWhenTheirIdsShareAHashedSlot() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val shadow = org.robolectric.Shadows.shadowOf(manager)
        val id = NewEpisodeFcmLogic.EPISODE_NOTIFICATION_ID_BASE
        val dataA = mapOf("podcastTitle" to "Show A", "guid" to "a-1")
        val dataB = mapOf("podcastTitle" to "Show B", "guid" to "b-1")
        NewEpisodeNotifications.show(context, "Aa", dataA)
        NewEpisodeNotifications.show(context, "BB", dataB)
        val slotB = org.robolectric.Shadows.shadowOf(shadow.getNotification("BB", id).contentIntent).requestCode

        NewEpisodeNotifications.show(context, "Aa", dataA, Episode("-1", "Hydrated A", "", "https://cdn/a.mp3", "Aa"), fetchArtwork = true)
        NewEpisodeNotifications.show(context, "BB", dataB, Episode("-2", "Hydrated B", "", "https://cdn/b.mp3", "BB"), fetchArtwork = true)

        val a = shadow.getNotification("Aa", id)
        val b = shadow.getNotification("BB", id)
        assertEquals("\"Hydrated A\"", a.extras.getString(android.app.Notification.EXTRA_TEXT))
        assertEquals("\"Hydrated B\"", b.extras.getString(android.app.Notification.EXTRA_TEXT))
        assertEquals(slotB, org.robolectric.Shadows.shadowOf(b.contentIntent).requestCode)
        assertEquals("BB", org.robolectric.Shadows.shadowOf(b.contentIntent).savedIntent.getStringExtra("podcast_id"))
    }

    @Test
    fun severalShowsAreGroupedUnderOneQuietSummary() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val shadow = org.robolectric.Shadows.shadowOf(manager)
        NewEpisodeNotifications.show(context, "1", mapOf("podcastTitle" to "One", "guid" to "g1"))
        assertNull(shadow.getNotification(NewEpisodeFcmLogic.EPISODE_SUMMARY_NOTIFICATION_ID))

        NewEpisodeNotifications.show(context, "2", mapOf("podcastTitle" to "Two", "guid" to "g2"))
        val summary = shadow.getNotification(NewEpisodeFcmLogic.EPISODE_SUMMARY_NOTIFICATION_ID)
        assertNotNull(summary)
        assertTrue(summary.flags and android.app.Notification.FLAG_GROUP_SUMMARY != 0)
        assertEquals(NewEpisodeFcmLogic.EPISODE_GROUP_KEY, summary.group)
        assertEquals(android.app.Notification.GROUP_ALERT_CHILDREN, summary.groupAlertBehavior)
        assertEquals("2 shows have new episodes", summary.extras.getString(android.app.Notification.EXTRA_TEXT))

        val summaryTap = org.robolectric.Shadows.shadowOf(summary.contentIntent).savedIntent
        assertEquals(NewEpisodeFcmLogic.SUMMARY_NOTIFICATION_TYPE, summaryTap.getStringExtra("notification_type"))
        assertNull(summaryTap.getStringExtra("podcast_id"))
        // A tap on the bundle clears every show's notification with it, so it opens the list of new episodes.
        assertEquals(NewEpisodeFcmLogic.NEW_EPISODES_ROUTE, summaryTap.getStringExtra("target_route"))

        androidHasListedThePosts()
        manager.cancel("2", NewEpisodeFcmLogic.EPISODE_NOTIFICATION_ID_BASE)
        NewEpisodeNotifications.show(context, "1", mapOf("podcastTitle" to "One", "guid" to "g1-next"))
        assertNull(shadow.getNotification(NewEpisodeFcmLogic.EPISODE_SUMMARY_NOTIFICATION_ID))
        assertNotNull(shadow.getNotification("1", NewEpisodeFcmLogic.EPISODE_NOTIFICATION_ID_BASE))
    }

    @Test
    fun aSnoozedShowsTapStillOpensItsOwnShowAfterAnotherShowTakesItsSlot() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val shadow = org.robolectric.Shadows.shadowOf(manager)
        val id = NewEpisodeFcmLogic.EPISODE_NOTIFICATION_ID_BASE
        NewEpisodeNotifications.show(context, "A", mapOf("podcastTitle" to "Show A", "guid" to "a-1", "episodeId" to "1"))
        val snoozedTap = shadow.getNotification("A", id).contentIntent
        androidHasListedThePosts()
        // Android stops listing a snoozed notification but keeps its tap for when it comes back.
        manager.cancel("A", id)

        NewEpisodeNotifications.show(context, "B", mapOf("podcastTitle" to "Show B", "guid" to "b-1", "episodeId" to "2"))

        val tapB = shadow.getNotification("B", id).contentIntent
        assertEquals(org.robolectric.Shadows.shadowOf(snoozedTap).requestCode, org.robolectric.Shadows.shadowOf(tapB).requestCode)
        assertNotEquals(snoozedTap, tapB)
        assertEquals("A", org.robolectric.Shadows.shadowOf(snoozedTap).savedIntent.getStringExtra("podcast_id"))
        assertEquals("B", org.robolectric.Shadows.shadowOf(tapB).savedIntent.getStringExtra("podcast_id"))
    }

    @Test
    @Config(shadows = [GroupCancellingNotificationManager::class])
    fun theLastShowLeftKeepsItsNewNotificationWhenTheSummaryGoes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val shadow = org.robolectric.Shadows.shadowOf(manager)
        val id = NewEpisodeFcmLogic.EPISODE_NOTIFICATION_ID_BASE
        // Like Android, this manager cancels a summary's whole group with it.
        val grouped = { summary: Boolean ->
            androidx.core.app.NotificationCompat.Builder(context, "boxlore_new_episodes_v1")
                .setSmallIcon(android.R.drawable.ic_dialog_info).setGroup(NewEpisodeFcmLogic.EPISODE_GROUP_KEY).setGroupSummary(summary).build()
        }
        manager.notify("probe", id, grouped(false))
        manager.notify(NewEpisodeFcmLogic.EPISODE_SUMMARY_NOTIFICATION_ID, grouped(true))
        manager.cancel(NewEpisodeFcmLogic.EPISODE_SUMMARY_NOTIFICATION_ID)
        assertNull(shadow.getNotification("probe", id))

        NewEpisodeNotifications.show(context, "A", mapOf("podcastTitle" to "Show A", "guid" to "a-1"))
        NewEpisodeNotifications.show(context, "B", mapOf("podcastTitle" to "Show B", "guid" to "b-1"))
        assertNotNull(shadow.getNotification(NewEpisodeFcmLogic.EPISODE_SUMMARY_NOTIFICATION_ID))

        androidHasListedThePosts()
        manager.cancel("B", id)
        NewEpisodeNotifications.show(context, "A", mapOf("podcastTitle" to "Show A", "guid" to "a-2", "episodeTitle" to "Second"))

        val a = shadow.getNotification("A", id)
        assertNotNull(a)
        assertEquals("\"Second\"", a.extras.getString(android.app.Notification.EXTRA_TEXT))
        assertNull(shadow.getNotification(NewEpisodeFcmLogic.EPISODE_SUMMARY_NOTIFICATION_ID))
    }

    @Test
    @Config(shadows = [GroupCancellingNotificationManager::class])
    fun anArtworkUpdateForTheLastShowLeftKeepsItsNotification() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val shadow = org.robolectric.Shadows.shadowOf(manager)
        val id = NewEpisodeFcmLogic.EPISODE_NOTIFICATION_ID_BASE
        val dataA = mapOf("podcastTitle" to "Show A", "guid" to "a-1")
        NewEpisodeNotifications.show(context, "A", dataA)
        NewEpisodeNotifications.show(context, "B", mapOf("podcastTitle" to "Show B", "guid" to "b-1"))

        androidHasListedThePosts()
        manager.cancel("B", id)
        NewEpisodeNotifications.show(context, "A", dataA, Episode("-1", "Hydrated A", "", "https://cdn/a.mp3", "A"), fetchArtwork = true)

        val a = shadow.getNotification("A", id)
        assertNotNull(a)
        assertEquals("\"Hydrated A\"", a.extras.getString(android.app.Notification.EXTRA_TEXT))
    }

    @Test
    fun theSummaryIsOnlyEverCancelledBeforeAShowsNotificationIsPosted() {
        // No other show left: a showing summary goes first, and nothing is posted after.
        assertEquals(
            NewEpisodeFcmLogic.SummaryPlan(cancelSummaryFirst = true, summaryCount = null),
            NewEpisodeFcmLogic.planSummary(otherShowsShowing = 0, summaryShowing = true, isUpdate = false),
        )
        // An update leaves it, so the show's notification is updated in place and stays quiet.
        assertEquals(
            NewEpisodeFcmLogic.SummaryPlan(cancelSummaryFirst = false, summaryCount = null),
            NewEpisodeFcmLogic.planSummary(otherShowsShowing = 0, summaryShowing = true, isUpdate = true),
        )
        assertEquals(
            NewEpisodeFcmLogic.SummaryPlan(cancelSummaryFirst = false, summaryCount = null),
            NewEpisodeFcmLogic.planSummary(otherShowsShowing = 0, summaryShowing = false, isUpdate = false),
        )
        // Other shows still showing: never cancelled, posted after for every show.
        listOf(true, false).forEach { summaryShowing ->
            assertEquals(
                NewEpisodeFcmLogic.SummaryPlan(cancelSummaryFirst = false, summaryCount = 3),
                NewEpisodeFcmLogic.planSummary(otherShowsShowing = 2, summaryShowing = summaryShowing, isUpdate = false),
            )
        }
    }

    @Test
    fun tapSlotsNeverReuseRequestCodesOlderBuildsOrOtherNotificationsUse() {
        val legacy = NewEpisodeFcmLogic.EPISODE_NOTIFICATION_ID_BASE until
            NewEpisodeFcmLogic.EPISODE_NOTIFICATION_ID_BASE + NewEpisodeFcmLogic.MAX_EPISODE_NOTIFICATION_SLOTS
        val announcements = NewEpisodeFcmLogic.ANNOUNCEMENT_REQUEST_CODE_BASE until
            NewEpisodeFcmLogic.ANNOUNCEMENT_ACTION_REQUEST_CODE_BASE + NewEpisodeFcmLogic.MAX_ANNOUNCEMENT_SLOTS
        (0 until NewEpisodeFcmLogic.MAX_EPISODE_NOTIFICATION_SLOTS).forEach { slot ->
            val code = NewEpisodeFcmLogic.EPISODE_REQUEST_CODE_BASE + slot
            assertTrue(code !in legacy)
            assertTrue(code !in announcements)
            assertNotEquals(NewEpisodeFcmLogic.EPISODE_SUMMARY_REQUEST_CODE, code)
        }
    }

    @Test
    fun theOldestShowMakesRoomOnceEverySlotIsShowingAndTapSlotsStayDistinct() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val shadow = org.robolectric.Shadows.shadowOf(manager)
        val id = NewEpisodeFcmLogic.EPISODE_NOTIFICATION_ID_BASE
        val shows = (0..NewEpisodeFcmLogic.MAX_EPISODE_NOTIFICATION_SLOTS).map { "show-$it" }
        shows.forEach { NewEpisodeNotifications.show(context, it, mapOf("podcastTitle" to it, "guid" to "g-$it")) }

        assertNull(shadow.getNotification(shows.first(), id))
        val showing = shows.drop(1).map { shadow.getNotification(it, id) }
        assertTrue(showing.all { it != null })
        val requestCodes = showing.map { org.robolectric.Shadows.shadowOf(it.contentIntent).requestCode }.toSet()
        assertEquals(NewEpisodeFcmLogic.MAX_EPISODE_NOTIFICATION_SLOTS, requestCodes.size)
        shows.drop(1).zip(showing).forEach { (show, notification) ->
            assertEquals(show, org.robolectric.Shadows.shadowOf(notification.contentIntent).savedIntent.getStringExtra("podcast_id"))
        }
    }

    @Test
    fun anUntaggedNotificationFromAnOlderBuildIsClearedWhenItsShowPostsAgain() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val legacyId = NewEpisodeFcmLogic.EPISODE_NOTIFICATION_ID_BASE + NewEpisodeFcmLogic.episodeSlot("123")
        manager.notify(legacyId, legacyNotification(context, "Show", null))
        NewEpisodeNotifications.show(context, "123", mapOf("podcastTitle" to "Show", "guid" to "g"))
        val shadow = org.robolectric.Shadows.shadowOf(manager)
        assertNull(shadow.getNotification(legacyId))
        assertNotNull(shadow.getNotification("123", NewEpisodeFcmLogic.EPISODE_NOTIFICATION_ID_BASE))
    }

    @Test
    fun anotherShowsOlderBuildNotificationKeepsShowingAndKeepsOpeningItsOwnShow() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val shadow = org.robolectric.Shadows.shadowOf(manager)
        // An older build left show BB's notification at the hashed id it shares with "Aa", tapping
        // through the request code of that hashed slot.
        val slot = NewEpisodeFcmLogic.episodeSlot("BB")
        assertEquals(NewEpisodeFcmLogic.episodeSlot("Aa"), slot)
        val legacyId = NewEpisodeFcmLogic.EPISODE_NOTIFICATION_ID_BASE + slot
        val legacyTap = android.app.PendingIntent.getActivity(
            context,
            NewEpisodeFcmLogic.EPISODE_NOTIFICATION_ID_BASE + slot,
            NewEpisodeFcmLogic.createNormalizedPushIntent(context, "boxlore://podcast/BB", "new_episode", podcastId = "BB"),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        manager.notify(legacyId, legacyNotification(context, "Show B", legacyTap))

        NewEpisodeNotifications.show(context, "Aa", mapOf("podcastTitle" to "Show A", "guid" to "a-1"))

        val legacy = shadow.getNotification(legacyId)
        assertNotNull(legacy)
        assertEquals("BB", org.robolectric.Shadows.shadowOf(legacy.contentIntent).savedIntent.getStringExtra("podcast_id"))
        val a = shadow.getNotification("Aa", NewEpisodeFcmLogic.EPISODE_NOTIFICATION_ID_BASE)
        val tapA = org.robolectric.Shadows.shadowOf(a.contentIntent)
        val legacyCodes = NewEpisodeFcmLogic.EPISODE_NOTIFICATION_ID_BASE until
            NewEpisodeFcmLogic.EPISODE_NOTIFICATION_ID_BASE + NewEpisodeFcmLogic.MAX_EPISODE_NOTIFICATION_SLOTS
        assertTrue(tapA.requestCode !in legacyCodes)
        assertEquals("Aa", tapA.savedIntent.getStringExtra("podcast_id"))
    }

    @Test
    fun onlyTheShowsOwnOlderBuildCopyIsCleared() {
        val title = NewEpisodeFcmLogic.legacyEpisodeNotificationTitle("Show A")
        val id = NewEpisodeFcmLogic.EPISODE_NOTIFICATION_ID_BASE + NewEpisodeFcmLogic.episodeSlot("Aa")
        val own = NewEpisodeFcmLogic.LegacyEpisodeNotification(id, title)
        val other = NewEpisodeFcmLogic.LegacyEpisodeNotification(id, NewEpisodeFcmLogic.legacyEpisodeNotificationTitle("Show B"))
        val elsewhere = NewEpisodeFcmLogic.LegacyEpisodeNotification(id + 1, title)
        assertEquals(id, NewEpisodeFcmLogic.legacyCopyToClear("Aa", title, listOf(own)))
        assertNull(NewEpisodeFcmLogic.legacyCopyToClear("Aa", title, listOf(other)))
        assertNull(NewEpisodeFcmLogic.legacyCopyToClear("Aa", title, listOf(elsewhere)))
    }

    @Test
    @Config(shadows = [LaggingNotificationManager::class])
    fun backToBackPushesGetTheirOwnTapsAndASummaryBeforeAndroidListsThem() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val shadow = org.robolectric.Shadows.shadowOf(manager) as LaggingNotificationManager
        val id = NewEpisodeFcmLogic.EPISODE_NOTIFICATION_ID_BASE
        // Android has not listed A's notification yet when B's push arrives.
        shadow.freeze()
        NewEpisodeNotifications.show(context, "A", mapOf("podcastTitle" to "Show A", "guid" to "a-1"))
        NewEpisodeNotifications.show(context, "B", mapOf("podcastTitle" to "Show B", "guid" to "b-1"))
        shadow.catchUp()

        val tapA = org.robolectric.Shadows.shadowOf(shadow.getNotification("A", id).contentIntent)
        val tapB = org.robolectric.Shadows.shadowOf(shadow.getNotification("B", id).contentIntent)
        assertNotEquals(tapA.requestCode, tapB.requestCode)
        assertEquals("A", tapA.savedIntent.getStringExtra("podcast_id"))
        assertEquals("B", tapB.savedIntent.getStringExtra("podcast_id"))
        val summary = shadow.getNotification(NewEpisodeFcmLogic.EPISODE_SUMMARY_NOTIFICATION_ID)
        assertEquals("2 shows have new episodes", summary.extras.getString(android.app.Notification.EXTRA_TEXT))
    }

    @Test
    @Config(shadows = [LaggingNotificationManager::class])
    fun theSummaryIsNotCancelledWhileAnotherShowsNewNotificationIsOnItsWay() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val shadow = org.robolectric.Shadows.shadowOf(manager) as LaggingNotificationManager
        val id = NewEpisodeFcmLogic.EPISODE_NOTIFICATION_ID_BASE
        NewEpisodeNotifications.show(context, "C", mapOf("podcastTitle" to "Show C", "guid" to "c-1"))
        NewEpisodeNotifications.show(context, "X", mapOf("podcastTitle" to "Show X", "guid" to "x-1"))
        androidHasListedThePosts()
        manager.cancel("X", id)

        // D's notification and the summary's update are not listed yet when C's next push arrives.
        shadow.freeze()
        NewEpisodeNotifications.show(context, "D", mapOf("podcastTitle" to "Show D", "guid" to "d-1"))
        NewEpisodeNotifications.show(context, "C", mapOf("podcastTitle" to "Show C", "guid" to "c-2", "episodeTitle" to "Next"))
        shadow.catchUp()

        assertNotNull(shadow.getNotification("D", id))
        assertEquals("\"Next\"", shadow.getNotification("C", id).extras.getString(android.app.Notification.EXTRA_TEXT))
        val summary = shadow.getNotification(NewEpisodeFcmLogic.EPISODE_SUMMARY_NOTIFICATION_ID)
        assertEquals("2 shows have new episodes", summary.extras.getString(android.app.Notification.EXTRA_TEXT))
    }

    @Test
    @Config(shadows = [LaggingNotificationManager::class])
    fun aLateArtworkUpdateCannotReplaceANewerReleaseAndroidHasNotListed() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val shadow = org.robolectric.Shadows.shadowOf(manager) as LaggingNotificationManager
        val id = NewEpisodeFcmLogic.EPISODE_NOTIFICATION_ID_BASE
        val older = mapOf("podcastTitle" to "Show", "guid" to "old-release", "episodeTitle" to "Older")
        NewEpisodeNotifications.show(context, "A", older)
        shadow.freeze()
        NewEpisodeNotifications.show(context, "A", older + mapOf("guid" to "new-release", "episodeTitle" to "Newer"))
        NewEpisodeNotifications.show(context, "A", older, Episode("-1", "Older", "", "https://cdn/a.mp3", "A"), fetchArtwork = true)
        shadow.catchUp()

        assertEquals("\"Newer\"", shadow.getNotification("A", id).extras.getString(android.app.Notification.EXTRA_TEXT))
    }

    private fun legacyNotification(context: Context, podcastTitle: String, tap: android.app.PendingIntent?): android.app.Notification =
        androidx.core.app.NotificationCompat.Builder(context, "boxlore_new_episodes_v1")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(NewEpisodeFcmLogic.legacyEpisodeNotificationTitle(podcastTitle))
            .apply { if (tap != null) setContentIntent(tap) }
            .build()
}

/** A notification manager that, like Android's, cancels every notification in a group with its summary. */
@Implements(android.app.NotificationManager::class)
open class GroupCancellingNotificationManager : ShadowNotificationManager() {
    @Implementation
    override fun cancel(id: Int) {
        cancel(null, id)
    }

    @Implementation
    override fun cancel(tag: String?, id: Int) {
        // What is really posted, which Android cancels from even before it lists it.
        val posted = super.getActiveNotifications()
        val summaryGroup = posted
            .firstOrNull { it.tag == tag && it.id == id && it.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY != 0 }
            ?.notification
            ?.group
        super.cancel(tag, id)
        if (summaryGroup != null) {
            posted.filter { it.notification.group == summaryGroup }.forEach { super.cancel(it.tag, it.id) }
        }
    }
}

/**
 * A notification manager whose `activeNotifications` trails `notify()` and `cancel()`, as Android's
 * does: after [freeze] it keeps listing what was showing then, until [catchUp].
 */
@Implements(android.app.NotificationManager::class)
class LaggingNotificationManager : GroupCancellingNotificationManager() {
    private var listed: Array<android.service.notification.StatusBarNotification>? = null

    fun freeze() {
        listed = super.getActiveNotifications()
    }

    fun catchUp() {
        listed = null
    }

    @Implementation
    override fun getActiveNotifications(): Array<android.service.notification.StatusBarNotification> =
        listed ?: super.getActiveNotifications()
}
