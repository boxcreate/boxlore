package cx.aswin.boxlore.core.model.text

import android.content.res.Resources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class UiTextTest {
    private val resources: Resources = RuntimeEnvironment.getApplication().resources

    @Test
    fun `raw text resolves to itself`() {
        assertEquals("The Daily", UiText.raw("The Daily").resolve(resources))
    }

    @Test
    fun `string resources resolve with and without arguments`() {
        assertEquals(resources.getString(android.R.string.ok), UiText.res(android.R.string.ok).resolve(resources))
        val withArgs = PluralResources(resources)
        assertEquals("Played: 3 of 5", UiText.res(FORMAT_ID, 3, 5).resolve(withArgs))
    }

    @Test
    fun `plurals pick the form by count and resolve nested text first`() {
        val fake = PluralResources(resources)
        assertEquals("1 episode", UiText.plural(PLURAL_ID, 1, 1).resolve(fake))
        assertEquals("4 episodes", UiText.plural(PLURAL_ID, 4, 4).resolve(fake))
        assertEquals("Played: Ok of 2", UiText.res(FORMAT_ID, UiText.raw("Ok"), 2).resolve(fake))
    }

    @Test
    fun `equality compares the resource and arguments`() {
        assertEquals(UiText.res(FORMAT_ID, 1, 2), UiText.Res(FORMAT_ID, listOf(1, 2)))
        assertNotEquals(UiText.res(FORMAT_ID, 1, 2), UiText.res(FORMAT_ID, 2, 1))
        assertEquals(UiText.plural(PLURAL_ID, 3, 3), UiText.Plural(PLURAL_ID, 3, listOf(3)))
    }

    /** English-only stand-ins for app resources, which this module has none of. */
    @Suppress("DEPRECATION")
    private class PluralResources(base: Resources) : Resources(base.assets, base.displayMetrics, base.configuration) {
        override fun getString(id: Int, vararg formatArgs: Any?): String =
            if (id == FORMAT_ID) String.format(java.util.Locale.US, "Played: %1\$s of %2\$s", *formatArgs) else super.getString(id, *formatArgs)

        override fun getQuantityString(id: Int, quantity: Int, vararg formatArgs: Any?): String {
            check(id == PLURAL_ID)
            val pattern = if (quantity == 1) "%d episode" else "%d episodes"
            return String.format(java.util.Locale.US, pattern, *formatArgs)
        }
    }

    private companion object {
        const val FORMAT_ID = 0x7f000001
        const val PLURAL_ID = 0x7f000002
    }
}
