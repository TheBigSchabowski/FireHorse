package repertoire.desktop

import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.useResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import repertoire.core.DirectoryStore
import repertoire.core.FilePicker
import repertoire.core.Store
import repertoire.core.UserDirs
import repertoire.ui.App
import repertoire.ui.AppViewModel
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

fun main() = application {
    val viewModel = remember {
        val dir = System.getProperty("repertoire.dataDir")?.let(::File)
            ?: File(UserDirs.documents(), "EroeffnungsPuzzle")
        AppViewModel(Store(DirectoryStore(dir)), DialogFilePicker)
    }
    Window(
        onCloseRequest = ::exitApplication,
        title = "FireHorse",
        icon = remember(::windowIcon),
        // -Dwindow.size=393x852 opens at phone size, to check the narrow layout that Android uses.
        state = System.getProperty("window.size")?.split("x")?.map { it.toInt().dp }
            ?.let { (w, h) -> rememberWindowState(width = w, height = h) }
            ?: rememberWindowState(width = 1200.dp, height = 820.dp),
        // Ctrl+B and the key after it, before a focused text field can take them.
        onPreviewKeyEvent = viewModel.shortcutKeys::handle,
        onKeyEvent = { event ->
            val session = viewModel.session
            val command = event.isMetaPressed || event.isCtrlPressed
            when {
                event.type != KeyEventType.KeyDown -> false
                // The colors window: Escape closes it, nothing underneath is answered blindly.
                viewModel.colorsOpen -> (event.key == Key.Escape).also { if (it) viewModel.colorsOpen = false }
                // Escape steps back out of the preferences; the screens underneath stay out of reach.
                viewModel.prefs != null -> (event.key == Key.Escape).also { if (it) viewModel.prefsBack() }
                // Cmd/Ctrl+T opens the theory field while entering a variant.
                command && event.key == Key.T && viewModel.editor != null -> viewModel.toggleTheory().let { true }
                // While drawing, single keys have meanings of their own (1–6, o, u, k, m, Esc) …
                viewModel.shortcutKeys.handleDrawing(event) -> true
                // … and ← → switch the pen, ↑ ↓ circle or field.
                viewModel.penActive && event.key == Key.DirectionLeft -> viewModel.penStep(-1).let { true }
                viewModel.penActive && event.key == Key.DirectionRight -> viewModel.penStep(1).let { true }
                viewModel.penActive && (event.key == Key.DirectionUp || event.key == Key.DirectionDown) -> viewModel.penToggleField().let { true }
                // Arrow keys step through the moves of the variant being edited. A focused text
                // field gets them first, so typing in "Theorie" is not affected.
                viewModel.editor != null && event.key == Key.DirectionLeft -> viewModel.editorStepBack().let { true }
                viewModel.editor != null && event.key == Key.DirectionRight -> viewModel.editorStepForward().let { true }
                // A puzzle underneath the editor or a dialog must not be answered blindly.
                viewModel.editor != null || viewModel.paste != null || viewModel.showShortcuts -> false
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

/** The title bar and task bar icon on Linux and Windows; the Mac takes the one of the app bundle. */
private fun windowIcon(): Painter? =
    if (System.getProperty("os.name").startsWith("Mac")) null else useResource("icon.png") { BitmapPainter(loadImageBitmap(it)) }

/** The system's save / open dialogs. */
private object DialogFilePicker : FilePicker {
    private var lastDir: String = UserDirs.downloads().path

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
