package cx.aswin.boxlore.feature.info.branded

import cx.aswin.boxlore.feature.info.branded.conspiracy.ConspiracyPage
import cx.aswin.boxlore.feature.info.branded.crimejunkie.CrimeJunkiePage
import cx.aswin.boxlore.feature.info.branded.daily.DailyPage
import cx.aswin.boxlore.feature.info.branded.jre.JrePage
import cx.aswin.boxlore.feature.info.branded.rottenmango.RottenMangoPage
import cx.aswin.boxlore.feature.info.branded.vergecast.VergecastPage
import cx.aswin.boxlore.feature.info.branded.waveform.WaveformPage

/** The shows with a branded page. */
internal object BrandedShowPages {
    val all: List<BrandedShowPage> = listOf(VergecastPage, ConspiracyPage, WaveformPage, CrimeJunkiePage, JrePage, RottenMangoPage, DailyPage)
}
