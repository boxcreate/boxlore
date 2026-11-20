package cx.aswin.boxlore.language

import android.app.Application
import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.prefs.AppLanguage
import cx.aswin.boxlore.core.prefs.BoxcastPrefs
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The App language on both paths: Android 13+ hands it to the framework's per-app language and lets
 * Android recreate; Android 12 stores it, recreates the activity and applies it as an override that
 * outlasts configuration changes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AppLanguageControllerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var recreated = 0

    @Before
    fun setUp() {
        context.getSharedPreferences(BoxcastPrefs.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    @Config(sdk = [33])
    fun `Android 13 uses the per-app language and leaves recreation to Android`() {
        val controller = AppLanguageController(context)
        val manager = context.getSystemService(LocaleManager::class.java)
        manager.applicationLocales = LocaleList.getEmptyLocaleList()
        assertEquals(AppLanguage.SYSTEM, controller.current())
        assertFalse(controller.hasChosen())

        controller.set(AppLanguage.GERMAN) { recreated++ }
        assertEquals("de", manager.applicationLocales.toLanguageTags())
        assertEquals(AppLanguage.GERMAN, controller.current())
        assertTrue(controller.hasChosen())
        assertNull(controller.onboardingSuggestion())

        controller.set(AppLanguage.SYSTEM) { recreated++ }
        assertTrue(manager.applicationLocales.isEmpty)
        assertEquals(AppLanguage.SYSTEM, controller.current())
        // Picking the phone's language still counts as an answer.
        assertTrue(controller.hasChosen())
        assertEquals(0, recreated)
        assertNull(AppLanguageController.localeOverride(context))
    }

    @Test
    @Config(sdk = [33])
    fun `a language set in the system's settings counts as picked`() {
        context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags("pt-BR")
        val controller = AppLanguageController(context)
        assertEquals(AppLanguage.PORTUGUESE_BRAZIL, controller.current())
        assertTrue(controller.hasChosen())
        context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.getEmptyLocaleList()
    }

    @Test
    @Config(sdk = [33])
    fun `a pick stored on Android 12 is handed to Android 13 once, and a later system reset is kept`() {
        val manager = context.getSystemService(LocaleManager::class.java)
        manager.applicationLocales = LocaleList.getEmptyLocaleList()
        BoxcastPrefs(context).setAppLanguageTag("fr")

        AppLanguageController(context).adoptStoredChoice()
        assertEquals("fr", manager.applicationLocales.toLanguageTags())
        assertEquals(AppLanguage.FRENCH, AppLanguageController(context).current())

        // The listener then resets it in the system's per-app language setting: it stays reset.
        manager.applicationLocales = LocaleList.getEmptyLocaleList()
        AppLanguageController(context).adoptStoredChoice()
        assertTrue(manager.applicationLocales.isEmpty)
    }

    @Test
    @Config(sdk = [31])
    fun `Android 12 gives the application context the stored language too`() {
        assertEquals(context, AppLanguageController.applicationBase(context))
        BoxcastPrefs(context).setAppLanguageTag("de")
        val wrapped = AppLanguageController.applicationBase(context)
        assertEquals("de", wrapped.resources.configuration.locales.get(0).language)
        assertEquals(context, AppLanguageController.applicationBase(context, sdkInt = 33))
    }

    @Test
    @Config(sdk = [33])
    fun `a pick made on Android 13 is never handed over again`() {
        val manager = context.getSystemService(LocaleManager::class.java)
        manager.applicationLocales = LocaleList.getEmptyLocaleList()
        val controller = AppLanguageController(context)
        controller.set(AppLanguage.SPANISH) { recreated++ }
        manager.applicationLocales = LocaleList.getEmptyLocaleList()
        controller.adoptStoredChoice()
        assertTrue(manager.applicationLocales.isEmpty)
    }

    @Test
    @Config(sdk = [31])
    fun `Android 12 stores the pick, recreates once per change and overrides the activity's language`() {
        val controller = AppLanguageController(context)
        assertEquals(AppLanguage.SYSTEM, controller.current())
        assertNull(AppLanguageController.localeOverride(context))

        controller.set(AppLanguage.FRENCH) { recreated++ }
        assertEquals(1, recreated)
        assertEquals("fr", BoxcastPrefs(context).getAppLanguageTag())
        assertEquals(AppLanguage.FRENCH, controller.current())
        controller.set(AppLanguage.FRENCH) { recreated++ }
        assertEquals(1, recreated)

        val override = AppLanguageController.localeOverride(context)!!
        val activityContext = ContextThemeWrapper(context, 0).apply { applyOverrideConfiguration(override) }
        assertEquals(Locale.FRENCH, activityContext.resources.configuration.locales.get(0))
        // Only the language is overridden; everything else still follows the window.
        assertEquals(context.resources.configuration.densityDpi, activityContext.resources.configuration.densityDpi)

        controller.set(AppLanguage.SYSTEM) { recreated++ }
        assertEquals(2, recreated)
        assertNull(AppLanguageController.localeOverride(context))
        assertTrue(controller.hasChosen())
    }

    @Test
    @Config(sdk = [31], qualifiers = "es-rMX")
    fun `onboarding is offered the phone's translation until a language is picked`() {
        val controller = AppLanguageController(context)
        assertEquals(AppLanguage.SPANISH, controller.onboardingSuggestion())
        controller.set(AppLanguage.ENGLISH) { recreated++ }
        assertNull(controller.onboardingSuggestion())
    }

    @Test
    @Config(sdk = [31], qualifiers = "en-rUS")
    fun `a phone with no translation is asked nothing`() {
        assertNull(AppLanguageController(context).onboardingSuggestion())
    }
}
