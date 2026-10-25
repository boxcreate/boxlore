package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.database.EpisodeAliasEntity

/** [CatalogIdentityStore] in memory, for refresh tests on the fake catalog DAO. */
internal class InMemoryCatalogIdentityStore : CatalogIdentityStore {
    val showNamespaces = mutableMapOf<String, Long>()
    val aliases = linkedMapOf<String, EpisodeAliasEntity>()
    val listenerRows = mutableMapOf<String, List<LocalCatalogListenerReference>>()

    override suspend fun showNamespace(podcastId: String): Long? = showNamespaces[podcastId]

    override suspend fun keepShowNamespace(podcastId: String, epNs: Long,) {
        showNamespaces.putIfAbsent(podcastId, epNs)
    }

    override suspend fun aliasesForShow(podcastId: String): List<EpisodeAliasEntity> = aliases.values.filter { it.podcastId == podcastId }

    override suspend fun aliases(ids: List<String>): List<EpisodeAliasEntity> = ids.mapNotNull(aliases::get)

    override suspend fun insertAliases(aliases: List<EpisodeAliasEntity>) {
        for (alias in aliases) this.aliases.putIfAbsent(alias.aliasId, alias)
    }

    override suspend fun updateAlias(alias: EpisodeAliasEntity) {
        aliases[alias.aliasId] = alias
    }

    override suspend fun listenerReferences(podcastId: String): List<LocalCatalogListenerReference> = listenerRows[podcastId].orEmpty()
}
