package cx.aswin.boxcast.core.data.analytics

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import java.text.SimpleDateFormat
import java.util.*

/**
 * AppHealthReporter — Privacy-respecting, first-party analytics.
 *
 * Tracks anonymous usage metrics using Firebase Realtime Database.
 * No personal data is collected. The device is identified by a random UUID
 * that has no correlation to any user account, email, or hardware ID.
 *
 * Metrics tracked:
 * - Daily Active Users (DAU) — deduplicated by UUID, max 1 heartbeat/day
 * - New Installs — first-ever launch detection
 * - Sessions — cold start counter
 * - Foreground Engagement Time — time the app is visible on screen
 * - Background Playback Time — time audio is playing while app is backgrounded
 * - App Version — for version distribution tracking
 */
class AppHealthReporter(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "boxcast_health"
        private const val KEY_DEVICE_ID = "anonymous_device_id"
        private const val KEY_LAST_HEARTBEAT_DATE = "last_heartbeat_date"
        private const val KEY_IS_FIRST_LAUNCH = "is_first_launch_done"
        private const val KEY_SESSION_REPORTED = "session_reported_for_date"
        private const val ENGAGEMENT_CAP_MS = 30 * 60 * 1000L // 30 min cap per flush
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val db = FirebaseDatabase.getInstance("https://boxcasts-default-rtdb.asia-southeast1.firebasedatabase.app")
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private val deviceId: String
    private val appVersion: String
    private var foregroundStartMs: Long = 0L
    private var playbackStartMs: Long = 0L
    private var isPlaybackTracking = false

    init {
        // Generate or retrieve stable anonymous device ID
        var id = prefs.getString(KEY_DEVICE_ID, null)
        if (id == null) {
            id = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        }
        deviceId = id

        // Get app version (append -debug for debug builds)
        val rawVersion = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
        val isDebug = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        appVersion = if (isDebug) "$rawVersion-debug" else rawVersion
    }

    private fun todayStr(): String = dateFormat.format(Date())

    /**
     * Call from Activity.onResume() or the initial LaunchedEffect.
     * Handles: daily heartbeat, new user detection, session counting, engagement start.
     */
    fun onAppForeground() {
        val today = todayStr()
        foregroundStartMs = System.currentTimeMillis()

        // 1. Daily heartbeat (max once per day — deduplicates via same key overwrite)
        val lastHeartbeat = prefs.getString(KEY_LAST_HEARTBEAT_DATE, "")
        if (lastHeartbeat != today) {
            val payload = mapOf(
                "t" to ServerValue.TIMESTAMP,
                "v" to appVersion
            )
            db.reference.child("daily/$today/devices/$deviceId").setValue(payload)
            prefs.edit().putString(KEY_LAST_HEARTBEAT_DATE, today).apply()
        }

        // 2. New user detection (first-ever launch)
        val isFirstLaunchDone = prefs.getBoolean(KEY_IS_FIRST_LAUNCH, false)
        if (!isFirstLaunchDone) {
            val payload = mapOf(
                "t" to ServerValue.TIMESTAMP,
                "v" to appVersion
            )
            db.reference.child("daily/$today/new_users/$deviceId").setValue(payload)
            db.reference.child("devices/$deviceId").setValue(mapOf(
                "first_seen" to ServerValue.TIMESTAMP,
                "v" to appVersion
            ))
            prefs.edit().putBoolean(KEY_IS_FIRST_LAUNCH, true).apply()
        }

        // 3. Session counting (once per cold start per day)
        val sessionKey = prefs.getString(KEY_SESSION_REPORTED, "")
        val sessionTag = "$today-${android.os.Process.myPid()}"
        if (sessionKey != sessionTag) {
            db.reference.child("daily/$today/total_sessions")
                .setValue(ServerValue.increment(1))
            prefs.edit().putString(KEY_SESSION_REPORTED, sessionTag).apply()
        }
    }

    /**
     * Call from Activity.onPause() or lifecycle observer.
     * Flushes accumulated foreground engagement time.
     */
    fun onAppBackground() {
        if (foregroundStartMs > 0) {
            val elapsed = System.currentTimeMillis() - foregroundStartMs
            val capped = minOf(elapsed, ENGAGEMENT_CAP_MS)
            if (capped > 1000) { // Only report if > 1 second
                val today = todayStr()
                db.reference.child("daily/$today/total_engagement_ms")
                    .setValue(ServerValue.increment(capped))
            }
            foregroundStartMs = 0L
        }
    }

    /**
     * Call when playback starts (including background playback).
     */
    fun onPlaybackStarted() {
        if (!isPlaybackTracking) {
            playbackStartMs = System.currentTimeMillis()
            isPlaybackTracking = true
        }
    }

    /**
     * Call when playback stops/pauses.
     * Flushes accumulated playback time.
     */
    fun onPlaybackStopped() {
        if (isPlaybackTracking && playbackStartMs > 0) {
            val elapsed = System.currentTimeMillis() - playbackStartMs
            if (elapsed > 1000) { // Only report if > 1 second
                val today = todayStr()
                db.reference.child("daily/$today/total_playback_ms")
                    .setValue(ServerValue.increment(elapsed))
            }
            playbackStartMs = 0L
            isPlaybackTracking = false
        }
    }

    /**
     * Log a feature announcement interaction (e.g. user saw & dismissed the Android Auto dialog).
     * Writes to daily/<date>/feature_events/<announcementId>/<deviceId>
     */
    fun logFeatureAnnouncementSeen(announcementId: String) {
        val today = todayStr()
        val safeId = announcementId.replace(".", "_")
        val payload = mapOf(
            "t" to ServerValue.TIMESTAMP,
            "v" to appVersion
        )
        db.reference.child("daily/$today/feature_events/$safeId/$deviceId").setValue(payload)
    }
}
