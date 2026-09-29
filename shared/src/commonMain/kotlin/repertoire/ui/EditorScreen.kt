package repertoire.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import repertoire.core.Cloze
import repertoire.core.numberedSan

@Composable
fun EditorScreen(vm: AppViewModel, e: EditorState, modifier: Modifier = Modifier) {
    val position = e.position
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Text(
            if (e.editingId == null) "Neue Variante" else "Variante bearbeiten",
            style = MaterialTheme.typography.titleLarge,
        )
        e.note?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF90CAF9))
        }
        Spacer(Modifier.height(12.dp))
        BoardWithPanel(
            board = { modifier ->
                ChessBoard(
                    position = position,
                    whiteBottom = e.playWhite,
                    lastMove = e.shown.lastOrNull(),
                    marks = emptyMap(),
                    interactive = true,
                    onMove = vm::editorPlay,
                    modifier = modifier,
                    drawModeState = e.drawMode,
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
                when {
                    !e.atEnd -> "Zug ${e.cursor} von ${e.moves.size}. Ein anderer Zug auf dem Brett ersetzt ab hier den Rest."
                    e.setUp -> "Spiele auf dem Brett die Züge ab der eingefügten Stellung (beide Seiten)."
                    else -> "Spiele auf dem Brett die Züge vom Start bis zur Stellung (beide Seiten)."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (e.setUp) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Startet ab einer eigenen Stellung, nicht ab der Grundstellung.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = vm::editorResetStart) { Text("Doch ab Grundstellung") }
                }
            }
            Spacer(Modifier.height(8.dp))
            MoveList(vm, e)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { vm.editorGoTo(0) }, enabled = e.cursor > 0) { Text("|◀") }
                OutlinedButton(onClick = vm::editorStepBack, enabled = e.cursor > 0) { Text("◀") }
                OutlinedButton(onClick = vm::editorStepForward, enabled = !e.atEnd) { Text("▶") }
                OutlinedButton(onClick = vm::editorToEnd, enabled = !e.atEnd) { Text("▶|") }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (e.atEnd) {
                    OutlinedButton(onClick = vm::editorUndo, enabled = e.moves.isNotEmpty()) { Text("Zug zurück") }
                } else {
                    OutlinedButton(onClick = vm::editorTruncate) { Text("Ab hier kürzen") }
                }
                OutlinedButton(onClick = vm::editorClear, enabled = e.moves.isNotEmpty()) { Text("Alle löschen") }
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
            if (vm.editorRestartsProgress()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Geänderte Züge sind ein neues Puzzle: der Lernstand dieser Variante fängt beim Speichern von vorn an.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::saveEditor, enabled = e.name.isNotBlank() && e.moves.isNotEmpty()) { Text("Speichern") }
                TextButton(onClick = vm::cancelEditor) { Text("Abbrechen") }
            }
        }
    }
}

/**
 * Every move of the variant, clickable: the board jumps to that point without losing what comes
 * after it. The move on the board is highlighted.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MoveList(vm: AppViewModel, e: EditorState) {
    if (e.moves.isEmpty()) {
        Text("Noch keine Züge", style = MaterialTheme.typography.bodyLarge)
        return
    }
    val positions = e.positions
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MoveChip("Start", e.cursor == 0) { vm.editorGoTo(0) }
        e.moves.forEachIndexed { i, move ->
            val before = positions[i]
            // "1. e4" for White, the bare move for Black, as in a game score.
            val label = if (before.whiteToMove) numberedSan(before, move) else before.san(move)
            MoveChip(label, e.cursor == i + 1) { vm.editorGoTo(i + 1) }
        }
    }
}

@Composable
private fun MoveChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                RoundedCornerShape(6.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** Free text about the variant; "((…))" turns a part into a gap that training asks for. */
@OptIn(ExperimentalLayoutApi::class)
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
            if (it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when {
                // Cmd/Ctrl+Shift+C as in Anki: make a gap.
                it.key == Key.C && it.isShiftPressed && (it.isMetaPressed || it.isCtrlPressed) -> vm.theoryGap().let { true }
                // "7" + Tab → "@7. Bg5", Tab again → "@7... h6".
                it.key == Key.Tab && !it.isShiftPressed -> vm.theoryComplete()
                else -> false
            }
        },
    )
    FlowRow {
        TextButton(onClick = vm::theoryGap, enabled = !e.theory.selection.collapsed || e.shown.isNotEmpty()) {
            Text(if (e.theory.selection.collapsed) "Lücke: letzter Zug" else "Auswahl → Lücke")
        }
        TextButton(onClick = vm::theoryMarker, enabled = e.shown.isNotEmpty()) { Text("@ Markierung: letzter Zug") }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        val gaps = Cloze.gaps(e.theory.text).size
        Text(
            if (gaps == 0) "Keine Lücken – wird nach dem Lösen nur angezeigt." else "$gaps Lücke(n) zum Ausfüllen",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Text(
        "((Antwort)) wird abgefragt, ((Nb6|Sb6)) erlaubt beides, ((Nb6::Springer)) zeigt einen Tipp. " +
            "Cmd/Strg+Shift+C macht die Auswahl zur Lücke. @8. Ng4 wird im Training ein Link zur Stellung nach diesem Zug – " +
            "tipp 8 und Tab (nochmal Tab für Schwarz).",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    vm.editorBrokenMarkers().takeIf { it.isNotEmpty() }?.let { broken ->
        Text(
            "Diese Züge gibt es in der Variante nicht: " + broken.joinToString(", "),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFFFB74D),
        )
    }
}
