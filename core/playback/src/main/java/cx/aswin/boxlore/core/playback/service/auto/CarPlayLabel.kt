package cx.aswin.boxlore.core.playback.service.auto

import cx.aswin.boxlore.core.analytics.PendingEntryPoint

/**
 * Names a play `android_auto_*` only when Android Auto asked for it. The app's own plays reach the
 * session callback too, and labelling them replaced the entry point the app had set, so phone plays
 * were counted as car plays.
 */
internal fun labelCarPlay(fromCar: Boolean, entryPoint: String) {
    if (fromCar) PendingEntryPoint.set(mapOf("entry_point" to entryPoint))
}
