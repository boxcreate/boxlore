package cx.aswin.boxlore.feature.briefing

import cx.aswin.boxlore.core.designsystem.R
import cx.aswin.boxlore.core.designsystem.briefing.briefingPlayerArtworkUri
import cx.aswin.boxlore.core.designsystem.briefing.briefingShowPodcast
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The show Home and the Brief screen hand to playback, resolved from real resources. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BriefingShowTest {
    @Test
    fun `brief plays as the boxlore brief from boxlore`() {
        val context = RuntimeEnvironment.getApplication()

        val show = briefingShowPodcast(context, "us")

        assertEquals("The boxlore brief", show.title)
        assertEquals(context.getString(R.string.briefing_brand_name), show.title)
        assertEquals("boxlore", show.artist)
        assertEquals("briefing_us", show.id)
        assertEquals(briefingPlayerArtworkUri(context.packageName, "us"), show.imageUrl)
    }
}
