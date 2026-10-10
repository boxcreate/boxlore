package cx.aswin.boxlore.updates

import com.google.android.play.core.install.model.AppUpdateType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PlayUpdatePolicyTest {
    @Test fun `immediate-only offers remain actionable even at normal priority`() {
        assertEquals(AppUpdateType.IMMEDIATE, choosePlayUpdateType(false, true, false))
        assertEquals(AppUpdateType.FLEXIBLE, choosePlayUpdateType(false, true, true))
        assertEquals(AppUpdateType.IMMEDIATE, choosePlayUpdateType(true, true, true))
        assertNull(choosePlayUpdateType(false, false, false))
    }
}
