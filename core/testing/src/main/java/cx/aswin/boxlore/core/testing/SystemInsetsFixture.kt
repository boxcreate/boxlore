package cx.aswin.boxlore.core.testing

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File

/**
 * Fake system bars, camera cutout and keyboard for a test window, in dp (captures run at mdpi, so
 * 1dp is 1px). Start and end are left and right: captures run left to right.
 */
data class SystemInsets(
    val statusTop: Int = 24,
    val navBottom: Int = 0,
    val navStart: Int = 0,
    val navEnd: Int = 0,
    val cutoutStart: Int = 0,
    val cutoutTop: Int = 0,
    val imeBottom: Int = 0,
) {
    fun toWindowInsets(): WindowInsetsCompat {
        val navigation = Insets.of(navStart, 0, navEnd, navBottom)
        val cutout = Insets.of(cutoutStart, cutoutTop, 0, 0)
        val builder = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, statusTop, 0, 0))
            .setInsets(WindowInsetsCompat.Type.navigationBars(), navigation)
            .setInsets(WindowInsetsCompat.Type.displayCutout(), cutout)
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, imeBottom))
            .setVisible(WindowInsetsCompat.Type.systemBars(), true)
            .setVisible(WindowInsetsCompat.Type.displayCutout(), cutout != Insets.NONE)
            .setVisible(WindowInsetsCompat.Type.ime(), imeBottom > 0)
        return builder.build()
    }

    /** The areas each non-zero inset covers in a [width]x[height] window, as left/top/right/bottom. */
    fun bands(width: Int, height: Int): List<IntArray> = listOfNotNull(
        intArrayOf(0, 0, width, statusTop).takeIf { statusTop > 0 },
        intArrayOf(0, 0, width, cutoutTop).takeIf { cutoutTop > 0 },
        intArrayOf(0, 0, cutoutStart, height).takeIf { cutoutStart > 0 },
        intArrayOf(0, 0, navStart, height).takeIf { navStart > 0 },
        intArrayOf(width - navEnd, 0, width, height).takeIf { navEnd > 0 },
        intArrayOf(0, height - navBottom, width, height).takeIf { navBottom > 0 },
        intArrayOf(0, height - imeBottom, width, height).takeIf { imeBottom > 0 },
    )

    companion object {
        /** A sideways phone with 3-button navigation at the end and the camera cutout at the start. */
        val SIDEWAYS_3_BUTTON = SystemInsets(statusTop = 24, navEnd = 48, cutoutStart = 32)

        /** A foldable in tabletop: only the status bar. */
        val TABLETOP_STATUS = SystemInsets(statusTop = 24)
    }
}

/**
 * Sends fake [insets] to [activity]'s window the way the system does, so Compose's `WindowInsets`
 * (safe drawing, system bars, cutout, IME) see them. The test window must draw edge to edge.
 */
fun applyFakeSystemInsets(activity: Activity, insets: SystemInsets) {
    ViewCompat.dispatchApplyWindowInsets(activity.window.decorView, insets.toWindowInsets())
}

private const val INSET_OVERLAY_COLOR = 0x4DFF0000 // 30% red

/** Draws a 30% red band over every non-zero inset area of the PNG at [file], in place. */
internal fun drawInsetOverlay(file: File, insets: SystemInsets) {
    val decoded = requireNotNull(BitmapFactory.decodeFile(file.absolutePath)) { "Unreadable capture: $file" }
    val bitmap = decoded.copy(Bitmap.Config.ARGB_8888, true)
    decoded.recycle()
    try {
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { color = INSET_OVERLAY_COLOR }
        insets.bands(bitmap.width, bitmap.height).forEach { band ->
            canvas.drawRect(band[0].toFloat(), band[1].toFloat(), band[2].toFloat(), band[3].toFloat(), paint)
        }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    } finally {
        bitmap.recycle()
    }
}
