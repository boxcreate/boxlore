package cx.aswin.boxlore.core.playback.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Says when the phone starts or stops showing on a car's screen, for as long as it is watched.
 *
 * Android Auto's app stays connected to the player with no car present, so a connection alone does
 * not mean a drive. It announces each change of its car connection; [onChange] then asks
 * [CarConnectionState] what the connection is now.
 */
internal class CarConnectionWatcher(private val context: Context, private val onChange: () -> Unit) {
    private var receiver: BroadcastReceiver? = null

    fun start() {
        if (receiver != null) return
        val listening = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = onChange()
        }
        try {
            // Android Auto is another app, so its announcement only reaches an exported receiver.
            ContextCompat.registerReceiver(context, listening, IntentFilter(ACTION_CAR_CONNECTION_UPDATED), ContextCompat.RECEIVER_EXPORTED)
            receiver = listening
        } catch (error: RuntimeException) {
            // The check made when Android Auto connects still runs; only a later change is missed.
            Log.w("AutoBrowse", "Unable to watch the car connection", error)
        }
    }

    fun stop() {
        val listening = receiver ?: return
        receiver = null
        runCatching { context.unregisterReceiver(listening) }
    }

    companion object {
        /** Sent by Android Auto whenever its car connection changes (the Car App Library's `CarConnection` listens for it). */
        const val ACTION_CAR_CONNECTION_UPDATED = "androidx.car.app.connection.action.CAR_CONNECTION_UPDATED"
    }
}
