package cx.aswin.boxlore.core.testing

/** Whether system animations are off for a capture, applied through [apply]. */
internal class MotionSetting(private val apply: (reduced: Boolean) -> Unit) {
    var reduced = false
        private set

    fun set(enabled: Boolean) {
        reduced = enabled
        apply(enabled)
    }

    /** Runs [block] with motion [reduced] or not, then puts back the setting it found. */
    fun <T> during(enabled: Boolean, block: () -> T): T {
        val previous = reduced
        if (enabled != previous) set(enabled)
        try {
            return block()
        } finally {
            if (enabled != previous) set(previous)
        }
    }
}
