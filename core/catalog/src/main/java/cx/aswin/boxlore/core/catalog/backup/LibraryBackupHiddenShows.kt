package cx.aswin.boxlore.core.catalog.backup

import cx.aswin.boxlore.core.model.HiddenShow

/**
 * One hidden show in a file backup ([GlobalPreferencesBackup.hiddenShows]). Every field is nullable
 * because Gson fills a missing field with null; a row without an ID is skipped on restore.
 */
data class HiddenShowBackup(
    val id: String? = null,
    val title: String? = null,
    val imageUrl: String? = null,
    val aliasIds: List<String?>? = null,
)

internal fun List<HiddenShow>.toHiddenShowBackups(): List<HiddenShowBackup> = map { show ->
    HiddenShowBackup(id = show.id, title = show.title, imageUrl = show.imageUrl, aliasIds = show.aliasIds.toList())
}

/** The restorable rows; Gson can leave a row, a field or an alias null. */
internal fun List<HiddenShowBackup?>.toHiddenShows(): List<HiddenShow> = mapNotNull { row ->
    val id = row?.id?.trim()?.takeIf(String::isNotEmpty) ?: return@mapNotNull null
    HiddenShow(
        id = id,
        title = row.title.orEmpty(),
        imageUrl = row.imageUrl.orEmpty(),
        aliasIds = row.aliasIds.orEmpty().filterNotNull().map(String::trim).filter(String::isNotEmpty).toSet(),
    )
}
