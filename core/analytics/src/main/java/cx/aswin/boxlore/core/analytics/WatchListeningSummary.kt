package cx.aswin.boxlore.core.analytics

import android.content.Context
import java.time.LocalDate

/**
 * Adds up the watch's merges into one `watch_changes_merged` event a day. A playing watch sends its
 * place about once a minute and every send is a merge, so an event per merge would cost one listener
 * hundreds a day. A day's counts wait in [Store] and go out once a later day begins: with that day's
 * first merge, or through [sendFinished] when the app starts.
 *
 * Each day is sent at most once and never before it ends. A merge dated on or before a day already
 * counted (the phone's clock went back) joins the day still being counted, or the day after the last
 * one sent.
 */
object WatchListeningSummary {
    /** The merges of one local day. */
    data class Day(val epochDay: Long, val merges: Int, val episodes: Int, val follows: Int)

    /** The day still being counted and the last day sent: SharedPreferences in the app. */
    interface Store {
        fun load(): Day?

        fun save(day: Day?)

        fun lastSent(): Long?

        fun saveLastSent(epochDay: Long)
    }

    fun store(context: Context): Store = AnalyticsLocalState.watchListeningStore(context)

    fun today(): Long = LocalDate.now().toEpochDay()

    /** Counts one merge, sending an earlier day that is still waiting first. */
    @Synchronized
    fun add(store: Store, episodes: Int, follows: Int, today: Long = today(), send: (Day) -> Unit = ::emit,) {
        val waiting = store.load()
        val sentThrough = store.lastSent()
        val day = maxOf(today, waiting?.epochDay ?: today, sentThrough?.plus(1) ?: today)
        if (waiting != null && waiting.epochDay < day) finish(store, waiting, send)
        val counted = waiting?.takeIf { it.epochDay == day } ?: Day(day, merges = 0, episodes = 0, follows = 0)
        store.save(counted.copy(merges = counted.merges + 1, episodes = counted.episodes + episodes, follows = counted.follows + follows))
    }

    /** Sends a day that has ended; today's counts, or a day the clock has not reached, keep waiting. */
    @Synchronized
    fun sendFinished(store: Store, today: Long = today(), send: (Day) -> Unit = ::emit,) {
        val waiting = store.load() ?: return
        if (waiting.epochDay >= today) return
        finish(store, waiting, send)
    }

    private fun finish(store: Store, day: Day, send: (Day) -> Unit) {
        store.save(null)
        store.saveLastSent(day.epochDay)
        send(day)
    }

    private fun emit(day: Day) = WatchAnalyticsTracks.trackWatchChangesMerged(day)

    internal fun encode(day: Day): String = listOf(day.epochDay, day.merges, day.episodes, day.follows).joinToString(";")

    internal fun decode(encoded: String?): Day? {
        val parts = encoded?.split(";")?.mapNotNull { it.toLongOrNull() } ?: return null
        if (parts.size != 4) return null
        return Day(parts[0], merges = parts[1].toInt(), episodes = parts[2].toInt(), follows = parts[3].toInt())
    }
}
