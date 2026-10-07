package repertoire.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** The colors to draw with, as on lichess; [code] is how a color is saved. */
enum class MarkColor(val code: String, val label: String) {
    GREEN("green", "grün"),
    RED("red", "rot"),
    BLUE("blue", "blau"),
    YELLOW("yellow", "gelb"),
}

/**
 * Something drawn on the board: an arrow from [from] to [to], or on a single square (the same
 * [from] and [to]) a circle, or with [field] the whole square filled in.
 */
@Serializable(with = MarkSerializer::class)
data class Mark(val from: Int, val to: Int, val color: MarkColor = MarkColor.GREEN, val field: Boolean = false) {
    init {
        require(!field || from == to) { "Ein Feld ist kein Pfeil" }
    }

    val circle: Boolean get() = from == to && !this.field

    /** Where it is and what it is, without the color: drawing the same shape in another color replaces it. */
    val shape: Mark get() = copy(color = MarkColor.GREEN)

    /** "d5" for a circle, "#d5" for a field, "e2e4" for an arrow, "red:" in front for a color other than green, as it is saved. */
    override fun toString(): String {
        val body = when {
            field -> "#" + squareName(from)
            from == to -> squareName(from)
            else -> squareName(from) + squareName(to)
        }
        return if (color == MarkColor.GREEN) body else "${color.code}:$body"
    }

    /** "Kreis d5", "rotes Feld d5", "blauer Pfeil e2→e4", for feedback. */
    fun describe(): String {
        val (noun, ending) = when {
            field -> "Feld" to "es"
            from == to -> "Kreis" to "er"
            else -> "Pfeil" to "er"
        }
        val where = if (from == to) squareName(from) else "${squareName(from)}→${squareName(to)}"
        return (if (color == MarkColor.GREEN) "" else color.label + ending + " ") + "$noun $where"
    }

    companion object {
        fun parse(text: String): Mark {
            val (colorCode, rest) = if (':' in text) text.split(':', limit = 2).let { it[0] to it[1] } else null to text
            val color = colorCode?.let { c -> MarkColor.entries.firstOrNull { it.code == c } ?: throw IllegalArgumentException("Unbekannte Farbe „$c“") }
                ?: MarkColor.GREEN
            val field = rest.startsWith("#")
            val squares = rest.removePrefix("#")
            require(squares.length == 2 || (squares.length == 4 && !field)) { "„$text“ ist weder ein Feld noch ein Pfeil" }
            val from = parseSquare(squares.substring(0, 2))
            return Mark(from, if (squares.length == 4) parseSquare(squares.substring(2)) else from, color, field)
        }
    }
}

object MarkSerializer : KSerializer<Mark> {
    override val descriptor = PrimitiveSerialDescriptor("repertoire.Mark", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Mark) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): Mark = Mark.parse(decoder.decodeString())
}

/**
 * Arrows and circles kept with a variant, at the position after its first [ply] moves. They are
 * seen when going through the variant and, after the puzzle is solved, drawn from memory.
 */
@Serializable
data class Drawing(
    val ply: Int,
    val marks: List<Mark>,
    /** What is asked, e.g. "Wo sind die schwachen Felder?"; may be empty. */
    val question: String = "",
)

/** How a drawing from memory compares to the saved one. */
data class DrawingCheck(val right: Set<Mark>, val missing: Set<Mark>, val extra: Set<Mark>) {
    val correct: Boolean get() = missing.isEmpty() && extra.isEmpty()

    companion object {
        /** The color counts, and an arrow only in its direction; the order in which things are drawn does not. */
        fun of(saved: Drawing, drawn: Collection<Mark>): DrawingCheck {
            val want = saved.marks.toSet()
            val got = drawn.toSet()
            return DrawingCheck(want intersect got, want - got, got - want)
        }
    }
}

/**
 * The marks after drawing [mark], as on lichess: the same thing twice takes it away, the same shape
 * in another color takes the new color, anything else is added.
 */
fun List<Mark>.toggle(mark: Mark): List<Mark> = when {
    mark in this -> this - mark
    any { it.shape == mark.shape } -> map { if (it.shape == mark.shape) mark else it }
    else -> this + mark
}
