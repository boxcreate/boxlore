package cx.aswin.boxlore.backup

/** An in-memory folder; [reachError] and [writeError] make it fail the way a real provider would. */
internal class FakeAutoBackupFolder(vararg existing: String) : AutoBackupFolder {
    val files = linkedMapOf<String, ByteArray>().apply { existing.forEach { put(it, ByteArray(0)) } }
    val writes = mutableListOf<Pair<String, String>>()
    var reachError: Exception? = null
    var writeError: Exception? = null
    var listError: Exception? = null

    override fun checkReachable() {
        reachError?.let { throw it }
    }

    override fun listNames(): List<String> {
        listError?.let { throw it }
        return files.keys.toList()
    }

    override fun write(name: String, tempName: String, bytes: ByteArray) {
        writes += name to tempName
        writeError?.let { throw it }
        files[name] = bytes
    }

    override fun delete(name: String) {
        files.remove(name)
    }
}
