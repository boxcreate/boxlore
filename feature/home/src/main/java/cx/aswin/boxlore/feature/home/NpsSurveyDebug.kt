package cx.aswin.boxlore.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.prefs.NpsSurveyRule
import cx.aswin.boxlore.core.prefs.NpsSurveySchedule

/**
 * Debug: what the automatic NPS survey still waits for on this phone. [waitsFor] leaves out the finish
 * moment, which the next finished episode supplies; [finished] is the count of episodes finished here.
 */
internal fun npsSurveyDebugStatus(waitsFor: List<NpsSurveyRule>, finished: Int): String {
    if (waitsFor.isEmpty()) return "Ready: the survey appears after the next episode you finish, once nothing is playing."
    val reasons = waitsFor.map { rule ->
        when (rule) {
            NpsSurveyRule.FinishMoment -> "an episode to finish"
            NpsSurveyRule.FinishedEpisodes -> "${NpsSurveySchedule.MIN_FINISHED_EPISODES} episodes finished in boxlore ($finished so far)"
            NpsSurveyRule.DaysInstalled -> "${NpsSurveySchedule.MIN_DAYS_INSTALLED} days since install"
            NpsSurveyRule.FirstRun -> "a second start of boxlore"
            NpsSurveyRule.QuietDays -> "the quiet days after an install, import or sign-in"
            NpsSurveyRule.Cooldown -> "the 14 days after the last prompt"
            NpsSurveyRule.AskAgainGap -> "${NpsSurveySchedule.ASK_AGAIN_DAYS} days since it last appeared"
        }
    }
    return "Waiting for: " + reasons.joinToString("; ") + "."
}

/** The Flags tab's survey tools: its status and a reset of its timing (last showing, cooldown, this session's prompt). */
@Composable
internal fun NpsSurveyDebugSection(status: String?, onReset: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("NPS survey", style = MaterialTheme.typography.titleSmall)
        Text(
            text = status ?: "Checking…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
            Text("Reset NPS survey timing")
        }
        Text(
            text = "Long-press Feedback on Home to open the survey now.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
