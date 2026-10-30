package cx.aswin.boxlore.core.database

/** Stored values of [ListeningSessionEntity.source] and [ListeningSessionEntity.output]. Never renamed. */
object ListeningSessionValues {
    /** Played from a Lore queue. Other sources are the analytics glossary's canonical entry points. */
    const val SOURCE_LORE = "lore"

    /** Played from the Smart Queue's automatic refill. */
    const val SOURCE_SMART_QUEUE = "smart_queue"

    const val OUTPUT_SPEAKER = "speaker"
    const val OUTPUT_HEADPHONES = "headphones"
    const val OUTPUT_BLUETOOTH = "bluetooth"
    const val OUTPUT_CAR = "car"
    const val OUTPUT_CAST = "cast"
}
