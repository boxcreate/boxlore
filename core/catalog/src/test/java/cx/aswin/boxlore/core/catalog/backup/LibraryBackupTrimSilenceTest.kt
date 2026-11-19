package cx.aswin.boxlore.core.catalog.backup

import com.google.gson.Gson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** The app's trim silence setting travels in a library backup; an older backup leaves it as it is. */
class LibraryBackupTrimSilenceTest {
    private val gson = Gson()

    @Test
    fun `gson round trips trim silence`() {
        val parsed = gson.fromJson(gson.toJson(GlobalPreferencesBackup(trimSilenceEnabled = true)), GlobalPreferencesBackup::class.java)
        assertEquals(true, parsed.trimSilenceEnabled)
        val off = gson.fromJson(gson.toJson(GlobalPreferencesBackup(trimSilenceEnabled = false)), GlobalPreferencesBackup::class.java)
        assertEquals(false, off.trimSilenceEnabled)
    }

    @Test
    fun `an older backup has no trim silence setting to restore`() {
        val parsed = gson.fromJson("""{"skipBeginningMs":30000}""", GlobalPreferencesBackup::class.java)
        assertNull(parsed.trimSilenceEnabled)
    }
}
