package repertoire.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import repertoire.core.Settings

/** The six areas of the phone's screen for double taps: two columns and three rows, read like a book. */
enum class TapZone(val label: String, val short: String) {
    TOP_LEFT("oben links", "oben l."),
    TOP_RIGHT("oben rechts", "oben r."),
    MIDDLE_LEFT("Mitte links", "Mitte l."),
    MIDDLE_RIGHT("Mitte rechts", "Mitte r."),
    BOTTOM_LEFT("unten links", "unten l."),
    BOTTOM_RIGHT("unten rechts", "unten r."),
    ;

    companion object {
        fun at(position: Offset, size: IntSize): TapZone {
            val column = if (position.x < size.width / 2f) 0 else 1
            val row = (position.y / size.height * 3).toInt().coerceIn(0, 2)
            return entries[row * 2 + column]
        }
    }
}

/** What a double tap into [zone] runs; null if nothing. */
fun Settings.commandAt(zone: TapZone): Command? =
    tapZones.getOrNull(zone.ordinal)?.let { name -> Command.entries.firstOrNull { it.name == name } }

/** The first zone that runs [command], for the list of shortcuts. */
fun Settings.zoneOf(command: Command): TapZone? =
    if (tapZonesOn) TapZone.entries.firstOrNull { commandAt(it) == command } else null

/** [tapZones] with [zone] set to [command] (null: nothing). */
fun Settings.withZone(zone: TapZone, command: Command?): Settings {
    val zones = List(TapZone.entries.size) { tapZones.getOrElse(it) { "" } }.toMutableList()
    zones[zone.ordinal] = command?.name ?: ""
    return copy(tapZones = zones)
}

/**
 * Calls [onDoubleTap] with the zone of two quick taps into the same zone. Only taps that nothing
 * else used count: buttons, text fields and the board consume theirs, so they work as before and
 * a double tap only does something on a free spot. Nothing is consumed here.
 */
fun Modifier.doubleTapZones(onDoubleTap: (TapZone) -> Unit): Modifier = pointerInput(Unit) {
    var lastZone: TapZone? = null
    var lastUp = 0L
    awaitEachGesture {
        // The default pass comes after the children's, so what they used is consumed already.
        val down = awaitFirstDown(requireUnconsumed = false)
        // Null when the pointer was consumed, e.g. for scrolling, or left the screen.
        val up = if (down.isConsumed) null else waitForUpOrCancellation()
        if (up == null ||
            (up.position - down.position).getDistance() > viewConfiguration.touchSlop ||
            up.uptimeMillis - down.uptimeMillis >= viewConfiguration.longPressTimeoutMillis
        ) {
            lastZone = null
            return@awaitEachGesture
        }
        val zone = TapZone.at(down.position, size)
        if (zone == lastZone && down.uptimeMillis - lastUp <= viewConfiguration.doubleTapTimeoutMillis) {
            lastZone = null
            onDoubleTap(zone)
        } else {
            lastZone = zone
            lastUp = up.uptimeMillis
        }
    }
}
