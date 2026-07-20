package cx.aswin.boxlore.core.playback

/** Chapters / transcript generation [PlaybackRepository] API. */
fun PlaybackRepository.generateAutoTranscript() = chaptersController.generateAutoTranscript()

fun PlaybackRepository.generateAutoChapters() = chaptersController.generateAutoChapters()
