package repertoire

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import repertoire.core.FileStore
import repertoire.core.Position
import repertoire.core.Store
import repertoire.ui.AppViewModel
import repertoire.ui.Command
import repertoire.ui.KeyTarget
import repertoire.ui.keyOf
import repertoire.ui.keysChanged
import repertoire.ui.prefixLabel
import repertoire.ui.formatSeconds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private class Memory : FileStore {
    val files = mutableMapOf<String, String>()
    override val location = "Speicher"
    override fun read(name: String): String? = files[name]
    override fun write(name: String, text: String) {
        files[name] = text
    }
}

// Building key events by hand needs Compose's internal constructor.
@OptIn(InternalComposeUiApi::class)
class ShortcutsTest {
    private fun app() = AppViewModel(Store(Memory()))

    /**
     * Press, type and release, as the window delivers a key: a typed character arrives as
     * [Key.Unknown] with [KeyEventType.Unknown]. Returns what the handler consumed.
     */
    private fun AppViewModel.key(key: Key, char: Char, ctrl: Boolean = false): List<Boolean> = listOf(
        KeyEvent(key, KeyEventType.KeyDown, char.code, isCtrlPressed = ctrl),
        KeyEvent(Key.Unknown, KeyEventType.Unknown, char.code, isCtrlPressed = ctrl),
        KeyEvent(key, KeyEventType.KeyUp, char.code, isCtrlPressed = ctrl),
    ).map(shortcutKeys::handle)

    private fun AppViewModel.ctrlB() = key(Key.B, '\u0002', ctrl = true)

    @Test
    fun prefixThenLetterRunsTheCommandAndTypesNothing() {
        val vm = app()
        assertEquals(listOf(true, true, true), vm.ctrlB())
        assertTrue(vm.prefix)
        // All three events of the "p" are swallowed, so no text field gets the letter.
        assertEquals(listOf(true, true, true), vm.key(Key.P, 'p'))
        assertFalse(vm.prefix)
        assertEquals(Command.PASTE_PGN, vm.paste?.kind)
        // The next letter is typed normally again.
        assertEquals(listOf(false, false, false), vm.key(Key.E, 'e'))
    }

    @Test
    fun lettersWithoutPrefixAreLeftAlone() {
        val vm = app()
        assertEquals(listOf(false, false, false), vm.key(Key.P, 'p'))
        assertNull(vm.paste)
    }

    @Test
    fun lettingGoOfCtrlFirstIsNotSwallowed() {
        val vm = app()
        assertTrue(vm.shortcutKeys.handle(KeyEvent(Key.B, KeyEventType.KeyDown, 2, isCtrlPressed = true)))
        assertFalse(vm.shortcutKeys.handle(KeyEvent(Key.CtrlLeft, KeyEventType.KeyUp)))
        assertTrue(vm.shortcutKeys.handle(KeyEvent(Key.B, KeyEventType.KeyUp, 'b'.code)))
        assertTrue(vm.prefix)
    }

    @Test
    fun questionMarkOpensTheListOnAnyLayout() {
        val vm = app()
        vm.ctrlB()
        // Shift on its own keeps the prefix waiting, as "?" needs it.
        assertFalse(vm.shortcutKeys.handle(KeyEvent(Key.ShiftLeft, KeyEventType.KeyDown, isShiftPressed = true)))
        assertTrue(vm.prefix)
        // German layout: "?" is Shift+ß, where a US keyboard has the minus key.
        vm.key(Key.Minus, '?')
        assertTrue(vm.showShortcuts)
    }

    @Test
    fun escapeCancelsThePrefix() {
        val vm = app()
        vm.ctrlB()
        vm.key(Key.Escape, '\u001b')
        assertFalse(vm.prefix)
        assertNull(vm.paste)
        assertFalse(vm.showShortcuts)
    }

    @Test
    fun newVariantFromAPuzzleReturnsToThePuzzle() {
        val vm = app()
        vm.newVariant()
        vm.editor!!.name = "Französisch"
        var p = Position.START
        for (san in listOf("e4", "e6")) {
            val move = p.parseSan(san)!!
            vm.editorPlay(move)
            p = p.play(move)
        }
        vm.saveEditor()
        vm.practice(vm.library.variants.single().id)
        val session = vm.session!!

        vm.ctrlB()
        vm.key(Key.C, 'c')
        assertNotNull(vm.editor)
        assertSame(session, vm.session)
        assertTrue(vm.editor!!.note!!.contains("Puzzle wartet"))

        vm.cancelEditor()
        assertSame(session, vm.session)
    }

    @Test
    fun anEditorInUseIsNotThrownAway() {
        val vm = app()
        vm.newVariant()
        vm.editor!!.name = "Halb fertig"
        val editor = vm.editor
        vm.run(Command.NEW_VARIANT)
        vm.run(Command.PASTE_FEN)
        assertSame(editor, vm.editor)
        assertNull(vm.paste)
        assertNotNull(vm.flash)
    }

    @Test
    fun drawingNeedsABoard() {
        val vm = app()
        vm.run(Command.DRAW)
        assertFalse(vm.drawMode)
        assertNotNull(vm.flash)
        vm.newVariant()
        vm.run(Command.DRAW)
        assertTrue(vm.drawMode)
        vm.run(Command.DRAW)
        assertFalse(vm.drawMode)
    }

    @Test
    fun holdingTheHorsePicksByTime() {
        assertEquals(Command.SHORTCUTS, Command.forHold(300))
        assertEquals(Command.DRAW, Command.forHold(1200))
        assertEquals(Command.NEW_VARIANT, Command.forHold(2500))
        assertEquals(Command.PASTE_PGN, Command.forHold(3000))
        assertEquals(Command.PASTE_FEN, Command.forHold(4100))
        assertEquals(Command.PASTE_FEN, Command.forHold(12_000))
    }

    @Test
    fun theHoldStepCanBeSlower() {
        assertEquals(Command.SHORTCUTS, Command.forHold(1200, stepMs = 1500))
        assertEquals(Command.DRAW, Command.forHold(1500, stepMs = 1500))
        assertEquals(Command.NEW_VARIANT, Command.forHold(4000, stepMs = 1500))
        assertEquals(Command.PASTE_PGN, Command.forHold(4500, stepMs = 1500))
        assertEquals(Command.PASTE_FEN, Command.forHold(6000, stepMs = 1500))
    }

    @Test
    fun secondsAreWrittenTheGermanWay() {
        assertEquals("1 s", formatSeconds(1000))
        assertEquals("1,5 s", formatSeconds(1500))
        assertEquals("0,75 s", formatSeconds(750))
        assertEquals("12 s", formatSeconds(12_000))
    }

    @Test
    fun aCommandCanGetAnotherKey() {
        val vm = app()
        vm.keyCapture = KeyTarget.Of(Command.PASTE_PGN)
        // The key press that sets it is swallowed and runs nothing.
        assertEquals(listOf(true, true, true), vm.key(Key.G, 'g'))
        assertNull(vm.keyCapture)
        assertEquals('g', vm.library.settings.keyOf(Command.PASTE_PGN))
        vm.ctrlB()
        vm.key(Key.G, 'g')
        assertEquals(Command.PASTE_PGN, vm.paste?.kind)
        vm.cancelPaste()
        // p is free now.
        vm.ctrlB()
        vm.key(Key.P, 'p')
        assertNull(vm.paste)
        assertNotNull(vm.flash)
    }

    @Test
    fun aTakenKeyIsSwapped() {
        val vm = app()
        vm.setKey(KeyTarget.Of(Command.PASTE_PGN), 'f')
        val settings = vm.library.settings
        assertEquals('f', settings.keyOf(Command.PASTE_PGN))
        assertEquals('p', settings.keyOf(Command.PASTE_FEN))
        // Back to its own key: nothing is stored as changed any more.
        vm.setKey(KeyTarget.Of(Command.PASTE_PGN), 'p')
        assertEquals(emptyMap(), vm.library.settings.keys)
        assertFalse(vm.library.settings.keysChanged)
    }

    @Test
    fun thePrefixLetterCanBeChanged() {
        val vm = app()
        vm.keyCapture = KeyTarget.Prefix
        vm.key(Key.K, '\u000b', ctrl = true)
        assertEquals("Strg+K", vm.library.settings.prefixLabel)
        vm.ctrlB()
        assertFalse(vm.prefix)
        vm.key(Key.K, '\u000b', ctrl = true)
        assertTrue(vm.prefix)
        vm.key(Key.C, 'c')
        assertNotNull(vm.editor)
        vm.resetKeys()
        assertEquals("Strg+B", vm.library.settings.prefixLabel)
    }

    @Test
    fun onlyLettersDigitsAndQuestionMarkCanBeKeys() {
        val vm = app()
        vm.keyCapture = KeyTarget.Of(Command.DRAW)
        vm.key(Key.Tab, '\t')
        assertEquals(KeyTarget.Of(Command.DRAW), vm.keyCapture)
        assertNotNull(vm.flash)
        vm.key(Key.Escape, '\u001b')
        assertNull(vm.keyCapture)
        assertEquals('m', vm.library.settings.keyOf(Command.DRAW))
    }

    @Test
    fun theListStaysReachableWithQuestionMark() {
        val vm = app()
        vm.setKey(KeyTarget.Of(Command.SHORTCUTS), 'l')
        vm.ctrlB()
        vm.key(Key.Slash, '?')
        assertTrue(vm.showShortcuts)
    }

    @Test
    fun copyingTheBoardGivesFenAndPgnUpToTheCursor() {
        val vm = app()
        val copied = mutableListOf<String>()
        vm.copyToClipboard = { copied += it }
        vm.run(Command.COPY_FEN)
        assertTrue(copied.isEmpty(), "ohne Brett nichts kopieren")

        vm.run(Command.NEW_VARIANT)
        vm.run(Command.COPY_PGN)
        assertTrue(copied.isEmpty(), "ohne Zug kein PGN")
        for (uci in listOf("e2e4", "c7c5", "g1f3")) vm.editorPlay(repertoire.core.Move.fromUci(uci))
        vm.ctrlB()
        vm.key(Key.Three, '3')
        assertEquals("1. e4 c5 2. Nf3", copied.last())
        vm.ctrlB()
        vm.key(Key.Two, '2')
        assertEquals("rnbqkbnr/pp1ppppp/8/2p5/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq - 1 2", copied.last())

        // Half-way through the line it is the position on the board that counts.
        vm.editor!!.cursor = 2
        vm.run(Command.COPY_PGN)
        assertEquals("1. e4 c5", copied.last())
        vm.run(Command.COPY_FEN)
        assertEquals("rnbqkbnr/pp1ppppp/8/2p5/4P3/8/PPPP1PPP/RNBQKBNR w KQkq c6 0 2", copied.last())
    }

    @Test
    fun copiedPgnReadsBackFromASetUpPosition() {
        val fen = "rnbqkbnr/pp1ppppp/8/2p5/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2"
        val line = repertoire.core.Pgn.parse(repertoire.core.Pgn.export(fen, listOf("2. Nf3", "2... d6")))
        assertEquals(fen, line.startFen)
        assertEquals(listOf("g1f3", "d7d6"), line.moves)
    }
}
