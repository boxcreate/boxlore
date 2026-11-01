package cx.aswin.boxlore.feature.settings.feedback

import androidx.compose.runtime.saveable.Saver
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What Feedback shows: the conversation list, a new message, or one conversation. */
sealed interface FeedbackPane {
    data object List : FeedbackPane

    data object Compose : FeedbackPane

    data class Thread(val id: String) : FeedbackPane
}

internal val FeedbackPaneSaver = Saver<FeedbackPane, String>(
    save = { pane ->
        when (pane) {
            FeedbackPane.List -> "list"
            FeedbackPane.Compose -> "compose"
            is FeedbackPane.Thread -> "thread:${pane.id}"
        }
    },
    restore = { saved ->
        when {
            saved == "list" -> FeedbackPane.List
            saved.startsWith("thread:") -> FeedbackPane.Thread(saved.removePrefix("thread:"))
            else -> FeedbackPane.Compose
        }
    },
)

private val THREAD_ID = Regex("[0-9a-f]{24}")

/** A notification's conversation first; otherwise the list once there are conversations, else straight to writing. */
internal fun initialFeedbackPane(openThreadId: String?, hasConversations: Boolean): FeedbackPane = when {
    openThreadId != null && THREAD_ID.matches(openThreadId) -> FeedbackPane.Thread(openThreadId)
    hasConversations -> FeedbackPane.List
    else -> FeedbackPane.Compose
}

/**
 * Where Back goes from [pane]; null leaves Feedback. One pane at a time: a conversation goes back
 * to the list, and a new message too when there is a list to go back to. Side by side, Back leaves.
 */
internal fun feedbackBack(pane: FeedbackPane, hasConversations: Boolean, split: Boolean): FeedbackPane? = when {
    split -> null
    pane is FeedbackPane.Thread -> FeedbackPane.List
    pane == FeedbackPane.Compose && hasConversations -> FeedbackPane.List
    else -> null
}

/** List and conversation side by side: a landscape tablet with at least 840dp for Feedback. */
internal fun feedbackUsesSplit(paneWidthDp: Float, layout: BoxloreWindowLayout): Boolean {
    val width = if (paneWidthDp > 0f) paneWidthDp else layout.widthDp
    return layout.usesTabletLayout && width >= 840f && layout.widthDp > layout.heightDp
}

private fun clock(at: Instant, zone: ZoneId, locale: Locale, is24Hour: Boolean): String =
    DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a", locale).format(at.atZone(zone))

private fun day(date: LocalDate, today: LocalDate, locale: Locale): String =
    DateTimeFormatter.ofPattern(if (date.year == today.year) "d MMM" else "d MMM yyyy", locale).format(date)

/** The list's time: the clock today, "Yesterday", then the date. */
internal fun feedbackListTime(atMs: Long, nowMs: Long, zone: ZoneId, locale: Locale, is24Hour: Boolean): String {
    val at = Instant.ofEpochMilli(atMs)
    val date = at.atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    return when (date) {
        today -> clock(at, zone, locale, is24Hour)
        today.minusDays(1) -> "Yesterday"
        else -> day(date, today, locale)
    }
}

/** A message's time: "Today, 11:02", "Yesterday, 18:20" or "2 Nov, 09:41". */
internal fun feedbackMessageTime(atMs: Long, nowMs: Long, zone: ZoneId, locale: Locale, is24Hour: Boolean): String {
    val at = Instant.ofEpochMilli(atMs)
    val date = at.atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    val dayPart = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> day(date, today, locale)
    }
    return "$dayPart, ${clock(at, zone, locale, is24Hour)}"
}

/** A status line: the server's label, and the time when the server says it matters. */
internal fun feedbackStatusLine(label: String, showTime: Boolean, time: String): String = if (showTime && time.isNotBlank()) "$label · $time" else label
