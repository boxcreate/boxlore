package cx.aswin.boxlore.language

import android.annotation.SuppressLint
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
import cx.aswin.boxlore.core.prefs.AppLanguage
import cx.aswin.boxlore.core.prefs.AppLanguageMatcher
import cx.aswin.boxlore.core.prefs.BoxcastPrefs
import java.util.Locale

/**
 * The App language (Settings › Appearance › App language, and onboarding's first question).
 *
 * - Android 13+: the framework's per-app language ([LocaleManager.getApplicationLocales]), the same
 *   setting as the system's Settings › Apps › boxlore › Language. Android applies it to every part of
 *   the app and recreates the activity itself.
 * - Android 12/12L: the choice is stored in [BoxcastPrefs] and applied to the activity through
 *   [localeOverride] in `MainActivity.attachBaseContext`; [set] recreates the activity. The application
 *   context follows it from the next app start ([applicationBase]); text built in services
 *   (notifications, Android Auto) and widgets keeps following the phone's language there.
 *
 * Either way the choice is also stored, so onboarding knows the listener has answered.
 *
 * `NewApi` is suppressed because every Android 13 call goes through `perAppLocales`, which exists only
 * when [sdkInt] (the device's, or an older one in tests) is 33 or more; lint can't follow [sdkInt].
 */
@SuppressLint("NewApi")
class AppLanguageController(
    context: Context,
    private val prefs: BoxcastPrefs = BoxcastPrefs(context),
    private val sdkInt: Int = Build.VERSION.SDK_INT,
) {
    private val appContext = context.applicationContext

    /** The framework's per-app language on Android 13+; null on Android 12/12L. */
    private val perAppLocales: PerAppLocales?
        get() = if (sdkInt >= Build.VERSION_CODES.TIRAMISU) PerAppLocales(appContext) else null

    /** The language picked now: the per-app language on Android 13+, else the stored choice. */
    fun current(): AppLanguage {
        val manager = perAppLocales ?: return AppLanguage.fromTag(prefs.getAppLanguageTag())
        val locales = manager.applicationLocales
        return if (locales.isEmpty) AppLanguage.SYSTEM else AppLanguage.fromTag(locales[0].toLanguageTag())
    }

    /** Whether the listener has picked a language here or in the system's per-app language setting. */
    fun hasChosen(): Boolean = prefs.getAppLanguageTag() != null || perAppLocales?.applicationLocales?.isEmpty == false

    /**
     * The translation onboarding offers first, from the phone's languages and region
     * ([AppLanguageMatcher]); null once a language was picked or when nothing matches.
     */
    fun onboardingSuggestion(): AppLanguage? = if (hasChosen()) null else AppLanguageMatcher.suggest(systemLocales())

    /** The tag of the translation boxlore's screens show now, for Echo's server text; null for English. */
    fun shownTranslationTag(): String? = AppLanguageMatcher.shown(current(), systemLocales())?.tag

    /**
     * Picks [language]. Android 13+ recreates the activity when the language really changes;
     * on Android 12 [recreate] is called so [localeOverride] applies the new choice.
     */
    fun set(language: AppLanguage, recreate: () -> Unit) {
        val changed = current() != language
        prefs.setAppLanguageTag(language.tag)
        val manager = perAppLocales
        if (manager != null) {
            prefs.markAppLanguageWithFramework()
            if (changed) {
                manager.applicationLocales =
                    if (language == AppLanguage.SYSTEM) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(language.tag)
            }
        } else if (changed) {
            recreate()
        }
    }

    /**
     * Android 13+: hands a language picked on Android 12 (before an OS update, or restored from its
     * backup) to the per-app language once, so it isn't silently lost. Later changes made in the
     * system's own per-app setting are left alone. Called at app start.
     */
    fun adoptStoredChoice() {
        val manager = perAppLocales ?: return
        if (prefs.isAppLanguageWithFramework()) return
        prefs.markAppLanguageWithFramework()
        val tag = prefs.getAppLanguageTag()
        if (!tag.isNullOrEmpty() && manager.applicationLocales.isEmpty) {
            manager.applicationLocales = LocaleList.forLanguageTags(tag)
        }
    }

    /** The phone's own language list, whatever the app language is. */
    fun systemLocales(): List<Locale> {
        val list = perAppLocales?.systemLocales ?: Resources.getSystem().configuration.locales
        return (0 until list.size()).map { list[it] }
    }

    companion object {
        /**
         * The configuration change that shows the activity in the stored App language on Android
         * 12/12L, for `applyOverrideConfiguration` in `attachBaseContext`; it lasts through rotation and
         * resizing. Null on Android 13+ (the framework applies the language) or when the phone's
         * language is picked.
         */
        fun localeOverride(base: Context, sdkInt: Int = Build.VERSION.SDK_INT): Configuration? {
            if (sdkInt >= Build.VERSION_CODES.TIRAMISU) return null
            val tag = BoxcastPrefs(base).getAppLanguageTag()
            if (tag.isNullOrEmpty()) return null
            return Configuration().apply { setLocales(LocaleList.forLanguageTags(tag)) }
        }

        /**
         * The Application's base context on Android 12/12L, in the stored App language, so text
         * resolved through the application context (toasts, queue titles built in view models)
         * matches the screens. A new pick reaches it the next time the app starts.
         */
        fun applicationBase(base: Context, sdkInt: Int = Build.VERSION.SDK_INT): Context =
            localeOverride(base, sdkInt)?.let(base::createConfigurationContext) ?: base
    }
}

/** Android 13's [LocaleManager], kept apart so the Android 12 path never touches it. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class PerAppLocales(context: Context) {
    private val manager: LocaleManager = context.getSystemService(LocaleManager::class.java)

    var applicationLocales: LocaleList
        get() = manager.applicationLocales
        set(value) {
            manager.applicationLocales = value
        }

    val systemLocales: LocaleList get() = manager.systemLocales
}
