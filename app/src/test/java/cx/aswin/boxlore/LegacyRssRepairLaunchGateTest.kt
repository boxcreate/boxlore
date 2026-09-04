package cx.aswin.boxlore

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LegacyRssRepairLaunchGateTest {
    @Test
    fun `upgraded installs enable repair through version 30 except version 20`() {
        for (versionName in listOf(
            "0.0.18",
            "0.0.19",
            "0.0.21",
            "0.0.22",
            "0.0.23",
            "0.0.24",
            "0.0.25",
            "0.0.26",
            "0.0.27",
            "0.0.28",
            "0.0.29",
            "0.0.30",
        )) {
            assertEquals(
                LegacyRssRepairLaunchDecision.Enabled,
                LegacyRssRepairLaunchGate.evaluate(
                    versionName = versionName,
                    firstInstallTime = 100L,
                    lastUpdateTime = 200L,
                ),
            )
        }
        for (versionName in listOf("0.0.20", "0.0.31")) {
            assertEquals(
                LegacyRssRepairLaunchDecision.Disabled,
                LegacyRssRepairLaunchGate.evaluate(
                    versionName = versionName,
                    firstInstallTime = 100L,
                    lastUpdateTime = 200L,
                ),
            )
        }
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
        for (versionName in listOf(
            "0.0.19",
            "0.0.21",
            "0.0.22",
            "0.0.23",
            "0.0.24",
            "0.0.25",
            "0.0.26",
            "0.0.27",
            "0.0.28",
            "0.0.29",
            "0.0.30",
        )) {
            assertEquals(
                LegacyRssRepairLaunchDecision.SettleWithoutRepair,
                LegacyRssRepairLaunchGate.evaluate(
                    versionName = versionName,
                    firstInstallTime = 100L,
                    lastUpdateTime = 100L,
                ),
            )
        }
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
