package cx.aswin.boxlore.feature.settings.pages

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal data class EchoLoop(val row: Int, val durations: List<Int>) {
    init {
        require(row >= 0 && durations.isNotEmpty() && durations.all { it > 0 })
    }

    val duration: Long = durations.sumOf { it.toLong() }

    fun frameAt(elapsed: Long, loop: Boolean = true): Int {
        var remaining = if (loop) elapsed.coerceAtLeast(0) % duration else elapsed.coerceIn(0, duration - 1)
        durations.forEachIndexed { index, time ->
            if (remaining < time) return index
            remaining -= time
        }
        return durations.lastIndex
    }
}

internal fun parseEchoLoops(manifest: String): Map<String, EchoLoop> =
    Json.parseToJsonElement(manifest).jsonObject.getValue("states").jsonArray.associate { element ->
        val state = element.jsonObject
        val durations = state.getValue("frame_durations_ms").jsonArray.map { it.jsonPrimitive.int }
        require(durations.size == state.getValue("frame_count").jsonPrimitive.int && durations.size <= 8)
        val row = state.getValue("row").jsonPrimitive.int
        require(row in 0..8)
        state.getValue("id").jsonPrimitive.content to EchoLoop(row, durations)
    }.also { require(it.containsKey("idle") && it.containsKey("syncing")) }

internal data class EchoPose(val state: String, val frame: Int, val alpha: Float = 1f)
internal data class EchoFrame(val first: EchoPose, val second: EchoPose? = null)

/** One reusable spin; rapid requests replace the destination rather than queueing costumes. */
internal class EchoPlayback(private val loops: Map<String, EchoLoop>, private val spin: EchoLoop, initial: String) {
    private var shown = safe(initial)
    private var requested = shown
    private var loopStart = 0L
    private var spinStart: Long? = null
    private var outgoing = EchoPose(shown, 0)
    private var revealing: String? = null

    private fun safe(id: String) = id.takeIf(loops::containsKey) ?: "idle"

    fun frame(now: Long, target: String): EchoFrame {
        requested = safe(target)
        val start = spinStart
        if (start == null) {
            if (requested == shown) return EchoFrame(EchoPose(shown, loops.getValue(shown).frameAt(now - loopStart)))
            outgoing = EchoPose(shown, loops.getValue(shown).frameAt(now - loopStart))
            spinStart = now
            revealing = null
            return EchoFrame(outgoing)
        }
        val elapsed = (now - start).coerceAtLeast(0)
        val revealStart = 60 + spin.duration
        return when {
            elapsed < 60 -> {
                val blend = elapsed / 60f
                EchoFrame(outgoing.copy(alpha = 1 - blend), EchoPose("spin", 0, blend))
            }
            elapsed < revealStart -> EchoFrame(EchoPose("spin", spin.frameAt(elapsed - 60, loop = false)))
            elapsed < revealStart + 60 -> {
                val destination = revealing ?: requested.also { revealing = it }
                val blend = (elapsed - revealStart) / 60f
                EchoFrame(EchoPose("spin", spin.durations.lastIndex, 1 - blend), EchoPose(destination, 0, blend))
            }
            else -> {
                shown = revealing ?: requested
                loopStart = now
                spinStart = null
                frame(now, requested)
            }
        }
    }
}
