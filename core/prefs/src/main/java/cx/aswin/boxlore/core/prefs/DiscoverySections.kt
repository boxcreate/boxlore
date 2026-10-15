package cx.aswin.boxlore.core.prefs

/** A discovery section the listener can switch off. Hero, Your shows and charts always stay. */
enum class DiscoverySection {
    /** The Brief card on Home. Off is the same choice as the card's "hide forever". */
    DAILY_BRIEF,

    /** Home's Picked for you rows and Explore's For you tab. */
    PICKED_FOR_YOU,

    /** Home's Because you like section. */
    BECAUSE_YOU_LIKE,

    /** Home's greeting and its curated rows. */
    CURATED_ROWS,

    /** The More like this section on episode pages. */
    MORE_LIKE_THIS,
}

/**
 * Which optional discovery sections are on. Every section starts on; a section that is off is
 * neither shown nor requested from the server.
 */
data class DiscoverySections(
    val dailyBrief: Boolean = true,
    val pickedForYou: Boolean = true,
    val becauseYouLike: Boolean = true,
    val curatedRows: Boolean = true,
    val moreLikeThis: Boolean = true,
) {
    fun isOn(section: DiscoverySection): Boolean = when (section) {
        DiscoverySection.DAILY_BRIEF -> dailyBrief
        DiscoverySection.PICKED_FOR_YOU -> pickedForYou
        DiscoverySection.BECAUSE_YOU_LIKE -> becauseYouLike
        DiscoverySection.CURATED_ROWS -> curatedRows
        DiscoverySection.MORE_LIKE_THIS -> moreLikeThis
    }

    /** How many sections are off, out of every [DiscoverySection]. */
    val offCount: Int
        get() = DiscoverySection.entries.count { !isOn(it) }

    fun with(section: DiscoverySection, on: Boolean): DiscoverySections = when (section) {
        DiscoverySection.DAILY_BRIEF -> copy(dailyBrief = on)
        DiscoverySection.PICKED_FOR_YOU -> copy(pickedForYou = on)
        DiscoverySection.BECAUSE_YOU_LIKE -> copy(becauseYouLike = on)
        DiscoverySection.CURATED_ROWS -> copy(curatedRows = on)
        DiscoverySection.MORE_LIKE_THIS -> copy(moreLikeThis = on)
    }
}
