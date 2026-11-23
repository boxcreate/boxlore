package cx.aswin.boxlore.feature.onboarding

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.SentimentVerySatisfied
import androidx.compose.material.icons.rounded.Work
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource

/**
 * Echo's app-owned opening choices: eight one-word topics that fit one line on any phone. [answer]
 * is sent verbatim as the listener's first history entry and matches the label, so it never
 * implies a hidden preference; Echo asks for detail in later turns. [genre] is the Pick topics
 * genre key it matches.
 */
internal enum class EchoInitialTopic(val answer: String, @StringRes val labelRes: Int, val icon: ImageVector, val genre: String) {
    SCIENCE("Science", R.string.echo_topic_science, Icons.Rounded.Science, "Science"),
    TECH("Tech", R.string.echo_topic_tech, Icons.Rounded.Computer, "Technology"),
    HISTORY("History", R.string.echo_topic_history, Icons.Rounded.AccountBalance, "History"),
    TRUE_CRIME("True crime", R.string.echo_topic_true_crime, Icons.Rounded.Fingerprint, "True Crime"),
    COMEDY("Comedy", R.string.echo_topic_comedy, Icons.Rounded.SentimentVerySatisfied, "Comedy"),
    NEWS("News", R.string.echo_topic_news, Icons.Rounded.Newspaper, "News"),
    BUSINESS("Business", R.string.echo_topic_business, Icons.Rounded.Work, "Business"),
    HEALTH("Health", R.string.echo_topic_health, Icons.Rounded.SelfImprovement, "Health"),
    ;

    companion object {
        fun fromAnswer(answer: String): EchoInitialTopic? = entries.firstOrNull { it.answer == answer }

        /** The Pick topics genres for the topics picked with Echo, picked now or already sent. */
        fun genres(state: OnboardingUiState): Set<String> {
            val sent = state.aiHistory.firstOrNull { it.role == "user" }?.parts.orEmpty().flatMap { part -> part.text.split('.', ',') }
            return (state.aiSelectedOptions + sent).mapNotNull { fromAnswer(it.trim())?.genre }.toSet()
        }
    }
}

@Composable
internal fun echoAnswerLabel(answer: String, initial: Boolean): String {
    val topic = if (initial) EchoInitialTopic.fromAnswer(answer) else null
    return if (topic != null) stringResource(topic.labelRes) else answer.substringBefore('|').trim()
}
