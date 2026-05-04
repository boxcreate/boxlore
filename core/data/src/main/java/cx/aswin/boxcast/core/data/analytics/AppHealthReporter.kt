package cx.aswin.boxcast.core.data.analytics

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * AppHealthReporter — Privacy-respecting, first-party analytics (Insight Engine).
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
 * Background audio is flushed via MediaSession service hooks.
 */
class AppHealthReporter(
    private val context: Context,
    private val telemetryUrl: String,
    private val telemetryKey: String
) {

    companion object {
        private const val PREFS_NAME = "boxcast_health"
        private const val KEY_DEVICE_ID = "anonymous_device_id"
        private const val KEY_LAST_HEARTBEAT_DATE = "last_heartbeat_date"
        private const val KEY_IS_FIRST_LAUNCH = "is_first_launch_done"
        private const val KEY_SESSION_REPORTED = "session_reported_for_date"
        private const val ENGAGEMENT_CAP_MS = 30 * 60 * 1000L // 30 min cap per flush
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    
    // OkHttpClient with aggressive timeouts so we don't hold up the background process
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val deviceId: String
    private val appVersion: String
    private var foregroundStartMs: Long = 0L
    private var isPlaybackTracking = false
    private var playbackStartMs: Long = 0L
    private val cohortId: String

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
        
        // Define cohort based on the ISO week of the first launch
        var cohort = prefs.getString("user_cohort_id", null)
        if (cohort == null) {
            val cal = Calendar.getInstance()
            cohort = "${cal.get(Calendar.YEAR)}-W${cal.get(Calendar.WEEK_OF_YEAR)}"
            prefs.edit().putString("user_cohort_id", cohort).apply()
        }
        cohortId = cohort
    }

    private fun todayStr(): String = dateFormat.format(Date())

    /**
     * Call from Activity.onResume() or the initial LaunchedEffect.
     * Handles: daily heartbeat, session counting, engagement start.
     */
    fun onAppForeground() {
        val today = todayStr()
        foregroundStartMs = System.currentTimeMillis()

        // Daily heartbeat
        val lastHeartbeat = prefs.getString(KEY_LAST_HEARTBEAT_DATE, "")
        if (lastHeartbeat != today) {
            SessionAggregator.incrementAggregate("daily_heartbeat", 1)
            prefs.edit().putString(KEY_LAST_HEARTBEAT_DATE, today).apply()
        }

        // New user detection (first-ever launch)
        val isFirstLaunchDone = prefs.getBoolean(KEY_IS_FIRST_LAUNCH, false)
        if (!isFirstLaunchDone) {
            SessionAggregator.incrementAggregate("new_install", 1)
            prefs.edit().putBoolean(KEY_IS_FIRST_LAUNCH, true).apply()
        }

        // Session counting (once per cold start per day)
        val sessionKey = prefs.getString(KEY_SESSION_REPORTED, "")
        val sessionTag = "$today-${android.os.Process.myPid()}"
        if (sessionKey != sessionTag) {
            SessionAggregator.incrementAggregate("total_sessions", 1)
            prefs.edit().putString(KEY_SESSION_REPORTED, sessionTag).apply()
        }
    }

    /**
     * Call from Activity.onPause() or lifecycle observer.
     * Flushes accumulated foreground engagement time, then dispatches the batch payload.
     */
    fun onAppBackground() {
        if (foregroundStartMs > 0) {
            val elapsed = System.currentTimeMillis() - foregroundStartMs
            val capped = minOf(elapsed, ENGAGEMENT_CAP_MS)
            if (capped > 1000) { // Only report if > 1 second
                SessionAggregator.incrementAggregate("total_engagement_sec", (capped / 1000).toInt())
            }
            foregroundStartMs = 0L
        }
        
        flushBatchToServer()
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
     * Tallies accumulated playback time, then flushes.
     */
    fun onPlaybackStopped(flushNow: Boolean = true) {
        if (isPlaybackTracking && playbackStartMs > 0) {
            val elapsed = System.currentTimeMillis() - playbackStartMs
            if (elapsed > 1000) {
                SessionAggregator.incrementAggregate("total_playback_sec", (elapsed / 1000).toInt())
            }
            playbackStartMs = 0L
            isPlaybackTracking = false
        }
        
        if (flushNow) {
            flushBatchToServer()
        }
    }

    /**
     * Log a feature announcement interaction.
     */
    fun logFeatureAnnouncementSeen(announcementId: String) {
        val safeId = announcementId.replace(".", "_")
        SessionAggregator.incrementAggregate("feature_seen_$safeId", 1)
    }

    /**
     * Compiles the SessionAggregator tallies and fires a fire-and-forget HTTP request.
     */
    fun flushBatchToServer() {
        // Run in background coroutine
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val payloadMap = SessionAggregator.flushToPayload()
                if (payloadMap.isEmpty()) return@launch // Nothing to flush
                
                val fullPayload = JSONObject(payloadMap).apply {
                    put("device_id", deviceId)
                    put("cohort_id", cohortId)
                    put("app_version", appVersion)
                    put("heartbeat", true) // Ensures DAU is tracked
                }

                val endpointUrl = if (telemetryUrl.endsWith("/")) "${telemetryUrl}ingest" else "$telemetryUrl/ingest"

                val request = Request.Builder()
                    .url(endpointUrl)
                    .addHeader("Authorization", "Bearer $telemetryKey")
                    .post(fullPayload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                // Fire and forget
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e("Telemetry", "Failed to flush: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.e("Telemetry", "Network error flushing telemetry", e)
            }
        }
    }
}
