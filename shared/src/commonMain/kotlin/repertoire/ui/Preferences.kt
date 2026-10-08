package repertoire.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.layout.width
import androidx.compose.ui.input.key.onPreviewKeyEvent
import repertoire.core.PenColor
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.RadioButton
import repertoire.core.EngineEntry
import repertoire.core.Explorer
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import repertoire.core.Ink
import repertoire.core.Library
import repertoire.core.Mark
import repertoire.core.Position
import repertoire.core.Settings
import repertoire.core.parseSquare
import kotlin.math.roundToInt

/** The preferences screen: the configuration, or the help with an optional topic open. */
sealed interface Prefs {
    data object Config : Prefs

    /** [fromConfig]: opened from a "Hilfe" button there, so going back returns to it. */
    data class Help(val topic: HelpTopic? = null, val fromConfig: Boolean = false) : Prefs
}

/** A page of the help. [touch]: only on a phone (true), only with a keyboard (false), or both (null). */
enum class HelpTopic(val title: String, val summary: String, val touch: Boolean? = null) {
    OVERVIEW("So funktioniert FireHorse", "Varianten anlegen, als Puzzle lernen, Spaced Repetition"),
    VARIANTS("Varianten anlegen und bearbeiten", "Neue Variante, Weiterführen, Durchgehen, Bearbeiten"),
    PASTE("PGN und FEN einfügen", "Zugfolgen und Stellungen von woanders übernehmen"),
    LEARNING("Lernen und Üben", "Lernen, Frei üben, Züge üben, neue Varianten pro Tag"),
    THEORY("Theorie und Lücken", "Notizen zur Variante, die beim Üben abgefragt werden"),
    BOARD("Auf dem Brett zeichnen", "Pfeile und Kreise, Zeichenmodus, Zeichnungen zum Abfragen"),
    PLAYER("Abspielen", "Eine Variante von allein durchlaufen lassen, Tempo, Wiederholen"),
    PLAYLIST("Playlists, Anpinnen, Auswählen", "Varianten nacheinander abspielen oder lernen, eigene Playlists, mehrere auf einmal löschen"),
    HORSE("Das Pferd", "Kürzel am Handy: antippen und halten", touch = true),
    TAP_ZONES("Doppeltippen", "Bereiche des Bildschirms für Kürzel, Raster frei wählbar", touch = true),
    KEYBOARD("Tastenkürzel", "Strg+B und die übrigen Tasten, eigene Belegung", touch = false),
    LICHESS("Datenbank, Engine und Notizen", "lichess-Eröffnungsdatenbank, Engines wie Stockfish, eigene Eröffnungsnotizen"),
    DATA("Speichern und Übertragen", "Wo alles liegt, Export und Import"),
    ;

    fun shownOn(isTouch: Boolean) = touch == null || touch == isTouch
}

@Composable
fun PreferencesScreen(vm: AppViewModel, prefs: Prefs, modifier: Modifier = Modifier) {
    val touch = LocalTouch.current
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Einstellungen", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.prefs = null }) { Text("Schließen") }
        }
        PrimaryTabRow(selectedTabIndex = if (prefs is Prefs.Help) 1 else 0) {
            Tab(prefs == Prefs.Config, onClick = vm::openConfig, text = { Text("Konfiguration") })
            Tab(prefs is Prefs.Help, onClick = { vm.openHelp() }, text = { Text("Hilfe") })
        }
        // A fresh scroll position for every page, so a help page does not open halfway down.
        key(prefs) {
            val scroll = rememberScrollState()
            var viewport by remember { mutableStateOf<LayoutCoordinates?>(null) }
            // Scrolls so that [target] sits at the top of the page.
            val scrollTo: suspend (LayoutCoordinates) -> Unit = { target ->
                viewport?.let { v -> scroll.animateScrollTo(scroll.value + v.localPositionOf(target, Offset.Zero).y.roundToInt()) }
            }
            Column(Modifier.fillMaxSize().onGloballyPositioned { viewport = it }.verticalScroll(scroll)) {
                // Lines and rows stay readable in a wide window instead of stretching across it.
                Column(Modifier.widthIn(max = 720.dp).padding(24.dp)) {
                    when {
                        prefs is Prefs.Help && prefs.topic != null -> HelpPage(vm, prefs, touch)
                        prefs is Prefs.Help -> HelpIndex(vm, touch)
                        else -> ConfigPage(vm, touch, scrollTo)
                    }
                    HorseClearance()
                }
            }
        }
    }
}

// ---- Configuration -----------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConfigPage(vm: AppViewModel, touch: Boolean, scrollTo: suspend (LayoutCoordinates) -> Unit) {
    val settings = vm.library.settings
    Section("Lernen", onHelp = { vm.openHelp(HelpTopic.LEARNING) }) {
        Text("Neue Varianten pro Tag")
        Spacer(Modifier.height(6.dp))
        Stepper(
            "${settings.newPerDay}",
            onMinus = { vm.setNewPerDay(settings.newPerDay - 5) },
            onPlus = { vm.setNewPerDay(settings.newPerDay + 5) },
            minus = "−5",
            plus = "+5",
        )
    }
    Section("Abspielen", onHelp = { vm.openHelp(HelpTopic.PLAYER) }) {
        Text("Zeit pro Zug")
        Spacer(Modifier.height(6.dp))
        Stepper(
            formatSeconds(settings.playDelayMs.toLong()),
            onMinus = { vm.setPlayDelay(settings.playDelayMs - PLAY_DELAY_INCREMENT) },
            onPlus = { vm.setPlayDelay(settings.playDelayMs + PLAY_DELAY_INCREMENT) },
            minusEnabled = settings.playDelayMs > PLAY_DELAY_MIN,
            plusEnabled = settings.playDelayMs < PLAY_DELAY_MAX,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Nach dem letzten Zug von vorn beginnen", modifier = Modifier.weight(1f))
            Switch(settings.playLoop, onCheckedChange = vm::setPlayLoop)
        }
    }
    Section("lichess", onHelp = { vm.openHelp(HelpTopic.LICHESS) }) {
        LichessToken(vm, settings.lichessToken)
    }
    if (!touch) {
        Section("Engines", onHelp = { vm.openHelp(HelpTopic.LICHESS) }) {
            EngineSettings(vm, settings)
        }
    }
    Section("Brett", onHelp = { vm.openHelp(HelpTopic.BOARD) }) {
        Text("Farben")
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (theme in BoardTheme.entries) {
                val selected = BoardTheme.named(settings.boardTheme) == theme
                OutlinedButton(
                    onClick = { vm.setBoardTheme(theme) },
                    border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                ) {
                    Box(Modifier.size(14.dp).background(theme.light))
                    Box(Modifier.size(14.dp).background(theme.dark))
                    Text(theme.label, Modifier.padding(start = 8.dp))
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Zeichenfarben" + if (LocalTouch.current) "" else " (${settings.shortcutLabel(Command.COLORS)})")
        PenColors(vm)
    }
    if (touch) {
        val step = settings.holdStepMs
        Section("Das Pferd", onHelp = { vm.openHelp(HelpTopic.HORSE) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Pferd anzeigen", modifier = Modifier.weight(1f))
                Switch(settings.horseOn, onCheckedChange = vm::setHorseOn)
            }
            if (!settings.horseOn) {
                Hint("Ohne Pferd erreichst du die Kürzel nur noch durch Doppeltippen" + (if (settings.tapZonesOn) "." else " – und das ist gerade aus. Die Einstellungen gibt es weiter auf der Startseite."))
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Unten links statt rechts", modifier = Modifier.weight(1f))
                    Switch(settings.horseLeft, onCheckedChange = vm::setHorseLeft)
                }
                Spacer(Modifier.height(8.dp))
                Text("Haltezeit pro Schritt")
                Spacer(Modifier.height(6.dp))
                Stepper(
                    formatSeconds(step.toLong()),
                    onMinus = { vm.setHoldStep(step - HOLD_STEP_INCREMENT) },
                    onPlus = { vm.setHoldStep(step + HOLD_STEP_INCREMENT) },
                    minusEnabled = step > HOLD_STEP_MIN,
                    plusEnabled = step < HOLD_STEP_MAX,
                )
                Hint(
                    Command.entries.filter { (it.holdSteps ?: 0) > 0 }
                        .joinToString(", ") { "${it.short} nach ${formatSeconds(it.holdSteps!!.toLong() * step)}" } + ".",
                )
            }
        }
        Section("Doppeltippen", onHelp = { vm.openHelp(HelpTopic.TAP_ZONES) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Zweimal auf eine freie Stelle tippen führt ein Kürzel aus", modifier = Modifier.weight(1f))
                Switch(settings.tapZonesOn, onCheckedChange = vm::setTapZonesOn)
            }
            if (settings.tapZonesOn) {
                Spacer(Modifier.height(8.dp))
                Text("Spalten")
                Spacer(Modifier.height(6.dp))
                Stepper(
                    "${settings.tapCols}",
                    onMinus = { vm.setTapGrid(settings.tapCols - 1, settings.tapRows) },
                    onPlus = { vm.setTapGrid(settings.tapCols + 1, settings.tapRows) },
                    minusEnabled = settings.tapCols > 1,
                    plusEnabled = settings.tapCols < TAP_COLS_MAX,
                )
                Spacer(Modifier.height(8.dp))
                Text("Reihen")
                Spacer(Modifier.height(6.dp))
                Stepper(
                    "${settings.tapRows}",
                    onMinus = { vm.setTapGrid(settings.tapCols, settings.tapRows - 1) },
                    onPlus = { vm.setTapGrid(settings.tapCols, settings.tapRows + 1) },
                    minusEnabled = settings.tapRows > 1,
                    plusEnabled = settings.tapRows < TAP_ROWS_MAX,
                )
                Spacer(Modifier.height(8.dp))
                Hint("Der Bildschirm in ${settings.zones.size} Bereich(en). Antippen, um festzulegen, was ein Bereich macht:")
                Spacer(Modifier.height(8.dp))
                ZoneGrid(settings, onPick = vm::setTapZone)
            }
        }
    } else {
        // Ctrl+B with a free key opens the configuration right here, once it is known where "here" is.
        var keysAt by remember { mutableStateOf<LayoutCoordinates?>(null) }
        val placed = keysAt != null
        LaunchedEffect(vm.showKeys, placed) {
            val at = keysAt
            if (vm.showKeys && at != null) {
                scrollTo(at)
                vm.showKeys = false
            }
        }
        Column(Modifier.onGloballyPositioned { keysAt = it }) { KeySettings(vm, settings) }
    }
    Section("Daten", onHelp = { vm.openHelp(HelpTopic.DATA) }, last = true) {
        Hint("Gespeichert in: ${vm.dataLocation}")
        if (vm.canTransfer) {
            Spacer(Modifier.height(8.dp))
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
            vm.importing?.let { ImportChoice(vm, it, vm.library.variants.size) }
        }
    }
}

/** After a file is opened: add its variants to the ones there are, or delete those and keep only the file's. */
@Composable
private fun ImportChoice(vm: AppViewModel, file: AppViewModel.PendingImport, have: Int) {
    AlertDialog(
        onDismissRequest = vm::cancelImport,
        title = { Text("Importieren") },
        text = {
            Column {
                Text("Die Datei hat ${file.variants} Variante(n). Du hast gerade $have.")
                Spacer(Modifier.height(10.dp))
                Text("Dazunehmen", fontWeight = FontWeight.Bold)
                Hint("Die Varianten der Datei kommen dazu, deine bleiben. Eine, die es schon gibt, wird aktualisiert.")
                Spacer(Modifier.height(8.dp))
                Text("Ersetzen", fontWeight = FontWeight.Bold, color = Color(0xFFE57373))
                Hint(
                    "Alle deine Varianten werden mit ihrem Lernstand gelöscht, danach gibt es nur noch die aus der Datei. " +
                        "Notizen, Einstellungen und Playlists bleiben (Playlists ohne die gelöschten Varianten).",
                )
            }
        },
        confirmButton = { Button(onClick = { vm.finishImport(replace = false) }) { Text("Dazunehmen") } },
        dismissButton = {
            Row {
                TextButton(onClick = vm::cancelImport) { Text("Abbrechen") }
                TextButton(onClick = { vm.finishImport(replace = true) }) { Text("Ersetzen", color = Color(0xFFE57373)) }
            }
        },
    )
}

/** The personal lichess API token for the opening database, hidden unless asked to show it. */
@Composable
private fun LichessToken(vm: AppViewModel, saved: String) {
    var text by remember { mutableStateOf(saved) }
    var shown by remember { mutableStateOf(false) }
    Hint(
        "Für die Eröffnungsdatenbank (" + (if (LocalTouch.current) "Kürzel „Datenbank“" else vm.library.settings.shortcutLabel(Command.EXPLORER)) +
            ") will lichess einen persönlichen API-Schlüssel. Auf ${Explorer.TOKEN_PAGE} einen neuen anlegen – kein Häkchen nötig – und hier einfügen. " +
            "Er bleibt auf diesem Gerät und kommt nicht mit in Exporte.",
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        text,
        {
            text = it
            vm.setLichessToken(it)
        },
        label = { Text("API-Schlüssel (lip_…)") },
        singleLine = true,
        visualTransformation = if (shown) VisualTransformation.None else PasswordVisualTransformation(),
        modifier = Modifier.typing(vm).fillMaxWidth().onPreviewKeyEvent(vm.shortcutKeys::handle),
    )
    TextButton(onClick = { shown = !shown }, enabled = text.isNotEmpty()) { Text(if (shown) "Verbergen" else "Anzeigen") }
}

/**
 * The engines on this computer: those set up, those lying in the engine folder, a path to add one,
 * and how hard they work. Nothing is downloaded; an engine is whatever UCI program is given.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EngineSettings(vm: AppViewModel, settings: Settings) {
    val scope = rememberCoroutineScope()
    var path by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun add(p: String) {
        busy = true
        error = null
        scope.launch {
            try {
                vm.addEngine(p)
                path = ""
            } catch (e: IllegalStateException) {
                error = e.message
            } finally {
                busy = false
            }
        }
    }
    Hint(
        "Eine Engine ist ein UCI-Programm auf diesem Computer, z. B. Stockfish. Gib den Pfad zur Programmdatei an " +
            "oder leg sie in den Ordner ${vm.engineFolder} – dann steht sie hier zum Hinzufügen. " +
            "${settings.shortcutLabel(Command.ENGINE)} lässt die gewählte Engine die Stellung auf dem Brett rechnen.",
    )
    Spacer(Modifier.height(8.dp))
    val chosen = settings.chosenEngine
    for (e in settings.engines) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = e == chosen, onClick = { vm.chooseEngine(e.path) })
            Column(Modifier.weight(1f)) {
                Text(e.name)
                Hint(e.path)
            }
            TextButton(onClick = { vm.removeEngine(e.path) }) { Text("Entfernen") }
        }
        // Each engine works its own way; the chosen one shows how.
        if (e == chosen) EngineOptions(vm, e)
    }
    val found = remember(settings.engines) { vm.enginesInFolder() }
    for (f in found) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Im Ordner: " + f.substringAfterLast('/'))
                Hint(f)
            }
            TextButton(onClick = { add(f) }, enabled = !busy) { Text("Hinzufügen") }
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            path,
            {
                path = it
                error = null
            },
            label = { Text("Pfad zur Engine") },
            placeholder = { Text("/Pfad/zu/stockfish") },
            singleLine = true,
            isError = error != null,
            modifier = Modifier.typing(vm).weight(1f).onPreviewKeyEvent(vm.shortcutKeys::handle),
        )
        Spacer(Modifier.width(8.dp))
        Button(onClick = { add(path) }, enabled = path.isNotBlank() && !busy) { Text(if (busy) "Prüfe …" else "Hinzufügen") }
    }
    error?.let { Text(it, color = Color(0xFFE57373), style = MaterialTheme.typography.bodyMedium) }
}

/** How hard one engine works: lines, depth, threads, hash. */
@Composable
private fun EngineOptions(vm: AppViewModel, e: EngineEntry) {
    Column(Modifier.padding(start = 48.dp, bottom = 12.dp)) {
        Text("Linien")
        Stepper("${e.lines}", onMinus = { vm.setEngineLines(e.path, e.lines - 1) }, onPlus = { vm.setEngineLines(e.path, e.lines + 1) },
            minusEnabled = e.lines > 1, plusEnabled = e.lines < 5)
        Spacer(Modifier.height(8.dp))
        Text("Tiefe")
        Stepper(
            if (e.depth == 0) "unbegrenzt" else "${e.depth}",
            onMinus = { vm.stepEngineDepth(e.path, -1) }, onPlus = { vm.stepEngineDepth(e.path, 1) },
            minusEnabled = e.depth != 0, plusEnabled = e.depth != ENGINE_DEPTHS.last(),
        )
        Hint("Unbegrenzt rechnet die Engine weiter, bis sich die Stellung ändert; mit einer Tiefe hört sie dort auf und die Linien bleiben stehen.")
        Spacer(Modifier.height(8.dp))
        Text("Threads (Prozessorkerne)")
        Stepper("${e.threads}", onMinus = { vm.setEngineThreads(e.path, e.threads - 1) }, onPlus = { vm.setEngineThreads(e.path, e.threads + 1) },
            minusEnabled = e.threads > 1)
        Spacer(Modifier.height(8.dp))
        Text("Hash (Speicher)")
        Stepper("${e.hashMb} MB", onMinus = { vm.setEngineHash(e.path, e.hashMb / 2) }, onPlus = { vm.setEngineHash(e.path, e.hashMb * 2) },
            minusEnabled = e.hashMb > 16, plusEnabled = e.hashMb < 8192)
    }
}

/** The keys: Ctrl+B and its letters, and the single keys while drawing. */
@Composable
private fun KeySettings(vm: AppViewModel, settings: Settings) {
    Section("Tastenkürzel", onHelp = { vm.openHelp(HelpTopic.KEYBOARD) }) {
        Hint(
            "Erst ${settings.prefixLabel}, dann die Taste. Zum Ändern auf eine Taste klicken und die neue drücken " +
                "(Esc bricht ab). Ist sie schon vergeben, tauschen die beiden. Ein freier Buchstabe nach ${settings.prefixLabel} führt hierher.",
        )
        Spacer(Modifier.height(8.dp))
        KeyRow("Präfix: Strg + …", settings.prefixKey.uppercase(), vm, KeyTarget.Prefix)
        for (command in Command.entries) KeyRow(command.label, settings.keyOf(command).toString(), vm, KeyTarget.Of(command))
        Spacer(Modifier.height(12.dp))
        Text("Beim Zeichnen (ohne ${settings.prefixLabel})")
        Hint("Gelten nur, solange gezeichnet wird; dann ist jede Taste allein ein Befehl. Esc beendet das Zeichnen immer.")
        Spacer(Modifier.height(4.dp))
        for (draw in DrawKey.entries) KeyRow(draw.label, settings.drawKeyOf(draw).toString(), vm, KeyTarget.Draw(draw))
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = vm::resetKeys, enabled = settings.keysChanged) { Text("Standard wiederherstellen") }
    }
}

/** One configurable key: a click, then the next key press sets it. */
@Composable
private fun KeyRow(label: String, key: String, vm: AppViewModel, target: KeyTarget) {
    val waiting = vm.keyCapture == target
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        OutlinedButton(
            onClick = { vm.keyCapture = if (waiting) null else target },
            border = BorderStroke(1.dp, if (waiting) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
            modifier = Modifier.widthIn(min = 120.dp),
        ) {
            Text(
                if (waiting) "Taste drücken …" else key,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (waiting) FontWeight.Normal else FontWeight.Bold,
            )
        }
    }
}

/** A group of settings with a link to the help page that explains it. */
@Composable
private fun Section(title: String, onHelp: () -> Unit, last: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        TextButton(onClick = onHelp) { Text("Hilfe") }
    }
    Spacer(Modifier.height(4.dp))
    Column(content = content)
    if (!last) {
        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun Stepper(
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    minus: String = "−",
    plus: String = "+",
    minusEnabled: Boolean = true,
    plusEnabled: Boolean = true,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = onMinus, enabled = minusEnabled) { Text(minus) }
        Text(value, modifier = Modifier.padding(horizontal = 16.dp))
        OutlinedButton(onClick = onPlus, enabled = plusEnabled) { Text(plus) }
    }
}

/** The six zones as on the screen; with [onPick] each one can be given a shortcut. */
@Composable
private fun ZoneGrid(settings: Settings, onPick: ((TapZone, Command?) -> Unit)?) {
    Column(
        Modifier.widthIn(max = 360.dp).fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (row in 0 until settings.tapRows) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (column in 0 until settings.tapCols) {
                    val zone = TapZone(column, row)
                    ZoneTile(zone.label(settings.tapCols, settings.tapRows), settings.commandAt(zone), zone, onPick, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ZoneTile(label: String, command: Command?, zone: TapZone, onPick: ((TapZone, Command?) -> Unit)?, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(
        modifier.height(64.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .then(if (onPick != null) Modifier.clickable { open = true } else Modifier)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                command?.short ?: "—",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (command != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onPick != null) {
            DropdownMenu(open, onDismissRequest = { open = false }) {
                DropdownMenuItem(text = { Text("Nichts") }, onClick = { onPick(zone, null); open = false })
                for (c in Command.entries) {
                    DropdownMenuItem(text = { Text(c.label) }, onClick = { onPick(zone, c); open = false })
                }
            }
        }
    }
}

// ---- Help ----------------------------------------------------------------------------------

@Composable
private fun HelpIndex(vm: AppViewModel, touch: Boolean) {
    Para(
        "FireHorse trainiert Eröffnungen als Puzzle. Hier steht, wie alles funktioniert – " +
            "such dir ein Thema aus. In der Konfiguration führt „Hilfe“ neben jedem Bereich direkt zur passenden Seite.",
    )
    Spacer(Modifier.height(8.dp))
    for (topic in HelpTopic.entries.filter { it.shownOn(touch) }) {
        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { vm.openHelp(topic) }) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(topic.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(topic.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun HelpPage(vm: AppViewModel, prefs: Prefs.Help, touch: Boolean) {
    val topic = prefs.topic ?: return
    val settings = vm.library.settings
    TextButton(onClick = vm::prefsBack) { Text(if (prefs.fromConfig) "← Konfiguration" else "← Alle Themen") }
    Text(topic.title, style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(12.dp))
    when (topic) {
        HelpTopic.OVERVIEW -> {
            Para(
                "Du legst Varianten selbst an: die Züge vom Start bis zu einer Stellung, dazu Eröffnung und Namen " +
                    "(„Sizilianisch“ / „Scheveningen“).",
            )
            Para(
                "Beim Üben steht nur der Name auf einer schwarzen Tafel, und du spielst beide Seiten vom Start, bis die " +
                    "Stellung auf dem Brett steht. Jede Zugfolge zählt, die in deinen Varianten vorkommt, also auch Zugumstellungen.",
            )
            Para(
                "Wann eine Variante wieder drankommt, rechnet die App mit Spaced Repetition (FSRS) aus: was sitzt, " +
                    "kommt erst in Tagen oder Wochen wieder, was wackelt, bald.",
            )
            SubHeading("Loslegen")
            Para("1.  „+ Neue Variante“ oder „PGN/FEN einfügen“ auf der Startseite.")
            Para("2.  Züge spielen, Eröffnung und Namen eintragen, speichern.")
            Para("3.  „Lernen“ fragt ab, was heute dran ist.")
            Para(
                if (touch) {
                    "Kürzel erreichst du am Handy über das Pferd unten rechts und durch Doppeltippen auf eine freie Stelle."
                } else {
                    "Kürzel erreichst du mit ${settings.prefixLabel} und einer Taste; ${settings.shortcutLabel(Command.SHORTCUTS)} zeigt alle."
                },
            )
        }
        HelpTopic.VARIANTS -> {
            Bullet("+ Neue Variante", "Züge vom Start bis zur Stellung spielen, Eröffnung und Namen eintragen, die Farbe wählen, mit der du spielst, speichern.")
            Bullet("Weiterführen", "übernimmt die Züge einer vorhandenen Variante, dann hängst du nur noch an, was anders ist.")
            Bullet(
                "Durchgehen",
                "öffnet die Variante am Start. Mit |◀ ◀ ▶ ▶| oder einem Tipp auf einen Zug in der Liste gehst du die Züge durch – die Variante bleibt dabei, wie sie ist. " +
                    "Ein anderer Zug auf dem Brett öffnet einen Nebenstrang nur zum Anschauen; „↩ Zurück zur Variante“" +
                    (if (touch) "" else " oder Strg+B Enter") + " führt zurück, „Züge bearbeiten“ wechselt ins Bearbeiten.",
            )
            Bullet("Bearbeiten", "dasselbe Brett, nur gleich bei der Endstellung. Ein anderer Zug mitten in der Variante ersetzt ab dort den Rest, „Ab hier kürzen“ wirft ihn ohne Ersatz weg, „Zug zurück“ nimmt den letzten zurück.")
            Para("Geänderte Züge sind ein neues Puzzle: der Lernstand dieser Variante fängt von vorn an.")
            Para(
                "Eine neue Variante geht auch mitten in einem Puzzle (Kürzel „Neue Variante“): sie legt sich darüber, " +
                    "nach Speichern oder Abbrechen geht es mit dem Puzzle weiter. Eine angefangene Variante wird dabei nie überschrieben.",
            )
        }
        HelpTopic.PASTE -> {
            Para("„PGN/FEN einfügen“ übernimmt eine Zugfolge oder Stellung, die du irgendwo gefunden hast. Einfach ins Feld einfügen – " + if (touch) "lange drücken, dann „Einfügen“." else "Cmd/Strg+V.")
            Bullet("PGN", "daraus wird die Hauptvariante. Kopfzeilen, Kommentare {…}, Nebenvarianten (…), !? und $1 und das Ergebnis fallen weg; „Opening“/„Variation“ aus dem Kopf füllen die Namen, und wer den letzten Zug gemacht hat, ist die Seite, die du spielst.")
            Bullet("FEN", "daraus wird die Stellung, ab der die Variante beginnt. Du spielst von dort die Züge, die du üben willst, und trainierst sie später auch ab dort.")
            Bullet("Zugliste", "„e4 c5 Nf3“ geht genauso.")
            Para("Das Eingefügte landet erst im Editor: durchgehen, mit „Ab hier kürzen“ zuschneiden, benennen, speichern.")
        }
        HelpTopic.LEARNING -> {
            Bullet("Lernen", "die heute fälligen Varianten und ein paar neue, mit Wertung.")
            Bullet("Frei üben", "alle Varianten gemischt, ohne Wertung.")
            Bullet("Üben", "bei einer Variante: nur diese.")
            Bullet("Züge üben", "die Stellungen einer Variante gemischt, jeweils nur den nächsten Zug finden.")
            Para("Wie viele neue Varianten pro Tag dazukommen, stellst du in der Konfiguration ein – gerade sind es ${settings.newPerDay}.")
            SubHeading("Während eines Puzzles")
            Bullet("Zielstellung zeigen", "blendet die gesuchte Stellung als Hilfe ein.")
            Bullet("↺ Nochmal von vorn", "setzt das Brett zurück; Fehler von vorher zählen trotzdem.")
            Bullet("Falscher Zug", "wird korrigiert, und die Variante kommt bald wieder. Ein Zug aus einer anderen gespeicherten Variante gibt nur einen Hinweis.")
            Bullet("Gut / Weiter", "am Ende: „Gut“, oder „Weiter“ nach einem Fehler." + if (touch) "" else " Am Computer gehen auch Enter und Leertaste.")
            Bullet("Variante nicht mehr abfragen", "pausiert sie; „Wieder abfragen“ auf der Startseite holt sie zurück.")
            ConfigButton(vm, "Neue pro Tag einstellen")
        }
        HelpTopic.THEORY -> {
            Para(
                "Im Editor öffnet „✎ Theorie“" + (if (touch) "" else " (Cmd/Strg+T)") + " ein Textfeld für Notizen zur Variante. " +
                    "Nach dem Lösen des Puzzles erscheinen sie unter dem Brett, mit ein paar Besonderheiten:",
            )
            Bullet("((e6))", "wird zur Lücke: nach dem Lösen trägst du ein, was dort stand, und „Prüfen“ zeigt, ob es stimmt.")
            Bullet("((Nfd7|Sfd7::Springer))", "„|“ trennt Antworten, die alle zählen; nach „::“ steht ein Hinweis, der in der leeren Lücke erscheint.")
            Bullet("@8. Ng4", "wird ein Link, der das Brett nach diesem Zug zeigt.")
            if (!touch) Bullet("8 + Tab", "ergänzt „@8. …“ mit dem weißen Zug der Variante, nochmal Tab für den schwarzen.")
            Bullet("Züge üben", "fragt keine Theorie ab, nur den nächsten Zug.")
        }
        HelpTopic.BOARD -> {
            Bullet(
                "Pfeile und Kreise",
                "ein Feld lange gedrückt halten und auf ein anderes ziehen malt einen Pfeil, loslassen auf demselben Feld kreist es ein" +
                    (if (touch) "." else "; am Computer geht auch die rechte Maustaste.") +
                    " Derselbe Pfeil nochmal nimmt ihn wieder weg; mit dem nächsten Zug oder der nächsten Berührung des Bretts ist alles wieder sauber.",
            )
            Bullet(
                "Zeichenmodus",
                "dreimal schnell aufs Brett tippen" + (if (touch) "" else " oder ${settings.shortcutLabel(Command.DRAW)}") + ". Ein grüner Rahmen zeigt ihn an: " +
                    "Antippen kreist ein Feld ein, Ziehen malt einen Pfeil, und Züge sind gesperrt. „Löschen“ wischt alles weg, " +
                    "„Fertig“ oder nochmal dreimal tippen beendet ihn.",
            )
            Bullet(
                "Farben und Felder",
                "beim Zeichnen liegt unten auf dem Brett eine Leiste: sechs Stifte, dazu Kreis oder ausgefülltes Feld " +
                    "für das, was ein Tipp auf ein Feld malt. Die Wahl gilt für alles Zeichnen, bis du sie änderst. " +
                    "Dasselbe mit einem anderen Stift färbt um." +
                    (if (touch) "" else " Am Computer: Shift malt mit Stift 2, Alt mit 3, beide mit 4.") +
                    " Welche Farbe jeder Stift hat, stellst du " + (if (touch) "" else "mit ${settings.shortcutLabel(Command.COLORS)} oder ") +
                    "in der Konfiguration unter „Brett“ ein: aus 16 Farben oder als eigenen Code mit Namen, z. B. #7FFFD4 „Aquamarin“.",
            )
            if (!touch) {
                SubHeading("Tasten beim Zeichnen")
                Para(
                    "Solange gezeichnet wird (Zeichenmodus, eine Zeichnung zum Abfragen oder aus dem Gedächtnis), haben einzelne Tasten " +
                        "eine eigene Bedeutung – ohne ${settings.prefixLabel}. Steht der Cursor in einem Textfeld, landen Buchstaben dort; " +
                        "ein Tipp aufs Brett holt die Tasten zurück.",
                )
                for (draw in DrawKey.entries) ShortcutRow(settings.drawKeyOf(draw).toString(), draw.label, null)
                ShortcutRow("Esc", "Zeichnen beenden", null)
                ShortcutRow("← →  /  ↑ ↓", "Stift wechseln  /  Kreis oder Feld", null)
                Para("Belegen lassen sie sich in der Konfiguration unter „Tastenkürzel“.")
            }
            Para("So Gezeichnetes ist nur für dich, für den Moment: es wird nicht gespeichert und nie abgefragt. Das gilt im Editor wie beim Üben.")
            Bullet(
                "Wegwischen",
                (if (touch) "das Kürzel „Wegwischen“" else settings.shortcutLabel(Command.CLEAR)) +
                    " nimmt sofort alles Gezeichnete vom Brett, wie „Löschen“ in der Leiste. Gespeicherte Zeichnungen, die nur angezeigt werden, bleiben.",
            )
            if (!touch) {
                Bullet(
                    "Ausblenden",
                    "Shift+A (ohne ${settings.prefixLabel}, nicht in einem Textfeld) zeigt das Brett ohne alles Gezeichnete, " +
                        "auch ohne die Zeichnungen zum Abfragen; nochmal Shift+A zeigt es wieder. " +
                        "Weg ist dabei nichts. Sobald du zeichnest, ist alles wieder zu sehen.",
                )
            }
            SubHeading("Zeichnungen zum Abfragen")
            Para(
                "Im Editor (Durchgehen oder Bearbeiten) legt „+ Zeichnung hier“" + (if (touch) "" else " oder ${settings.shortcutLabel(Command.DRAW_KEPT)}") +
                    " eine Zeichnung an, die mit der Variante gespeichert wird. " +
                    "Der Rahmen wird blau: Antippen kreist ein Feld ein, Ziehen malt einen Pfeil, dasselbe nochmal nimmt es weg. " +
                    "Dazu kannst du eine Frage oder einen Namen eintragen („Wo sind die schwachen Felder?“).",
            )
            Para("Beim Durchgehen und Abspielen siehst du sie blau an ihrer Stellung. Ein Tipp auf eine Zeichnung in der Liste springt dorthin und bearbeitet sie.")
            Para(
                "Nach dem Puzzle (und nach den Theorie-Lücken) wird jede Zeichnung abgefragt: das Brett zeigt ihre Stellung, du zeichnest aus dem Gedächtnis, " +
                    "„Prüfen“ vergleicht. Grün war richtig, orange fehlte, rot war zu viel; jeder Fehler zählt wie ein falscher Zug. " +
                    "Der Stift zählt mit. "  +
                    "Ein Pfeil zählt nur in seiner Richtung. „Züge üben“ fragt keine Zeichnungen ab.",
            )
            Para("Ändern sich die Züge vor einer Zeichnung, passt sie nicht mehr zur Stellung und fällt weg.")
        }
        HelpTopic.PLAYER -> {
            Para("Wer sich eine Stellung oder Zugfolge nicht mehr merkt, lässt die Variante einfach ablaufen, statt jeden Zug anzuklicken.")
            Bullet("▶ Abspielen", "auf der Startseite bei einer Variante, oder im Editor direkt unter dem Brett. Es beginnt am Start (oder am Ende erneut von vorn) und geht von allein Zug um Zug weiter.")
            Bullet("⏸ Pause", "hält an der aktuellen Stellung an. Auch jeder Klick auf ein Feld, einen Pfeil oder einen Zug in der Liste hält den Player an.")
            Bullet("− / +", "weniger oder mehr Zeit pro Zug, gerade ${formatSeconds(settings.playDelayMs.toLong())} (zwischen ${formatSeconds(PLAY_DELAY_MIN.toLong())} und ${formatSeconds(PLAY_DELAY_MAX.toLong())}).")
            Bullet("Wiederholen", "beginnt nach dem letzten Zug wieder von vorn, bis du pausierst.")
            Para(
                "Als Kürzel: " + (if (touch) "ein Bereich beim Doppeltippen lässt sich auf „Abspielen“ legen." else "${settings.shortcutLabel(Command.PLAY)}.") +
                    " Das Gleiche stellst du auch in der Konfiguration ein.",
            )
            ConfigButton(vm, "Tempo einstellen")
        }
        HelpTopic.PLAYLIST -> {
            Para("Eine Playlist sind Varianten, die nacheinander drankommen, ohne dass du jede einzeln anklickst. Jede Eröffnung auf der Startseite ist schon eine – mit all ihren Varianten in der Reihenfolge der Liste. Dazu kannst du dir eigene zusammenstellen.")
            Bullet("▶ Abspielen", "zeigt die Stellungen: die Varianten laufen nacheinander mit dem Player ab. Die Endstellung bleibt kurz stehen, dann beginnt die nächste. „← Vorherige“ und „Nächste →“ springen, ⏸ hält an. Mit „Wiederholen“ fängt sie nach der letzten Variante von vorn an.")
            Bullet("Lernen", "fragt die Varianten nacheinander als Puzzle ab. Erst wenn du eins ohne Fehler gelöst hast, geht es mit „Gut – nächstes Puzzle“ weiter; mit Fehler kommt dasselbe Puzzle sofort nochmal. Oben steht, wo du bist („Puzzle 2 von 5“).")
            Para("Lernen mit Spaced Repetition bleibt davon unberührt: Playlists zählen nicht als Wiederholung.")
            SubHeading("Eigene Playlists")
            Bullet("+ Neue Playlist", "unter „Meine Playlists“: Varianten antippen – aus beliebigen Eröffnungen –, dann „Als Playlist speichern“ und einen Namen geben.")
            Bullet("Reihenfolge", "zeigt die Varianten der Playlist; ↑ und ↓ verschieben eine, ✕ nimmt sie heraus.")
            Bullet("Varianten ändern", "hakt die Varianten der Playlist an; weitere antippen oder abwählen, dann „Übernehmen“. Neue kommen ans Ende.")
            Bullet("Umbenennen, Löschen", "betreffen nur die Playlist, die Varianten bleiben.")
            SubHeading("Anpinnen")
            Bullet("Anpinnen", "holt eine Eröffnung ganz nach oben, über alle anderen. Bis zu ${Library.MAX_PINNED} Eröffnungen gehen; „Lösen“ stellt sie wieder an ihren Platz.")
            SubHeading("Auswählen und Löschen")
            Bullet("Auswählen", "bei einer Eröffnung zeigt an jeder Variante ein Kästchen; " + (if (touch) "antippen" else "anklicken") + " wählt sie aus. „Alle auswählen“ nimmt eine ganze Eröffnung dazu. Oben steht dann, was mit der Auswahl geht: als Playlist speichern, abspielen, lernen oder löschen. „Fertig“ beendet das Auswählen.")
            Bullet("Löschen", "bei einer Eröffnung löscht sie mit allen Varianten. Wie beim Löschen einer Variante fragt die App erst nach, und der Lernstand ist danach weg.")
        }
        HelpTopic.HORSE -> {
            Para("Unten rechts sitzt ein kleines Pferd. Es ersetzt am Handy die Tastenkürzel vom Computer.")
            Bullet("Antippen", "öffnet die Liste aller Kürzel; jeder Eintrag lässt sich dort auch direkt antippen.")
            Bullet("Halten", "wählt nach der Zeit. Der Ring ums Pferd füllt sich einmal pro Schritt, und daneben steht, was beim Loslassen passiert.")
            Bullet("Wegziehen", "den Finger vom Pferd wegziehen bricht ab.")
            SubHeading("Gerade eingestellt: ${formatSeconds(settings.holdStepMs.toLong())} pro Schritt")
            for (command in Command.entries.filter { (it.holdSteps ?: 0) > 0 }) {
                ShortcutRow(formatSeconds(command.holdSteps!!.toLong() * settings.holdStepMs) + " halten", command.label, null)
            }
            Spacer(Modifier.height(8.dp))
            Para("Zu schnell oder zu langsam? Die Haltezeit pro Schritt lässt sich zwischen ${formatSeconds(HOLD_STEP_MIN.toLong())} und ${formatSeconds(HOLD_STEP_MAX.toLong())} einstellen.")
            ConfigButton(vm, "Haltezeit einstellen")
        }
        HelpTopic.TAP_ZONES -> {
            Para(
                "Der Bildschirm ist in Bereiche geteilt, standardmäßig zwei Spalten und drei Reihen; wie viele es sein sollen, " +
                    "stellst du in der Konfiguration ein (1–$TAP_COLS_MAX Spalten, 1–$TAP_ROWS_MAX Reihen – zum Beispiel nur oben und unten). Zweimal schnell auf eine freie " +
                    "Stelle tippen führt aus, was diesem Bereich zugeordnet ist – auf jedem Bildschirm, auch mitten in einem Puzzle.",
            )
            Para(
                "Frei heißt: kein Knopf, kein Textfeld und nicht das Brett. Die funktionieren ganz normal weiter; " +
                    "auf dem Brett schaltet dreimal Tippen den Zeichenmodus um.",
            )
            SubHeading(if (settings.tapZonesOn) "Gerade eingestellt" else "Gerade ausgeschaltet – so wäre es eingestellt")
            ZoneGrid(settings, onPick = null)
            Spacer(Modifier.height(12.dp))
            ConfigButton(vm, "Bereiche einstellen")
        }
        HelpTopic.KEYBOARD -> {
            Para(
                "Erst ${settings.prefixLabel}, dann eine Taste – wie bei tmux. Geht überall, auch mitten in einem Puzzle: " +
                    "danach geht es dort weiter. Die Taste selbst landet nie in einem Textfeld.",
            )
            for (command in Command.entries) ShortcutRow(settings.shortcutLabel(command), command.label, null)
            SubHeading("Tasten ändern")
            Para(
                "Passt ein Buchstabe nicht, lässt er sich in der Konfiguration ändern: auf die Taste klicken, die neue drücken. " +
                    "Erlaubt sind Buchstaben, Ziffern und ?; ist die Taste schon vergeben, tauschen die beiden. " +
                    "Auch das B nach Strg lässt sich durch einen anderen Buchstaben ersetzen. " +
                    "Die Liste der Kürzel kommt immer auch mit ? oder h, falls man die eigene Belegung vergessen hat.",
            )
            Para(
                "${settings.prefixLabel} ist der Navigator zwischen den Modi: ein Buchstabe, der noch keinen Befehl hat, " +
                    "öffnet die Konfiguration bei den Tastenkürzeln, wo du ihn belegen kannst. " +
                    "Die oft gebrauchten Befehle liegen in der unteren Reihe (y x c v b n m), nah an Strg und B. " +
                    "Strg darf gedrückt bleiben: ${settings.prefixLabel} Strg+V ist dasselbe wie ${settings.prefixLabel} v, " +
                    "zweimal ${settings.prefixLabel} also ${settings.prefixLabel} ${settings.prefixKey}.",
            )
            ConfigButton(vm, "Tasten ändern")
            SubHeading("Weitere Tasten")
            for ((keys, label) in OTHER_KEYS) ShortcutRow(keys, label, null)
        }
        HelpTopic.LICHESS -> {
            val db = if (touch) "das Kürzel „Datenbank“" else settings.shortcutLabel(Command.EXPLORER)
            val notes = if (touch) "das Kürzel „Notizen“" else settings.shortcutLabel(Command.NOTES)
            SubHeading("Eröffnungsdatenbank")
            Para(
                "$db öffnet neben dem Brett lichess' Eröffnungsdatenbank für die Stellung, die gerade auf dem Brett steht: " +
                    "jeder Zug, der dort gespielt wurde, wie oft, und wie die Partien ausgingen (weiß · remis · schwarz). " +
                    "„Meister“ sind Partien starker Spieler über dem Brett, „lichess“ Online-Partien ab 1600 (Blitz und langsamer). " +
                    "Das Fenster geht mit dem Brett mit, ein Klick auf einen Zug spielt ihn im Editor. Nochmal $db schließt es.",
            )
            Para(
                "lichess gibt die Datenbank nur mit einem persönlichen API-Schlüssel heraus: auf ${Explorer.TOKEN_PAGE} einen neuen anlegen " +
                    "(kein Häkchen nötig) und in der Konfiguration unter „lichess“ einfügen. Jeder nimmt seinen eigenen; er bleibt auf dem Gerät.",
            )
            ConfigButton(vm, "Schlüssel eintragen")
            if (!touch) {
                SubHeading("Engine")
                Para(
                    "${settings.shortcutLabel(Command.ENGINE)} lässt eine Engine auf diesem Computer die Stellung auf dem Brett rechnen: die besten Linien " +
                        "mit Bewertung aus Sicht von Weiß (+0,31 heißt Weiß steht etwas besser, #3 Matt in drei), immer tiefer, bis sich die Stellung ändert. " +
                        "Ein Klick auf eine Linie spielt ihren ersten Zug im Editor.",
                )
                Para(
                    "FireHorse lädt keine Engine herunter. Unter Konfiguration → Engines gibst du den Pfad zu einer UCI-Engine an, " +
                        "die schon auf dem Computer ist (z. B. die Stockfish-Datei, die En Croissant installiert hat), oder legst sie in den Ordner " +
                        "${vm.engineFolder}. Mehrere Engines lassen sich einrichten und wählen; jede hat ihre eigenen Linien, Tiefe, Threads und Hash " +
                        "– „unbegrenzt“ rechnet immer weiter, eine feste Tiefe hält dort an.",
                )
            }
            SubHeading("Eröffnungsnotizen")
            Para(
                "$notes öffnet deine Notizen zur Eröffnung, die gerade offen ist – Pläne, typische Fallen, was für die ganze Eröffnung gilt. " +
                    "Oben wechselst du zu einer anderen Eröffnung oder zu „Allgemein“ für alles, was zu keiner bestimmten gehört. " +
                    "Gespeichert wird beim Wechseln und beim Schließen.",
            )
        }
        HelpTopic.DATA -> {
            Para("Alles wird nach jeder Änderung und jeder Antwort sofort gespeichert, in: ${vm.dataLocation}")
            if (vm.canTransfer) {
                Bullet("Alle exportieren", "schreibt Varianten und Lernstand in eine .json-Datei, z. B. um sie vom Computer aufs Handy zu bringen. „Exportieren“ bei einer Eröffnung auf der Startseite nimmt nur diese.")
                Bullet(
                    "Importieren",
                    "holt eine solche Datei herein. Danach fragt FireHorse: „Dazunehmen“ – neue Varianten kommen dazu, vorhandene werden aktualisiert, " +
                        "deine anderen bleiben. „Ersetzen“ – alle deine Varianten werden mit Lernstand gelöscht und nur die aus der Datei geladen.",
                )
            }
            if (touch) Para("Achtung: Deinstallieren löscht den App-Speicher und damit den Lernstand. Vorher exportieren.")
            ConfigButton(vm, "Zu Export und Import")
        }
    }
}

@Composable
private fun ConfigButton(vm: AppViewModel, text: String) {
    Spacer(Modifier.height(4.dp))
    OutlinedButton(onClick = vm::openConfig) { Text(text) }
}

@Composable
private fun Para(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 10.dp))
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SubHeading(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
}

/** "• **Title** – text" */
@Composable
private fun Bullet(title: String, text: String) {
    Row(Modifier.padding(bottom = 8.dp)) {
        Text("•  ", style = MaterialTheme.typography.bodyMedium)
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(title) }
                append(" – ")
                append(text)
            },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/**
 * The six pens: pick one, then its color on the little board of inks, or type in any color as
 * "#7FFFD4" with a name. A board shows all six as arrows and fields, as they will look. The same
 * in the configuration and in its own window (Ctrl+B k).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PenColors(vm: AppViewModel) {
    val colors = vm.library.settings.penColors
    var chosen by remember { mutableStateOf(0) }
    Text(
        "Sechs Stifte zum Zeichnen; beim Zeichnen wechseln ← → (am Handy die Leiste unten auf dem Brett). " +
            "Tippe einen Stift an, dann seine Farbe – aus dem Farbbrett oder als Code wie #7FFFD4. Schon Gezeichnetes nimmt die neue Farbe an.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        colors.forEachIndexed { i, color ->
            Box(
                Modifier.size(36.dp)
                    .border(3.dp, if (i == chosen) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(8.dp))
                    .padding(4.dp)
                    .background(color.solid, RoundedCornerShape(5.dp))
                    .clickable { chosen = i },
                contentAlignment = Alignment.Center,
            ) {
                Text("${i + 1}", color = color.onTop, fontWeight = FontWeight.SemiBold)
            }
        }
    }
    Spacer(Modifier.height(4.dp))
    Text("Stift ${chosen + 1}: ${colors[chosen].label} (${colors[chosen].hex})", style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // The inks laid out like a little board, four by four.
        Column {
            for (row in Ink.entries.chunked(4)) {
                Row {
                    for (ink in row) {
                        val current = ink.pen == colors[chosen]
                        Box(
                            Modifier.size(40.dp).background(ink.pen.solid)
                                .border(if (current) 3.dp else 0.dp, if (current) Color.White else Color.Transparent)
                                .clickable { vm.setPenColor(chosen, ink.pen) },
                        )
                    }
                }
            }
        }
        ChessBoard(
            position = Position.START,
            whiteBottom = true,
            lastMove = null,
            marks = emptyMap(),
            interactive = false,
            onMove = {},
            modifier = Modifier.size(220.dp),
            kept = PEN_PREVIEW.map(::KeptMark),
        )
    }
    Spacer(Modifier.height(12.dp))
    HexColor(vm, chosen, colors[chosen])
    TextButton(onClick = vm::resetPens) { Text("Standardfarben") }
}

/** Any color for pen [index], typed in as a code like "#7FFFD4", with a name for the feedback. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HexColor(vm: AppViewModel, index: Int, current: PenColor) {
    // Starts with what the pen has, again whenever another pen is picked or the color changes.
    var code by remember(index, current) { mutableStateOf(current.hex) }
    var name by remember(index, current) { mutableStateOf(current.label) }
    val typed = PenColor.parse(code)
    Text("Eigene Farbe", style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(4.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                .padding(3.dp).background(typed?.solid ?: Color.Transparent, RoundedCornerShape(4.dp)),
        )
        OutlinedTextField(
            code, { code = it },
            label = { Text("Code") }, placeholder = { Text("#7FFFD4") },
            singleLine = true, isError = typed == null,
            modifier = Modifier.typing(vm).width(130.dp).onPreviewKeyEvent(vm.shortcutKeys::handle),
        )
        OutlinedTextField(
            name, { name = it },
            label = { Text("Name") }, placeholder = { Text("Aquamarin") },
            singleLine = true,
            modifier = Modifier.typing(vm).width(170.dp).onPreviewKeyEvent(vm.shortcutKeys::handle),
        )
        Button(onClick = { vm.setPenHex(index, code, name) }, enabled = typed != null) { Text("Für Stift ${index + 1}") }
    }
    if (typed == null) {
        Text(
            "Kein Farbcode – sechs Zeichen 0–9 und A–F, z. B. #7FFFD4 (Aquamarin) oder #FF1493 (Pink).",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFE57373),
        )
    }
}

/** The pen colors in a window over whatever is open: Ctrl+B k. */
@Composable
internal fun PenColorsDialog(vm: AppViewModel) {
    AlertDialog(
        onDismissRequest = { vm.colorsOpen = false },
        title = { Text("Zeichenfarben") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { PenColors(vm) } },
        confirmButton = { Button(onClick = { vm.colorsOpen = false }) { Text("Fertig") } },
    )
}

/** One arrow and one field per pen, on the files a to f. */
private val PEN_PREVIEW: List<Mark> = (0 until Ink.PENS).flatMap { i ->
    val file = 'a' + i
    val field = parseSquare("${file}5")
    listOf(Mark(parseSquare("${file}2"), parseSquare("${file}4"), i), Mark(field, field, i, field = true))
}
