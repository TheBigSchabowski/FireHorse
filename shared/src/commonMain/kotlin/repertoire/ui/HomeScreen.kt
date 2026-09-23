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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
        Text("Eröffnungs-Puzzle", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = vm::startLearning, enabled = stats.due + stats.new > 0) { Text("Lernen") }
            OutlinedButton(onClick = vm::startCram, enabled = stats.total > 0) { Text("Frei üben") }
            FilledTonalButton(onClick = { vm.newVariant() }) { Text("+ Neue Variante") }
        }
        Spacer(Modifier.height(6.dp))
        Text("${stats.due} fällig · ${stats.new} neu · ${stats.total} Varianten", style = MaterialTheme.typography.bodyMedium)

        Spacer(Modifier.height(20.dp))
        if (vm.library.variants.isEmpty()) {
            Text(
                "Noch keine Varianten. Klick auf „+ Neue Variante“, spiel die Züge bis zur Stellung und gib ihr einen Namen.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val groups = vm.library.variants.groupBy { it.opening.ifBlank { "Ohne Eröffnung" } }.toSortedMap(String.CASE_INSENSITIVE_ORDER)
        for ((opening, variants) in groups) {
            Text(opening, style = MaterialTheme.typography.titleLarge)
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
        Spacer(Modifier.height(12.dp))
        Text(
            "Gespeichert in: ${vm.dataLocation}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
                "$status  ·  ${if (v.playWhite) "Weiß" else "Schwarz"}",
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
