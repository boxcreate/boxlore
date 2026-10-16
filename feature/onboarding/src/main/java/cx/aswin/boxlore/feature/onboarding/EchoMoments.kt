package cx.aswin.boxlore.feature.onboarding

import cx.aswin.boxlore.core.designsystem.echo.EchoBeat
import cx.aswin.boxlore.core.designsystem.echo.EchoFace
import cx.aswin.boxlore.core.designsystem.echo.EchoGaze
import cx.aswin.boxlore.core.designsystem.echo.EchoIdle

/** The moments of the Echo conversation that Echo acts out. */
internal enum class EchoOnboardingPhase { Welcome, Question, Thinking, Preparing, Presenting, Ready, Rest }

/** What Echo plays for a moment: a manifest sequence of poses, then its eyes' idle once it settles. */
internal data class EchoMoment(val sequence: String, val idle: EchoIdle)

/**
 * Echo sits centred above its bubble, with the question and answers below, so most looks are down. Each
 * moment plays motion clips (see the `echo-character` manifest) and rests between them with live eyes.
 * - Welcome: waves, then waits, reading the question.
 * - Question: an open-hand asking gesture for each new question, then waits.
 * - Thinking: a thumbs up for the answer, then, while the next question loads, writes in a notebook and
 *   taps its chin in turn, pondering between.
 * - Preparing: shuffles through podcast cards and searches with a magnifying glass while suggestions build.
 * - Presenting (each show card): a small happy hop.
 * - Ready: fans its cards out to the listener.
 * - Rest (errors): waits.
 */
internal fun echoOnboardingMoment(phase: EchoOnboardingPhase): EchoMoment = when (phase) {
    EchoOnboardingPhase.Welcome -> EchoMoment("onboarding-welcome", EchoQuestionIdle)
    EchoOnboardingPhase.Question -> EchoMoment("onboarding-question", EchoQuestionIdle)
    EchoOnboardingPhase.Thinking -> EchoMoment("onboarding-thinking", EchoPonderingIdle)
    EchoOnboardingPhase.Preparing -> EchoMoment("onboarding-preparing", EchoSortingIdle)
    EchoOnboardingPhase.Presenting -> EchoMoment("onboarding-presenting", EchoPresentingIdle)
    EchoOnboardingPhase.Ready -> EchoMoment("onboarding-ready", EchoPresentingIdle)
    EchoOnboardingPhase.Rest -> EchoMoment("onboarding-rest", EchoQuestionIdle.copy(intro = emptyList()))
}

private val AtListener = EchoGaze(0f, 0f)

/** Reads the question below, then watches the listener, looks over the answers, smiles, glances aside. */
internal val EchoQuestionIdle = EchoIdle(
    intro = listOf(
        EchoBeat(EchoGaze(-0.5f, 0.6f), 450, blinks = 1),
        EchoBeat(EchoGaze(0f, 0.6f), 450),
        EchoBeat(EchoGaze(0.5f, 0.6f), 700),
    ),
    loop = listOf(
        EchoBeat(AtListener, 2400, blinks = 1),
        EchoBeat(EchoGaze(-0.4f, 1f), 1200, blinks = 1),
        EchoBeat(EchoGaze(0.4f, 1f), 1000),
        EchoBeat(AtListener, 1100, EchoFace.Happy),
        EchoBeat(AtListener, 2000),
        EchoBeat(EchoGaze(0.8f, 0.1f), 1300, EchoFace.Curious, blinks = 1),
        EchoBeat(AtListener, 2600, blinks = 2),
    ),
)

/** Ponders between moves: looks up and to the side as if thinking, curious now and then, back to the listener. */
internal val EchoPonderingIdle = EchoIdle(
    intro = emptyList(),
    loop = listOf(
        EchoBeat(EchoGaze(-0.6f, -0.7f), 700, blinks = 1),
        EchoBeat(EchoGaze(-0.2f, -0.8f), 600, EchoFace.Curious),
        EchoBeat(EchoGaze(0.5f, -0.6f), 700),
        EchoBeat(AtListener, 900, blinks = 1),
        EchoBeat(EchoGaze(0.3f, -0.8f), 800, EchoFace.Curious),
    ),
)

/** Looks over the cards in its hand and the box beside it, and looks up pleased. */
internal val EchoSortingIdle = EchoIdle(
    intro = emptyList(),
    loop = listOf(
        EchoBeat(EchoGaze(-0.6f, 0.6f), 900, blinks = 1),
        EchoBeat(EchoGaze(-0.3f, 0.6f), 600),
        EchoBeat(EchoGaze(0.6f, 0.7f), 800),
        EchoBeat(EchoGaze(-0.6f, 0.6f), 900),
        EchoBeat(AtListener, 1000, EchoFace.Happy, blinks = 1),
    ),
)

/** Holds the cards out to the listener, smiling, with a glance down at them. */
internal val EchoPresentingIdle = EchoIdle(
    intro = emptyList(),
    loop = listOf(
        EchoBeat(AtListener, 1800, EchoFace.Happy),
        EchoBeat(EchoGaze(0f, 0.8f), 700, blinks = 1),
        EchoBeat(AtListener, 2200, EchoFace.Happy, blinks = 1),
        EchoBeat(AtListener, 1500),
        EchoBeat(EchoGaze(-0.5f, 0.2f), 900, blinks = 1),
        EchoBeat(AtListener, 1600, EchoFace.Happy),
    ),
)

/** Every onboarding sequence, loaded ahead so Echo never waits to change pose. Last in the file: it reads the idles above. */
internal val EchoOnboardingSequences = EchoOnboardingPhase.entries.map { echoOnboardingMoment(it).sequence }.distinct()
