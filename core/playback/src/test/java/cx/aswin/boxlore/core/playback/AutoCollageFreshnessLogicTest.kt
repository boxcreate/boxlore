package cx.aswin.boxlore.core.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoCollageFreshnessLogicTest {
    @Test
    fun signatureChangesWhenContentKeysChange() {
        val a =
            AutoCollageFreshnessLogic.buildSignature(
                contentKeysOrUrls = listOf("ep-1", "ep-2"),
                loadedImageCount = 2,
                expectedImageCount = 2,
            )
        val b =
            AutoCollageFreshnessLogic.buildSignature(
                contentKeysOrUrls = listOf("ep-3", "ep-2"),
                loadedImageCount = 2,
                expectedImageCount = 2,
            )
        assertNotEquals(a, b)
    }

    @Test
    fun signatureChangesWhenLoadedCountChanges() {
        val partial =
            AutoCollageFreshnessLogic.buildSignature(
                contentKeysOrUrls = listOf("ep-1"),
                loadedImageCount = 0,
                expectedImageCount = 1,
            )
        val full =
            AutoCollageFreshnessLogic.buildSignature(
                contentKeysOrUrls = listOf("ep-1"),
                loadedImageCount = 1,
                expectedImageCount = 1,
            )
        assertNotEquals(partial, full)
    }

    @Test
    fun fullCollageStaysFreshWithinFullTtl() {
        val signature =
            AutoCollageFreshnessLogic.buildSignature(
                contentKeysOrUrls = listOf("a"),
                loadedImageCount = 1,
                expectedImageCount = 1,
            )
        assertTrue(
            AutoCollageFreshnessLogic.isFresh(
                ageMs = AutoCollageFreshnessLogic.FULL_TTL_MS - 1,
                storedSignature = signature,
                currentSignature = signature,
                loadedImageCount = 1,
                expectedImageCount = 1,
            ),
        )
        assertFalse(
            AutoCollageFreshnessLogic.isFresh(
                ageMs = AutoCollageFreshnessLogic.FULL_TTL_MS + 1,
                storedSignature = signature,
                currentSignature = signature,
                loadedImageCount = 1,
                expectedImageCount = 1,
            ),
        )
    }

    @Test
    fun partialCollageExpiresSoonerThanFull() {
        val signature =
            AutoCollageFreshnessLogic.buildSignature(
                contentKeysOrUrls = listOf("a", "b"),
                loadedImageCount = 1,
                expectedImageCount = 2,
            )
        assertTrue(
            AutoCollageFreshnessLogic.isFresh(
                ageMs = AutoCollageFreshnessLogic.PARTIAL_TTL_MS - 1,
                storedSignature = signature,
                currentSignature = signature,
                loadedImageCount = 1,
                expectedImageCount = 2,
            ),
        )
        assertFalse(
            AutoCollageFreshnessLogic.isFresh(
                ageMs = AutoCollageFreshnessLogic.PARTIAL_TTL_MS + 1,
                storedSignature = signature,
                currentSignature = signature,
                loadedImageCount = 1,
                expectedImageCount = 2,
            ),
        )
    }

    @Test
    fun mismatchedSignatureIsNeverFresh() {
        assertFalse(
            AutoCollageFreshnessLogic.isFresh(
                ageMs = 0,
                storedSignature = "old",
                currentSignature = "new",
                loadedImageCount = 4,
                expectedImageCount = 4,
            ),
        )
        assertFalse(
            AutoCollageFreshnessLogic.isFresh(
                ageMs = 0,
                storedSignature = null,
                currentSignature = "new",
                loadedImageCount = 4,
                expectedImageCount = 4,
            ),
        )
    }

    @Test
    fun partialTtlIsShorterThanFullTtl() {
        assertTrue(AutoCollageFreshnessLogic.PARTIAL_TTL_MS < AutoCollageFreshnessLogic.FULL_TTL_MS)
        assertEquals("collage-v4", AutoCollageFreshnessLogic.SIGNATURE_VERSION)
    }
}
