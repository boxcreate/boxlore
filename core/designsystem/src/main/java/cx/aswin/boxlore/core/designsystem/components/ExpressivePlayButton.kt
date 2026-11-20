package cx.aswin.boxlore.core.designsystem.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.R
import cx.aswin.boxlore.core.designsystem.text.asString
import cx.aswin.boxlore.core.designsystem.theme.ExpressivePressFeedback
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.contrastColor
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.text.UiText

data class ExpressivePlayButtonState(
    val isPlaying: Boolean,
    val isResume: Boolean,
    val isLoading: Boolean = false,
    val progress: Float = 0f,
    val timeText: String? = null,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExpressivePlayButton(onClick: () -> Unit, state: ExpressivePlayButtonState, accentColor: Color, modifier: Modifier = Modifier) {
    Surface(
        color = accentColor,
        contentColor = accentColor.contrastColor(),
        shape = CircleShape,
        modifier = modifier
            .widthIn(min = 160.dp)
            .expressiveClickable(feedback = ExpressivePressFeedback.Control, enabled = !state.isLoading, shape = CircleShape, onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            PlayButtonProgressStrip(
                isResume = state.isResume,
                progress = state.progress,
                accentColor = accentColor,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 14.dp)
            ) {
                if (state.isLoading) {
                    BoxLoreLoader.CircularWavy(
                        size = 24.dp,
                        color = accentColor.contrastColor(),
                    )
                } else {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = stringResource(if (state.isPlaying) R.string.play_button_pause else R.string.play_button_play),
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = getPlayButtonDisplayText(state).asString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = GoogleSansWeight.bold,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .basicMarquee(iterations = Int.MAX_VALUE)
                )
            }
        }
    }
}

@Composable
private fun PlayButtonProgressStrip(isResume: Boolean, progress: Float, accentColor: Color) {
    if (isResume && progress > 0f) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = accentColor.contrastColor(alpha = 0.5f),
                trackColor = Color.Transparent,
                drawStopIndicator = {}
            )
        }
    }
}

private fun getPlayButtonDisplayText(state: ExpressivePlayButtonState,): UiText = when {
    state.isLoading -> UiText.res(R.string.play_button_loading)
    state.isPlaying -> UiText.res(R.string.play_button_pause)
    state.isResume && state.timeText != null -> UiText.res(R.string.play_button_resume_at, state.timeText)
    state.isResume -> UiText.res(R.string.play_button_resume)
    else -> UiText.res(R.string.play_button_play)
}
