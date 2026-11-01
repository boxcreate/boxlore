package cx.aswin.boxlore.sync

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FeedFirstSwitchTest {
    @get:Rule
    val folder = TemporaryFolder()

    /** A PostHog stand-in: [answer] is the flag value, [exposures] counts exposure reads. */
    private class FakeFlag(var answer: Any? = null, var throwing: Boolean = false) {
        var exposures = 0
        var reads = 0

        fun read(sendExposure: Boolean): Any? {
            reads++
            if (throwing) error("PostHog not started")
            if (sendExposure) exposures++
            return answer
        }
    }

    private class MemoryFlag(var value: Boolean? = null) : LastKnownFlag {
        var writes = 0

        override fun read(): Boolean? = value

        override fun write(value: Boolean) {
            writes++
            this.value = value
        }
    }

    private fun switch(flag: FakeFlag, lastKnown: LastKnownFlag = MemoryFlag()) = FeedFirstSwitch(flag::read, lastKnown)

    @Test
    fun noAnswerAndNothingSavedIsOffWithoutExposure() {
        val flag = FakeFlag(answer = null)
        val saved = MemoryFlag()

        assertFalse(switch(flag, saved).isOn())
        assertEquals(0, flag.exposures)
        assertNull(saved.value)
    }

    @Test
    fun liveOnIsOnSavedAndExposedOnce() {
        val flag = FakeFlag(answer = true)
        val saved = MemoryFlag()
        val feedFirst = switch(flag, saved)

        assertTrue(feedFirst.isOn())
        assertTrue(feedFirst.isOn())
        assertEquals(true, saved.value)
        assertEquals(1, flag.exposures)
    }

    @Test
    fun liveOffIsOffSavedAndExposed() {
        val flag = FakeFlag(answer = false)
        val saved = MemoryFlag(value = true)

        assertFalse(switch(flag, saved).isOn())
        assertEquals(false, saved.value)
        assertEquals(1, flag.exposures)
    }

    @Test
    fun throwingReadFallsBackToTheSavedAnswerWithoutExposure() {
        val flag = FakeFlag(answer = true, throwing = true)

        assertFalse(switch(flag, MemoryFlag()).isOn())
        assertTrue(switch(flag, MemoryFlag(value = true)).isOn())
        assertEquals(0, flag.exposures)
    }

    @Test
    fun throwingExposureStillUsesTheLiveAnswer() {
        val saved = MemoryFlag()
        val feedFirst =
            FeedFirstSwitch(
                readFlag = { sendExposure -> if (sendExposure) error("capture failed") else true },
                lastKnown = saved,
            )

        assertTrue(feedFirst.isOn())
        assertEquals(true, saved.value)
    }

    @Test
    fun unknownAnswerUsesTheSavedAnswerWithoutExposure() {
        val flag = FakeFlag(answer = null)

        assertTrue(switch(flag, MemoryFlag(value = true)).isOn())
        assertEquals(0, flag.exposures)
    }

    @Test
    fun onlyBooleanAnswersCount() {
        listOf<Any>("true", "test", 1, emptyMap<String, Any>()).forEach { raw ->
            val flag = FakeFlag(answer = raw)
            val saved = MemoryFlag()

            assertFalse("answer $raw", switch(flag, saved).isOn())
            assertNull("answer $raw", saved.value)
            assertEquals("answer $raw", 0, flag.exposures)
        }
    }

    @Test
    fun valueIsFixedForTheProcessAcrossFlagLoads() {
        val flag = FakeFlag(answer = true)
        val saved = MemoryFlag()
        val feedFirst = switch(flag, saved)
        assertTrue(feedFirst.isOn())

        // PostHog loads flags again mid-session and the rollout now says off; the loaded-flags
        // callback hands the new answer to remember (RolloutFlagMemoryTest covers that wiring).
        flag.answer = false
        feedFirst.remember(false)

        assertTrue(feedFirst.isOn())
        assertEquals(1, flag.exposures)
        // The next process starts from the new answer, even before PostHog is ready.
        assertEquals(false, saved.value)
        assertFalse(switch(FakeFlag(answer = null), saved).isOn())
    }

    @Test
    fun valueFromTheSavedAnswerStaysWhenPostHogAnswersLater() {
        val flag = FakeFlag(answer = null)
        val saved = MemoryFlag(value = false)
        val feedFirst = switch(flag, saved)
        assertFalse(feedFirst.isOn())

        flag.answer = true
        feedFirst.remember(true)

        assertFalse(feedFirst.isOn())
        assertEquals(true, saved.value)
        assertEquals(0, flag.exposures)
    }

    @Test
    fun rememberNeverExposesAndKeepsTheSavedAnswerWithoutABooleanOne() {
        val flag = FakeFlag(answer = null)
        val saved = MemoryFlag(value = true)
        val feedFirst = switch(flag, saved)

        feedFirst.remember(null)
        feedFirst.remember("test")

        assertEquals(true, saved.value)
        assertEquals(0, saved.writes)
        assertEquals(0, flag.reads)
        assertEquals(0, flag.exposures)
    }

    @Test
    fun exposureReadDecidesWhenAFlagLoadLandsBetweenTheTwoReads() {
        // The first read says on, then a flag load flips the answer before the exposure read.
        var answer = true
        val exposed = mutableListOf<Any?>()
        val saved = MemoryFlag()
        val feedFirst =
            FeedFirstSwitch(
                readFlag = { sendExposure ->
                    val value = answer
                    if (sendExposure) exposed += value else answer = false
                    value
                },
                lastKnown = saved,
            )

        // The recorded exposure response (false) is the value this process uses.
        assertFalse(feedFirst.isOn())
        assertEquals(listOf<Any?>(false), exposed)
        assertEquals(false, saved.value)
    }

    @Test
    fun flagVanishingBeforeTheExposureReadKeepsTheFirstAnswer() {
        val saved = MemoryFlag()
        val feedFirst = FeedFirstSwitch(readFlag = { sendExposure -> if (sendExposure) null else true }, lastKnown = saved)

        assertTrue(feedFirst.isOn())
        assertEquals(true, saved.value)
    }

    @Test
    fun rollbackNeedsALiveOffBecauseADisabledFlagCountsAsNoAnswer() {
        val saved = MemoryFlag(value = true)

        // A disabled or deleted flag is missing from PostHog's answer: a phone that saved `on` stays on.
        val disabled = FakeFlag(answer = null)
        assertTrue(switch(disabled, saved).isOn())
        switch(disabled, saved).remember(null)
        assertEquals(true, saved.value)

        // An active flag at a 0% release condition answers false: off from this process on, and saved.
        assertFalse(switch(FakeFlag(answer = false), saved).isOn())
        assertEquals(false, saved.value)
        assertFalse(switch(FakeFlag(answer = null), saved).isOn())
    }

    @Test
    fun firstReadDecidesOnceEvenWhenCalledFromManyThreads() {
        val flag = FakeFlag(answer = true)
        val feedFirst = switch(flag)
        val threads = List(8) { Thread { repeat(50) { feedFirst.isOn() } } }

        threads.forEach(Thread::start)
        threads.forEach(Thread::join)

        assertEquals(1, flag.exposures)
        assertEquals(2, flag.reads)
    }

    @Test
    fun fileKeepsTheLastAnswer() {
        val file = File(folder.root, "no_backup/${FeedFirstSwitch.LAST_KNOWN_FILE}")
        val saved = FileLastKnownFlag(file)
        assertNull(saved.read())

        saved.write(true)
        assertEquals(true, FileLastKnownFlag(file).read())
        saved.write(false)
        assertEquals(false, FileLastKnownFlag(file).read())
        assertFalse(File(file.parentFile, "${file.name}.tmp").exists())
    }

    @Test
    fun unreadableOrUnwritableFileCountsAsNoAnswer() {
        val garbage = folder.newFile("garbage").apply { writeText("maybe") }
        assertNull(FileLastKnownFlag(garbage).read())

        // A directory where the file should be: reads give no answer and writes fail quietly.
        val blocked = folder.newFolder("blocked")
        val saved = FileLastKnownFlag(blocked)
        saved.write(true)
        assertNull(saved.read())

        assertFalse(switch(FakeFlag(answer = null), saved).isOn())
        assertTrue(switch(FakeFlag(answer = true), saved).isOn())
    }
}
