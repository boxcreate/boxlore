package cx.aswin.boxlore.feature.info.branded

import cx.aswin.boxlore.feature.info.branded.conspiracy.ConspiracyPage
import cx.aswin.boxlore.feature.info.branded.vergecast.VergecastPage
import cx.aswin.boxlore.feature.info.branded.waveform.WaveformPage

/** The shows with a branded page. */
internal object BrandedShowPages {
    val all: List<BrandedShowPage> = listOf(VergecastPage, ConspiracyPage, WaveformPage)
}
