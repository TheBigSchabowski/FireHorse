package repertoire

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import repertoire.core.EngineEntry
import repertoire.core.FileStore
import repertoire.core.Library
import repertoire.core.Settings
import repertoire.core.Move
import repertoire.core.Store
import repertoire.ui.AppViewModel
import repertoire.ui.ENGINE_DEPTHS
import repertoire.ui.EngineOpponent
import repertoire.ui.stepIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class OpponentMemory : FileStore {
    val files = mutableMapOf<String, String>()
    override val location = "Speicher"
    override fun read(name: String): String? = files[name]
    override fun write(name: String, text: String) {
        files[name] = text
    }
}

@OptIn(InternalComposeUiApi::class)
class OpponentTest {
    private fun editorWith(vararg moves: String): AppViewModel {
        val vm = AppViewModel(Store(OpponentMemory()))
        vm.newVariant()
        for (m in moves) vm.editorPlay(Move.fromUci(m))
        return vm
    }

    @Test
    fun ctrlBShiftROpensTheDialogInTheEditor() {
        val vm = editorWith("e2e4")
        vm.shortcutKeys.handle(KeyEvent(Key.B, KeyEventType.KeyDown, 2, isCtrlPressed = true))
        vm.shortcutKeys.handle(KeyEvent(Key.R, KeyEventType.KeyDown, 'R'.code, isShiftPressed = true))
        assertTrue(vm.opponentDialog)
        assertFalse(vm.engineOpen)
    }

    @Test
    fun withoutAnEditorItOnlySaysWhere() {
        val vm = AppViewModel(Store(OpponentMemory()))
        vm.openOpponent()
        assertFalse(vm.opponentDialog)
        assertNotNull(vm.flash)
    }

    @Test
    fun theEngineMovesOnlyForItsSideAtTheEndOfTheLine() {
        val vm = editorWith("e2e4", "c7c5")
        vm.setOpponent(EngineOpponent(white = true, depth = 12))
        assertTrue(vm.opponentToMove)
        vm.setOpponent(EngineOpponent(white = false, depth = 12))
        assertFalse(vm.opponentToMove)
        // Stepping back to a position where Black is to move does not wake it.
        vm.editorStepBack()
        assertFalse(vm.opponentToMove)
    }

    @Test
    fun itsMovePlaysOnlyIfTheBoardIsStillThere() {
        val vm = editorWith("e2e4", "c7c5")
        vm.setOpponent(EngineOpponent(white = true, depth = 12))
        val fen = vm.editor!!.position.fen
        vm.opponentPlay(fen, "g1f3")
        assertEquals(3, vm.editor!!.moves.size)
        assertFalse(vm.opponentToMove)
        // An answer for a position that is gone is dropped.
        vm.editorPlay(Move.fromUci("d7d6"))
        vm.opponentPlay(fen, "d2d4")
        assertEquals(listOf("e2e4", "c7c5", "g1f3", "d7d6"), vm.editor!!.moves.map { it.uci })
    }

    @Test
    fun theDepthIsKeptAndOffTurnsItOff() {
        val vm = editorWith("e2e4")
        vm.setOpponent(EngineOpponent(white = false, depth = 22))
        assertEquals(22, vm.library.settings.opponentDepth)
        vm.setOpponent(null)
        assertNull(vm.editor!!.opponent)
        assertFalse(vm.opponentToMove)
    }

    @Test
    fun aTypedDepthStepsToItsNeighbours() {
        assertEquals(18, stepIn(ENGINE_DEPTHS, 17, 1))
        assertEquals(16, stepIn(ENGINE_DEPTHS, 17, -1))
        assertEquals(50, stepIn(ENGINE_DEPTHS, 77, 1))
        assertEquals(50, stepIn(ENGINE_DEPTHS, 77, -1))
        assertEquals(0, stepIn(ENGINE_DEPTHS, 0, -1))
    }

    @Test
    fun typedEngineValuesAreKeptInBounds() {
        val store = Store(OpponentMemory())
        store.saveLibrary(Library(settings = Settings(engines = listOf(EngineEntry("Test", "/x")))))
        val vm = AppViewModel(store)
        vm.setEngineDepth("/x", 33)
        vm.setEngineHash("/x", 3000)
        vm.setEngineHash("/x", 5)
        val e = vm.library.settings.engines.single()
        assertEquals(33, e.depth)
        assertEquals(16, e.hashMb)
    }
}
