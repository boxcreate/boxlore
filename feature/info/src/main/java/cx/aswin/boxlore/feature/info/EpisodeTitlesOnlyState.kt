package cx.aswin.boxlore.feature.info

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * A show page's Episode titles only state. The app's [shared] copy is already running, so a new page
 * opens with the stored choice instead of drawing full rows until prefs answer; without one the page
 * reads [stored] itself and starts off.
 */
internal fun episodeTitlesOnlyState(
    shared: StateFlow<Boolean>?,
    stored: Flow<Boolean>,
    scope: CoroutineScope,
): StateFlow<Boolean> = shared ?: stored.stateIn(scope, SharingStarted.WhileSubscribed(5_000), initialValue = false)
