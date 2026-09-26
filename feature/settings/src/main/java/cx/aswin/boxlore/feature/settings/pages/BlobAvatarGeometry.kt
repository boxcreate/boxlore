package cx.aswin.boxlore.feature.settings.pages

import kotlin.math.sqrt

internal object BlobAvatarGeometry {
    const val BODY_WIDTH_RATIO = 0.72f
    const val BODY_HEIGHT_RATIO = 0.68f
    const val FLOAT_OFFSET_RATIO = 0.035f

    const val HEADBAND_TOP_RATIO = 0.54f
    const val HEADBAND_STROKE_RATIO = 0.058f
    const val HEADBAND_MIN_STROKE = 3f

    const val CUP_SPACING_RATIO = 0.48f
    const val CUP_WIDTH_RATIO = 0.14f
    const val CUP_HEIGHT_RATIO = 0.30f

    const val BLUSH_SPACING_RATIO = 0.28f
    const val BLUSH_RADIUS_RATIO = 0.075f

    const val EYE_SPACING_RATIO = 0.18f
    const val EYE_WIDTH_RATIO = 0.072f
    const val EYE_HEIGHT_RATIO = 0.105f
    const val STROKE_WIDTH_RATIO = 0.032f
    const val MIN_STROKE_WIDTH = 1.5f

    const val EYE_MAX_SHIFT_X_RATIO = 0.28f
    const val EYE_MAX_SHIFT_Y_RATIO = 0.18f

    const val SOUNDWAVE_MAX_RADIUS_RATIO = 0.20f
    const val SPOTLIGHT_BACKDROP_RADIUS_RATIO = 0.45f

    fun computeBodyWidth(w: Float, breatheX: Float = 1f): Float =
        w * BODY_WIDTH_RATIO * breatheX

    fun computeBodyHeight(h: Float, breatheY: Float = 1f): Float =
        h * BODY_HEIGHT_RATIO * breatheY

    fun computeFloatOffset(h: Float, progress: Float): Float =
        h * FLOAT_OFFSET_RATIO * progress

    fun computeHeadbandStroke(w: Float): Float =
        (w * HEADBAND_STROKE_RATIO).coerceAtLeast(HEADBAND_MIN_STROKE)

    fun computeHeadbandTopY(centerY: Float, bodyHeight: Float): Float =
        centerY - (bodyHeight * HEADBAND_TOP_RATIO)

    fun computeStrokeWidth(w: Float): Float =
        (w * STROKE_WIDTH_RATIO).coerceAtLeast(MIN_STROKE_WIDTH)

    fun computeCupParams(w: Float, bodyWidth: Float, pulse: Float = 1f): Pair<Float, Float> =
        Pair(bodyWidth * CUP_SPACING_RATIO, w * CUP_WIDTH_RATIO * pulse)

    fun computeBlushParams(w: Float, bodyWidth: Float): Pair<Float, Float> =
        Pair(bodyWidth * BLUSH_SPACING_RATIO, w * BLUSH_RADIUS_RATIO)

    fun computeEyeDimensions(w: Float): Pair<Float, Float> =
        Pair(w * EYE_WIDTH_RATIO, w * EYE_HEIGHT_RATIO)

    fun computeEyeMaxShifts(eyeWidth: Float, eyeHeight: Float): Pair<Float, Float> =
        Pair(eyeWidth * EYE_MAX_SHIFT_X_RATIO, eyeHeight * EYE_MAX_SHIFT_Y_RATIO)

    fun computeSoundwaveRadius(w: Float, progress: Float): Float =
        w * (0.07f + (progress.coerceIn(0f, 1f) * SOUNDWAVE_MAX_RADIUS_RATIO))

    fun computeSoundwaveAlpha(progress: Float): Float =
        ((1f - progress.coerceIn(0f, 1f)) * 0.65f).coerceIn(0f, 1f)

    fun computeSquishScaleX(squishY: Float): Float =
        if (squishY <= 0f) 1f else (1f / sqrt(squishY)).coerceIn(0.80f, 1.25f)
}
