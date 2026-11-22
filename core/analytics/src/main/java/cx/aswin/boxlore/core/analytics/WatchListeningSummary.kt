package cx.aswin.boxlore.core.analytics

import android.content.Context
import java.time.LocalDate

/**
 * Adds up the watch's merges into one `watch_changes_merged` event a day. A playing watch sends its
 * place about once a minute and every send is a merge, so an event per merge would cost one listener
 * hundreds a day. A day's counts wait in [Store] and go out once a later day begins: with that day's
 * first merge, or through [sendFinished] when the app starts.
 */
object WatchListeningSummary {
    /** The merges of one local day. */
    data class Day(val epochDay: Long, val merges: Int, val episodes: Int, val follows: Int)

    /** The day still being counted: SharedPreferences in the app. */
    interface Store {
        fun load(): Day?

        fun save(day: Day?)
    }

    fun store(context: Context): Store = AnalyticsLocalState.watchListeningStore(context)

    fun today(): Long = LocalDate.now().toEpochDay()

    /** Counts one merge into [today], sending an earlier day that is still waiting first. */
    @Synchronized
    fun add(store: Store, episodes: Int, follows: Int, today: Long = today(), send: (Day) -> Unit = ::emit,) {
        val waiting = store.load()
        val counted = waiting?.takeIf { it.epochDay == today } ?: Day(today, merges = 0, episodes = 0, follows = 0)
        store.save(counted.copy(merges = counted.merges + 1, episodes = counted.episodes + episodes, follows = counted.follows + follows))
        if (waiting != null && waiting.epochDay != today) send(waiting)
    }

    /** Sends a day that has ended; today's counts keep waiting. */
    @Synchronized
    fun sendFinished(store: Store, today: Long = today(), send: (Day) -> Unit = ::emit,) {
        val waiting = store.load() ?: return
        if (waiting.epochDay == today) return
        store.save(null)
        send(waiting)
    }

    private fun emit(day: Day) = WatchAnalyticsTracks.trackWatchChangesMerged(day)

    internal fun encode(day: Day): String = listOf(day.epochDay, day.merges, day.episodes, day.follows).joinToString(";")

    internal fun decode(encoded: String?): Day? {
        val parts = encoded?.split(";")?.mapNotNull { it.toLongOrNull() } ?: return null
        if (parts.size != 4) return null
        return Day(parts[0], merges = parts[1].toInt(), episodes = parts[2].toInt(), follows = parts[3].toInt())
    }
}
