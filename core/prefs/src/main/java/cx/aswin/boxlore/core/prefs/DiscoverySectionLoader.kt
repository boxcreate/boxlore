package cx.aswin.boxlore.core.prefs

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Loads a switchable discovery section: [load] runs for each key while the section is on and
 * [clear] runs while it is off, so a section that is off never makes its request. Turning the
 * section off, or a new key, cancels a load in flight.
 */
suspend fun <K> collectDiscoverySection(
    keys: Flow<K>,
    on: Flow<Boolean>,
    clear: suspend () -> Unit,
    load: suspend (K) -> Unit,
) {
    combine(keys, on) { key, enabled -> key to enabled }
        .distinctUntilChanged()
        .collectLatest { (key, enabled) -> if (enabled) load(key) else clear() }
}
