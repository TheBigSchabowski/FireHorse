package repertoire.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import repertoire.core.DirectoryStore
import repertoire.core.Store
import repertoire.ui.App
import repertoire.ui.AppViewModel

class MainActivity : ComponentActivity() {
    // Survives rotation only as long as the process does; the progress itself is saved after every answer.
    private val viewModel by lazy { AppViewModel(Store(DirectoryStore(filesDir))) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Back leaves a puzzle or the editor instead of closing the app.
            BackHandler(enabled = viewModel.session != null || viewModel.editor != null) {
                if (viewModel.session != null) viewModel.endSession() else viewModel.cancelEditor()
            }
            App(viewModel, Modifier.safeDrawingPadding())
        }
    }
}
