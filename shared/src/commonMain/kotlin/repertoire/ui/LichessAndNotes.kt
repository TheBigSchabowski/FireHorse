package repertoire.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import repertoire.core.Explorer
import repertoire.core.ExplorerResult
import repertoire.core.ExplorerMove
import repertoire.core.ExplorerSource
import repertoire.core.GameSite
import repertoire.core.GameSpeed
import repertoire.core.GameTree
import repertoire.core.TreeRequest
import repertoire.core.Library
import repertoire.core.Position

private sealed interface Lookup {
    data object Loading : Lookup
    data class Found(val result: ExplorerResult) : Lookup
    data class Failed(val message: String) : Lookup
}

/**
 * The opening database for the position on the board (Ctrl+B l), in a window at the side that
 * follows the board: every move played there, how often, and how the games ended. "Meister" and
 * "lichess" ask lichess; "Partien" is one player's own games as a tree, as on openingtree.com. A
 * click on a move plays it in the editor.
 */
@Composable
fun ExplorerPanel(vm: AppViewModel, modifier: Modifier = Modifier, handle: Modifier = Modifier) {
    Surface(
        modifier.width(340.dp).heightIn(max = 560.dp),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.padding(12.dp)) {
            // The title is the handle to drag the window by.
            Row(handle, verticalAlignment = Alignment.CenterVertically) {
                Text("⠿ ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (vm.explorerSource == ExplorerSource.GAMES) "Eigene Partien" else "lichess-Datenbank",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { vm.explorerOpen = false }) { Text("✕") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (source in ExplorerSource.entries) {
                    FilterChip(
                        selected = vm.explorerSource == source,
                        onClick = { vm.explorerSource = source },
                        label = { Text(source.label) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            val token = vm.library.settings.lichessToken
            val position = vm.boardPosition
            when {
                vm.explorerSource == ExplorerSource.GAMES -> GamesTree(vm, position)
                token.isBlank() -> {
                    Hint(
                        "lichess gibt die Datenbank nur mit einem persönlichen API-Schlüssel heraus. Den machst du auf " +
                            "${Explorer.TOKEN_PAGE} (kein Häkchen nötig) und trägst ihn unter Konfiguration → lichess ein.",
                    )
                    TextButton(onClick = vm::openConfig) { Text("Schlüssel eintragen") }
                }
                position == null -> Hint("Die Datenbank zeigt die Stellung auf dem Brett – öffne eine Variante oder ein Puzzle.")
                else -> ExplorerTable(vm, vm.explorerSource, position)
            }
        }
    }
}

@Composable
private fun ExplorerTable(vm: AppViewModel, source: ExplorerSource, position: Position) {
    val fen = position.fen
    // Per position: what was found for the last one is never shown for this one, not even for a moment.
    var lookup by remember(source, fen) { mutableStateOf<Lookup>(Lookup.Loading) }
    LaunchedEffect(source, fen) {
        lookup = Lookup.Loading
        lookup = try {
            Lookup.Found(vm.explore(source, fen))
        } catch (e: IllegalStateException) {
            Lookup.Failed(e.message ?: "Das hat nicht geklappt.")
        }
    }
    when (val l = lookup) {
        Lookup.Loading -> Hint("Frage lichess …")
        is Lookup.Failed -> Text(l.message, color = Color(0xFFE57373), style = MaterialTheme.typography.bodyMedium)
        is Lookup.Found -> {
            val r = l.result
            r.opening?.takeIf { it.name.isNotBlank() }?.let {
                Text("${it.eco} ${it.name}".trim(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
            Hint("${Explorer.count(r.games)} Partien")
            Spacer(Modifier.height(6.dp))
            if (r.moves.isEmpty()) {
                Hint("Diese Stellung kommt in der Datenbank nicht vor.")
            } else {
                Column(Modifier.verticalScroll(rememberScrollState())) { MoveRows(vm, r.moves, position) }
            }
        }
    }
}

/** The moves of a position, most played first: a click plays one in the editor. */
@Composable
private fun MoveRows(vm: AppViewModel, moves: List<ExplorerMove>, position: Position) {
    for (m in moves) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable { vm.playSuggested(m.moveIn(position)) }
                .padding(vertical = 4.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(m.san, modifier = Modifier.width(56.dp), fontWeight = FontWeight.Bold)
            Text(
                Explorer.count(m.played ?: m.games),
                modifier = Modifier.width(72.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ResultBar(m.white, m.draws, m.black, Modifier.weight(1f))
        }
    }
}

/**
 * "Partien": one player's games as a tree, as openingtree.com builds it. Without a tree, or on
 * "Andere Partien", the form to load one; while loading, how far it got.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GamesTree(vm: AppViewModel, position: Position?) {
    val tree = vm.gameTree
    val loading = vm.treeLoading
    when {
        loading != null -> {
            Hint(if (loading == 0) "Lade Partien …" else "Lade Partien … $loading im Baum")
            TextButton(onClick = vm::cancelTree) { Text("Abbrechen") }
        }
        tree == null || vm.treeFormOpen -> TreeForm(vm, tree)
        else -> {
            Text(tree.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { vm.treeFormOpen = true }) { Text("Andere Partien") }
                if (tree.request.site != GameSite.PGN_FILE) TextButton(onClick = vm::loadTree) { Text("Neu laden") }
                TextButton(onClick = vm::exportTree) { Text("Als .tree speichern") }
            }
            if (position == null) {
                Hint("Der Baum zeigt die Stellung auf dem Brett – öffne eine Variante oder ein Puzzle.")
            } else {
                TreeTable(vm, tree, position)
            }
        }
    }
}

@Composable
private fun TreeTable(vm: AppViewModel, tree: GameTree, position: Position) {
    val node = remember(tree, position.fen) { tree.nodeAt(position) }
    val result = remember(tree, position.fen) { tree.lookup(position) }
    if (node == null || node.games == 0) {
        Hint("Diese Stellung kam in den Partien nicht vor.")
        return
    }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Hint(
            listOfNotNull(
                "${Explorer.count(node.games.toLong())} Partien",
                "${tree.score(node)} % für " + if (tree.white) "Weiß" else "Schwarz",
                node.averageOpponent?.let { "Ø Gegner $it" },
            ).joinToString(" · "),
        )
        TreeGameLine("Zuletzt", tree, node.last)
        TreeGameLine("Bester Sieg", tree, node.bestWin)
        TreeGameLine("Schlimmste Niederlage", tree, node.worstLoss)
        Spacer(Modifier.height(6.dp))
        if (result.moves.isEmpty()) {
            val ended = node.ended.size
            Hint(if (ended > 0) "Hier endete${if (ended > 1) "n $ended Partien" else " eine Partie"}." else "Weiter reicht der Baum hier nicht.")
        } else {
            MoveRows(vm, result.moves, position)
        }
    }
}

/** One game that stands out at a position, e.g. the last one played; a click opens it on its site. */
@Composable
private fun TreeGameLine(label: String, tree: GameTree, index: Int) {
    val game = tree.games.getOrNull(index) ?: return
    val uri = LocalUriHandler.current
    val opponent = if (tree.white) game.black to game.blackElo else game.white to game.whiteElo
    val date = game.date.split('.').takeIf { it.size == 3 && it.none { p -> p.contains('?') } }?.reversed()?.joinToString(".") ?: ""
    val text = "$label: " + listOf(date, "gegen ${opponent.first}" + (opponent.second?.let { " ($it)" } ?: ""), game.result.replace("1/2-1/2", "½-½"))
        .filter { it.isNotBlank() }.joinToString(", ") + if (game.url.isNotEmpty()) " ↗" else ""
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (game.url.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = if (game.url.isNotEmpty()) Modifier.clickable { runCatching { uri.openUri(game.url) } } else Modifier,
    )
}

/** Which games to build a tree from: site, player, color, speeds, how many. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TreeForm(vm: AppViewModel, tree: GameTree?) {
    val r = vm.treeRequest
    fun change(update: (TreeRequest) -> TreeRequest) {
        vm.treeRequest = update(vm.treeRequest)
    }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Hint("Wie openingtree.com: die Partien eines Spielers als Baum – was er in jeder Stellung spielt und wie es ausging.")
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (site in GameSite.entries) {
                FilterChip(selected = r.site == site, onClick = { change { it.copy(site = site) } }, label = { Text(site.label) })
            }
        }
        OutlinedTextField(
            r.player,
            { name -> change { it.copy(player = name) } },
            label = {
                Text(
                    when (r.site) {
                        GameSite.PGN_FILE -> "Spieler in der Datei (leer = alle)"
                        else -> "Konto bei ${r.site.label}"
                    },
                )
            },
            singleLine = true,
            modifier = Modifier.typing(vm).fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = r.white, onClick = { change { it.copy(white = true) } }, label = { Text("mit Weiß") })
            FilterChip(selected = !r.white, onClick = { change { it.copy(white = false) } }, label = { Text("mit Schwarz") })
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (speed in GameSpeed.entries) {
                FilterChip(
                    selected = speed in r.speeds,
                    onClick = { change { it.copy(speeds = if (speed in it.speeds) it.speeds - speed else it.speeds + speed) } },
                    label = { Text(speed.label) },
                )
            }
            if (r.site != GameSite.PGN_FILE) {
                FilterChip(selected = r.ratedOnly, onClick = { change { it.copy(ratedOnly = !it.ratedOnly) } }, label = { Text("nur gewertet") })
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("Höchstens so viele Partien, die neuesten zuerst", style = MaterialTheme.typography.bodySmall)
        NumberStepper(
            vm, r.max, 1..TreeRequest.MAX, onValue = { n -> change { it.copy(max = n) } },
            onMinus = { change { it.copy(max = stepIn(TREE_SIZES, it.max, -1)) } },
            onPlus = { change { it.copy(max = stepIn(TREE_SIZES, it.max, 1)) } },
            fieldWidth = 88,
        )
        vm.treeError?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, color = Color(0xFFE57373), style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = vm::loadTree) { Text(if (r.site == GameSite.PGN_FILE) "PGN-Datei öffnen" else "Laden") }
            TextButton(onClick = vm::importTree) { Text("openingtree-Datei öffnen") }
            if (tree != null) {
                TextButton(onClick = { vm.treeFormOpen = false }) { Text("Zurück") }
                TextButton(onClick = vm::clearTree) { Text("Baum löschen") }
            }
        }
        Hint(
            "lichess und chess.com geben die Partien ohne Anmeldung heraus; mit lichess-Schlüssel geht es schneller. " +
                "In den Baum kommen die ersten ${GameTree.MAX_PLIES / 2} Züge jeder Partie. Eine .tree-Datei von openingtree.com " +
                "lässt sich öffnen, und „Als .tree speichern“ gibt den Baum an openingtree zurück.",
        )
    }
}

private val TREE_SIZES = listOf(1, 50, 100, 200, 500, 1000, 2000, 3000, 5000)

/** White, draws and black of the games as one bar, as on lichess, with the percents written in. */
@Composable
private fun ResultBar(white: Long, draws: Long, black: Long, modifier: Modifier) {
    val (w, d, b) = Explorer.percents(white, draws, black)
    Row(modifier.height(18.dp).clip(RoundedCornerShape(3.dp))) {
        for ((share, colors) in listOf(w to (Color(0xFFEEEEEE) to Color.Black), d to (Color(0xFF9E9E9E) to Color.Black), b to (Color(0xFF303030) to Color.White))) {
            if (share == 0) continue
            Box(Modifier.weight(share.toFloat()).fillMaxHeight().background(colors.first), contentAlignment = Alignment.Center) {
                if (share >= 12) Text("$share%", color = colors.second, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/**
 * Notes of one's own on an opening (Ctrl+B e), and general ones on openings as a whole. Opens at
 * the opening on screen; the others can be picked above the text. Saved when switching and closing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotesDialog(vm: AppViewModel, opening: String) {
    var text by remember(opening) { mutableStateOf(vm.library.notes[opening] ?: "") }
    fun close() {
        vm.saveNotes(opening, text)
        vm.notesOf = null
    }
    AlertDialog(
        onDismissRequest = ::close,
        title = { Text("Eröffnungsnotizen") },
        text = {
            Column {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (o in vm.notesOpenings) {
                        FilterChip(
                            selected = o == opening,
                            onClick = {
                                vm.saveNotes(opening, text)
                                vm.notesOf = o
                            },
                            label = { Text(o + if (o != opening && vm.library.notes[o] != null) " ✎" else "") },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    text,
                    { text = it },
                    label = { Text(if (opening == Library.GENERAL_NOTES) "Allgemeine Notizen" else "Notizen zu $opening") },
                    minLines = 10,
                    maxLines = 20,
                    modifier = Modifier.typing(vm).fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Hint("Pläne, typische Fallen, was du dir zur Eröffnung als Ganzes merken willst. Gespeichert wird beim Wechseln und Schließen.")
            }
        },
        confirmButton = { Button(onClick = ::close) { Text("Schließen") } },
    )
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
