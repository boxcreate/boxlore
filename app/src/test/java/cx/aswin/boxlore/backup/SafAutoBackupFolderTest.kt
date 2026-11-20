package cx.aswin.boxlore.backup

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The real Storage Access Framework folder against a small in-memory documents provider. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SafAutoBackupFolderTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val tree: Uri = DocumentsContract.buildTreeDocumentUri(AUTHORITY, FakeDocumentsProvider.ROOT)
    private lateinit var provider: FakeDocumentsProvider
    private val name = "boxlore_auto_backup_2026-12-15_0905.json"
    private val bytes = "{\"version\":8}".toByteArray()

    @Before fun setUp() {
        provider = Robolectric.setupContentProvider(FakeDocumentsProvider::class.java, AUTHORITY)
        provider.dir = File(application.cacheDir, "fake-docs").apply {
            deleteRecursively()
            mkdirs()
        }
        application.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }

    private fun folder() = SafAutoBackupFolder(application, tree)

    @Test fun `writes through the temporary name and renames it when the provider can rename`() {
        provider.add(AutoBackupFiles.tempName(name)) // left by a stopped run
        folder().checkReachable()
        folder().write(name, AutoBackupFiles.tempName(name), bytes)
        assertEquals(listOf(name), folder().listNames())
        assertArrayEquals(bytes, provider.bytesOf(name))
        assertEquals(1, provider.renames)
    }

    @Test fun `the backup is created as JSON, so restore lists it after the rename`() {
        folder().write(name, AutoBackupFiles.tempName(name), bytes)
        assertEquals("application/json", provider.mimeOf(name))
    }

    @Test fun `writes the file directly when the provider can't rename`() {
        provider.supportsRename = false
        folder().write(name, AutoBackupFiles.tempName(name), bytes)
        assertEquals(listOf(name), folder().listNames())
        assertArrayEquals(bytes, provider.bytesOf(name))
        assertEquals(0, provider.renames)
    }

    @Test fun `a backup from the same minute is replaced only once the new one has its name`() {
        provider.add(name, "old".toByteArray())
        folder().write(name, AutoBackupFiles.tempName(name), bytes)
        assertEquals(listOf(name), folder().listNames())
        assertArrayEquals(bytes, provider.bytesOf(name))
    }

    @Test fun `a failed rename keeps the earlier backup from the same minute and removes the new file`() {
        provider.add(name, "old".toByteArray())
        provider.failRenameOf = AutoBackupFiles.tempName(name)
        val failed = runCatching { folder().write(name, AutoBackupFiles.tempName(name), bytes) }
        assertTrue(failed.isFailure)
        assertEquals(listOf(name), folder().listNames())
        assertArrayEquals("old".toByteArray(), provider.bytesOf(name))
    }

    @Test fun `without renaming, an existing backup is never overwritten`() {
        provider.supportsRename = false
        provider.add(name, "old".toByteArray())
        assertTrue(runCatching { folder().write(name, AutoBackupFiles.tempName(name), bytes) }.exceptionOrNull() is IOException)
        assertEquals(listOf(name), folder().listNames())
        assertArrayEquals("old".toByteArray(), provider.bytesOf(name))
    }

    @Test fun `without renaming, a new file that fails partway is removed`() {
        provider.supportsRename = false
        provider.failOpenOf = name
        assertTrue(runCatching { folder().write(name, AutoBackupFiles.tempName(name), bytes) }.isFailure)
        assertTrue(folder().listNames().isEmpty())
    }

    @Test fun `deletes only the named file`() {
        provider.add("keep-me.txt")
        provider.add(name)
        folder().delete(name)
        assertEquals(listOf("keep-me.txt"), folder().listNames())
    }

    @Test(expected = FileNotFoundException::class)
    fun `a folder the provider no longer finds reads as missing`() {
        provider.gone = true
        folder().checkReachable()
    }

    @Test(expected = SecurityException::class)
    fun `a folder without a lasting write grant reads as permission lost`() {
        application.contentResolver.releasePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        folder().checkReachable()
    }

    private companion object {
        const val AUTHORITY = "cx.aswin.boxlore.test.documents"
    }
}

/** Answers the Storage Access Framework calls [SafAutoBackupFolder] makes, keeping files in [dir]. */
class FakeDocumentsProvider : ContentProvider() {
    lateinit var dir: File
    var supportsRename = true
    var gone = false
    var renames = 0

    /** Renaming the document with this name fails, as a provider can. */
    var failRenameOf: String? = null

    /** Opening the document with this name fails, so a write stops partway. */
    var failOpenOf: String? = null
    private val names = linkedMapOf<String, String>()
    private var nextId = 0

    fun add(name: String, content: ByteArray = ByteArray(0)): String = "d${nextId++}".also { id ->
        names[id] = name
        File(dir, id).writeBytes(content)
    }

    private val mimes = mutableMapOf<String, String>()

    fun mimeOf(name: String): String? = mimes[names.entries.single { it.value == name }.key]

    fun bytesOf(name: String): ByteArray = File(dir, names.entries.single { it.value == name }.key).readBytes()

    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor? {
        if (gone) return null
        val columns = projection ?: arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_FLAGS)
        val cursor = MatrixCursor(columns)
        val id = DocumentsContract.getDocumentId(uri)
        val rows = if (uri.lastPathSegment == "children") names.keys.toList() else listOf(id).filter { it == ROOT || it in names }
        rows.forEach { row -> cursor.addRow(columns.map { column(row, it) }.toTypedArray()) }
        return cursor
    }

    private fun column(id: String, column: String): Any? = when (column) {
        Document.COLUMN_DOCUMENT_ID -> id
        Document.COLUMN_DISPLAY_NAME -> if (id == ROOT) "Sync" else names[id]
        Document.COLUMN_FLAGS -> if (supportsRename) Document.FLAG_SUPPORTS_RENAME or Document.FLAG_SUPPORTS_DELETE else Document.FLAG_SUPPORTS_DELETE
        else -> null
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        @Suppress("DEPRECATION")
        val target = extras?.getParcelable<Uri>(EXTRA_URI) ?: return null
        val id = DocumentsContract.getDocumentId(target)
        return when (method) {
            "android:createDocument" -> {
                val created = add(extras.getString(Document.COLUMN_DISPLAY_NAME).orEmpty())
                extras.getString(Document.COLUMN_MIME_TYPE)?.let { mimes[created] = it }
                uriBundle(target, created)
            }
            "android:renameDocument" -> {
                if (names[id] != null && names[id] == failRenameOf) error("rename refused")
                renames++
                names[id] = extras.getString(Document.COLUMN_DISPLAY_NAME).orEmpty()
                Bundle()
            }
            "android:deleteDocument" -> {
                names.remove(id)
                File(dir, id).delete()
                Bundle()
            }
            else -> null
        }
    }

    private fun uriBundle(parent: Uri, id: String) = Bundle().apply {
        val tree = DocumentsContract.buildTreeDocumentUri(parent.authority, DocumentsContract.getTreeDocumentId(parent))
        putParcelable(EXTRA_URI, DocumentsContract.buildDocumentUriUsingTree(tree, id))
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val id = DocumentsContract.getDocumentId(uri)
        if (names[id] != null && names[id] == failOpenOf) throw FileNotFoundException("open refused")
        return ParcelFileDescriptor.open(File(dir, id), ParcelFileDescriptor.parseMode(mode))
    }

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0

    companion object {
        const val ROOT = "root"

        /** DocumentsContract.EXTRA_URI, which the SDK hides. */
        private const val EXTRA_URI = "uri"
    }
}
