package repertoire.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import repertoire.core.Variant
import repertoire.core.compactMoves
import repertoire.core.currentTimeMillis
import repertoire.core.shortDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val stats = vm.stats()
    Column(modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())) {
        Text("FireHorse", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = vm::startLearning, enabled = stats.due + stats.new > 0) { Text("Lernen") }
            OutlinedButton(onClick = vm::startCram, enabled = stats.total > 0) { Text("Frei üben") }
            FilledTonalButton(onClick = { vm.newVariant() }) { Text("+ Neue Variante") }
            OutlinedButton(onClick = vm::openPaste) { Text("PGN/FEN einfügen") }
        }
        Spacer(Modifier.height(6.dp))
        Text("${stats.due} fällig · ${stats.new} neu · ${stats.total} Varianten", style = MaterialTheme.typography.bodyMedium)

        Spacer(Modifier.height(20.dp))
        if (vm.library.variants.isEmpty()) {
            Text(
                "Noch keine Varianten. Klick auf „+ Neue Variante“, spiel die Züge bis zur Stellung und gib ihr einen Namen – " +
                    "oder füg mit „PGN/FEN einfügen“ eine Zugfolge ein, die du irgendwo gefunden hast.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val groups = vm.library.variants.groupBy { it.opening.ifBlank { "Ohne Eröffnung" } }.toSortedMap(String.CASE_INSENSITIVE_ORDER)
        for ((opening, variants) in groups) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(opening, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (vm.canTransfer) TextButton(onClick = { vm.export(opening) }) { Text("Exportieren") }
            }
            Spacer(Modifier.height(8.dp))
            for (v in variants) {
                VariantRow(vm, v)
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.height(8.dp))
        Text("Neue Varianten pro Tag")
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { vm.setNewPerDay(vm.library.settings.newPerDay - 5) }) { Text("−5") }
            Text("${vm.library.settings.newPerDay}", modifier = Modifier.padding(horizontal = 16.dp))
            OutlinedButton(onClick = { vm.setNewPerDay(vm.library.settings.newPerDay + 5) }) { Text("+5") }
        }
        if (vm.canTransfer) {
            Spacer(Modifier.height(20.dp))
            Text("Varianten und Lernstand übertragen")
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.export() }, enabled = vm.library.variants.isNotEmpty()) { Text("Alle exportieren") }
                OutlinedButton(onClick = vm::import) { Text("Importieren") }
            }
            vm.message?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            if (LocalTouch.current) {
                "Das Pferd unten rechts: antippen zeigt die Kürzel, halten führt sie aus."
            } else {
                "Strg+B, dann ? zeigt alle Tastenkürzel."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Gespeichert in: ${vm.dataLocation}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorseClearance()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VariantRow(vm: AppViewModel, v: Variant) {
    var confirmDelete by remember(v.id) { mutableStateOf(false) }
    val review = vm.reviewOf(v.id)
    val status = when {
        review == null -> "neu"
        review.suspended -> "pausiert"
        review.due <= currentTimeMillis() -> "fällig"
        else -> "nächste Abfrage " + shortDate(review.due)
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(v.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "$status  ·  ${if (v.playWhite) "Weiß" else "Schwarz"}" + if (v.setUp) "  ·  ab eigener Stellung" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                compactMoves(v.numberedSan()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Wraps onto several lines on a phone instead of squeezing the text away.
            FlowRow {
                if (review?.suspended == true) {
                    TextButton(onClick = { vm.unsuspend(v.id) }) { Text("Wieder abfragen") }
                }
                TextButton(onClick = { vm.practice(v.id) }) { Text("Üben") }
                TextButton(onClick = { vm.practiceMoves(v.id) }) { Text("Züge üben") }
                TextButton(onClick = { vm.editVariant(v, fromStart = true) }) { Text("Durchgehen") }
                TextButton(onClick = { vm.newVariant(template = v) }) { Text("Weiterführen") }
                TextButton(onClick = { vm.editVariant(v) }) { Text("Bearbeiten") }
                if (confirmDelete) {
                    TextButton(onClick = { vm.deleteVariant(v.id) }) { Text("Wirklich löschen", color = Color(0xFFE57373)) }
                    TextButton(onClick = { confirmDelete = false }) { Text("Nein") }
                } else {
                    TextButton(onClick = { confirmDelete = true }) { Text("Löschen") }
                }
            }
        }
    }
}

/**
 * Paste a PGN or a FEN from somewhere else. The field is focused right away, so Cmd/Strg+V is enough;
 * what comes out of the text is then opened in the editor instead of being saved straight away.
 */
@Composable
internal fun PasteDialog(vm: AppViewModel, p: PasteState) {
    val focus = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = vm::cancelPaste,
        title = {
            Text(
                when (p.kind) {
                    Command.PASTE_PGN -> "PGN einfügen"
                    Command.PASTE_FEN -> "FEN einfügen"
                    else -> "PGN oder FEN einfügen"
                },
            )
        },
        text = {
            Column {
                LaunchedEffect(Unit) { focus.requestFocus() }
                Text(
                    "Zugfolge oder Stellung hier einfügen – am Mac mit Cmd+V, am Handy lange drücken. " +
                        "Aus einer PGN wird die Zugfolge (Kommentare und Nebenvarianten fallen weg), " +
                        "aus einer FEN die Stellung, ab der du weiterspielst.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    p.text,
                    vm::setPasteText,
                    label = { Text("PGN / FEN") },
                    placeholder = {
                        Text(
                            if (p.kind == Command.PASTE_FEN) {
                                "rnbqkbnr/pp1ppppp/8/2p5/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2"
                            } else {
                                "1. e4 c5 2. Nf3 d6 3. d4 cxd4"
                            },
                        )
                    },
                    minLines = 4,
                    maxLines = 8,
                    isError = p.error != null,
                    // The dialog may have a window of its own, which the main window's keys never see.
                    modifier = Modifier.fillMaxWidth().focusRequester(focus).onPreviewKeyEvent(vm.shortcutKeys::handle),
                )
                p.error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = Color(0xFFE57373), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Button(onClick = vm::applyPaste, enabled = p.text.isNotBlank()) { Text("Übernehmen") }
        },
        dismissButton = { TextButton(onClick = vm::cancelPaste) { Text("Abbrechen") } },
    )
}
