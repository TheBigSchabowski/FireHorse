package repertoire.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import repertoire.core.Cloze
import repertoire.core.Position
import repertoire.core.compactMoves
import repertoire.core.numberedSan

@Composable
fun EditorScreen(vm: AppViewModel, e: EditorState, modifier: Modifier = Modifier) {
    val position = e.position
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Text(
            if (e.editingId == null) "Neue Variante" else "Variante bearbeiten",
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(12.dp))
        BoardWithPanel(
            board = { modifier ->
                ChessBoard(
                    position = position,
                    whiteBottom = e.playWhite,
                    lastMove = e.moves.lastOrNull(),
                    marks = emptyMap(),
                    interactive = true,
                    onMove = vm::editorPlay,
                    modifier = modifier,
                )
            },
            top = { Chalkboard(e.opening, e.name.ifBlank { "Name der Variante" }) },
        ) {
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                e.opening, { e.opening = it }, label = { Text("Eröffnung (z. B. Sizilianisch)") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                e.name, { e.name = it }, label = { Text("Variante (z. B. Scheveningen)") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Spiele auf dem Brett die Züge vom Start bis zur Stellung (beide Seiten).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            val numbered = e.moves.runningFold(Position.START) { p, m -> p.play(m) }
                .zip(e.moves) { before, move -> numberedSan(before, move) }
            Text(
                compactMoves(numbered).ifEmpty { "Noch keine Züge" },
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = vm::editorUndo, enabled = e.moves.isNotEmpty()) { Text("Zug zurück") }
                OutlinedButton(onClick = { e.moves = emptyList() }, enabled = e.moves.isNotEmpty()) { Text("Von vorn") }
            }
            Spacer(Modifier.height(12.dp))
            TheoryEditor(vm, e)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Ich spiele:", modifier = Modifier.padding(end = 8.dp))
                FilterChip(selected = e.playWhite, onClick = { e.playWhite = true }, label = { Text("Weiß") })
                Spacer(Modifier.width(6.dp))
                FilterChip(selected = !e.playWhite, onClick = { e.playWhite = false }, label = { Text("Schwarz") })
            }
            vm.editorDuplicate()?.let {
                Spacer(Modifier.height(12.dp))
                Text("Diese Stellung ist schon gespeichert als „$it“.", color = Color(0xFFFFB74D))
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::saveEditor, enabled = e.name.isNotBlank() && e.moves.isNotEmpty()) { Text("Speichern") }
                TextButton(onClick = vm::cancelEditor) { Text("Abbrechen") }
            }
        }
    }
}

/** Free text about the variant; "((…))" turns a part into a gap that training asks for. */
@Composable
private fun TheoryEditor(vm: AppViewModel, e: EditorState) {
    TextButton(onClick = vm::toggleTheory) {
        Text(if (e.showTheory) "Theorie ausblenden" else "✎ Theorie (Cmd/Strg+T)")
    }
    if (!e.showTheory) return
    val focus = remember { FocusRequester() }
    LaunchedEffect(e.focusTheory) { if (e.focusTheory > 0) focus.requestFocus() }
    OutlinedTextField(
        e.theory, { e.theory = it },
        label = { Text("Theorie") },
        placeholder = { Text("Wenn Weiß g4 spielt, dann ((Nfd7)).") },
        minLines = 4,
        modifier = Modifier.fillMaxWidth().focusRequester(focus).onPreviewKeyEvent {
            // Cmd/Ctrl+Shift+C as in Anki: make a gap.
            val gapKey = it.type == KeyEventType.KeyDown && it.key == Key.C && it.isShiftPressed &&
                (it.isMetaPressed || it.isCtrlPressed)
            if (gapKey) vm.theoryGap()
            gapKey
        },
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = vm::theoryGap, enabled = !e.theory.selection.collapsed || e.moves.isNotEmpty()) {
            Text(if (e.theory.selection.collapsed) "Lücke: letzter Zug" else "Auswahl → Lücke")
        }
        val gaps = Cloze.gaps(e.theory.text).size
        Text(
            if (gaps == 0) "Keine Lücken – wird nach dem Lösen nur angezeigt." else "$gaps Lücke(n) zum Ausfüllen",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Text(
        "((Antwort)) wird abgefragt, ((Nb6|Sb6)) erlaubt beides, ((Nb6::Springer)) zeigt einen Tipp. " +
            "Cmd/Strg+Shift+C macht die Auswahl zur Lücke.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
