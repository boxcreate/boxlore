package cx.aswin.boxlore.feature.briefing

internal fun briefingStoryParagraphs(script: String): List<String> {
    val raw = script.split("\n\n").filter { it.isNotBlank() }
    return raw.mapIndexed { index, paragraph ->
        cleanBriefingParagraph(
            paragraph = paragraph,
            isFirst = index == 0,
            isLast = index == raw.lastIndex,
        )
    }.filter { it.isNotBlank() }
}

private fun cleanBriefingParagraph(
    paragraph: String,
    isFirst: Boolean,
    isLast: Boolean,
): String {
    var text = paragraph.trim()
    if (isFirst) {
        text = removeBriefingGreeting(text)
    }
    if (isLast) {
        text = removeBriefingOutro(text)
    }
    return text
}

private fun removeBriefingGreeting(text: String): String {
    val hasGreeting = briefingGreetingPrefixes().any { text.startsWith(it, ignoreCase = true) }
    val periodIndex = text.indexOf('.')
    return if (hasGreeting && periodIndex != -1 && periodIndex < 120) {
        text.substring(periodIndex + 1).trim()
    } else {
        text
    }
}

private fun removeBriefingOutro(text: String): String {
    val withoutClosing = removeClosingOutro(text)
    val lastBriefIndex = lastBriefReferenceIndex(withoutClosing)
    return trimTrailingBriefReference(withoutClosing, lastBriefIndex)
}

private fun removeClosingOutro(text: String): String {
    val outroIndex =
        briefingOutroSubstrings()
            .map { text.indexOf(it, ignoreCase = true) }
            .firstOrNull { it != -1 }
            ?: return text
    return text.substring(0, outroIndex).trim()
}

private fun lastBriefReferenceIndex(text: String): Int =
    text
        .lastIndexOf("boxlore brief", ignoreCase = true)
        .coerceAtLeast(text.lastIndexOf("boxcast brief", ignoreCase = true))

private fun trimTrailingBriefReference(
    text: String,
    lastBriefIndex: Int,
): String {
    if (lastBriefIndex == -1 || text.length - lastBriefIndex >= 100) return text
    val lastPeriod = text.lastIndexOf('.', text.length - 2)
    return if (lastPeriod != -1) text.substring(0, lastPeriod + 1).trim() else text
}

private fun briefingGreetingPrefixes(): List<String> =
    listOf(
        "This is the boxlore brief for",
        "This is the boxcast brief for",
        "Welcome to the boxlore brief for",
        "Welcome to the boxcast brief for",
        "Welcome to the daily brief for"
    )

private fun briefingOutroSubstrings(): List<String> =
    listOf(
        "That's your boxlore brief. See you tomorrow.",
        "That's your boxcast brief. See you tomorrow.",
        "That's your boxlore brief. See you tomorrow",
        "That's your boxcast brief. See you tomorrow",
        "See you tomorrow.",
        "See you tomorrow"
    )
