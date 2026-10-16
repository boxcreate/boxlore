package cx.aswin.boxlore.core.designsystem.echo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Echo's screen inside a frame, as fractions of the square frame. */
data class EchoScreenBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val centerX get() = (left + right) / 2f
    val centerY get() = (top + bottom) / 2f
}

/** How far Echo reaches in its square frame (a still, or everything a clip covers), as fractions of the frame. */
data class EchoReach(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** What the code-drawn face shows on Echo's screen. */
enum class EchoFace { Idle, Blink, Happy, Curious }

/**
 * One step of a sequence: a still [frame] held for [millis] (zero holds forever), or a [clip] played once
 * (then [frame] is the still it starts and ends on, and [millis] its length). [face] is what the screen
 * shows; an idle still runs the eyes' idle script instead.
 */
data class EchoCharacterStep(val frame: String, val face: EchoFace, val millis: Long, val clip: String? = null)

data class EchoCharacterSpec(
    val screens: Map<String, EchoScreenBox>,
    val sequences: Map<String, EchoSequence>,
    val clips: Map<String, EchoMotionClip> = emptyMap(),
    val reaches: Map<String, EchoReach> = emptyMap(),
) {
    /** How far a sequence's stills and clips reach, together; null when any of them doesn't say. */
    fun reach(id: String): EchoReach? {
        val sequence = sequence(id)
        val parts = (sequence.steps + sequence.loop).map { step -> step.clip?.let { clips[it]?.reach } ?: reaches[step.frame] }
        if (parts.any { it == null }) return null
        val all = parts.filterNotNull()
        return EchoReach(all.minOf { it.left }, all.minOf { it.top }, all.maxOf { it.right }, all.maxOf { it.bottom })
    }

    fun sequence(id: String): EchoSequence = requireNotNull(sequences[id]) { "Unknown Echo sequence $id" }

    /** Every still a sequence can show, including the rests its clips start and end on. */
    fun stills(id: String): List<String> = sequence(id).let { it.steps + it.loop }.map { it.frame }.distinct()
}

fun parseEchoCharacterManifest(manifest: String): EchoCharacterSpec {
    val root = Json.parseToJsonElement(manifest).jsonObject
    require(root.getValue("schema").jsonPrimitive.content == "echo-character")
    val screens = root.getValue("frames").jsonObject.mapValues { (_, frame) ->
        val box = frame.jsonObject.getValue("screen").jsonArray.map { it.jsonPrimitive.float }
        require(box.size == 4 && box[0] < box[2] && box[1] < box[3])
        EchoScreenBox(box[0], box[1], box[2], box[3])
    }
    val reaches = root.getValue("frames").jsonObject.mapNotNull { (id, frame) -> frame.jsonObject["reach"]?.let { id to parseEchoReach(it) } }.toMap()
    val clips = root["clips"]?.jsonObject?.mapValues { (id, element) ->
        val clip = element.jsonObject
        val tracks = clip.getValue("tracks").jsonArray.map { row ->
            val t = row.jsonArray.map { it.jsonPrimitive.float }
            EchoScreenTrack(t[0], t[1], t[2], t[3], t[4])
        }
        EchoMotionClip(
            id = id,
            file = clip.getValue("file").jsonPrimitive.content,
            fps = clip.getValue("fps").jsonPrimitive.int,
            frames = clip.getValue("frames").jsonPrimitive.int,
            size = clip.getValue("size").jsonPrimitive.int,
            face = parseEchoFace(clip["face"]?.jsonPrimitive?.content ?: "idle"),
            rest = clip.getValue("rest").jsonPrimitive.content,
            tracks = tracks,
            mask = clip["mask"]?.jsonPrimitive?.content == "true",
            reach = clip["reach"]?.let(::parseEchoReach),
        ).also { require(it.rest in screens) { "Clip $id rests on unknown frame ${it.rest}" } }
    }.orEmpty()
    fun step(element: JsonElement): EchoCharacterStep {
        val step = element.jsonObject
        val face = step["face"]?.jsonPrimitive?.content?.let(::parseEchoFace)
        step["clip"]?.jsonPrimitive?.content?.let { id ->
            val clip = requireNotNull(clips[id]) { "Sequence uses unknown clip $id" }
            return EchoCharacterStep(clip.rest, face ?: clip.face, clip.millis, id)
        }
        val frame = step.getValue("frame").jsonPrimitive.content
        require(frame in screens) { "Sequence uses unknown frame $frame" }
        return EchoCharacterStep(frame, face ?: EchoFace.Idle, step.getValue("ms").jsonPrimitive.int.toLong())
    }
    val sequences = root.getValue("sequences").jsonObject.mapValues { (_, sequence) ->
        if (sequence is JsonArray) {
            EchoSequence(sequence.map(::step))
        } else {
            val body = sequence.jsonObject
            EchoSequence(body.getValue("steps").jsonArray.map(::step), body["loop"]?.jsonArray?.map(::step).orEmpty())
        }
    }
    return EchoCharacterSpec(screens, sequences, clips, reaches)
}

private fun parseEchoReach(element: JsonElement): EchoReach {
    val r = element.jsonArray.map { it.jsonPrimitive.float }
    require(r.size == 4 && r[0] < r[2] && r[1] < r[3])
    return EchoReach(r[0], r[1], r[2], r[3])
}

private fun parseEchoFace(name: String): EchoFace = EchoFace.valueOf(name.replaceFirstChar(Char::uppercase))

/** Resting blinks: a 150ms blink on an uneven rhythm (about every 3-4.5s), so it never feels mechanical. */
fun echoIsBlinking(restingMillis: Long): Boolean {
    if (restingMillis < 0) return false
    val cycle = EchoBlinkGaps.sum()
    var t = restingMillis % cycle
    for (gap in EchoBlinkGaps) {
        if (t < gap) return t >= gap - EchoBlinkMillis
        t -= gap
    }
    return false
}

private val EchoBlinkGaps = longArrayOf(3400, 4300, 2900, 3800)
const val EchoBlinkMillis = 150L

/** Where Echo looks: [x] from -1 (left) to 1 (right), [y] from -1 (up) to 1 (down). */
data class EchoGaze(val x: Float, val y: Float)

/**
 * One beat of Echo's idle: its eyes dart to [gaze] (a quick eye movement, like a person's) and hold there
 * for [holdMillis] with [face]. [blinks] blinks (one, or two in quick succession) start as the eyes move,
 * the way people blink when they shift their gaze; a beat at the same gaze just blinks.
 */
data class EchoBeat(val gaze: EchoGaze, val holdMillis: Long, val face: EchoFace = EchoFace.Idle, val blinks: Int = 0)

/** What Echo's eyes are doing at one moment of its idle. */
data class EchoIdlePose(val gaze: EchoGaze, val face: EchoFace)

/** An idle script: [intro] plays once as Echo arrives, then [loop] repeats. */
data class EchoIdle(val intro: List<EchoBeat>, val loop: List<EchoBeat>) {
    init {
        require(loop.isNotEmpty())
    }
}

/** How long the eyes take to dart to a new beat. */
const val EchoSaccadeMillis = 180L

/** Echo's eyes [restingMillis] after its idle starts: the intro once, then the loop forever. */
fun echoIdlePoseAt(restingMillis: Long, idle: EchoIdle): EchoIdlePose {
    var t = restingMillis.coerceAtLeast(0L)
    var from = idle.intro.firstOrNull()?.gaze ?: idle.loop.last().gaze
    for (beat in idle.intro) {
        if (t < EchoSaccadeMillis + beat.holdMillis) return echoBeatPose(beat, from, t)
        t -= EchoSaccadeMillis + beat.holdMillis
        from = beat.gaze
    }
    val cycle = idle.loop.sumOf { EchoSaccadeMillis + it.holdMillis }
    if (t >= cycle) from = idle.loop.last().gaze
    t %= cycle
    for (beat in idle.loop) {
        if (t < EchoSaccadeMillis + beat.holdMillis) return echoBeatPose(beat, from, t)
        t -= EchoSaccadeMillis + beat.holdMillis
        from = beat.gaze
    }
    return EchoIdlePose(idle.loop.last().gaze, idle.loop.last().face)
}

/** [t] into [beat]: the eyes dart from [from], blinking as they go, then hold with the beat's face. */
private fun echoBeatPose(beat: EchoBeat, from: EchoGaze, t: Long): EchoIdlePose {
    val e = easeInOut(t / EchoSaccadeMillis.toFloat())
    val gaze = EchoGaze(from.x + (beat.gaze.x - from.x) * e, from.y + (beat.gaze.y - from.y) * e)
    val blinking = (0 until beat.blinks).any { k -> t - k * EchoDoubleBlinkMillis in 0 until EchoBlinkMillis }
    val face = when {
        blinking -> EchoFace.Blink
        t < EchoSaccadeMillis -> EchoFace.Idle
        else -> beat.face
    }
    return EchoIdlePose(gaze, face)
}

private fun easeInOut(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

private const val EchoDoubleBlinkMillis = 310L

/** A lit dot on Echo's LED screen, by grid column and row. */
data class EchoCell(val col: Int, val row: Int)

/**
 * The LED grid over the screen, about 26 dots across. From 3px apart the dots sit on whole pixels, so
 * nothing shimmers as the face moves; smaller screens (a small Echo beside a bubble) keep the 26
 * columns at a fractional pitch, so the face keeps its proportions.
 */
data class EchoGrid(val pitch: Float, val columns: Int, val rows: Int) {
    val wholePixels get() = pitch >= EchoMinWholePitchPx
}

fun echoGrid(screenWidthPx: Float, screenHeightPx: Float): EchoGrid {
    val exact = screenWidthPx / EchoGridTargetColumns
    val pitch = if (exact >= EchoMinWholePitchPx) Math.round(exact).toFloat() else exact
    return EchoGrid(pitch, maxOf(1, (screenWidthPx / pitch + 0.001f).toInt()), maxOf(1, (screenHeightPx / pitch + 0.001f).toInt()))
}

/** Where the face sits on the grid for [gaze], in whole cells, so it moves one cell at a time. */
data class EchoFaceStep(val x: Int, val y: Int)

fun echoFaceStep(gaze: EchoGaze, grid: EchoGrid): EchoFaceStep =
    EchoFaceStep(Math.round(gaze.x * EchoGazeShiftX * grid.columns), Math.round(gaze.y * EchoGazeShiftY * grid.rows))

/** One eye on the grid: its pixel pattern placed with its top-left cell at [col], [row]. */
data class EchoEye(val col: Int, val row: Int, val pattern: List<String>) {
    val width get() = pattern.first().length
    val height get() = pattern.size

    val cells: List<EchoCell>
        get() = pattern.flatMapIndexed { r, line -> line.mapIndexedNotNull { c, cell -> if (cell == '#') EchoCell(col + c, row + r) else null } }
}

/**
 * Echo's face: just two eyes, each a small pixel pattern ([echoEyePattern]), mirrored about the screen's
 * centre a third of the way in from each side. A blink sits low in the eye, like a lid closing. The
 * whole face moves by whole cells ([step]), so it keeps its exact shape.
 */
fun echoFaceEyes(face: EchoFace, step: EchoFaceStep, grid: EchoGrid): List<EchoEye> {
    val idleTop = (grid.rows - EchoIdleEye.size) / 2
    return listOf(true, false).map { left ->
        val pattern = echoEyePattern(face, left)
        val width = pattern.first().length
        val leftCol = Math.round(grid.columns * EchoEyeCenter - width / 2f)
        val col = if (left) leftCol else grid.columns - leftCol - width
        val lowered = if (face == EchoFace.Blink) EchoBlinkDropRows else 0f
        val row = idleTop + Math.round((EchoIdleEye.size - pattern.size) / 2f + lowered)
        EchoEye(col + step.x, row + step.y, pattern)
    }
}

/** Pixel patterns for each eye, read row by row; '#' lights a dot. */
fun echoEyePattern(face: EchoFace, left: Boolean): List<String> = when (face) {
    EchoFace.Idle -> EchoIdleEye
    EchoFace.Blink -> listOf("######", "######")
    EchoFace.Happy -> listOf("..###..", ".#####.", "##...##", "#.....#")
    EchoFace.Curious -> if (left) listOf(".##.", "####", "####", "####", ".##.") else EchoIdleEye
}

private val EchoIdleEye = listOf(".##.", "####", "####", "####", "####", "####", "####", "####", ".##.")

private const val EchoGridTargetColumns = 26
private const val EchoMinWholePitchPx = 3f
private const val EchoEyeCenter = 0.34f
private const val EchoBlinkDropRows = 1.5f
private const val EchoGazeShiftX = 0.14f
private const val EchoGazeShiftY = 0.12f
