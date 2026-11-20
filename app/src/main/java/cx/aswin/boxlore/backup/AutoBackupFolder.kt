package cx.aswin.boxlore.backup

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.util.Log
import androidx.core.net.toUri
import java.io.FileNotFoundException
import java.io.IOException

/**
 * The folder the automatic backup writes to. Implementations throw [SecurityException] when boxlore may no
 * longer write there, [FileNotFoundException] when the folder is gone, and [IOException] for anything else.
 */
internal interface AutoBackupFolder {
    /** Fails as described above when the folder can't be written now. */
    fun checkReachable()

    fun listNames(): List<String>

    /** Saves [bytes] as [name], through [tempName] and a rename where the folder supports renaming. */
    fun write(name: String, tempName: String, bytes: ByteArray)

    fun delete(name: String)
}

/** A document tree the listener picked with the system folder picker (Storage Access Framework). */
internal class SafAutoBackupFolder(context: Context, private val treeUri: Uri) : AutoBackupFolder {
    private val context = context.applicationContext
    private val resolver: ContentResolver get() = context.contentResolver
    private val directory: Uri by lazy {
        DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
    }

    override fun checkReachable() {
        val granted = resolver.persistedUriPermissions.any { it.uri == treeUri && it.isWritePermission }
        if (!granted) throw SecurityException("No write grant for the backup folder")
        val found =
            try {
                resolver.query(directory, arrayOf(Document.COLUMN_DOCUMENT_ID), null, null, null)?.use { it.moveToFirst() } ?: false
            } catch (_: IllegalArgumentException) {
                false
            }
        if (!found) throw FileNotFoundException("The backup folder is gone")
    }

    override fun listNames(): List<String> = children().map { it.first }

    override fun write(name: String, tempName: String, bytes: ByteArray) {
        children().filter { it.first == tempName }.forEach { (_, uri) -> deleteDocument(uri) }
        val temp = DocumentsContract.createDocument(resolver, directory, BINARY_MIME, tempName)
            ?: throw IOException("Couldn't create the backup file")
        if (supportsRename(temp)) writeThroughRename(temp, name, bytes) else writeInPlace(temp, name, bytes)
    }

    /**
     * Writes [temp], then gives it [name]. A backup already called [name] (an earlier run in the same minute)
     * is moved aside first and only deleted once the new one has the name; if anything fails, the new file is
     * removed and the earlier backup gets its name back.
     */
    private fun writeThroughRename(temp: Uri, name: String, bytes: ByteArray) {
        var aside: Uri? = null
        try {
            writeBytes(temp, bytes)
            aside = existing(name)?.let { rename(it, AutoBackupFiles.asideName(name)) }
            rename(temp, name)
        } catch (error: Exception) {
            runCatching { deleteDocument(temp) }
            aside?.let { runCatching { rename(it, name) } }
            throw error
        }
        // A copy left behind is a leftover the next run removes.
        aside?.let { runCatching { deleteDocument(it) } }
    }

    /**
     * For folders that can't rename. A backup already called [name] is never overwritten: the run fails and
     * retries under a later minute's name. A new file that fails partway is removed, so a half-written backup
     * is never counted as one.
     */
    private fun writeInPlace(temp: Uri, name: String, bytes: ByteArray) {
        runCatching { deleteDocument(temp) }
        if (existing(name) != null) throw IOException("A backup with this name is already there")
        val target = DocumentsContract.createDocument(resolver, directory, JSON_MIME, name)
            ?: throw IOException("Couldn't create the backup file")
        try {
            writeBytes(target, bytes)
        } catch (error: Exception) {
            runCatching { deleteDocument(target) }
            throw error
        }
    }

    private fun rename(document: Uri, name: String): Uri =
        DocumentsContract.renameDocument(resolver, document, name) ?: throw IOException("Couldn't rename the backup file")

    override fun delete(name: String) {
        children().filter { it.first == name }.forEach { (_, uri) -> deleteDocument(uri) }
    }

    private fun children(): List<Pair<String, Uri>> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, DocumentsContract.getDocumentId(directory))
        val projection = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME)
        return resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0)
                    val name = cursor.getString(1)
                    if (id != null && name != null) add(name to DocumentsContract.buildDocumentUriUsingTree(treeUri, id))
                }
            }
        } ?: throw FileNotFoundException("The backup folder can't be listed")
    }

    private fun existing(name: String): Uri? = children().firstOrNull { it.first == name }?.second

    private fun supportsRename(document: Uri): Boolean =
        resolver.query(document, arrayOf(Document.COLUMN_FLAGS), null, null, null)?.use { cursor ->
            cursor.moveToFirst() && cursor.getInt(0) and Document.FLAG_SUPPORTS_RENAME != 0
        } ?: false

    private fun writeBytes(document: Uri, bytes: ByteArray) {
        val stream =
            try {
                resolver.openOutputStream(document, "wt")
            } catch (_: IllegalArgumentException) {
                resolver.openOutputStream(document, "w")
            }
        (stream ?: throw IOException("Couldn't open the backup file")).use { it.write(bytes) }
    }

    private fun deleteDocument(document: Uri) {
        if (!DocumentsContract.deleteDocument(resolver, document)) Log.w(TAG, "A backup file wasn't deleted")
    }

    private companion object {
        const val TAG = "AutoBackupFolder"
        const val BINARY_MIME = "application/octet-stream"
        const val JSON_MIME = "application/json"
    }
}

/** Taking, releasing and naming the folder grant the picker returns. */
internal object AutoBackupFolderGrant {
    private const val FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    /** Keeps access to [uri] across restarts; false when the provider refuses a lasting grant. */
    fun take(context: Context, uri: Uri): Boolean =
        try {
            context.contentResolver.takePersistableUriPermission(uri, FLAGS)
            true
        } catch (_: SecurityException) {
            false
        }

    fun release(context: Context, uri: String) {
        runCatching { context.contentResolver.releasePersistableUriPermission(uri.toUri(), FLAGS) }
    }

    /** The folder's name as the picker showed it, or null when the provider doesn't say. */
    fun displayName(context: Context, uri: Uri): String? = runCatching {
        val document = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
        context.contentResolver.query(document, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()?.takeIf { it.isNotBlank() }
}
