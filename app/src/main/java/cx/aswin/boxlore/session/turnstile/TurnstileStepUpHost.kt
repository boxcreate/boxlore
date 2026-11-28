package cx.aswin.boxlore.session.turnstile

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeOut
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
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.aswin.boxlore.R
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.designsystem.components.BoxLoreLoader
import cx.aswin.boxlore.core.designsystem.components.BoxloreModalBottomSheet
import cx.aswin.boxlore.core.designsystem.echo.EchoBeat
import cx.aswin.boxlore.core.designsystem.echo.EchoCharacter
import cx.aswin.boxlore.core.designsystem.echo.EchoFace
import cx.aswin.boxlore.core.designsystem.echo.EchoGaze
import cx.aswin.boxlore.core.designsystem.echo.EchoIdle
import cx.aswin.boxlore.core.designsystem.layout.boxloreAvailableHeightDp
import cx.aswin.boxlore.core.designsystem.theme.ExpressiveMotion
import cx.aswin.boxlore.core.designsystem.theme.rememberGoogleSansFamily

/** Builds the page view for one run; [onMessage] and [onFailed] report back to the controller. */
internal fun interface TurnstilePageFactory {
    fun create(context: Context, pageUrl: String, onMessage: (String?) -> Unit, onFailed: (String) -> Unit): View

    /** Calls [onDrawn] once [page] has drawn what it shows now; by default on its next turn of the main thread. */
    fun whenDrawn(page: View, onDrawn: () -> Unit) {
        page.post(onDrawn)
    }
}

/**
 * Keeps one run's page alive while its presentation changes: invisible, then the phone sheet or
 * the centered dialog (resizes, folds and rotation can move it between them). The same view is
 * moved, so Turnstile's state survives.
 */
internal class TurnstilePageHolder(
    private val create: (Context) -> View,
    private val destroy: (View) -> Unit,
    private val whenDrawn: (View, () -> Unit) -> Unit = { page, onDrawn -> page.post(onDrawn) },
) {
    private var view: View? = null
    private var cardSize: IntSize? = null

    /** The latest draw asked for; an earlier one finishing says nothing about the page's current size. */
    private var drawRequest = 0

    /** False from the moment the page is moved into a card, or its card changes size, until it has drawn there. */
    var drawn by mutableStateOf(false)
        private set

    fun attach(context: Context): View {
        val page = view ?: create(context).also { view = it }
        (page.parent as? ViewGroup)?.removeView(page)
        return page
    }

    /** [attach], then watches for the page to draw in its new place (a card shows a loader until then). */
    fun attachToCard(context: Context): View {
        val page = attach(context)
        drawn = false
        cardSize = null
        // Asked once the page is in the card's window: WebView reports drawing only while attached.
        page.addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(attached: View) {
                    attached.removeOnAttachStateChangeListener(this)
                    if (view === attached) requestDraw(attached)
                }

                override fun onViewDetachedFromWindow(detached: View) = Unit
            },
        )
        return page
    }

    /**
     * The page's size in its card. A drawn page redraws at a new size, so the loader returns until it
     * has; the page itself is never reloaded.
     */
    fun cardResized(size: IntSize) {
        val previous = cardSize
        cardSize = size
        val page = view ?: return
        if (previous == null || previous == size) return
        // Not in the card's window yet: the attach listener asks for the first draw there.
        if (!page.isAttachedToWindow) return
        requestDraw(page)
    }

    /** Shows the loader until [page] has drawn as it is now; only the latest request may hide it. */
    private fun requestDraw(page: View) {
        val request = ++drawRequest
        drawn = false
        whenDrawn(page) { if (view === page && request == drawRequest) drawn = true }
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
 * The app-wide Turnstile host. Composed once in the app root, so a check can run on any screen: at
 * launch, on Home or in onboarding. While its activity is started it tells [controller] a check can
 * run; it shows nothing unless one is running, and the sheet appears only if Turnstile asks for
 * interaction.
 */
@Composable
internal fun TurnstileStepUpHost(
    controller: TurnstileStepUpController,
    apiBaseUrl: String,
    pageFactory: TurnstilePageFactory = TurnstileWebViewPage,
    destroyPage: (View) -> Unit = TurnstileWebViewPage::destroy,
) {
    LifecycleStartEffect(controller) {
        controller.hostStarted()
        onStopOrDispose { controller.hostStopped() }
    }
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
            whenDrawn = pageFactory::whenDrawn,
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
        TurnstileStepUpSheet(onSkip = { controller.cancel(attempt) }, loading = !holder.drawn) {
            AndroidView(
                factory = holder::attachToCard,
                modifier = Modifier.fillMaxWidth().height(72.dp).onSizeChanged(holder::cardResized).testTag(CHALLENGE_PAGE_TAG),
            )
        }
    }
}

/**
 * The shared adaptive sheet: a phone sheet, or a centered dialog on expanded and folded windows.
 * Echo stands on its stage beside its line and glances down at the check now and then; the check
 * (drawn by Cloudflare) sits in a card so it reads as part of the sheet.
 *
 * The phone sheet opens fully rather than half way, Echo shrinks when the space the check has (the
 * window, or one half of a half-open fold) is under [CHECK_SHORT_WINDOW_DP] tall (landscape phones,
 * short resizable windows, a tabletop half), Skip moves up beside Echo's line there, and the content
 * scrolls when it still doesn't fit, so the check and Skip are always reachable. While [loading], a small loader sits in the card behind
 * the not-yet-drawn check.
 *
 * Only Skip or a passed check closes it: a tap outside, a swipe down or Back does nothing, so the
 * check is never skipped by accident. It has no drag handle, since it can't be dragged.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TurnstileStepUpSheet(onSkip: () -> Unit, loading: Boolean = false, challenge: @Composable () -> Unit) {
    TrackScreenView("connection_check")
    val short = boxloreAvailableHeightDp() < CHECK_SHORT_WINDOW_DP
    BoxloreModalBottomSheet(
        onDismissRequest = {},
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { it != SheetValue.Hidden }),
        sheetGesturesEnabled = false,
        dragHandle = null,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false, shouldDismissOnClickOutside = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = if (short) 8.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (short) 8.dp else 16.dp),
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
                    // boxlore's type sets its weight in the font itself, so a heavier title needs its own family.
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = rememberGoogleSansFamily(weight = CHECK_TITLE_WEIGHT, opticalSize = 22f),
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                // Short spaces have no room for a row of its own under the check.
                if (short) CheckSkipButton(onSkip)
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
                Box(Modifier.padding(8.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.animation.AnimatedVisibility(visible = loading, enter = EnterTransition.None, exit = fadeOut(ExpressiveMotion.SleekFadeSpec)) {
                        BoxLoreLoader.CircularWavy(
                            size = 24.dp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag(CHALLENGE_LOADER_TAG),
                        )
                    }
                    challenge()
                }
            }
            if (!short) CheckSkipButton(onSkip, Modifier.align(Alignment.End))
        }
    }
}

@Composable
private fun CheckSkipButton(onSkip: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onSkip, modifier = modifier) {
        Text(stringResource(R.string.session_check_skip))
    }
}

/** Below this window height (dp) Echo and the gaps shrink so the check and Skip fit without scrolling. */
internal const val CHECK_SHORT_WINDOW_DP = 480f

/** The title's font weight: a step above body text, like Echo's lines elsewhere. */
private const val CHECK_TITLE_WEIGHT = 500

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
internal const val CHALLENGE_LOADER_TAG = "turnstile_challenge_loader"
