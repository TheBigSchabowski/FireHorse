package repertoire

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import repertoire.core.DirectoryStore
import repertoire.core.FilePicker
import repertoire.core.GameSite
import repertoire.core.Store
import repertoire.ui.AppViewModel
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TreeLoadTest {
    private val pgn = """
        [Event "x"]
        [White "Ich"]
        [Black "Du"]
        [Result "1-0"]

        1. e4 e5 2. Nf3 1-0
    """.trimIndent()

    /** Loading a tree runs in the background on the app's main thread, as in the real window. */
    @Test
    fun aTreeIsLoadedInTheBackgroundAndKept() = runBlocking {
        val dir = Files.createTempDirectory("tree").toFile()
        val files = object : FilePicker {
            override fun save(suggestedName: String, text: String, onDone: (String?) -> Unit) = onDone(null)
            override fun open(onDone: (String?) -> Unit) = onDone(null)
            override fun openBytes(extensions: List<String>, onDone: (ByteArray?) -> Unit) = onDone(pgn.encodeToByteArray())
        }
        val vm = withContext(Dispatchers.Main) { AppViewModel(Store(DirectoryStore(dir)), files) }
        withContext(Dispatchers.Main) {
            vm.treeRequest = vm.treeRequest.copy(site = GameSite.PGN_FILE, player = "Ich")
            vm.loadTree()
        }
        val deadline = System.currentTimeMillis() + 10_000
        while (withContext(Dispatchers.Main) { vm.treeLoading != null } && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertNull(vm.treeError)
        assertEquals(1, vm.gameTree?.games?.size)
        assertEquals(1, Store(DirectoryStore(dir)).loadGameTree()?.games?.size, "kept for the next start")
    }
}
