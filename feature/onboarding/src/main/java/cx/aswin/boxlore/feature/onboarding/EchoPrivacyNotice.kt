package cx.aswin.boxlore.feature.onboarding

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration

@Composable
internal fun EchoPrivacyNotice() {
    val policy = stringResource(R.string.echo_privacy_policy)
    val copy = stringResource(R.string.echo_anonymous_note, policy)
    val notice = buildAnnotatedString {
        append(copy)
        val start = copy.indexOf(policy)
        addLink(
            LinkAnnotation.Url(
                "https://aswin.cx/boxlore/privacy/",
                TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline)),
            ),
            start,
            start + policy.length,
        )
    }
    Text(
        notice,
        Modifier.fillMaxWidth().testTag("echo_privacy_notice"),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
