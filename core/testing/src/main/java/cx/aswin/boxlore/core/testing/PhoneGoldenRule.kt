package cx.aswin.boxlore.core.testing

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.core.view.WindowCompat
import coil.Coil
import coil.ImageLoader
import coil.decode.DataSource
import coil.request.SuccessResult
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import cx.aswin.boxlore.core.designsystem.layout.BoxloreFold
import cx.aswin.boxlore.core.designsystem.layout.BoxloreFoldOrientation
import cx.aswin.boxlore.core.designsystem.layout.BoxloreFoldState
import cx.aswin.boxlore.core.designsystem.layout.BoxloreLayoutBounds
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxlorePaneWidth
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.robolectric.RuntimeEnvironment

private const val TABLET_CAPTURES_PROPERTY = "boxlore.tabletCaptures"
private const val AUDIT_SIZES_PROPERTY = "boxlore.auditSizes"
private const val INSETS_POSTURE = "insets"
private const val TABLETOP_INSETS_POSTURE = "tabletop-insets"
private const val BARS_POSTURE = "bars"
private const val AUDIT_CAPTURES = "audit"
private const val AUDIT_FONT_SCALE = 1.3f
private const val DEFAULT_CAPTURE_LOCALE = "en-rUS"

/** Fixed local capture inputs; changing these never replaces an existing phone baseline. */
data class PhoneGoldenConfiguration(
    val width: Int,
    val height: Int,
    val dark: Boolean,
    val posture: String = "",
    val fontScale: Float = 1f,
    val locale: String = DEFAULT_CAPTURE_LOCALE,
) {
    override fun toString(): String =
        "${width}x$height-${if (dark) "dark" else "light"}${if (posture.isEmpty()) "" else "-$posture"}" +
            (if (fontScale == 1f) "" else "-fs${(fontScale * 100).toInt()}") +
            if (locale == DEFAULT_CAPTURE_LOCALE) "" else "-$locale"

    /** The name `-PauditSizes` matches: [toString] without the light/dark part, such as `915x412-insets`. */
    val auditKey: String get() = toString().replace(if (dark) "-dark" else "-light", "")

    /** Fake system bars and cutout this capture sends to the window, or null for none. */
    val systemInsets: SystemInsets?
        get() = when (posture) {
            INSETS_POSTURE -> SystemInsets.SIDEWAYS_3_BUTTON
            TABLETOP_INSETS_POSTURE -> SystemInsets.TABLETOP_STATUS
            BARS_POSTURE -> SystemInsets.PHONE_BARS
            else -> null
        }

    /** The hinge posture without the insets suffix: `tabletop`, `book` or empty. */
    internal val foldPosture: String get() = if (posture == INSETS_POSTURE || posture == BARS_POSTURE) "" else posture.removeSuffix("-$INSETS_POSTURE")

    companion object {
        private val FOLD_POSTURES = listOf(TABLETOP_INSETS_POSTURE, "tabletop", "book", INSETS_POSTURE, BARS_POSTURE)
        private val AUDIT_KEY = Regex("""(\d+)x(\d+)(.*)""")
        private val FONT_SCALE_TOKEN = Regex("""-fs(\d+)(.*)""")

        /**
         * Light-only review sizes for `-PtabletCaptures=audit`: phones, wide cover screens (about
         * 443-475dp), split-screen halves, the 600x480 gate from both sides, upright and flat
         * book-foldable inner screens, tablets, and synthetic tabletop/book hinges. `insets` sizes add
         * fake system bars and a camera cutout and tint them red in the picture. Long-text captures use
         * enlarged text, German and the en-XA pseudo-locale (every string about a third longer) on the
         * sizes where text runs out first.
         */
        private val AUDIT_CONFIGURATIONS = listOf(
            PhoneGoldenConfiguration(412, 915, false),
            PhoneGoldenConfiguration(915, 412, false),
            PhoneGoldenConfiguration(915, 412, false, INSETS_POSTURE),
            PhoneGoldenConfiguration(412, 450, false),
            PhoneGoldenConfiguration(443, 649, false),
            PhoneGoldenConfiguration(475, 695, false),
            PhoneGoldenConfiguration(475, 695, false, fontScale = AUDIT_FONT_SCALE),
            PhoneGoldenConfiguration(695, 475, false, INSETS_POSTURE),
            PhoneGoldenConfiguration(540, 860, false),
            PhoneGoldenConfiguration(599, 479, false),
            PhoneGoldenConfiguration(600, 480, false),
            PhoneGoldenConfiguration(600, 960, false),
            PhoneGoldenConfiguration(750, 832, false),
            PhoneGoldenConfiguration(750, 832, false, "book"),
            PhoneGoldenConfiguration(800, 1_280, false),
            PhoneGoldenConfiguration(841, 701, false),
            PhoneGoldenConfiguration(932, 650, false),
            PhoneGoldenConfiguration(932, 650, false, "tabletop"),
            PhoneGoldenConfiguration(932, 650, false, TABLETOP_INSETS_POSTURE),
            PhoneGoldenConfiguration(932, 650, false, "book"),
            PhoneGoldenConfiguration(1_280, 800, false),
            PhoneGoldenConfiguration(1_600, 1_000, false),
            PhoneGoldenConfiguration(412, 915, false, fontScale = AUDIT_FONT_SCALE, locale = "de"),
            PhoneGoldenConfiguration(475, 695, false, locale = "de"),
            PhoneGoldenConfiguration(475, 695, false, locale = "en-rXA"),
            PhoneGoldenConfiguration(932, 650, false, locale = "de"),
        )

        /**
         * The audit review sizes plus [extra] ones a suite needs. When [sizes] (comma separated, from
         * `-PauditSizes`) is set, only the [auditKey]s it names are kept, in its order; a named size
         * outside the matrix, such as `360x800` or `412x915-fs130-de`, is captured in light.
         */
        fun auditConfigurations(
            extra: List<PhoneGoldenConfiguration> = emptyList(),
            sizes: String? = System.getProperty(AUDIT_SIZES_PROPERTY),
        ): List<PhoneGoldenConfiguration> {
            val all = (AUDIT_CONFIGURATIONS + extra).distinct()
            val wanted = sizes?.split(',')?.map(String::trim)?.filter(String::isNotEmpty).orEmpty()
            if (wanted.isEmpty()) return all
            return wanted.flatMap { key ->
                all.filter { it.auditKey == key || it.toString() == key }.ifEmpty { listOfNotNull(parseAuditKey(key)) }
            }.distinct()
        }

        /** `WxH`, then optionally a posture, `-fsNNN` and a locale, in [toString] order. */
        internal fun parseAuditKey(key: String): PhoneGoldenConfiguration? {
            val size = AUDIT_KEY.matchEntire(key) ?: return null
            var rest = size.groupValues[3]
            val posture = FOLD_POSTURES.firstOrNull { rest == "-$it" || rest.startsWith("-$it-") }.orEmpty()
            if (posture.isNotEmpty()) rest = rest.removePrefix("-$posture")
            val fontScale = FONT_SCALE_TOKEN.matchEntire(rest)
            if (fontScale != null) rest = fontScale.groupValues[2]
            if (rest.isNotEmpty() && !rest.startsWith("-")) return null
            return PhoneGoldenConfiguration(
                width = size.groupValues[1].toInt(),
                height = size.groupValues[2].toInt(),
                dark = false,
                posture = posture,
                fontScale = fontScale?.let { it.groupValues[1].toInt() / 100f } ?: 1f,
                locale = rest.removePrefix("-").ifEmpty { DEFAULT_CAPTURE_LOCALE },
            )
        }

        /**
         * Portrait phones and the Galaxy Z Fold 8 cover with a status bar and a gesture bar (`bars`),
         * as listeners see them. Suites that fit content to the height pass these as extra audit sizes.
         */
        val PHONE_BARS_CONFIGURATIONS = listOf(
            PhoneGoldenConfiguration(412, 915, false, BARS_POSTURE),
            PhoneGoldenConfiguration(360, 800, false, BARS_POSTURE),
            PhoneGoldenConfiguration(443, 649, false, BARS_POSTURE),
            PhoneGoldenConfiguration(475, 695, false, BARS_POSTURE),
        )

        /** [auditConfigurations] as test parameters. */
        fun auditMatrix(extra: List<PhoneGoldenConfiguration> = emptyList()): List<Array<Any>> =
            auditConfigurations(extra).map { arrayOf(it) }

        fun matrix(): List<Array<Any>> {
            if (System.getProperty(TABLET_CAPTURES_PROPERTY) == AUDIT_CAPTURES) return auditMatrix()
            val sizes = if (System.getProperty(TABLET_CAPTURES_PROPERTY) != null) {
                listOf(Triple(600, 960, ""), Triple(840, 1_280, ""), Triple(1_280, 800, ""), Triple(1_600, 1_000, ""), Triple(673, 841, "flat"), Triple(673, 841, "tabletop"))
            } else {
                listOf(Triple(360, 800, ""), Triple(412, 915, ""), Triple(800, 360, ""))
            }
            return sizes.flatMap { (width, height, posture) ->
                listOf(false, true).map { dark -> arrayOf(PhoneGoldenConfiguration(width, height, dark, posture)) }
            }
        }
    }
}

/** Roborazzi renders actuals; comparison below requires identical dimensions and ARGB pixels. */
class PhoneGoldenRule(val configuration: PhoneGoldenConfiguration) : TestRule {
    val compose = createAndroidComposeRule<ComponentActivity>()
    private var originalImageLoader: ImageLoader? = null
    private var fixtureImageLoader: ImageLoader? = null
    private val motion = MotionSetting(::applyReducedMotion)

    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            val orientation = if (configuration.width > configuration.height) "land" else "port"
            val night = if (configuration.dark) "night" else "notnight"
            RuntimeEnvironment.setQualifiers(
                "${configuration.locale}-w${configuration.width}dp-h${configuration.height}dp-$orientation-$night-mdpi",
            )
            if (configuration.fontScale != 1f) RuntimeEnvironment.setFontScale(configuration.fontScale)
            try {
                compose.apply(base, description).evaluate()
            } finally {
                if (motion.reduced) motion.set(false)
                if (configuration.fontScale != 1f) RuntimeEnvironment.setFontScale(1f)
                originalImageLoader?.let(Coil::setImageLoader)
                fixtureImageLoader?.shutdown()
            }
        }
    }

    fun prepare() {
        WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false)
        compose.mainClock.autoAdvance = false
        val context = compose.activity
        originalImageLoader = Coil.imageLoader(context)
        val artwork = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.rgb(20, 100, 120))
        }
        fixtureImageLoader = ImageLoader.Builder(context)
            .components {
                add(
                    coil.intercept.Interceptor { chain ->
                        SuccessResult(BitmapDrawable(context.resources, artwork), chain.request, DataSource.MEMORY_CACHE)
                    },
                )
            }
            .build()
        Coil.setImageLoader(requireNotNull(fixtureImageLoader))
    }

    private fun captureWindow(): BoxloreWindowLayout {
        // A 21dp synthetic hinge across the middle (410-431dp on the 673x841 tablet capture).
        val hingeTop = (configuration.height / 2 - 10).toFloat()
        val hingeLeft = (configuration.width / 2 - 10).toFloat()
        val folds = when (configuration.foldPosture) {
            "tabletop" -> listOf(
                BoxloreFold(
                    BoxloreFoldState.HalfOpened,
                    BoxloreFoldOrientation.Horizontal,
                    BoxloreLayoutBounds(0f, hingeTop, configuration.width.toFloat(), hingeTop + 21f),
                    true,
                    true,
                ),
            )
            "book" -> listOf(
                BoxloreFold(
                    BoxloreFoldState.HalfOpened,
                    BoxloreFoldOrientation.Vertical,
                    BoxloreLayoutBounds(hingeLeft, 0f, hingeLeft + 21f, configuration.height.toFloat()),
                    true,
                    true,
                ),
            )
            else -> emptyList()
        }
        return BoxloreWindowLayout(configuration.width.toFloat(), configuration.height.toFloat(), folds)
    }

    fun setContent(foldSafeViewport: Boolean = false, content: @Composable () -> Unit) {
        // Keep the frozen phone fixture's composition and animation start frame unchanged.
        if (System.getProperty(TABLET_CAPTURES_PROPERTY) == null) {
            compose.setContent(content)
            return
        }
        val window = if (System.getProperty(TABLET_CAPTURES_PROPERTY) == "before") {
            BoxloreWindowLayout.Unspecified
        } else {
            BoxloreTestWindows.forCapture(configuration.width, configuration.height, configuration.posture)
                ?: captureWindow()
        }
        compose.setContent {
            CompositionLocalProvider(
                LocalBoxloreWindowLayout provides window,
                LocalBoxlorePaneWidth provides configuration.width.toFloat(),
            ) {
                if (foldSafeViewport && window.usesTabletLayout) {
                    BoxLoreTheme(darkTheme = configuration.dark, dynamicColor = false) { GoldenFoldSafeViewport(window, content) }
                } else {
                    content()
                }
            }
        }
        dispatchSystemInsets()
    }

    /** True only for `-PtabletCaptures=audit`; audit-only suites skip themselves otherwise. */
    val isAuditCapture: Boolean get() = System.getProperty(TABLET_CAPTURES_PROPERTY) == AUDIT_CAPTURES

    /** Skips the calling test unless this is an audit capture run. */
    fun assumeAuditCapture() = assumeTrue("Audit captures run with -PtabletCaptures=audit", isAuditCapture)

    /**
     * Turns system animations off (animator duration scale 0, as with "Remove animations") or back
     * on. The rule turns them back on after the test. Set it before content that reads it once.
     */
    fun setReducedMotion(enabled: Boolean) = motion.set(enabled)

    private fun applyReducedMotion(enabled: Boolean) {
        val scale = if (enabled) 0f else 1f
        Settings.Global.putFloat(RuntimeEnvironment.getApplication().contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
        ValueAnimator::class.java.getDeclaredMethod("setDurationScale", Float::class.javaPrimitiveType).invoke(null, scale)
    }

    private fun dispatchSystemInsets(imeBottomDp: Int? = null) {
        val preset = configuration.systemInsets
        if (!isAuditCapture) return
        val insets = when {
            imeBottomDp != null -> (preset ?: SystemInsets()).copy(imeBottom = imeBottomDp)
            else -> preset ?: return
        }
        compose.runOnUiThread { applyFakeSystemInsets(compose.activity, insets) }
    }

    /**
     * Settles animations and writes `screen-config.png`. [allWindows] composites every window (sheets,
     * dialogs, menus and popups live in their own) instead of the content root only. [imeBottomDp]
     * keeps a keyboard of that height over the configuration's own insets.
     */
    fun capture(screen: String, allWindows: Boolean = false, imeBottomDp: Int? = null) {
        dispatchSystemInsets(imeBottomDp)
        compose.mainClock.advanceTimeBy(1_600)
        compose.waitForIdle()
        val filename = "$screen-$configuration.png"
        val actual = record(filename, allWindows)
        if (System.getProperty(TABLET_CAPTURES_PROPERTY) != null) return
        val root = File(requireNotNull(System.getProperty("boxlore.projectRoot")))
        val baseline = File(root, "screenshots/baselines/phone/$filename")
        if (System.getProperty("boxlore.phoneGoldens") == "record") {
            check(!baseline.exists()) { "Phone goldens are immutable: $baseline" }
            requireNotNull(baseline.parentFile).mkdirs()
            actual.copyTo(baseline, overwrite = false)
        } else {
            check(System.getProperty("boxlore.phoneGoldens") == "verify") {
                "Phone captures are local only; use -PphoneGoldens=record or verify"
            }
            comparePixels(baseline, actual)
        }
    }

    /**
     * Review frames of a motion: pauses the clock, runs [trigger], then writes
     * `screen-fNN-config.png` ([frames] of them, [stepMs] apart, the first one frame after
     * [trigger]). [reducedMotion] turns system animations off for this sequence only and names the
     * files `screen-reduced-fNN-config.png`. Phone golden runs skip sequences: they have no baselines.
     */
    fun captureSequence(
        screen: String,
        frames: Int,
        stepMs: Long,
        reducedMotion: Boolean = false,
        allWindows: Boolean = false,
        trigger: () -> Unit,
    ) {
        require(frames > 0 && stepMs > 0) { "A sequence needs at least one frame and a positive step" }
        if (System.getProperty(TABLET_CAPTURES_PROPERTY) == null) return
        compose.mainClock.autoAdvance = false
        motion.during(reducedMotion) {
            dispatchSystemInsets()
            compose.waitForIdle()
            compose.runOnUiThread(trigger)
            val name = if (reducedMotion) "$screen-reduced" else screen
            repeat(frames) { frame ->
                Snapshot.sendApplyNotifications()
                if (frame == 0) compose.mainClock.advanceTimeByFrame() else compose.mainClock.advanceTimeBy(stepMs)
                compose.waitForIdle()
                record("$name-f${frame.toString().padStart(2, '0')}-$configuration.png", allWindows)
            }
        }
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun record(filename: String, allWindows: Boolean): File {
        val root = File(requireNotNull(System.getProperty("boxlore.projectRoot")))
        val tabletMode = System.getProperty(TABLET_CAPTURES_PROPERTY)
        val directory = if (tabletMode == null) "phone-goldens/actual" else "tablet-captures/$tabletMode"
        val actual = File(root, "build/reports/$directory/$filename")
        requireNotNull(actual.parentFile).mkdirs()
        val options = RoborazziOptions(taskType = RoborazziTaskType.Record)
        if (allWindows) captureScreenRoboImage(actual.absolutePath, options) else compose.onRoot().captureRoboImage(actual.absolutePath, roborazziOptions = options)
        val render = requireNotNull(BitmapFactory.decodeFile(actual.absolutePath))
        try {
            assertEquals("Requested phone width", configuration.width, render.width)
            assertEquals("Requested phone height", configuration.height, render.height)
        } finally {
            render.recycle()
        }
        val insets = configuration.systemInsets
        if (insets != null && tabletMode == AUDIT_CAPTURES) drawInsetOverlay(actual, insets)
        return actual
    }

    private fun comparePixels(baseline: File, actual: File) {
        check(baseline.isFile) { "Missing phone baseline: $baseline" }
        val expectedBitmap = requireNotNull(BitmapFactory.decodeFile(baseline.absolutePath))
        val actualBitmap = requireNotNull(BitmapFactory.decodeFile(actual.absolutePath))
        try {
            assertEquals("Width: $actual", expectedBitmap.width, actualBitmap.width)
            assertEquals("Height: $actual", expectedBitmap.height, actualBitmap.height)
            val expectedPixels = IntArray(expectedBitmap.width * expectedBitmap.height)
            val actualPixels = IntArray(expectedPixels.size)
            expectedBitmap.getPixels(expectedPixels, 0, expectedBitmap.width, 0, 0, expectedBitmap.width, expectedBitmap.height)
            actualBitmap.getPixels(actualPixels, 0, actualBitmap.width, 0, 0, actualBitmap.width, actualBitmap.height)
            assertArrayEquals("Phone pixels changed. Inspect $actual", expectedPixels, actualPixels)
        } finally {
            expectedBitmap.recycle()
            actualBitmap.recycle()
        }
    }

    companion object {
        const val ARTWORK_URL = "https://images.example.invalid/phone-golden.png"
    }
}
