package cx.aswin.boxlore.core.catalog.backup

import kotlinx.coroutines.CancellationException

/** Restore helpers that rethrow coroutine cancellation and treat other failures as skippable. */
internal object LibraryBackupImportLogic {
    suspend fun <T> runRestore(block: suspend () -> T, onFailure: (Exception) -> Unit,): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        onFailure(e)
        null
    }

    suspend fun opmlImportCount(onFailure: (Exception) -> Unit = {}, block: suspend () -> Int,): Int = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        onFailure(e)
        -1
    }
}
