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

/**
 * Drops a car label left by a request that found nothing to play. A request that set none (an empty
 * or stale one) leaves whatever is pending, such as a phone play's own entry point.
 */
internal fun discardCarPlayLabel() {
    PendingEntryPoint.discardIf { (it["entry_point"] as? String)?.startsWith(CAR_ENTRY_PREFIX) == true }
}

private const val CAR_ENTRY_PREFIX = "android_auto_"
