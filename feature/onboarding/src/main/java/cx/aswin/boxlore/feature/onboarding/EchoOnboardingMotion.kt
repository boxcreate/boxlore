package cx.aswin.boxlore.feature.onboarding

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal data class EchoSpriteGeometry(val columns: Int = 8, val cellWidth: Int = 384, val cellHeight: Int = 256) {
    fun atlasSize(frameCount: Int): Pair<Int, Int> = columns * cellWidth to ((frameCount + columns - 1) / columns) * cellHeight
}

internal data class EchoOnboardingSpec(val id: String, val durations: List<Int>, val loop: Boolean, val poster: Int, val geometry: EchoSpriteGeometry = EchoSpriteGeometry()) {
    init {
        require(id in EchoOnboardingStates && durations.size == 16 && durations.all { it > 0 } && poster in durations.indices)
    }

    val duration = durations.sumOf { it.toLong() }
    val assetPath = "echo-onboarding/strips/$id.png"

    fun frameAt(elapsed: Long): Int {
        var remaining = if (loop) elapsed.coerceAtLeast(0) % duration else elapsed.coerceIn(0, duration - 1)
        durations.forEachIndexed { frame, duration ->
            if (remaining < duration) return frame
            remaining -= duration
        }
        return durations.lastIndex
    }
}

internal val EchoOnboardingStates = setOf("welcome", "asking", "waiting", "acknowledging", "thinking", "preparing", "presenting")
internal fun parseEchoOnboardingSpecs(manifest: String): Map<String, EchoOnboardingSpec> {
    val root = Json.parseToJsonElement(manifest).jsonObject
    return root.getValue("states").jsonArray.associate { element ->
        val state = element.jsonObject
        val id = state.getValue("id").jsonPrimitive.content
        val geometry = EchoSpriteGeometry()
        val durations = state.getValue("frame_durations_ms").jsonArray.map { it.jsonPrimitive.int }
        require(durations.size == state.getValue("frame_count").jsonPrimitive.int)
        require(durations.size == 16)
        require(state.getValue("columns").jsonPrimitive.int == geometry.columns && state.getValue("rows").jsonPrimitive.int == (durations.size + 7) / 8)
        require(state.getValue("cell_width_px").jsonPrimitive.int == geometry.cellWidth && state.getValue("cell_height_px").jsonPrimitive.int == geometry.cellHeight)
        id to EchoOnboardingSpec(id, durations, state.getValue("loop").jsonPrimitive.boolean, state.getValue("poster_frame").jsonPrimitive.int, geometry)
    }.also { require(it.keys == EchoOnboardingStates) }
}

internal enum class EchoOnboardingPhase { Welcome, Question, Thinking, Preparing, Presenting, Rest }

internal data class EchoOnboardingPose(val id: String, val elapsed: Long)

/** Pure sequence selection. New question/request keys reset the local clock; data is never gated. */
internal fun echoOnboardingPose(phase: EchoOnboardingPhase, elapsed: Long, specs: Map<String, EchoOnboardingSpec>): EchoOnboardingPose {
    var remaining = elapsed.coerceAtLeast(0)
    val sequence = when (phase) {
        EchoOnboardingPhase.Welcome -> listOf("welcome", "asking", "waiting")
        EchoOnboardingPhase.Question -> listOf("asking", "waiting")
        EchoOnboardingPhase.Thinking -> listOf("acknowledging", "thinking")
        EchoOnboardingPhase.Preparing -> listOf("preparing")
        EchoOnboardingPhase.Presenting -> listOf("presenting", "waiting")
        EchoOnboardingPhase.Rest -> listOf("waiting")
    }
    sequence.forEachIndexed { index, id ->
        val spec = specs.getValue(id)
        if (index == sequence.lastIndex || remaining < spec.duration) {
            return EchoOnboardingPose(id, remaining)
        }
        remaining -= spec.duration
    }
    error("Missing Echo sequence")
}
