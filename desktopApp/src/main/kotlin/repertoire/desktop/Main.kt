package repertoire.desktop

import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import repertoire.core.DirectoryStore
import repertoire.core.FilePicker
import repertoire.core.Store
import repertoire.ui.App
import repertoire.ui.AppViewModel
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

fun main() = application {
    val viewModel = remember {
        val dir = System.getProperty("repertoire.dataDir")?.let(::File)
            ?: File(System.getProperty("user.home"), "Documents/EroeffnungsPuzzle")
        AppViewModel(Store(DirectoryStore(dir)), DialogFilePicker)
    }
    Window(
        onCloseRequest = ::exitApplication,
        title = "Eröffnungs-Puzzle",
        // -Dwindow.size=393x852 opens at phone size, to check the narrow layout that Android uses.
        state = System.getProperty("window.size")?.split("x")?.map { it.toInt().dp }
            ?.let { (w, h) -> rememberWindowState(width = w, height = h) }
            ?: rememberWindowState(width = 1200.dp, height = 820.dp),
        onKeyEvent = { event ->
            val session = viewModel.session
            val command = event.isMetaPressed || event.isCtrlPressed
            when {
                event.type != KeyEventType.KeyDown -> false
                // Cmd/Ctrl+T opens the theory field while entering a variant.
                command && event.key == Key.T && viewModel.editor != null -> viewModel.toggleTheory().let { true }
                // Enter checks the theory gaps, then Enter / Space answers "Gut" (or "Weiter" after a mistake).
                session?.attempt?.finished != true -> false
                event.key == Key.Enter -> viewModel.confirm().let { true }
                event.key == Key.Spacebar && session.done -> viewModel.grade().let { true }
                else -> false
            }
        },
    ) {
        App(viewModel)
    }
}

/** The native macOS save / open dialogs. */
private object DialogFilePicker : FilePicker {
    private var lastDir: String = File(System.getProperty("user.home"), "Downloads").path

    private fun choose(title: String, mode: Int, name: String? = null): File? {
        val dialog = FileDialog(null as Frame?, title, mode)
        dialog.directory = lastDir
        name?.let { dialog.file = it }
        if (mode == FileDialog.LOAD) dialog.setFilenameFilter { _, n -> n.endsWith(".json", ignoreCase = true) }
        dialog.isVisible = true
        val file = dialog.file ?: return null
        lastDir = dialog.directory
        return File(dialog.directory, file)
    }

    override fun save(suggestedName: String, text: String, onDone: (String?) -> Unit) {
        val file = choose("Exportieren", FileDialog.SAVE, suggestedName)
        file?.writeText(text)
        onDone(file?.path)
    }

    override fun open(onDone: (String?) -> Unit) {
        onDone(choose("Importieren", FileDialog.LOAD)?.readText())
    }
}
