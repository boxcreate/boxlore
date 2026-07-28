package cx.aswin.boxlore.core.catalog.content

import cx.aswin.boxlore.core.ranking.CandidateSource

class ServerGroupedSectionProvider(
    private val loader: suspend (ContentContext) -> GroupedContentSections?,
) : GroupedCandidateProvider {
    override val source: CandidateSource = CandidateSource.SERVER_RECOMMENDATION

    override suspend fun sections(context: ContentContext): GroupedContentSections? = loader(context)
}
