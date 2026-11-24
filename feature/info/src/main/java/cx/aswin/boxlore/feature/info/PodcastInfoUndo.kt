package cx.aswin.boxlore.feature.info

import android.content.Context
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.feature.info.components.findActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What an Undo on the show page puts back. */
internal enum class UndoKind { HideShow, Archive, Unarchive }

/**
 * An Undo the show page has offered and that hasn't run out yet: what it undoes, the episodes
 * ([ids]) for archiving, and when the offer ends ([expiresAtMs], wall clock). Saved through a
 * recreation (a fold or resize that recreates the page), so the page offers it again for the time
 * it had left; never when the listener leaves the page and comes back.
 */
internal data class PendingUndo(val kind: UndoKind, val ids: List<String>, val expiresAtMs: Long) {
    companion object {
        /** How long each offer lasts: the snackbar durations the page shows them with. */
        fun durationMs(kind: UndoKind): Long = if (kind == UndoKind.HideShow) LONG_SNACKBAR_MS else SHORT_SNACKBAR_MS

        val Saver: Saver<PendingUndo?, Any> = Saver(
            save = { undo -> undo?.let { listOf(it.kind.name, it.expiresAtMs, ArrayList(it.ids)) } ?: false },
            restore = { saved ->
                (saved as? List<*>)?.let { parts ->
                    PendingUndo(
                        kind = UndoKind.valueOf(parts[0] as String),
                        ids = (parts[2] as List<*>).filterIsInstance<String>(),
                        expiresAtMs = parts[1] as Long,
                    )
                }
            },
        )

        private const val SHORT_SNACKBAR_MS = 4_000L
        private const val LONG_SNACKBAR_MS = 10_000L
    }

    /** Time left at [nowMs], or null once the offer has ended. */
    fun remainingAt(nowMs: Long): Long? = (expiresAtMs - nowMs).takeIf { it > 0 }
}

/** The clock pending Undos are timed on. Tests replace it to step past an offer's end. */
internal var undoClockMs: () -> Long = System::currentTimeMillis

/**
 * Whether the page's state is being saved for a recreation of its activity (a configuration
 * change), rather than because the listener left the page. Tests that emulate a recreation in
 * place replace it.
 */
internal var savingForRecreation: (Context) -> Boolean = { context -> context.findActivity()?.isChangingConfigurations == true }

/**
 * The page's saved pending Undo for [podcastId]. It is kept only when the page is saved for a
 * recreation: leaving the page (opening an episode, then Back) drops it, so Undo isn't offered
 * again on return.
 */
@Composable
internal fun rememberPendingUndo(podcastId: String): MutableState<PendingUndo?> {
    val context = LocalContext.current
    val saver = remember(context) {
        Saver<PendingUndo?, Any>(
            // Nothing saved when the page isn't being recreated, so a restore starts with no Undo.
            save = { undo -> if (savingForRecreation(context)) with(PendingUndo.Saver) { save(undo) } else null },
            restore = { saved -> PendingUndo.Saver.restore(saved) },
        )
    }
    return rememberSaveable(podcastId, stateSaver = saver) { mutableStateOf(null) }
}

/**
 * Shows [message] with Undo and records the offer in [pending] until it is used or runs out. Undo
 * calls [onUndo] once; the action's own analytics went out with the action, never here.
 */
internal fun CoroutineScope.offerUndo(
    snackbarHostState: SnackbarHostState,
    pending: MutableState<PendingUndo?>,
    undo: PendingUndo,
    message: String,
    actionLabel: String,
    /** The first offer keeps the page's usual snackbar length; a restored one shows for the time left. */
    duration: SnackbarDuration? = null,
    onUndo: suspend () -> Unit,
) {
    pending.value = undo
    launch {
        snackbarHostState.currentSnackbarData?.dismiss()
        val result = if (duration != null) {
            snackbarHostState.showSnackbar(message, actionLabel, duration = duration)
        } else {
            showForRemaining(snackbarHostState, message, actionLabel, undo.remainingAt(undoClockMs()) ?: return@launch)
        }
        if (pending.value === undo) pending.value = null
        if (result == SnackbarResult.ActionPerformed) onUndo()
    }
}

/** Shows the snackbar for [remainingMs], then takes it down if nothing was chosen. */
private suspend fun showForRemaining(host: SnackbarHostState, message: String, actionLabel: String, remainingMs: Long): SnackbarResult = coroutineScope {
    val timeout = launch {
        delay(remainingMs)
        if (host.currentSnackbarData?.visuals?.message == message) host.currentSnackbarData?.dismiss()
    }
    host.showSnackbar(message, actionLabel, duration = SnackbarDuration.Indefinite).also { timeout.cancel() }
}

/**
 * After the page is recreated (a fold or unfold), offers a saved Undo again for the time it had
 * left, unless the snackbar host already shows something. An expired offer is dropped. An archive
 * Undo looks its episodes up with [findEpisodes] when it runs, so episodes chosen with Select all
 * or in search, which the loaded list may not hold, come back too.
 */
@Composable
internal fun RestorePendingUndo(
    pending: MutableState<PendingUndo?>,
    snackbarHostState: SnackbarHostState,
    viewModel: PodcastInfoViewModel,
    findEpisodes: suspend (ids: List<String>) -> List<Episode>,
) {
    val resources = LocalContext.current.resources
    val find by rememberUpdatedState(findEpisodes)
    LaunchedEffect(Unit) {
        val undo = pending.value ?: return@LaunchedEffect
        if (snackbarHostState.currentSnackbarData != null) return@LaunchedEffect
        if (undo.remainingAt(undoClockMs()) == null) {
            pending.value = null
            return@LaunchedEffect
        }
        val (message, action) = when (undo.kind) {
            UndoKind.HideShow -> resources.getString(R.string.podcast_info_hidden_snackbar) to resources.getString(R.string.podcast_info_hidden_undo)
            UndoKind.Archive, UndoKind.Unarchive -> resources.getQuantityString(
                if (undo.kind == UndoKind.Archive) R.plurals.episode_archive_done else R.plurals.episode_unarchive_done,
                undo.ids.size,
                undo.ids.size,
            ) to resources.getString(R.string.episode_archive_undo)
        }
        offerUndo(snackbarHostState, pending, undo, message, action) {
            when (undo.kind) {
                UndoKind.HideShow -> viewModel.undoHideShow()
                UndoKind.Archive, UndoKind.Unarchive -> {
                    val changed = find(undo.ids)
                    if (changed.isNotEmpty()) viewModel.setEpisodesArchived(changed, archive = undo.kind == UndoKind.Unarchive, undo = true)
                }
            }
        }
    }
}
