package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.model.FolderDisplaySize
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import kotlinx.coroutines.flow.first

/**
 * The look auto-organize gives the genre folders it makes, as saved on the Auto-organize page:
 * a fixed size ([fixedSize] null chooses by how many shows the genre has) and whether small tiles
 * show covers.
 */
data class AutoOrganizeFolderLook(val fixedSize: FolderDisplaySize?, val tileCovers: Boolean)

/** A saved `FolderDisplaySize` name, or null when none is saved or the name is unknown. */
fun savedFolderDisplaySize(name: String?): FolderDisplaySize? =
    name?.let { saved -> FolderDisplaySize.entries.firstOrNull { it.name == saved } }

suspend fun UserPreferencesRepository.autoOrganizeFolderLook(): AutoOrganizeFolderLook = AutoOrganizeFolderLook(
    fixedSize = savedFolderDisplaySize(autoOrganizeFolderSizeStream.first()),
    tileCovers = autoOrganizeTileCoversStream.first(),
)

/**
 * Auto-organize with the saved look, so genre folders made when a show is followed or its genre
 * changes look like the ones made on Subscriptions.
 */
suspend fun FolderRepository.autoOrganizeWithLook(look: AutoOrganizeFolderLook) {
    autoOrganizeSubscribedShows(defaultDisplaySize = look.fixedSize, showPodcastGrid = look.tileCovers)
}
