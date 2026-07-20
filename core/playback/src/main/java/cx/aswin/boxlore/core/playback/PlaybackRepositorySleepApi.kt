package cx.aswin.boxlore.core.playback

/** Sleep timer / late-night nudge [PlaybackRepository] API. */
fun PlaybackRepository.setSleepTimer(
    durationMinutes: Int,
    dismissNudge: Boolean = true,
) = sleepController.setSleepTimer(durationMinutes, dismissNudge)

fun PlaybackRepository.isInNightWindow(): Boolean = sleepController.isInNightWindow()

fun PlaybackRepository.dismissLateNightNudge() = sleepController.dismissLateNightNudge()

fun PlaybackRepository.resetSleepNudgeForTesting() = sleepController.resetSleepNudgeForTesting()

fun PlaybackRepository.forceShowSleepPromptForTesting() = sleepController.forceShowSleepPromptForTesting()

fun PlaybackRepository.isDebugSkipSleepWindowEnabled(): Boolean = sleepController.isDebugSkipSleepWindowEnabled()

fun PlaybackRepository.setDebugSkipSleepWindow(enabled: Boolean) = sleepController.setDebugSkipSleepWindow(enabled)
