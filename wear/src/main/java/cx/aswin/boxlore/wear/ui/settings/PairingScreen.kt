package cx.aswin.boxlore.wear.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import cx.aswin.boxlore.wear.R
import cx.aswin.boxlore.wear.cloud.SignInProblem
import cx.aswin.boxlore.wear.cloud.WatchAccountState
import cx.aswin.boxlore.wear.ui.components.EmptyMessage
import kotlinx.coroutines.delay

/** What the sign-in screen's line under the title and its button say. */
internal object PairingCopy {
    /** The line under the title: the last try's problem, else what the phone does. */
    fun body(problem: SignInProblem?): Int = problem?.let(AccountCopy::problem) ?: R.string.wear_pairing_body

    /**
     * The button: Signing in while the phone is asked, Try again after a try that didn't work, else
     * Sign in with phone (also after the account signed this watch out: nothing was tried yet).
     */
    fun action(state: WatchAccountState, problem: SignInProblem?): Int = when {
        state == WatchAccountState.SigningIn -> R.string.wear_account_signing_in
        problem != null && problem != SignInProblem.AccountSignedOut -> R.string.wear_try_again
        else -> R.string.wear_account_sign_in
    }

    /** With the phone out of reach the watch asks it again now and then, while this screen is in front. */
    fun retriesBySelf(state: WatchAccountState, problem: SignInProblem?): Boolean = state == WatchAccountState.SignedOut && problem == SignInProblem.PhoneAway

    /** How long the screen waits before asking an out-of-reach phone again. */
    const val RETRY_MS = 30_000L
}

/**
 * The watch's first screen until it is signed in, and where signing out leads. The phone lends its
 * sign-in by itself: the watch asks it as soon as the screen is in front ([onSignInBySelf], which
 * waits for a tap after a sign-out or a refusal), shows progress while it does, and offers the try
 * again ([onSignIn]). Episodes already downloaded still play, from [onDownloads].
 */
@Composable
internal fun PairingScreen(
    state: WatchAccountState,
    problem: SignInProblem?,
    hasDownloads: Boolean,
    onSignIn: () -> Unit,
    onSignInBySelf: () -> Unit,
    onDownloads: () -> Unit,
) {
    val signIn by rememberUpdatedState(onSignInBySelf)
    LifecycleStartEffect(Unit) {
        signIn()
        onStopOrDispose {}
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val retries = PairingCopy.retriesBySelf(state, problem)
    LaunchedEffect(retries) {
        if (retries) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                delay(PairingCopy.RETRY_MS)
                signIn()
            }
        }
    }
    val signingIn = state == WatchAccountState.SigningIn
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            EdgeButton(onClick = onSignIn, enabled = !signingIn) {
                if (!signingIn) Icon(Icons.Rounded.Refresh, contentDescription = null)
                Text(stringResource(PairingCopy.action(state, problem)), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
    ) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item(key = "message") {
                EmptyMessage(
                    icon = Icons.Rounded.PhoneAndroid,
                    title = stringResource(R.string.wear_pairing_title),
                    body = stringResource(PairingCopy.body(problem)),
                    modifier = Modifier.transformedHeight(this, spec),
                )
            }
            if (signingIn) {
                item(key = "progress") {
                    val description = stringResource(R.string.wear_account_signing_in)
                    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp).transformedHeight(this, spec), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp).semantics { contentDescription = description })
                    }
                }
            }
            if (hasDownloads) {
                item(key = "downloads") {
                    FilledTonalButton(
                        onClick = onDownloads,
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                        icon = { Icon(Icons.Rounded.DownloadDone, contentDescription = null) },
                        label = { Text(stringResource(R.string.wear_downloads), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        secondaryLabel = { Text(stringResource(R.string.wear_pairing_downloads_detail), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
        }
    }
}
