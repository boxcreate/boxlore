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
 * implies a hidden preference; Echo asks for detail in later turns.
 */
internal enum class EchoInitialTopic(val answer: String, @StringRes val labelRes: Int, val icon: ImageVector) {
    SCIENCE("Science", R.string.echo_topic_science, Icons.Rounded.Science),
    TECH("Tech", R.string.echo_topic_tech, Icons.Rounded.Computer),
    HISTORY("History", R.string.echo_topic_history, Icons.Rounded.AccountBalance),
    TRUE_CRIME("True crime", R.string.echo_topic_true_crime, Icons.Rounded.Fingerprint),
    COMEDY("Comedy", R.string.echo_topic_comedy, Icons.Rounded.SentimentVerySatisfied),
    NEWS("News", R.string.echo_topic_news, Icons.Rounded.Newspaper),
    BUSINESS("Business", R.string.echo_topic_business, Icons.Rounded.Work),
    HEALTH("Health", R.string.echo_topic_health, Icons.Rounded.SelfImprovement),
    ;

    companion object {
        fun fromAnswer(answer: String): EchoInitialTopic? = entries.firstOrNull { it.answer == answer }
    }
}

@Composable
internal fun echoAnswerLabel(answer: String, initial: Boolean): String {
    val topic = if (initial) EchoInitialTopic.fromAnswer(answer) else null
    return if (topic != null) stringResource(topic.labelRes) else answer.substringBefore('|').trim()
}
