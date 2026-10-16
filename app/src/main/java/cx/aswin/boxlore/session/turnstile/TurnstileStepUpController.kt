package cx.aswin.boxlore.session.turnstile

import cx.aswin.boxlore.core.network.session.TurnstileResult
import cx.aswin.boxlore.core.network.session.TurnstileTokenSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** What [TurnstileStepUpHost] shows. */
internal sealed interface TurnstileStepUpState {
    data object Idle : TurnstileStepUpState

    /** A step-up is running; [attempt] keys its page. [interactive] once Turnstile needs the listener. */
    data class Running(val attempt: Int, val interactive: Boolean) : TurnstileStepUpState
}

/**
 * Runs one Turnstile step-up for the session manager. The page runs invisibly; if Turnstile asks
 * for interaction, the host shows it in a small sheet. A run that neither finishes nor asks within
 * [invisibleTimeoutMs] fails (this also covers the app being in the background, where no host is
 * on screen), and an interactive run gets [interactiveTimeoutMs]. Every failure lets onboarding
 * carry on with its non-AI path.
 */
internal class TurnstileStepUpController(
    private val invisibleTimeoutMs: Long = 20_000L,
    private val interactiveTimeoutMs: Long = 120_000L,
) : TurnstileTokenSource {
    private val mutableState = MutableStateFlow<TurnstileStepUpState>(TurnstileStepUpState.Idle)
    val state: StateFlow<TurnstileStepUpState> = mutableState.asStateFlow()

    private val runs = Mutex()
    private val lock = Any()
    private var attempt = 0
    private var result: CompletableDeferred<TurnstileResult>? = null
    private var asked: CompletableDeferred<Unit>? = null

    override suspend fun solve(): TurnstileResult = runs.withLock {
        val done = CompletableDeferred<TurnstileResult>()
        val interaction = CompletableDeferred<Unit>()
        val run = synchronized(lock) {
            result = done
            asked = interaction
            ++attempt
        }
        mutableState.value = TurnstileStepUpState.Running(run, interactive = false)
        try {
            val early = withTimeoutOrNull(invisibleTimeoutMs) {
                select<TurnstileResult?> {
                    done.onAwait { it }
                    interaction.onAwait { null }
                }
            }
            when {
                early != null -> early
                interaction.isCompleted -> withTimeoutOrNull(interactiveTimeoutMs) { done.await() } ?: TurnstileResult.Failed("interaction_timeout")
                else -> TurnstileResult.Failed("timeout")
            }
        } finally {
            synchronized(lock) {
                result = null
                asked = null
            }
            mutableState.value = TurnstileStepUpState.Idle
        }
    }

    /** A bridge message from the page of run [attempt]; messages from an older page are ignored. */
    fun onPageMessage(attempt: Int, message: String?) {
        when (val event = TurnstileBridge.parse(message)) {
            is TurnstileBridgeEvent.Token -> finish(attempt, TurnstileResult.Token(event.value))
            is TurnstileBridgeEvent.Error -> finish(attempt, TurnstileResult.Failed("turnstile_${event.code}"))
            TurnstileBridgeEvent.Expired -> finish(attempt, TurnstileResult.Failed("expired"))
            TurnstileBridgeEvent.ChallengeTimeout -> finish(attempt, TurnstileResult.Failed("challenge_timeout"))
            TurnstileBridgeEvent.Unsupported -> finish(attempt, TurnstileResult.Failed("unsupported"))
            TurnstileBridgeEvent.Interactive -> markInteractive(attempt)
            TurnstileBridgeEvent.InteractionDone,
            TurnstileBridgeEvent.Ready,
            TurnstileBridgeEvent.Unknown,
            -> Unit
        }
    }

    /** The page could not load or the WebView can't host the bridge. */
    fun onPageFailed(attempt: Int, reason: String) = finish(attempt, TurnstileResult.Failed(reason))

    /** The listener skipped the check (Skip, Back, swipe or a tap outside). */
    fun cancel(attempt: Int) = finish(attempt, TurnstileResult.Failed("cancelled"))

    private fun finish(run: Int, value: TurnstileResult) {
        synchronized(lock) { if (run == attempt) result?.complete(value) }
    }

    private fun markInteractive(run: Int) {
        val current = synchronized(lock) { (run == attempt).also { if (it) asked?.complete(Unit) } }
        if (!current) return
        mutableState.update { state ->
            if (state is TurnstileStepUpState.Running && state.attempt == run) state.copy(interactive = true) else state
        }
    }
}
