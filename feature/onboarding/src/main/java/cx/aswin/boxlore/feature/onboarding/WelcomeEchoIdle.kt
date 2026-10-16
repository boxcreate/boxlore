package cx.aswin.boxlore.feature.onboarding

import cx.aswin.boxlore.core.designsystem.echo.EchoBeat
import cx.aswin.boxlore.core.designsystem.echo.EchoFace
import cx.aswin.boxlore.core.designsystem.echo.EchoGaze
import cx.aswin.boxlore.core.designsystem.echo.EchoIdle

private val WelcomeAtListener = EchoGaze(0f, 0f)
private val AtButton = EchoGaze(-0.3f, 0.7f)

/**
 * Welcome (Echo sits at the right end of the button). Starting as it rises, Echo looks down at the button
 * edge it is climbing over, then darts up to the listener with a blink and an eye smile. From then on it
 * reads the headline up and to its left, looks back, checks the button below, smiles, and now and then
 * glances off to the side, curious. Only the eyes move; the body stays still, and Echo is never still
 * for long.
 */
internal val EchoWelcomeIdle = EchoIdle(
    intro = listOf(
        EchoBeat(AtButton, 300),
        EchoBeat(WelcomeAtListener, 1000, EchoFace.Happy, blinks = 1),
        EchoBeat(WelcomeAtListener, 700),
    ),
    loop = listOf(
        EchoBeat(EchoGaze(-0.9f, -0.7f), 650, blinks = 1),
        EchoBeat(EchoGaze(-0.6f, -0.7f), 550),
        EchoBeat(EchoGaze(-0.3f, -0.7f), 900),
        EchoBeat(WelcomeAtListener, 2200, blinks = 1),
        EchoBeat(EchoGaze(-0.5f, 0.8f), 1700, blinks = 1),
        EchoBeat(EchoGaze(-0.2f, 0.8f), 1100),
        EchoBeat(WelcomeAtListener, 1300, EchoFace.Happy, blinks = 2),
        EchoBeat(WelcomeAtListener, 2400),
        EchoBeat(EchoGaze(-1f, 0.1f), 1500, EchoFace.Curious),
        EchoBeat(WelcomeAtListener, 2600, blinks = 1),
    ),
)
