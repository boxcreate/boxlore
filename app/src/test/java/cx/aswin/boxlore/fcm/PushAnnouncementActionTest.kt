package cx.aswin.boxlore.fcm

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PushAnnouncementActionTest {
    @Test fun `a real notification action preserves the configured website destination`() {
        val route = "https://example.org/listen"
        val notification = show(route)
        val action = shadowOf(notification.actions.single().actionIntent).savedIntent
        assertEquals(route, action.getStringExtra("target_route"))
        assertEquals(Intent.ACTION_VIEW, action.action)
        assertNull(action.data)
        assertEquals("cx.aswin.boxlore.MainActivity", action.component?.className)
        assertNotEquals(notification.contentIntent, notification.actions.single().actionIntent)
    }

    @Test fun `legacy release download actions use the native updater target`() {
        val notification = show("https://github.com/boxcreate/boxlore/releases/download/v29/app.apk", release = true)
        val action = shadowOf(notification.actions.single().actionIntent).savedIntent
        assertEquals("boxlore://updates/download", action.getStringExtra("target_route"))
    }

    @Test fun `a general APK link stays a general web action`() {
        val route = "https://example.org/app.apk"
        val action = shadowOf(show(route).actions.single().actionIntent).savedIntent
        assertEquals(route, action.getStringExtra("target_route"))
    }

    @Test fun `unsafe and empty destinations do not create dead action buttons`() {
        assertNull(show("javascript:alert(1)").actions)
        assertNull(show("").actions)
    }

    private fun show(route: String, release: Boolean = false): Notification {
        val service = Robolectric.buildService(BoxLoreFcmService::class.java).create().get()
        val parsed = FcmPayloadParser.parse(mapOf("title" to "Action test", "body" to "Body", "route" to route, "category" to "ANNOUNCEMENT", "release_alert" to release.toString(), "show_action_in_push" to "true", "sound" to "silent"))
        service.showPushNotification(parsed)
        val manager = service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val id = NewEpisodeFcmLogic.ANNOUNCEMENT_NOTIFICATION_ID_BASE + NewEpisodeFcmLogic.announcementSlot(route)
        return shadowOf(manager).getNotification(id)
    }
}
