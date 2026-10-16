package cx.aswin.boxlore

/**
 * The release build drew its first frame so quickly that the splash flashed for a split second
 * and the launch felt abrupt. A cold launch now keeps the splash briefly, then fades it out; the
 * total stays well under a second so the app never feels slow to open.
 */
internal object SplashHoldPolicy {
    /** Minimum time the splash stays up after the Activity is created. */
    const val MIN_VISIBLE_MILLIS = 350L

    /** Fade-out once the hold ends. */
    const val EXIT_FADE_MILLIS = 300L

    /** Icon shrink during the fade, so the logo settles into the app instead of blinking out. */
    const val EXIT_ICON_SCALE = 0.92f

    fun keepOnScreen(createdAtMillis: Long, nowMillis: Long, coldStart: Boolean): Boolean =
        coldStart && nowMillis - createdAtMillis < MIN_VISIBLE_MILLIS
}
