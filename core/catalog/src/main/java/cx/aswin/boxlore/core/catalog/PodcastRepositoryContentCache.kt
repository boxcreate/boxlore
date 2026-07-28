package cx.aswin.boxlore.core.catalog

import com.google.gson.Gson

internal const val CONTENT_CATALOG_JSON = "catalog_v3_json"
internal const val CONTENT_CATALOG_ETAG = "catalog_v3_etag"
internal const val CONTENT_CATALOG_FETCHED_AT = "catalog_v3_fetched_at"

internal fun PodcastRepository.readCachedContentCatalog(
    now: Long,
): cx.aswin.boxlore.core.catalog.content.ContentCatalogSnapshot? {
    val json = contentCatalogPreferences.getString(CONTENT_CATALOG_JSON, null) ?: return null
    val fetchedAt = contentCatalogPreferences.getLong(CONTENT_CATALOG_FETCHED_AT, 0L)
    return runCatching {
        Gson().fromJson(
            json,
            cx.aswin.boxlore.core.network.model.ContentCatalogResponse::class.java,
        ).toContentCatalogSnapshot(fetchedAt.takeIf { it > 0L } ?: now)
    }.getOrNull()
}
