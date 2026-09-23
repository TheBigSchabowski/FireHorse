package repertoire.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** The whole app, shared by the desktop window and the Android activity. */
@Composable
fun App(vm: AppViewModel, modifier: Modifier = Modifier) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            val session = vm.session
            val editor = vm.editor
            when {
                session != null -> TrainingScreen(vm, session, modifier)
                editor != null -> EditorScreen(vm, editor, modifier)
                else -> HomeScreen(vm, modifier)
            }
        }
    }
}
