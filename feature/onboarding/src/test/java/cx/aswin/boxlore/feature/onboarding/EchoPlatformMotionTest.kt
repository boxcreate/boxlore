package cx.aswin.boxlore.feature.onboarding

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EchoPlatformMotionTest {
    @Test fun `all seven questions receive a unique soft silhouette`() {
        val silhouettes = (1..7).map { echoPlatformSilhouette("$it:Question $it") }
        assertEquals(7, silhouettes.distinct().size)
        assertEquals(EchoPlatformSilhouette.entries, silhouettes)
    }

    @Test fun `question identity survives loading completion and back recreation`() {
        val question = "4:Which tone suits you?"
        val silhouette = echoPlatformSilhouette(question)
        assertEquals(silhouette, echoPlatformSilhouette(question))
        assertEquals(silhouette, echoPlatformSilhouette("4:Saved question"))
        assertEquals(EchoPlatformSilhouette.PUFFY, echoPlatformSilhouette("1:Earlier question"))
    }

    @Test fun `invalid and out of contract turns have bounded stable fallbacks`() {
        listOf("", "question", "0:question", "-1:question", "-2147483648:question").forEach { key ->
            assertEquals(EchoPlatformSilhouette.PUFFY, echoPlatformSilhouette(key))
        }
        assertEquals(EchoPlatformSilhouette.CLOVER, echoPlatformSilhouette("2147483647:question"))
    }

    @Test fun `morph and rotation remain continuous through every intermediate contour`() {
        for (index in 0..5) {
            val start = echoPlatformPose(index.toFloat())
            val middle = echoPlatformPose(index + .5f)
            val end = echoPlatformPose((index + 1).toFloat())
            assertEquals(index, middle.from)
            assertEquals(index + 1, middle.to)
            assertEquals(.5f, middle.progress)
            assertEquals((start.rotation + end.rotation) / 2f, middle.rotation)
            assertTrue(end.rotation - start.rotation == 45f)
        }
    }

    @Test fun `disabled or interrupted animation can settle at any exact target`() {
        (0..6).forEach { index ->
            val pose = echoPlatformPose(index.toFloat())
            assertEquals(index, pose.from)
            assertEquals(0f, pose.progress)
        }
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -100f).forEach { value ->
            assertEquals(echoPlatformPose(0f), echoPlatformPose(value))
        }
        assertEquals(echoPlatformPose(6f), echoPlatformPose(100f))
    }
}
