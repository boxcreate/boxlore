package cx.aswin.boxlore.session.turnstile

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.aswin.boxlore.R
import cx.aswin.boxlore.core.designsystem.components.BoxloreModalBottomSheet
import cx.aswin.boxlore.core.designsystem.echo.EchoBeat
import cx.aswin.boxlore.core.designsystem.echo.EchoCharacter
import cx.aswin.boxlore.core.designsystem.echo.EchoFace
import cx.aswin.boxlore.core.designsystem.echo.EchoGaze
import cx.aswin.boxlore.core.designsystem.echo.EchoIdle
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout

/** Builds the page view for one run; [onMessage] and [onFailed] report back to the controller. */
internal fun interface TurnstilePageFactory {
    fun create(context: Context, pageUrl: String, onMessage: (String?) -> Unit, onFailed: (String) -> Unit): View
}

/**
 * Keeps one run's page alive while its presentation changes: invisible, then the phone sheet or
 * the centered dialog (resizes, folds and rotation can move it between them). The same view is
 * moved, so Turnstile's state survives.
 */
internal class TurnstilePageHolder(private val create: (Context) -> View, private val destroy: (View) -> Unit) {
    private var view: View? = null

    fun attach(context: Context): View {
        val page = view ?: create(context).also { view = it }
        (page.parent as? ViewGroup)?.removeView(page)
        return page
    }

    fun release() {
        view?.let { page ->
            (page.parent as? ViewGroup)?.removeView(page)
            destroy(page)
        }
        view = null
    }
}

/**
 * The app-wide Turnstile host. Composed once in the app root; it shows nothing unless a limited
 * session is stepping up, and the sheet appears only if Turnstile asks for interaction.
 */
@Composable
internal fun TurnstileStepUpHost(
    controller: TurnstileStepUpController,
    apiBaseUrl: String,
    pageFactory: TurnstilePageFactory = TurnstileWebViewPage,
    destroyPage: (View) -> Unit = TurnstileWebViewPage::destroy,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    val running = state as? TurnstileStepUpState.Running ?: return
    val attempt = running.attempt
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val currentDestroy by rememberUpdatedState(destroyPage)
    val holder = remember(attempt) {
        val pageUrl = TurnstileBridge.pageUrl(apiBaseUrl, dark)
        TurnstilePageHolder(
            create = { context ->
                pageFactory.create(
                    context,
                    pageUrl,
                    onMessage = { controller.onPageMessage(attempt, it) },
                    onFailed = { controller.onPageFailed(attempt, it) },
                )
            },
            destroy = { currentDestroy(it) },
        )
    }
    DisposableEffect(holder) { onDispose { holder.release() } }

    if (!running.interactive) {
        // A zero-size parent: the page is attached and laid out, so Turnstile runs normally, but
        // it is never drawn, never receives touches and is hidden from accessibility services.
        Box(Modifier.size(0.dp).clearAndSetSemantics {}) {
            AndroidView(factory = holder::attach, modifier = Modifier.requiredSize(300.dp, 65.dp).alpha(0f).testTag(INVISIBLE_PAGE_TAG))
        }
    } else {
        TurnstileStepUpSheet(onSkip = { controller.cancel(attempt) }) {
            AndroidView(factory = holder::attach, modifier = Modifier.fillMaxWidth().height(72.dp).testTag(CHALLENGE_PAGE_TAG))
        }
    }
}

/**
 * The shared adaptive sheet: a phone sheet, or a centered dialog on expanded and folded windows.
 * Echo stands on its stage beside its line and glances down at the check now and then; the check
 * (drawn by Cloudflare) sits in a card so it reads as part of the sheet.
 *
 * The phone sheet opens fully rather than half way, Echo shrinks on windows under
 * [CHECK_SHORT_WINDOW_DP] tall (landscape phones, short resizable windows), and the content scrolls
 * when it still doesn't fit, so the check and Skip are always reachable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TurnstileStepUpSheet(onSkip: () -> Unit, challenge: @Composable () -> Unit) {
    val short = LocalBoxloreWindowLayout.current.heightDp < CHECK_SHORT_WINDOW_DP
    BoxloreModalBottomSheet(onDismissRequest = onSkip, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (short) 12.dp else 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                EchoCharacter(
                    sequence = CHECK_ECHO_SEQUENCE,
                    modifier = Modifier.size(if (short) 56.dp else 96.dp).testTag(CHECK_ECHO_TAG),
                    idle = CheckEchoIdle,
                    stageColor = MaterialTheme.colorScheme.primaryContainer,
                )
                Text(
                    text = stringResource(R.string.session_check_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
            }
            Text(
                text = stringResource(R.string.session_check_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
                modifier = Modifier.fillMaxWidth().testTag(CHALLENGE_CARD_TAG),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Box(Modifier.padding(8.dp), contentAlignment = Alignment.Center) { challenge() }
            }
            TextButton(onClick = onSkip, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.session_check_skip))
            }
        }
    }
}

/** Below this window height (dp) Echo and the gaps shrink so the check and Skip fit without scrolling. */
internal const val CHECK_SHORT_WINDOW_DP = 480f

/** Echo's curious asking pose from onboarding, so the check feels like part of the same chat. */
private const val CHECK_ECHO_SEQUENCE = "onboarding-question"

/** Echo looks at the listener, then down at the check (below and to its right), and back. */
private val CheckEchoIdle = EchoIdle(
    intro = listOf(EchoBeat(EchoGaze(0.3f, 0.9f), 1400, EchoFace.Curious, blinks = 1)),
    loop = listOf(
        EchoBeat(EchoGaze(0f, 0f), 2600, blinks = 1),
        EchoBeat(EchoGaze(0.3f, 0.9f), 1400, EchoFace.Curious, blinks = 1),
        EchoBeat(EchoGaze(0f, 0f), 2200, EchoFace.Happy),
    ),
)

internal const val INVISIBLE_PAGE_TAG = "turnstile_invisible_page"
internal const val CHALLENGE_PAGE_TAG = "turnstile_challenge_page"
internal const val CHALLENGE_CARD_TAG = "turnstile_challenge_card"
internal const val CHECK_ECHO_TAG = "turnstile_check_echo"
