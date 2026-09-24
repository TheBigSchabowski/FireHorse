package repertoire

import repertoire.core.FileStore
import repertoire.core.Position
import repertoire.core.Store
import repertoire.ui.AppViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Library and progress in memory, so the editor can be driven like in the app. */
private class MemoryFiles : FileStore {
    val files = mutableMapOf<String, String>()
    override val location = "Speicher"
    override fun read(name: String): String? = files[name]
    override fun write(name: String, text: String) {
        files[name] = text
    }
}

private const val LINE = "e4 c5 Nf3 d6 d4 cxd4"

class EditorTest {
    private val files = MemoryFiles()

    private fun app() = AppViewModel(Store(files))

    /** Plays the moves on the board, exactly like a click on the board does. */
    private fun AppViewModel.play(san: String) {
        for (token in san.split(" ").filter { it.isNotBlank() }) {
            val board = editor!!.position
            editorPlay(board.parseSan(token) ?: error("$token ist in ${board.fen} nicht möglich"))
        }
    }

    private fun AppViewModel.add(name: String, san: String): String {
        newVariant()
        editor!!.opening = "Sizilianisch"
        editor!!.name = name
        play(san)
        saveEditor()
        return library.variants.last().id
    }

    private fun AppViewModel.san(): String = library.variants.last().numberedSan().joinToString(" ")

    @Test
    fun steppingThroughTheLineKeepsEveryMove() {
        val vm = app()
        val id = vm.add("Offen", LINE)
        vm.editVariant(vm.library.variants.first { it.id == id })
        val e = vm.editor!!
        assertEquals(6, e.cursor)

        vm.editorGoTo(2)
        assertEquals(2, e.shown.size)
        assertEquals(6, e.moves.size)
        assertEquals(Position.START.play(e.moves[0]).play(e.moves[1]).fen, e.position.fen)
        assertFalse(e.atEnd)

        vm.editorStepBack()
        vm.editorStepForward()
        vm.editorStepForward()
        assertEquals(3, e.cursor)
        vm.editorToEnd()
        assertTrue(e.atEnd)

        vm.saveEditor()
        assertEquals(6, vm.library.variants.first { it.id == id }.moves.size)
    }

    @Test
    fun goingThroughFromTheStartShowsTheStartPosition() {
        val vm = app()
        val id = vm.add("Offen", LINE)
        vm.editVariant(vm.library.variants.first { it.id == id }, fromStart = true)
        assertEquals(0, vm.editor!!.cursor)
        assertEquals(Position.START.fen, vm.editor!!.position.fen)
        // Walking forward through the stored line does not change it.
        repeat(6) { vm.editorStepForward() }
        assertEquals(6, vm.editor!!.moves.size)
    }

    @Test
    fun replayingTheStoredMoveOnlyWalksOn() {
        val vm = app()
        val id = vm.add("Offen", LINE)
        vm.editVariant(vm.library.variants.first { it.id == id })
        val e = vm.editor!!
        vm.editorGoTo(2)
        val before = e.moves
        vm.play("Nf3")
        assertEquals(before, e.moves)
        assertEquals(3, e.cursor)
    }

    @Test
    fun anotherMoveInTheMiddleReplacesTheRestOfTheLine() {
        val vm = app()
        val id = vm.add("Offen", LINE)
        vm.editVariant(vm.library.variants.first { it.id == id })
        vm.editorGoTo(2)
        vm.play("Bc4")
        assertEquals(3, vm.editor!!.moves.size)
        vm.saveEditor()
        assertEquals("1. e4 1... c5 2. Bc4", vm.san())
    }

    @Test
    fun theUpdatedPositionSurvivesARestart() {
        val vm = app()
        val id = vm.add("Offen", LINE)
        vm.editVariant(vm.library.variants.first { it.id == id })
        vm.editorGoTo(4)
        vm.editorTruncate()
        vm.play("Bb5+")
        vm.editor!!.name = "Klassisch"
        vm.saveEditor()

        // Same files, fresh app: the variant is updated, not duplicated.
        val restarted = app()
        assertEquals(1, restarted.library.variants.size)
        val saved = restarted.library.variants.single()
        assertEquals("Klassisch", saved.name)
        assertEquals("1. e4 1... c5 2. Nf3 2... d6 3. Bb5+", saved.numberedSan().joinToString(" "))
    }

    @Test
    fun undoAndClearWorkFromAnywhereInTheLine() {
        val vm = app()
        val id = vm.add("Offen", LINE)
        vm.editVariant(vm.library.variants.first { it.id == id })
        val e = vm.editor!!
        vm.editorUndo()
        assertEquals(5, e.moves.size)
        assertEquals(5, e.cursor)
        vm.editorGoTo(1)
        vm.editorTruncate()
        assertEquals(1, e.moves.size)
        vm.editorClear()
        assertEquals(0, e.moves.size)
        assertEquals(0, e.cursor)
    }

    @Test
    fun changedMovesAnnounceAndResetTheLearningState() {
        val vm = app()
        val id = vm.add("Offen", LINE)
        vm.startLearning()
        for (token in LINE.split(" ")) {
            vm.submitMove(vm.session!!.attempt!!.position.parseSan(token)!!)
        }
        vm.grade()
        vm.endSession()
        assertTrue(vm.progress.cards.containsKey(id))

        vm.editVariant(vm.library.variants.first { it.id == id })
        assertFalse(vm.editorRestartsProgress())
        vm.editorGoTo(2)
        vm.play("Bc4")
        assertTrue(vm.editorRestartsProgress())
        vm.saveEditor()
        assertNull(vm.progress.cards[id])
    }

    @Test
    fun onlyTheEndOfTheLineCountsAsADuplicate() {
        val vm = app()
        vm.add("Offen", LINE)
        vm.add("Kürzer", "e4 c5 Nf3")
        vm.editVariant(vm.library.variants.first())
        vm.editorGoTo(3)
        // The board stands in the other variant's position, but this line still ends elsewhere.
        assertNull(vm.editorDuplicate())
        vm.editorTruncate()
        assertEquals("Sizilianisch › Kürzer", vm.editorDuplicate())
    }
}
