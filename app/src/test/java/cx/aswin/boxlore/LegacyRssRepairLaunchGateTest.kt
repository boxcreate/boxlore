package cx.aswin.boxlore

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LegacyRssRepairLaunchGateTest {
    @Test
    fun `upgraded installs enable repair only on versions 18 and 19`() {
        assertEquals(
            LegacyRssRepairLaunchDecision.Enabled,
            LegacyRssRepairLaunchGate.evaluate(
                versionName = "0.0.18",
                firstInstallTime = 100L,
                lastUpdateTime = 200L,
            ),
        )
        assertEquals(
            LegacyRssRepairLaunchDecision.Enabled,
            LegacyRssRepairLaunchGate.evaluate(
                versionName = "0.0.19",
                firstInstallTime = 100L,
                lastUpdateTime = 200L,
            ),
        )
        assertEquals(
            LegacyRssRepairLaunchDecision.Disabled,
            LegacyRssRepairLaunchGate.evaluate(
                versionName = "0.0.20",
                firstInstallTime = 100L,
                lastUpdateTime = 200L,
            ),
        )
    }

    @Test
    fun `fresh allowed install is permanently settled without repair`() {
        assertEquals(
            LegacyRssRepairLaunchDecision.SettleWithoutRepair,
            LegacyRssRepairLaunchGate.evaluate(
                versionName = "0.0.18",
                firstInstallTime = 100L,
                lastUpdateTime = 100L,
            ),
        )
        assertEquals(
            LegacyRssRepairLaunchDecision.SettleWithoutRepair,
            LegacyRssRepairLaunchGate.evaluate(
                versionName = "0.0.19",
                firstInstallTime = 100L,
                lastUpdateTime = 100L,
            ),
        )
    }

    @Test
    fun `invalid install metadata fails closed`() {
        assertEquals(
            LegacyRssRepairLaunchDecision.Disabled,
            LegacyRssRepairLaunchGate.evaluate(
                versionName = "0.0.18",
                firstInstallTime = 0L,
                lastUpdateTime = 200L,
            ),
        )
        assertEquals(
            LegacyRssRepairLaunchDecision.Disabled,
            LegacyRssRepairLaunchGate.evaluate(
                versionName = "0.0.18",
                firstInstallTime = 200L,
                lastUpdateTime = 100L,
            ),
        )
    }
}
