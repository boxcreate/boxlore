package cx.aswin.boxlore

internal enum class LegacyRssRepairLaunchDecision {
    Enabled,
    SettleWithoutRepair,
    Disabled,
}

internal object LegacyRssRepairLaunchGate {
    private val allowedVersions = setOf("0.0.18", "0.0.19")

    fun evaluate(
        versionName: String,
        firstInstallTime: Long,
        lastUpdateTime: Long,
    ): LegacyRssRepairLaunchDecision {
        if (versionName !in allowedVersions ||
            firstInstallTime <= 0L ||
            lastUpdateTime < firstInstallTime
        ) {
            return LegacyRssRepairLaunchDecision.Disabled
        }
        return if (lastUpdateTime == firstInstallTime) {
            LegacyRssRepairLaunchDecision.SettleWithoutRepair
        } else {
            LegacyRssRepairLaunchDecision.Enabled
        }
    }
}
