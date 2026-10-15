package cx.aswin.boxlore.feature.onboarding

import android.os.SystemClock
import androidx.annotation.ArrayRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.random.Random
import kotlinx.coroutines.delay

/**
 * What a slow request is waiting on. Each step has its own hard-coded lines (no AI), and its own
 * countdown: 15 seconds for Echo's next question, 30 for building suggestions.
 */
internal enum class EchoWaitStep(@ArrayRes val lines: Int, val countdownSeconds: Int) {
    NextQuestion(R.array.echo_wait_next_question, 15),
    Gathering(R.array.echo_wait_gathering, 30),
    Finding(R.array.echo_wait_finding, 30),
    Sorting(R.array.echo_wait_sorting, 30),
    ;

    val building: Boolean get() = this != NextQuestion

    companion object {
        /** The build keeps one countdown while its stages change; only the lines follow the stage. */
        fun forBuild(stage: AiLoadingStage): EchoWaitStep = when (stage) {
            AiLoadingStage.FETCHING_CATALOGS -> Finding
            AiLoadingStage.ASSEMBLING_FEED -> Sorting
            else -> Gathering
        }
    }
}

internal data class EchoWaitStatus(val secondsLeft: Int, val rotation: Int, val overdue: Boolean)

internal object EchoWaitTiming {
    const val ShowAfterMillis = 4_000L
    const val RotateMillis = 5_000L

    /** Nothing for the first four seconds; then a line that changes every five and a countdown. */
    fun status(elapsedMillis: Long, countdownSeconds: Int): EchoWaitStatus? {
        if (elapsedMillis < ShowAfterMillis) return null
        val since = elapsedMillis - ShowAfterMillis
        val left = countdownSeconds - (since / 1_000L).toInt()
        return EchoWaitStatus(left.coerceAtLeast(0), (since / RotateMillis).toInt(), overdue = left <= 0)
    }

    fun lineIndex(seed: Int, rotation: Int, count: Int): Int = if (count <= 0) 0 else Math.floorMod(seed + rotation, count)
}

/** Request time on the monotonic clock, observed while the screen is started; null [requestKey] means idle. */
@Composable
internal fun rememberEchoWaitElapsed(requestKey: Any?): State<Long> {
    val elapsed = remember(requestKey) { mutableLongStateOf(0L) }
    val started = remember(requestKey) { SystemClock.elapsedRealtime() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(requestKey, lifecycle) {
        if (requestKey == null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                elapsed.longValue = SystemClock.elapsedRealtime() - started
                delay(250)
            }
        }
    }
    return elapsed
}

/** The current line and countdown text for a slow [step], or null while the wait is still short. */
internal data class EchoWaitCopy(val line: String, val detail: String)

@Composable
internal fun rememberEchoWaitCopy(step: EchoWaitStep, requestKey: Any?): EchoWaitCopy? {
    val elapsed = rememberEchoWaitElapsed(requestKey)
    val seed = remember(requestKey) { Random.nextInt(0, 1_000) }
    val status = (if (requestKey == null) null else EchoWaitTiming.status(elapsed.value, step.countdownSeconds)) ?: return null
    val lines = stringArrayResource(step.lines)
    val detail = when {
        !status.overdue -> pluralStringResource(R.plurals.echo_wait_seconds_left, status.secondsLeft, status.secondsLeft)
        step.building -> stringResource(R.string.echo_wait_overdue_build)
        else -> stringResource(R.string.echo_wait_overdue_answer)
    }
    return EchoWaitCopy(lines[EchoWaitTiming.lineIndex(seed, status.rotation, lines.size)], detail)
}

/** Slow-build note for the suggestions screen's loading state (Echo-led flow only). */
@Composable
internal fun EchoBuildWaitNote(uiState: OnboardingUiState, modifier: Modifier = Modifier) {
    val key = if (uiState.isSynthesizing) "build:${uiState.aiHistory.size}" else null
    val copy = rememberEchoWaitCopy(EchoWaitStep.forBuild(uiState.aiLoadingStage), key) ?: return
    Column(modifier.testTag("echo_build_wait"), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AnimatedContent(targetState = copy.line, transitionSpec = EchoMotion.handoff(), label = "Build wait line") { line ->
            Text(
                line,
                Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
        Text(copy.detail, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
    }
}
