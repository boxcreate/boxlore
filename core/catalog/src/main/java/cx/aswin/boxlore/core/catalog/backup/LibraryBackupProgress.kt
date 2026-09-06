package cx.aswin.boxlore.core.catalog.backup

enum class JsonBackupPhase {
    PREPARING,
    SUBSCRIBING,
    RESTORING_HISTORY,
    REFRESHING_FEEDS,
    COMPLETED,
}

data class JsonBackupProgress(
    val phase: JsonBackupPhase = JsonBackupPhase.PREPARING,
    val current: Int = 0,
    val total: Int = 0,
    val currentTitle: String = "",
) {
    val progressRatio: Float
        get() = if (total > 0) (current.toFloat() / total).coerceIn(0f, 1f) else 0f
}
