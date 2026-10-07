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
import repertoire.ui.Prefs
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

    private fun AppViewModel.drawKey(key: Key, char: Char, ctrl: Boolean = false) =
        shortcutKeys.handleDrawing(KeyEvent(key, KeyEventType.KeyDown, char.code, isCtrlPressed = ctrl))

    @Test
    fun aFreeLetterAfterCtrlBOpensTheKeysInTheConfiguration() {
        val vm = app()
        vm.ctrlB()
        assertEquals(listOf(true, true, true), vm.key(Key.Z, 'z'))
        assertEquals(Prefs.Config, vm.prefs)
        assertTrue(vm.showKeys)
        assertTrue(vm.flash!!.contains("z ist noch frei"))
        // Escape after Ctrl+B only cancels.
        vm.prefs = null
        vm.ctrlB()
        vm.key(Key.Escape, '\u001b')
        assertNull(vm.prefs)
    }

    @Test
    fun whileDrawingASingleKeyIsACommand() {
        val vm = app()
        vm.newVariant()
        // Not drawing: an "o" is just an "o".
        assertFalse(vm.drawKey(Key.O, 'o'))
        vm.run(Command.DRAW)
        assertTrue(vm.drawKey(Key.O, 'o'))
        assertTrue(vm.pen.value.field)
        assertTrue(vm.drawKey(Key.Three, '3'))
        assertEquals(2, vm.pen.value.color)
        // With Ctrl it is not the drawing key, and a key without a meaning is left alone.
        assertFalse(vm.drawKey(Key.O, 'o', ctrl = true))
        assertFalse(vm.drawKey(Key.Q, 'q'))
        // u ends drawing mode, as does Escape.
        assertTrue(vm.drawKey(Key.U, 'u'))
        assertFalse(vm.editor!!.drawMode.value)
        vm.run(Command.DRAW)
        assertTrue(vm.drawKey(Key.Escape, '\u001b'))
        assertFalse(vm.editor!!.drawMode.value)
        assertFalse(vm.drawKey(Key.O, 'o'))
    }

    @Test
    fun prefixThenLetterRunsTheCommandAndTypesNothing() {
        val vm = app()
        assertEquals(listOf(true, true, true), vm.ctrlB())
        assertTrue(vm.prefix)
        // All three events of the "x" are swallowed, so no text field gets the letter.
        assertEquals(listOf(true, true, true), vm.key(Key.X, 'x'))
        assertFalse(vm.prefix)
        assertEquals(Command.PASTE_PGN, vm.paste?.kind)
        // The next letter is typed normally again.
        assertEquals(listOf(false, false, false), vm.key(Key.E, 'e'))
    }

    @Test
    fun lettersWithoutPrefixAreLeftAlone() {
        val vm = app()
        assertEquals(listOf(false, false, false), vm.key(Key.X, 'x'))
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
        // x is free now.
        vm.ctrlB()
        vm.key(Key.X, 'x')
        assertNull(vm.paste)
        assertNotNull(vm.flash)
    }

    @Test
    fun aTakenKeyIsSwapped() {
        val vm = app()
        vm.setKey(KeyTarget.Of(Command.PASTE_PGN), 'f')
        val settings = vm.library.settings
        assertEquals('f', settings.keyOf(Command.PASTE_PGN))
        assertEquals('x', settings.keyOf(Command.PASTE_FEN))
        // Back to its own key: nothing is stored as changed any more.
        vm.setKey(KeyTarget.Of(Command.PASTE_PGN), 'x')
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
        vm.key(Key.V, 'v')
        assertEquals("1. e4 c5 2. Nf3", copied.last())
        vm.ctrlB()
        vm.key(Key.Y, 'y')
        assertEquals("rnbqkbnr/pp1ppppp/8/2p5/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq - 1 2", copied.last())

        // Half-way through the line it is the position on the board that counts.
        vm.editor!!.cursor = 2
        vm.run(Command.COPY_PGN)
        assertEquals("1. e4 c5", copied.last())
        vm.run(Command.COPY_FEN)
        assertEquals("rnbqkbnr/pp1ppppp/8/2p5/4P3/8/PPPP1PPP/RNBQKBNR w KQkq c6 0 2", copied.last())
    }

    @Test
    fun ctrlHeldStillPicksTheCommand() {
        val vm = app()
        val copied = mutableListOf<String>()
        vm.copyToClipboard = { copied += it }
        vm.newVariant()
        vm.editorPlay(repertoire.core.Move.fromUci("e2e4"))
        vm.ctrlB()
        vm.key(Key.V, '\u0016', ctrl = true)
        assertEquals("1. e4", copied.last())
        assertFalse(vm.prefix)
    }

    @Test
    fun ctrlBBOpensTheSettingsAtTheKeys() {
        val vm = app()
        vm.ctrlB()
        vm.key(Key.B, 'b')
        assertEquals(Prefs.Config, vm.prefs)
        assertTrue(vm.showKeys)
        // Again, with Ctrl still held: closes them.
        vm.ctrlB()
        assertTrue(vm.prefix)
        vm.ctrlB()
        assertFalse(vm.prefix)
        assertNull(vm.prefs)
        // From the list or a phone they open at the top.
        vm.showKeys = false
        vm.run(Command.SETTINGS)
        assertEquals(Prefs.Config, vm.prefs)
        assertFalse(vm.showKeys)
    }

    @Test
    fun shiftAHidesTheDrawingsAndShowsThemAgain() {
        val vm = app()
        val shiftA = KeyEvent(Key.A, KeyEventType.KeyDown, 'A'.code, isShiftPressed = true)
        assertTrue(vm.shortcutKeys.handleDirect(shiftA))
        assertFalse(vm.drawingsHidden.value, "ohne Brett nichts auszublenden")
        vm.newVariant()
        assertTrue(vm.shortcutKeys.handleDirect(shiftA))
        assertTrue(vm.drawingsHidden.value)
        assertTrue(vm.shortcutKeys.handleDirect(shiftA))
        assertFalse(vm.drawingsHidden.value)
        // A plain a, or Shift+A after Ctrl+B, is something else.
        assertFalse(vm.shortcutKeys.handleDirect(KeyEvent(Key.A, KeyEventType.KeyDown, 'a'.code)))
        vm.ctrlB()
        assertFalse(vm.shortcutKeys.handleDirect(shiftA))
        vm.key(Key.Escape, '\u001b')
        // While drawing everything stays in sight.
        vm.run(Command.DRAW)
        vm.shortcutKeys.handleDirect(shiftA)
        assertFalse(vm.drawingsHidden.value)
    }

    @Test
    fun shiftSOpensTheList() {
        val vm = app()
        assertTrue(vm.shortcutKeys.handleDirect(KeyEvent(Key.S, KeyEventType.KeyDown, 'S'.code, isShiftPressed = true)))
        assertTrue(vm.showShortcuts)
        assertFalse(vm.shortcutKeys.handleDirect(KeyEvent(Key.S, KeyEventType.KeyDown, 's'.code)))
    }

    private fun AppViewModel.addVariant(opening: String, vararg sans: String) {
        newVariant()
        editor!!.name = sans.joinToString(" ")
        editor!!.opening = opening
        var p = Position.START
        for (san in sans) {
            val move = p.parseSan(san)!!
            editorPlay(move)
            p = p.play(move)
        }
        saveEditor()
    }

    @Test
    fun ctrlNThenADigitLearnsThatOpening() {
        val vm = app()
        vm.addVariant("Sizilianisch", "e4", "c5")
        vm.addVariant("Französisch", "e4", "e6")
        val ctrlN = KeyEvent(Key.N, KeyEventType.KeyDown, 0x0e, isCtrlPressed = true)
        assertTrue(vm.shortcutKeys.handleDirect(ctrlN))
        assertTrue(vm.picking)
        // Alphabetical on the home screen: 1 Französisch, 2 Sizilianisch.
        assertEquals(listOf(true, true, true), vm.key(Key.Two, '2'))
        assertFalse(vm.picking)
        val session = assertNotNull(vm.session)
        assertEquals("Sizilianisch", session.attempt!!.quiz.variant.opening)

        // Any other key just ends it; a number without an opening says so.
        vm.shortcutKeys.handleDirect(ctrlN)
        vm.key(Key.E, 'e')
        assertFalse(vm.picking)
        vm.shortcutKeys.handleDirect(ctrlN)
        vm.key(Key.Five, '5')
        assertTrue(vm.flash!!.contains("5"))
    }

    @Test
    fun inATextFieldShiftLettersAreTyped() {
        val vm = app()
        val field = Any()
        vm.typingIn += field
        assertFalse(vm.shortcutKeys.handleDirect(KeyEvent(Key.S, KeyEventType.KeyDown, 'S'.code, isShiftPressed = true)))
        assertFalse(vm.shortcutKeys.handleDirect(KeyEvent(Key.N, KeyEventType.KeyDown, 0x0e, isCtrlPressed = true)))
        assertFalse(vm.showShortcuts)
        assertFalse(vm.picking)
        // Ctrl+B still works there.
        vm.ctrlB()
        vm.key(Key.Slash, '?')
        assertTrue(vm.showShortcuts)
        vm.typingIn -= field
        vm.showShortcuts = false
        assertTrue(vm.shortcutKeys.handleDirect(KeyEvent(Key.S, KeyEventType.KeyDown, 'S'.code, isShiftPressed = true)))
        assertTrue(vm.showShortcuts)
    }

    @Test
    fun ctrlBPCopiesTheWholeVariant() {
        val vm = app()
        val copied = mutableListOf<String>()
        vm.copyToClipboard = { copied += it }
        vm.addVariant("Sizilianisch", "e4", "c5", "Nf3")
        vm.practice(vm.library.variants.single().id)
        vm.ctrlB()
        vm.key(Key.P, 'p')
        assertTrue(copied.last().startsWith("[Event \"Sizilianisch: e4 c5 Nf3\"]"), copied.last())
        assertTrue(copied.last().endsWith("1. e4 c5 2. Nf3 *"), copied.last())
    }

    @Test
    fun eightTurnsTheBoardInTheEditorForGoodAndInAPuzzleForThisVariant() {
        val vm = app()
        val eight = KeyEvent(Key.Eight, KeyEventType.KeyDown, '8'.code)
        assertTrue(vm.shortcutKeys.handleDirect(eight))
        assertNotNull(vm.flash, "ohne Brett nichts zu drehen")

        // Two variants of one opening, so its playlist has a next one.
        vm.addVariant("Sizilianisch", "e4", "c5")
        vm.addVariant("Sizilianisch", "e4", "c5", "Nf3")
        vm.editVariant(vm.library.variants.first())
        vm.shortcutKeys.handleDirect(eight)
        assertTrue(vm.editorFlipped)
        // The side the variant is played from stays.
        assertTrue(vm.editor!!.playWhite == vm.library.variants.first().playWhite)
        vm.cancelEditor()
        vm.editVariant(vm.library.variants.last())
        assertTrue(vm.editorFlipped, "im Editor bleibt es gedreht")
        vm.shortcutKeys.handleDirect(eight)
        assertFalse(vm.editorFlipped)
        vm.cancelEditor()

        // Typing an 8 into a text field turns nothing.
        vm.newVariant()
        val field = Any()
        vm.typingIn += field
        assertFalse(vm.shortcutKeys.handleDirect(eight))
        assertFalse(vm.editorFlipped)
        vm.typingIn -= field
        vm.cancelEditor()

        vm.learnOpeningAt(1)
        val session = vm.session!!
        val first = session.attempt!!.quiz.variant.id
        vm.shortcutKeys.handleDirect(eight)
        assertTrue(session.flipped)
        vm.restartCurrent()
        assertTrue(session.flipped, "dieselbe Variante bleibt gedreht")
        for (move in session.attempt!!.quiz.variant.moves) vm.submitMove(repertoire.core.Move.fromUci(move))
        vm.confirm()
        assertTrue(session.attempt!!.quiz.variant.id != first)
        assertFalse(session.flipped, "die nächste Variante kommt wieder richtig herum")
    }

    @Test
    fun copiedPgnReadsBackFromASetUpPosition() {
        val fen = "rnbqkbnr/pp1ppppp/8/2p5/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2"
        val line = repertoire.core.Pgn.parse(repertoire.core.Pgn.export(fen, listOf("2. Nf3", "2... d6")))
        assertEquals(fen, line.startFen)
        assertEquals(listOf("g1f3", "d7d6"), line.moves)
    }
}
