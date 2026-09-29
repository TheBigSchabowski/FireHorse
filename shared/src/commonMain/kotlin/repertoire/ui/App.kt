package repertoire.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier

/**
 * The whole app, shared by the desktop window and the Android activity. [touch] shows the horse
 * button for the shortcuts, which the keyboard reaches with Ctrl+B.
 */
@Composable
fun App(vm: AppViewModel, modifier: Modifier = Modifier, touch: Boolean = false) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        CompositionLocalProvider(LocalTouch provides touch) {
            Surface(modifier = Modifier.fillMaxSize()) {
                Box(modifier.fillMaxSize()) {
                    val session = vm.session
                    val editor = vm.editor
                    vm.paste?.let { PasteDialog(vm, it) }
                    when {
                        // A variant started from inside a puzzle (Ctrl+B c) goes on top of it.
                        editor != null -> EditorScreen(vm, editor)
                        session != null -> TrainingScreen(vm, session)
                        else -> HomeScreen(vm)
                    }
                    ShortcutOverlay(vm)
                }
            }
        }
    }
}
