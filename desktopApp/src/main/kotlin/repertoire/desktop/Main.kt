package repertoire.desktop

import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import repertoire.core.DirectoryStore
import repertoire.core.Store
import repertoire.ui.App
import repertoire.ui.AppViewModel
import java.io.File

fun main() = application {
    val viewModel = remember {
        val dir = System.getProperty("repertoire.dataDir")?.let(::File)
            ?: File(System.getProperty("user.home"), "Documents/EroeffnungsPuzzle")
        AppViewModel(Store(DirectoryStore(dir)))
    }
    Window(
        onCloseRequest = ::exitApplication,
        title = "Eröffnungs-Puzzle",
        // -Dwindow.size=393x852 opens at phone size, to check the narrow layout that Android uses.
        state = System.getProperty("window.size")?.split("x")?.map { it.toInt().dp }
            ?.let { (w, h) -> rememberWindowState(width = w, height = h) }
            ?: rememberWindowState(width = 1200.dp, height = 820.dp),
        // Enter / Space answers "Gut" (or "Weiter" after a mistake) once a puzzle is solved.
        onKeyEvent = { event ->
            val attempt = viewModel.session?.attempt
            if (event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.Spacebar) &&
                attempt?.finished == true
            ) {
                viewModel.grade()
                true
            } else {
                false
            }
        },
    ) {
        App(viewModel)
    }
}
