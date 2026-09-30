package repertoire.ui

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import repertoire.core.Settings

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
    BOARD("Auf dem Brett zeichnen", "Pfeile und Kreise, Zeichenmodus"),
    HORSE("Das Pferd", "Kürzel am Handy: antippen und halten", touch = true),
    TAP_ZONES("Doppeltippen", "Sechs Bereiche des Bildschirms für Kürzel", touch = true),
    KEYBOARD("Tastenkürzel", "Strg+B und die übrigen Tasten", touch = false),
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
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
                when {
                    prefs is Prefs.Help && prefs.topic != null -> HelpPage(vm, prefs, touch)
                    prefs is Prefs.Help -> HelpIndex(vm, touch)
                    else -> ConfigPage(vm, touch)
                }
                HorseClearance()
            }
        }
    }
}

// ---- Configuration -----------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConfigPage(vm: AppViewModel, touch: Boolean) {
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
    if (touch) {
        val step = settings.holdStepMs
        Section("Das Pferd", onHelp = { vm.openHelp(HelpTopic.HORSE) }) {
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
        Section("Doppeltippen", onHelp = { vm.openHelp(HelpTopic.TAP_ZONES) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Zweimal auf eine freie Stelle tippen führt ein Kürzel aus", modifier = Modifier.weight(1f))
                Switch(settings.tapZonesOn, onCheckedChange = vm::setTapZonesOn)
            }
            if (settings.tapZonesOn) {
                Spacer(Modifier.height(6.dp))
                Hint("Der Bildschirm in sechs Bereichen. Antippen, um festzulegen, was ein Bereich macht:")
                Spacer(Modifier.height(8.dp))
                ZoneGrid(settings, onPick = vm::setTapZone)
            }
        }
    } else {
        Section("Tastenkürzel", onHelp = { vm.openHelp(HelpTopic.KEYBOARD) }) {
            Text("Erst Strg+B, dann eine Taste. Strg+B ? zeigt alle.")
        }
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
        for (row in 0..2) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (column in 0..1) {
                    val zone = TapZone.entries[row * 2 + column]
                    ZoneTile(zone, settings.commandAt(zone), onPick, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ZoneTile(zone: TapZone, command: Command?, onPick: ((TapZone, Command?) -> Unit)?, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(
        modifier.height(64.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .then(if (onPick != null) Modifier.clickable { open = true } else Modifier)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Column {
            Text(zone.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    "Kürzel erreichst du mit Strg+B und einer Taste; Strg+B ? zeigt alle."
                },
            )
        }
        HelpTopic.VARIANTS -> {
            Bullet("+ Neue Variante", "Züge vom Start bis zur Stellung spielen, Eröffnung und Namen eintragen, die Farbe wählen, mit der du spielst, speichern.")
            Bullet("Weiterführen", "übernimmt die Züge einer vorhandenen Variante, dann hängst du nur noch an, was anders ist.")
            Bullet("Durchgehen", "öffnet die Variante am Start. Mit |◀ ◀ ▶ ▶| oder einem Tipp auf einen Zug in der Liste gehst du die Züge durch – nichts wird dabei gelöscht.")
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
            Bullet("Gut / Weiter", "am Ende: „Gut“, oder „Weiter“ nach einem Fehler." + if (touch) "" else " Am Mac gehen auch Enter und Leertaste.")
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
                    (if (touch) "." else "; am Mac geht auch die rechte Maustaste.") +
                    " Derselbe Pfeil nochmal nimmt ihn wieder weg; mit dem nächsten Zug oder der nächsten Berührung des Bretts ist alles wieder sauber.",
            )
            Bullet(
                "Zeichenmodus",
                "dreimal schnell aufs Brett tippen" + (if (touch) "" else " oder Strg+B m") + ". Ein grüner Rahmen zeigt ihn an: " +
                    "Antippen kreist ein Feld ein, Ziehen malt einen Pfeil, und Züge sind gesperrt. „Löschen“ wischt alles weg, " +
                    "„Fertig“ oder nochmal dreimal tippen beendet ihn.",
            )
            Para("Gezeichnetes wird nicht gespeichert. Das gilt im Editor wie beim Üben.")
        }
        HelpTopic.HORSE -> {
            Para("Unten rechts sitzt ein kleines Pferd. Es ersetzt am Handy die Tastenkürzel vom Mac.")
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
                "Der Bildschirm ist in sechs Bereiche geteilt: zwei Spalten, drei Reihen. Zweimal schnell auf eine freie " +
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
            Para("Erst Strg+B, dann eine Taste – wie bei tmux. Geht überall, auch mitten in einem Puzzle: danach geht es dort weiter.")
            for (command in Command.entries) ShortcutRow("Strg+B ${command.key}", command.label, null)
            SubHeading("Weitere Tasten")
            for ((keys, label) in OTHER_KEYS) ShortcutRow(keys, label, null)
        }
        HelpTopic.DATA -> {
            Para("Alles wird nach jeder Änderung und jeder Antwort sofort gespeichert, in: ${vm.dataLocation}")
            if (vm.canTransfer) {
                Bullet("Alle exportieren", "schreibt Varianten und Lernstand in eine .json-Datei, z. B. um sie vom Mac aufs Handy zu bringen. „Exportieren“ bei einer Eröffnung auf der Startseite nimmt nur diese.")
                Bullet("Importieren", "holt eine solche Datei herein: neue Varianten kommen dazu, vorhandene werden aktualisiert.")
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
