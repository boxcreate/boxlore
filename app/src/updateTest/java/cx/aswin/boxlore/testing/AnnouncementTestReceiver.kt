package cx.aswin.boxlore.testing

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import cx.aswin.boxlore.BoxLoreApplication
import cx.aswin.boxlore.BuildConfig
import cx.aswin.boxlore.fcm.FcmPayloadParser
import cx.aswin.boxlore.fcm.toAnnouncement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

/** ADB-only, local in-app preview. This component cannot be packaged in shipping variants. */
class AnnouncementTestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!BuildConfig.BOXLORE_ISOLATED_TESTS || intent.action != "cx.aswin.boxlore.TEST_ANNOUNCEMENT") return
        val json = runCatching { JSONObject(intent.getStringExtra("payload") ?: return) }.getOrNull() ?: return
        val data = json.keys().asSequence().associateWith { json.optString(it) } + mapOf("test_mode" to "true", "release_version_code" to "0")
        val parsed = FcmPayloadParser.parse(data)
        if (parsed.title.isBlank() || parsed.body.isBlank()) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                (context.applicationContext as BoxLoreApplication).userPreferencesRepository.setAnnouncement(parsed.toAnnouncement())
                pending.resultCode = android.app.Activity.RESULT_OK
            } finally {
                pending.finish()
            }
        }
    }
}
