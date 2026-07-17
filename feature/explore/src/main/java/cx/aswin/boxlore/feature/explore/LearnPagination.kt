package cx.aswin.boxlore.feature.explore

import cx.aswin.boxlore.core.network.model.CuratedCuriosityResponseDto
import cx.aswin.boxlore.core.network.model.DailyCuriosityDto

internal sealed interface InitialCuriosityDeckResult {
    data class Found(
        val response: CuratedCuriosityResponseDto,
        val page: Int,
        val unseenItems: List<DailyCuriosityDto>
    ) : InitialCuriosityDeckResult

    data class Exhausted(val lastPage: Int) : InitialCuriosityDeckResult

    data class Failed(val page: Int) : InitialCuriosityDeckResult
}

internal suspend fun findFirstUnseenCuriosityDeck(
    dismissedIds: Set<String>,
    startPage: Int = 1,
    maxAttempts: Int = 5,
    fetchPage: suspend (Int) -> CuratedCuriosityResponseDto?
): InitialCuriosityDeckResult {
    val firstPage = startPage.coerceAtLeast(1)
    val attempts = maxAttempts.coerceAtLeast(1)
    var page = firstPage

    repeat(attempts) {
        val response = fetchPage(page) ?: return InitialCuriosityDeckResult.Failed(page)
        if (response.questionsStack.isEmpty()) {
            return InitialCuriosityDeckResult.Exhausted(page)
        }

        val unseenItems = response.questionsStack.filterNot {
            it.episode.id.toString() in dismissedIds
        }
        if (unseenItems.isNotEmpty()) {
            return InitialCuriosityDeckResult.Found(response, page, unseenItems)
        }
        page++
    }

    return InitialCuriosityDeckResult.Exhausted(page - 1)
}
