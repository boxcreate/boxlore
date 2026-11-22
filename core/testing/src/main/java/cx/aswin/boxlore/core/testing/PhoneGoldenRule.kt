package cx.aswin.boxlore.core.testing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.robolectric.RuntimeEnvironment

private const val TABLET_CAPTURES_PROPERTY = "boxlore.tabletCaptures"
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

    companion object {
        /**
         * Light-only review sizes for `-PtabletCaptures=audit`: phones, wide cover screens (about
         * 443-475dp), split-screen halves, upright and flat book-foldable inner screens, tablets, and
         * synthetic tabletop/book hinges. Long-text captures use enlarged text, German and the en-XA
         * pseudo-locale (every string about a third longer) on the sizes where text runs out first.
         */
        private fun auditMatrix(): List<Array<Any>> = listOf(
            PhoneGoldenConfiguration(412, 915, false),
            PhoneGoldenConfiguration(915, 412, false),
            PhoneGoldenConfiguration(443, 649, false),
            PhoneGoldenConfiguration(475, 695, false),
            PhoneGoldenConfiguration(475, 695, false, fontScale = AUDIT_FONT_SCALE),
            PhoneGoldenConfiguration(540, 860, false),
            PhoneGoldenConfiguration(600, 960, false),
            PhoneGoldenConfiguration(750, 832, false),
            PhoneGoldenConfiguration(800, 1_280, false),
            PhoneGoldenConfiguration(841, 701, false),
            PhoneGoldenConfiguration(932, 650, false),
            PhoneGoldenConfiguration(932, 650, false, "tabletop"),
            PhoneGoldenConfiguration(932, 650, false, "book"),
            PhoneGoldenConfiguration(1_280, 800, false),
            PhoneGoldenConfiguration(1_600, 1_000, false),
            PhoneGoldenConfiguration(412, 915, false, fontScale = AUDIT_FONT_SCALE, locale = "de"),
            PhoneGoldenConfiguration(475, 695, false, locale = "de"),
            PhoneGoldenConfiguration(475, 695, false, locale = "en-rXA"),
            PhoneGoldenConfiguration(932, 650, false, locale = "de"),
        ).map { arrayOf(it) }

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

    fun setContent(foldSafeViewport: Boolean = false, content: @Composable () -> Unit) {
        // Keep the frozen phone fixture's composition and animation start frame unchanged.
        if (System.getProperty(TABLET_CAPTURES_PROPERTY) == null) {
            compose.setContent(content)
            return
        }
        val window = if (System.getProperty(TABLET_CAPTURES_PROPERTY) == "before") {
            BoxloreWindowLayout.Unspecified
        } else {
            // A 21dp synthetic hinge across the middle (410-431dp on the 673x841 tablet capture).
            val hingeTop = (configuration.height / 2 - 10).toFloat()
            val hingeLeft = (configuration.width / 2 - 10).toFloat()
            val folds = when (configuration.posture) {
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
            BoxloreWindowLayout(configuration.width.toFloat(), configuration.height.toFloat(), folds)
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
    }

    @OptIn(ExperimentalRoborazziApi::class)
    fun capture(screen: String) {
        compose.mainClock.advanceTimeBy(1_600)
        compose.waitForIdle()
        val root = File(requireNotNull(System.getProperty("boxlore.projectRoot")))
        val filename = "$screen-$configuration.png"
        val tabletMode = System.getProperty(TABLET_CAPTURES_PROPERTY)
        val directory = if (tabletMode == null) "phone-goldens/actual" else "tablet-captures/$tabletMode"
        val actual = File(root, "build/reports/$directory/$filename")
        requireNotNull(actual.parentFile).mkdirs()
        compose.onRoot().captureRoboImage(
            actual.absolutePath,
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record),
        )
        val render = requireNotNull(BitmapFactory.decodeFile(actual.absolutePath))
        try {
            assertEquals("Requested phone width", configuration.width, render.width)
            assertEquals("Requested phone height", configuration.height, render.height)
        } finally {
            render.recycle()
        }
        if (tabletMode != null) return
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
