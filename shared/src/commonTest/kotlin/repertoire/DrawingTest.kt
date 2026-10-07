package repertoire

import repertoire.core.Drawing
import repertoire.core.DrawingCheck
import repertoire.core.Exchange
import repertoire.core.FileStore
import repertoire.core.Library
import repertoire.core.Mark
import repertoire.core.Ink
import repertoire.core.PenColor
import repertoire.core.Move
import repertoire.core.Progress
import repertoire.core.Store
import repertoire.core.parseSquare
import repertoire.core.toggle
import repertoire.ui.AppViewModel
import repertoire.ui.Command
import repertoire.ui.DrawKey
import repertoire.ui.KeyTarget
import repertoire.ui.Pen
import repertoire.ui.drawKeyOf
import repertoire.ui.keyOf
import repertoire.ui.keysChanged
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class DrawingFiles : FileStore {
    val files = mutableMapOf<String, String>()
    override val location = "Speicher"
    override fun read(name: String): String? = files[name]
    override fun write(name: String, text: String) {
        files[name] = text
    }
}

private fun mark(text: String) = Mark.parse(text)

class DrawingTest {
    private val files = DrawingFiles()

    private fun app() = AppViewModel(Store(files))

    private fun AppViewModel.play(san: String) {
        for (token in san.split(" ").filter { it.isNotBlank() }) {
            val board = editor!!.position
            editorPlay(board.parseSan(token) ?: error("$token ist in ${board.fen} nicht möglich"))
        }
    }

    /** "e4 c5 Nf3 d6" with a drawing after 2. Nf3 and one at the end. */
    private fun AppViewModel.withDrawings(): String {
        newVariant()
        editor!!.name = "Offen"
        play("e4 c5 Nf3 d6")
        editorGoTo(3)
        editorNewDrawing()
        editorDrawingMark(mark("d2d4"))
        editorDrawingMark(mark("d5"))
        editorDrawingQuestion("  Plan für Weiß?  ")
        editorToEnd()
        editorNewDrawing()
        editorDrawingMark(mark("f1b5"))
        saveEditor()
        return library.variants.last().id
    }

    @Test
    fun marksAreSavedAsSquaresAndArrows() {
        assertEquals(Mark(parseSquare("d5"), parseSquare("d5")), mark("d5"))
        assertEquals("e2e4", mark("e2e4").toString())
        assertTrue(mark("d5").circle)
        assertEquals("Pfeil e2→e4 (Grün)", mark("e2e4").describe())
    }

    @Test
    fun pensAndFieldsAreSavedReadably() {
        val second = Mark(parseSquare("e2"), parseSquare("e4"), 1)
        assertEquals("2:e2e4", second.toString())
        assertEquals(second, mark("2:e2e4"))
        val field = Mark(parseSquare("d5"), parseSquare("d5"), 5, field = true)
        assertEquals("6:#d5", field.toString())
        assertEquals(field, mark("6:#d5"))
        assertEquals("#d5", mark("#d5").toString())
        assertFalse(field.circle)
        assertEquals("Pfeil e2→e4 (Rot)", second.describe())
        assertEquals("Feld d5 (Türkis)", field.describe())
        // What the pens are set to is what feedback names.
        assertEquals("Pfeil e2→e4 (Gelb)", second.describe(listOf(Ink.GREEN.pen, Ink.YELLOW.pen) + Ink.DEFAULT.drop(2)))
    }

    @Test
    fun drawingsFromVersion1_12_0StillRead() {
        assertEquals(mark("2:e2e4"), mark("2:e2e4"))
        assertEquals(mark("3:#d5"), mark("blue:#d5"))
        assertEquals(mark("4:c3"), mark("yellow:c3"))
        assertEquals(mark("e2e4"), mark("green:e2e4"))
    }

    @Test
    fun aPenThatDoesNotExistIsAnError() {
        kotlin.test.assertFailsWith<IllegalArgumentException> { mark("7:e2e4") }
        kotlin.test.assertFailsWith<IllegalArgumentException> { mark("purple:e2e4") }
    }

    @Test
    fun thePensColorsAreSetInTheConfiguration() {
        val vm = app()
        assertEquals(Ink.DEFAULT, vm.library.settings.penColors)
        vm.setPenColor(4, Ink.PINK.pen)
        assertEquals(Ink.PINK.pen, vm.library.settings.penColors[4])
        assertEquals(Ink.PINK.pen, app().library.settings.penColors[4])
        vm.resetPens()
        assertEquals(Ink.DEFAULT, vm.library.settings.penColors)
    }

    @Test
    fun anyColorCanBeTypedInAsACodeWithAName() {
        val vm = app()
        assertTrue(vm.setPenHex(2, "#7FFFD4", "Aquamarin"))
        val aqua = PenColor(0x7FFFD4, "Aquamarin")
        assertEquals(aqua, vm.library.settings.penColors[2])
        assertEquals("#7FFFD4 Aquamarin", vm.library.settings.pens[2])
        // Survives a restart, and names it in the feedback.
        assertEquals(aqua, app().library.settings.penColors[2])
        assertEquals("Pfeil e2→e4 (Aquamarin)", mark("3:e2e4").describe(vm.library.settings.penColors))
        // Without a name it is called by its code; small letters and three digits work too.
        assertTrue(vm.setPenHex(3, "ff1493", ""))
        assertEquals(PenColor(0xFF1493, "#FF1493"), vm.library.settings.penColors[3])
        assertEquals(PenColor(0x77FFDD, "#77FFDD"), PenColor.parse("#7FD"))
        // Not a color: nothing changes.
        assertFalse(vm.setPenHex(3, "#GGGGGG", "Quatsch"))
        assertFalse(vm.setPenHex(3, "Aquamarin", ""))
        assertEquals(PenColor(0xFF1493, "#FF1493"), vm.library.settings.penColors[3])
    }

    @Test
    fun anInkTypedInAsACodeIsStillThatInk() {
        assertEquals(Ink.RED.pen, PenColor.parse("#882020"))
        assertEquals("RED", Ink.RED.pen.saved)
        // Settings of 1.13.0 named the inks only.
        assertEquals(listOf(Ink.PINK.pen) + Ink.DEFAULT.drop(1), Ink.pens(listOf("PINK")))
        assertEquals(Ink.DEFAULT, Ink.pens(listOf("UNSINN", "")))
    }

    @Test
    fun singleKeysWorkOnlyWhileDrawing() {
        val vm = app()
        val id = vm.withDrawings()
        vm.editVariant(vm.library.variants.first { it.id == id })
        // Not drawing: the keys mean nothing.
        vm.runDrawKey(DrawKey.SHAPE)
        vm.runDrawKey(DrawKey.PEN_3)
        assertEquals(Pen(), vm.pen.value)

        vm.run(Command.DRAW)
        vm.runDrawKey(DrawKey.PEN_3)
        assertEquals(2, vm.pen.value.color)
        vm.runDrawKey(DrawKey.SHAPE)
        assertTrue(vm.pen.value.field)
        vm.runDrawKey(DrawKey.COLORS)
        assertTrue(vm.colorsOpen)
        vm.colorsOpen = false
        vm.runDrawKey(DrawKey.DONE)
        assertFalse(vm.editor!!.drawMode.value)

        // A saved drawing: wiped with u, finished with m.
        vm.run(Command.DRAW_KEPT)
        vm.runDrawKey(DrawKey.CLEAR)
        assertEquals(emptyList(), vm.editor!!.editedDrawing!!.marks)
        vm.editorDrawingMark(mark("e4"))
        vm.runDrawKey(DrawKey.DONE)
        assertNull(vm.editor!!.drawingAt)
    }

    @Test
    fun theDrawingKeysCanBeChangedAndSwapLikeTheOthers() {
        val vm = app()
        vm.setKey(KeyTarget.Draw(DrawKey.SHAPE), 'f')
        assertEquals('f', vm.library.settings.drawKeyOf(DrawKey.SHAPE))
        // Taken already: the two swap.
        vm.setKey(KeyTarget.Draw(DrawKey.CLEAR), 'f')
        assertEquals('f', vm.library.settings.drawKeyOf(DrawKey.CLEAR))
        assertEquals('x', vm.library.settings.drawKeyOf(DrawKey.SHAPE))
        // Ctrl+B keys are a different set: k stays with the colors there.
        assertEquals('k', vm.library.settings.keyOf(Command.COLORS))
        assertTrue(vm.library.settings.keysChanged)
        vm.resetKeys()
        assertEquals('o', vm.library.settings.drawKeyOf(DrawKey.SHAPE))
        assertFalse(vm.library.settings.keysChanged)
    }

    @Test
    fun ctrlBKOpensAndClosesTheColors() {
        val vm = app()
        vm.run(Command.COLORS)
        assertTrue(vm.colorsOpen)
        vm.run(Command.COLORS)
        assertFalse(vm.colorsOpen)
    }

    @Test
    fun arrowKeysSwitchThePenOnlyWhileDrawing() {
        val vm = app()
        val id = vm.withDrawings()
        vm.editVariant(vm.library.variants.first { it.id == id })
        assertFalse(vm.penActive)
        vm.run(Command.DRAW)
        assertTrue(vm.penActive)
        vm.penStep(-1)
        assertEquals(5, vm.pen.value.color)
        vm.penStep(1)
        vm.penStep(1)
        assertEquals(1, vm.pen.value.color)
        vm.penToggleField()
        assertTrue(vm.pen.value.field)
        vm.run(Command.DRAW)
        vm.run(Command.DRAW_KEPT)
        assertTrue(vm.penActive)
    }

    @Test
    fun theSameShapeInAnotherColorTakesTheNewColor() {
        val marks = listOf(mark("e2e4"), mark("d5"))
        assertEquals(listOf(mark("2:e2e4"), mark("d5")), marks.toggle(mark("2:e2e4")))
        assertEquals(listOf(mark("d5")), marks.toggle(mark("e2e4")))
        // A circle and a field on the same square are two things.
        assertEquals(listOf(mark("e2e4"), mark("d5"), mark("#d5")), marks.toggle(mark("#d5")))
    }

    @Test
    fun theColorCountsInTheCheck() {
        val saved = Drawing(0, listOf(mark("2:e2e4"), mark("#d5")))
        assertTrue(DrawingCheck.of(saved, listOf(mark("#d5"), mark("2:e2e4"))).correct)
        assertFalse(DrawingCheck.of(saved, listOf(mark("#d5"), mark("e2e4"))).correct)
        assertFalse(DrawingCheck.of(saved, listOf(mark("d5"), mark("2:e2e4"))).correct)
    }

    @Test
    fun ctrlBNDrawsIntoTheDrawingOfThisPosition() {
        val vm = app()
        val id = vm.withDrawings()
        vm.editVariant(vm.library.variants.first { it.id == id }, fromStart = true)
        // No drawing at the start: a new one.
        vm.run(Command.DRAW_KEPT)
        assertEquals(0, vm.editor!!.editedDrawing!!.ply)
        vm.run(Command.DRAW_KEPT)
        assertNull(vm.editor!!.drawingAt)
        assertEquals(2, vm.editor!!.drawings.size)
        // At a position with a drawing: that one.
        vm.editorGoTo(3)
        vm.run(Command.DRAW_KEPT)
        assertEquals("Plan für Weiß?", vm.editor!!.editedDrawing!!.question)
        // Not without the editor.
        vm.cancelEditor()
        vm.run(Command.DRAW_KEPT)
        assertTrue(vm.flash!!.contains("Editor"))
    }

    @Test
    fun ctrlBUWipesWhatIsBeingDrawnButNotWhatIsOnlyShown() {
        val vm = app()
        val id = vm.withDrawings()
        vm.editVariant(vm.library.variants.first { it.id == id })
        val clears = vm.boardClears
        // Only shown: the scribbles go, the saved drawing stays.
        vm.run(Command.CLEAR)
        assertEquals(clears + 1, vm.boardClears)
        assertEquals(2, vm.editor!!.drawings.size)
        // Being drawn into: wiped.
        vm.run(Command.DRAW_KEPT)
        vm.run(Command.CLEAR)
        assertEquals(emptyList(), vm.editor!!.editedDrawing!!.marks)
        // Drawn from memory in a quiz: wiped as well.
        vm.cancelEditor()
        vm.practice(id)
        vm.solve()
        vm.drawingMark(mark("d5"))
        vm.run(Command.CLEAR)
        assertEquals(emptyList(), vm.session!!.drawingInput)
    }

    @Test
    fun anExportWithoutDrawingsStillImports() {
        val old = """{"format": "eroeffnungs-puzzle", "version": 1, "variants": [{"name": "Offen", "moves": "1. e4 c5"}]}"""
        assertEquals(emptyList(), Exchange.import(old, Library(), Progress()).library.variants.single().drawings)
    }

    @Test
    fun drawingsAreSavedWithTheVariantAndSurviveARestart() {
        val vm = app()
        vm.withDrawings()
        val expected = listOf(
            Drawing(3, listOf(mark("d2d4"), mark("d5")), "Plan für Weiß?"),
            Drawing(4, listOf(mark("f1b5"))),
        )
        assertEquals(expected, vm.library.variants.single().drawings)
        assertEquals(expected, app().library.variants.single().drawings)
    }

    @Test
    fun theSameMarkTwiceTakesItAwayAndAnEmptyDrawingIsNotKept() {
        val vm = app()
        vm.newVariant()
        vm.play("e4 c5")
        vm.editorNewDrawing()
        vm.editorDrawingMark(mark("d4"))
        vm.editorDrawingMark(mark("d4"))
        assertEquals(emptyList(), vm.editor!!.editedDrawing!!.marks)
        vm.editorFinishDrawing()
        assertEquals(emptyList(), vm.editor!!.drawings)
        assertNull(vm.editor!!.drawingAt)
    }

    @Test
    fun goingToAnotherMoveEndsTheDrawing() {
        val vm = app()
        vm.newVariant()
        vm.play("e4 c5")
        vm.editorNewDrawing()
        vm.editorDrawingMark(mark("d4"))
        vm.editorStepBack()
        assertNull(vm.editor!!.drawingAt)
        assertEquals(listOf(Drawing(2, listOf(mark("d4")))), vm.editor!!.drawings)
        assertEquals(emptyList(), vm.editor!!.drawingsHere)
        vm.editorStepForward()
        assertEquals(1, vm.editor!!.drawingsHere.size)
    }

    @Test
    fun aChangedLineDropsTheDrawingsBehindTheChange() {
        val vm = app()
        val id = vm.withDrawings()
        vm.editVariant(vm.library.variants.first { it.id == id })
        // Same moves again: nothing changes.
        vm.editorGoTo(0)
        vm.play("e4 c5 Nf3 d6")
        assertEquals(2, vm.editor!!.drawings.size)
        // Another 2nd move for Black: the drawing at the end no longer fits, the one before it does.
        vm.editorGoTo(3)
        vm.play("Nc6")
        assertEquals(listOf(3), vm.editor!!.drawings.map { it.ply })
        vm.editorUndo()
        vm.editorUndo()
        assertEquals(emptyList(), vm.editor!!.drawings)
    }

    @Test
    fun editingADrawingGoesToItsPosition() {
        val vm = app()
        val id = vm.withDrawings()
        vm.editVariant(vm.library.variants.first { it.id == id })
        assertEquals(4, vm.editor!!.cursor)
        vm.editorEditDrawing(0)
        assertEquals(3, vm.editor!!.cursor)
        assertEquals("Plan für Weiß?", vm.editor!!.editedDrawing!!.question)
        vm.editorDeleteDrawing()
        vm.saveEditor()
        assertEquals(listOf(4), vm.library.variants.single().drawings.map { it.ply })
    }

    @Test
    fun aCheckComparesWithoutCaringAboutOrderButAboutTheArrowsDirection() {
        val saved = Drawing(0, listOf(mark("e2e4"), mark("d5")))
        assertTrue(DrawingCheck.of(saved, listOf(mark("d5"), mark("e2e4"))).correct)
        val check = DrawingCheck.of(saved, listOf(mark("e4e2"), mark("d5")))
        assertFalse(check.correct)
        assertEquals(setOf(mark("d5")), check.right)
        assertEquals(setOf(mark("e2e4")), check.missing)
        assertEquals(setOf(mark("e4e2")), check.extra)
    }

    private fun AppViewModel.solve() {
        val attempt = session!!.attempt!!
        for (uci in attempt.quiz.variant.moves) submitMove(Move.fromUci(uci))
    }

    @Test
    fun afterThePuzzleEveryDrawingIsAskedInTurn() {
        val vm = app()
        val id = vm.withDrawings()
        vm.practice(id)
        val s = vm.session!!
        // Not while the puzzle is being solved.
        assertNull(s.drawing)
        vm.solve()
        assertFalse(s.done)
        assertEquals(3, s.drawing!!.ply)
        assertEquals("Plan für Weiß?", s.drawing!!.question)

        vm.drawingMark(mark("d5"))
        vm.drawingMark(mark("d2d4"))
        vm.confirm()
        assertTrue(s.drawingCheck!!.correct)
        // Nothing more can be drawn into a checked drawing.
        vm.drawingMark(mark("a1"))
        assertEquals(2, s.drawingInput.size)

        vm.confirm()
        assertEquals(4, s.drawing!!.ply)
        assertEquals(emptyList(), s.drawingInput)
        vm.drawingMark(mark("f1c4"))
        vm.confirm()
        assertFalse(s.drawingCheck!!.correct)
        assertTrue(s.done)
        assertEquals(1, vm.mistakes(s))
        assertTrue("Pfeil f1→b5 (Grün)" in s.feedback!!.text)
    }

    @Test
    fun theGapsComeBeforeTheDrawings() {
        val vm = app()
        val id = vm.withDrawings()
        vm.editVariant(vm.library.variants.first { it.id == id })
        vm.editor!!.theory = androidx.compose.ui.text.input.TextFieldValue("Weiß spielt ((Bb5)).")
        vm.saveEditor()
        vm.practice(id)
        val s = vm.session!!
        vm.solve()
        assertNull(s.drawing)
        vm.setGapInput(0, "Bb5")
        vm.confirm()
        assertEquals(3, s.drawing!!.ply)
        assertTrue("Zeichnungen" in s.feedback!!.text)
    }

    @Test
    fun singleMovesAskNoDrawings() {
        val vm = app()
        val id = vm.withDrawings()
        vm.practiceMoves(id)
        val s = vm.session!!
        val attempt = s.attempt!!
        vm.submitMove(Move.fromUci(attempt.quiz.variant.moves[attempt.startPly]))
        assertNull(s.drawing)
        assertTrue(s.done)
    }

    @Test
    fun drawingsGoAlongInTheExportFile() {
        val vm = app()
        vm.withDrawings()
        val v = vm.library.variants.single()
        val text = Exchange.export(listOf(v), Progress())
        assertTrue("\"d2d4\"" in text)
        assertEquals(v.drawings, Exchange.import(text, Library(), Progress()).library.variants.single().drawings)
        // A drawing after a move the line does not have is left out.
        val broken = text.replace("\"ply\": 4", "\"ply\": 9")
        assertEquals(listOf(3), Exchange.import(broken, Library(), Progress()).library.variants.single().drawings.map { it.ply })
    }
}
