package repertoire.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import repertoire.core.currentTimeMillis
import repertoire.shared.resources.Res
import repertoire.shared.resources.wn

/** True on a phone: the horse button instead of keyboard shortcuts. */
val LocalTouch = staticCompositionLocalOf { false }

/** Room at the end of a scrolling screen, so its last lines can be scrolled out from under the horse. */
@Composable
fun HorseClearance() {
    if (LocalTouch.current) Spacer(Modifier.height(72.dp))
}

/**
 * What can be reached from anywhere: with Ctrl+B and then [key] on the keyboard, or by holding
 * the horse button for [holdSeconds] on a phone (a short tap opens the list).
 */
enum class Command(val key: Char, val label: String, val holdSeconds: Int) {
    SHORTCUTS('?', "Alle Kürzel anzeigen", 0),
    DRAW('m', "Zeichnen auf dem Brett an/aus", 1),
    NEW_VARIANT('c', "Neue Variante", 2),
    PASTE_PGN('p', "PGN einfügen", 3),
    PASTE_FEN('f', "FEN einfügen", 4),
    ;

    companion object {
        /** The command the horse runs when it is let go after [heldMs]. */
        fun forHold(heldMs: Long): Command {
            val seconds = (heldMs / 1000).toInt().coerceAtMost(entries.maxOf { it.holdSeconds })
            return entries.first { it.holdSeconds == seconds }
        }
    }
}

/** The other shortcuts, for the list: keyboard, then what they do. */
private val OTHER_KEYS = listOf(
    "Cmd/Strg+T" to "Theorie öffnen (beim Eingeben einer Variante)",
    "← / →" to "Züge der Variante durchgehen",
    "Cmd/Strg+Shift+C" to "Auswahl in der Theorie zur Lücke machen",
    "8 + Tab" to "„@8. Ng4“ in der Theorie ergänzen, nochmal Tab für Schwarz",
    "Enter" to "Lücken prüfen, dann „Gut“ / „Weiter“",
    "Leertaste" to "„Gut“ / „Weiter“, wenn alles geprüft ist",
    "Rechtsklick" to "Ziehen malt einen Pfeil aufs Brett, auf einem Feld einen Kreis",
)

/** The same on a phone, without a keyboard. */
private val OTHER_TOUCH = listOf(
    "Feld halten" to "Pfeil ziehen oder Feld einkreisen",
    "3× tippen" to "aufs Brett: Zeichenmodus an/aus",
)

/**
 * Tmux-style prefix keys: Ctrl+B, then a letter. The keys that belong to a shortcut never reach a
 * text field, so "p" is not typed into the theory while it has the focus.
 */
class ShortcutKeys(private val vm: AppViewModel) {
    /** A key that was used for a shortcut: its typed character and its release are swallowed too. */
    private var swallow: Key? = null

    fun handle(event: KeyEvent): Boolean {
        swallow?.let { key ->
            when {
                // The next key: handled as usual.
                event.type == KeyEventType.KeyDown && event.key != key -> swallow = null
                // Letting go of Ctrl or Shift is none of our business.
                event.type == KeyEventType.KeyUp && event.key != key -> return false
                else -> {
                    if (event.type == KeyEventType.KeyUp) swallow = null
                    return true
                }
            }
        }
        if (event.type != KeyEventType.KeyDown) return false

        if (event.key == Key.B && event.isCtrlPressed && !event.isMetaPressed && !event.isAltPressed) {
            vm.prefix = !vm.prefix
            swallow = event.key
            return true
        }
        if (!vm.prefix || event.key in MODIFIERS) return false

        vm.prefix = false
        swallow = event.key
        if (event.key == Key.Escape) return true
        val command = when (event.key) {
            Key.P -> Command.PASTE_PGN
            Key.F -> Command.PASTE_FEN
            Key.C -> Command.NEW_VARIANT
            Key.M -> Command.DRAW
            Key.H, Key.Slash -> Command.SHORTCUTS
            // "?" sits on different keys depending on the layout (Shift+ß on a German keyboard).
            else -> Command.SHORTCUTS.takeIf { event.utf16CodePoint == '?'.code }
        }
        if (command != null) vm.run(command) else vm.flash = "Strg+B, dann ? zeigt, welche Kürzel es gibt."
        return true
    }

    private companion object {
        val MODIFIERS = setOf(
            Key.ShiftLeft, Key.ShiftRight, Key.CtrlLeft, Key.CtrlRight,
            Key.AltLeft, Key.AltRight, Key.MetaLeft, Key.MetaRight,
        )
    }
}

private val PILL = Color(0xE6151515)

/** Everything that floats above the screens: the prefix hint, short messages, the list and the horse. */
@Composable
fun BoxScope.ShortcutOverlay(vm: AppViewModel) {
    val touch = LocalTouch.current
    if (vm.showShortcuts) ShortcutList(vm, touch)

    Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        vm.flash?.let { text ->
            LaunchedEffect(text) {
                delay(2500)
                vm.flash = null
            }
            Pill(text)
            Spacer(Modifier.height(8.dp))
        }
        if (vm.prefix) {
            Pill("Strg+B …   " + Command.entries.joinToString("  ·  ") { "${it.key} ${it.short}" })
        }
    }
    if (touch) HorseButton(vm::run, Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 16.dp))
}

private val Command.short: String get() = when (this) {
    Command.SHORTCUTS -> "Liste"
    Command.DRAW -> "Zeichnen"
    Command.NEW_VARIANT -> "Variante"
    Command.PASTE_PGN -> "PGN"
    Command.PASTE_FEN -> "FEN"
}

@Composable
private fun Pill(text: String) {
    Text(
        text,
        color = Color.White,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = 16.dp).background(PILL, RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** Every shortcut; the commands can also be clicked, which is how they are found on a phone. */
@Composable
private fun ShortcutList(vm: AppViewModel, touch: Boolean) {
    AlertDialog(
        onDismissRequest = { vm.showShortcuts = false },
        title = { Text(if (touch) "Das Pferd" else "Tastenkürzel") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    if (touch) {
                        "Kurz tippen öffnet diese Liste, gedrückt halten wählt nach der Zeit – beim Halten steht daneben, was beim Loslassen passiert. Wegziehen bricht ab."
                    } else {
                        "Erst Strg+B drücken, dann die Taste. Geht überall, auch mitten in einem Puzzle: danach geht es dort weiter."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                for (command in Command.entries.filter { it != Command.SHORTCUTS }) {
                    val key = if (touch) "${command.holdSeconds} s halten" else "Strg+B ${command.key}"
                    ShortcutRow(key, command.label) { vm.run(command) }
                }
                Spacer(Modifier.height(12.dp))
                for ((key, label) in if (touch) OTHER_TOUCH else OTHER_KEYS) ShortcutRow(key, label, null)
            }
        },
        confirmButton = { TextButton(onClick = { vm.showShortcuts = false }) { Text("Schließen") } },
    )
}

@Composable
private fun ShortcutRow(key: String, label: String, onClick: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            key,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodyMedium,
            // One width for the whole column, so the descriptions line up.
            modifier = Modifier.width(if (LocalTouch.current) 124.dp else 170.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (onClick != null) FontWeight.SemiBold else FontWeight.Normal,
            color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * The phone's shortcut key: a small knight. A tap opens the list; holding it runs a [Command]
 * after [Command.holdSeconds], and the pill next to it says which one as the ring fills up.
 */
@Composable
private fun HorseButton(onCommand: (Command) -> Unit, modifier: Modifier = Modifier) {
    var held by remember { mutableStateOf<Long?>(null) }
    var cancelled by remember { mutableStateOf(false) }
    val ring = MaterialTheme.colorScheme.primary
    val last = Command.entries.maxOf { it.holdSeconds }

    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        held?.let { ms ->
            Pill(if (cancelled) "Loslassen bricht ab" else "Loslassen: " + Command.forHold(ms).label)
        }
        Box(
            Modifier.size(52.dp)
                .background(PILL, CircleShape)
                .border(1.dp, Color(0xFF4A4A4A), CircleShape)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        val start = currentTimeMillis()
                        val slop = 48.dp.toPx()
                        held = 0L
                        cancelled = false
                        while (true) {
                            // Wakes up regularly even without pointer events, so the ring keeps filling.
                            val change = withTimeoutOrNull(50) { awaitPointerEvent() }
                                ?.changes?.firstOrNull { it.id == down.id }
                            held = currentTimeMillis() - start
                            if (change == null) continue
                            change.consume()
                            val p = change.position
                            if (p.x < -slop || p.y < -slop || p.x > size.width + slop || p.y > size.height + slop) cancelled = true
                            if (!change.pressed) break
                        }
                        val ms = held ?: 0L
                        held = null
                        if (!cancelled) onCommand(Command.forHold(ms))
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Image(painterResource(Res.drawable.wn), contentDescription = "Kürzel", modifier = Modifier.size(34.dp))
            held?.let { ms ->
                Canvas(Modifier.fillMaxSize()) {
                    val sweep = if (ms >= last * 1000L) 360f else (ms % 1000) / 1000f * 360f
                    val stroke = 3.dp.toPx()
                    drawArc(
                        if (cancelled) Color.Gray else ring, -90f, sweep, useCenter = false,
                        topLeft = Offset(stroke / 2, stroke / 2), size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
            }
        }
    }
}
