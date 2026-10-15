package cx.aswin.boxlore.feature.onboarding

import java.io.DataInputStream
import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EchoOnboardingMotionTest {
    private val assets = File("src/main/assets/echo-onboarding")
    private val specs = parseEchoOnboardingSpecs(File(assets, "manifest.json").readText())

    @Test fun `every shipped state has a matching complete atlas and valid frame timings`() {
        assertEquals(EchoOnboardingStates, specs.keys)
        specs.values.forEach { spec ->
            assertEquals(0, spec.frameAt(-100))
            assertEquals(1, spec.frameAt(spec.durations.first().toLong()))
            assertEquals(if (spec.loop) 0 else spec.durations.lastIndex, spec.frameAt(spec.duration))
            DataInputStream(File(assets, spec.assetPath.removePrefix("echo-onboarding/")).inputStream()).use { stream ->
                assertEquals(0x89504e470d0a1a0aUL.toLong(), stream.readLong())
                stream.skipBytes(8)
                val (width, height) = spec.geometry.atlasSize(spec.durations.size)
                assertEquals(width, stream.readInt())
                assertEquals(height, stream.readInt())
            }
        }
    }

    @Test fun `one shots settle and new phases interrupt immediately`() {
        assertEquals("welcome", echoOnboardingPose(EchoOnboardingPhase.Welcome, 0, specs).id)
        val welcomeEnd = specs.getValue("welcome").duration + specs.getValue("asking").duration
        assertEquals("waiting", echoOnboardingPose(EchoOnboardingPhase.Welcome, welcomeEnd, specs).id)
        assertEquals("thinking", echoOnboardingPose(EchoOnboardingPhase.Thinking, specs.getValue("acknowledging").duration, specs).id)
        assertEquals("asking", echoOnboardingPose(EchoOnboardingPhase.Question, 0, specs).id)
        assertEquals("waiting", echoOnboardingPose(EchoOnboardingPhase.Presenting, 9000, specs).id)
    }

    @Test fun `real data and loading states drive animation independently of duration`() {
        fun phase(state: OnboardingUiState) = EchoScreenModel.avatarPhase(state, EchoScreenModel.kind(state, writing = false))
        assertEquals(EchoOnboardingPhase.Welcome, phase(OnboardingUiState()))
        assertEquals(EchoOnboardingPhase.Thinking, phase(OnboardingUiState(isAiLoading = true)))
        assertEquals(EchoOnboardingPhase.Preparing, phase(OnboardingUiState(isAiLoading = true, isSynthesizing = true)))
        assertEquals(EchoOnboardingPhase.Rest, phase(OnboardingUiState(onboardingError = "offline")))
        assertTrue(specs.values.all { it.poster in it.durations.indices })
    }

    @Test fun `waiting and generating loops have visible motion without multi second rests`() {
        listOf("waiting", "thinking", "preparing").forEach { id ->
            val spec = specs.getValue(id)
            assertEquals(2720L, spec.duration)
            assertTrue(spec.durations.max() <= 400)
            assertTrue(spec.durations.first() + spec.durations.last() <= 620)
            assertEquals(1, spec.frameAt(220))
        }
    }

    @Test fun `ready state presents once then stays animated in its waiting loop`() {
        val end = specs.getValue("presenting").duration
        assertEquals("presenting", echoOnboardingPose(EchoOnboardingPhase.Presenting, end - 1, specs).id)
        val waiting = echoOnboardingPose(EchoOnboardingPhase.Presenting, end, specs)
        assertEquals(EchoOnboardingPose("waiting", 0), waiting)
        val nextFrame = echoOnboardingPose(EchoOnboardingPhase.Presenting, end + 220, specs)
        assertEquals(1, specs.getValue(nextFrame.id).frameAt(nextFrame.elapsed))
        val nextLoop = echoOnboardingPose(EchoOnboardingPhase.Presenting, end + specs.getValue("waiting").duration, specs)
        assertEquals(0, specs.getValue(nextLoop.id).frameAt(nextLoop.elapsed))
    }
}
