package cx.aswin.boxlore.tv.ui

import androidx.compose.ui.input.key.Key
import cx.aswin.boxlore.tv.ui.components.TextFieldKeys
import cx.aswin.boxlore.tv.ui.components.TopItemKeys
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RemoteKeysTest {
    @Test
    fun `with the keyboard closed, Back, Up and Down give the remote back to the page`() {
        // Before, the box kept the remote with no outline and these keys did nothing.
        listOf(Key.Back, Key.DirectionUp, Key.DirectionDown).forEach { assertEquals(TextFieldKeys.Action.Leave, TextFieldKeys.action(it)) }
    }

    @Test
    fun `OK opens the keyboard again and other keys edit the text`() {
        assertEquals(TextFieldKeys.Action.Reopen, TextFieldKeys.action(Key.DirectionCenter))
        assertEquals(TextFieldKeys.Action.Type, TextFieldKeys.action(Key.DirectionLeft))
        assertEquals(TextFieldKeys.Action.Type, TextFieldKeys.action(Key.A))
    }

    @Test
    fun `Up, Left and Right on a list's first item show it whole again, Down does not`() {
        // Up on a half-hidden top card, with nothing above it, left the card cut off.
        listOf(Key.DirectionUp, Key.DirectionLeft, Key.DirectionRight).forEach { assertTrue(TopItemKeys.reveals(it)) }
        assertFalse(TopItemKeys.reveals(Key.DirectionDown))
    }
}
