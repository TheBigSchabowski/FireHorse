package repertoire.core

/**
 * Links from a theory text to a move of the variant: "@8. Ng4" (White's or Black's 8th move,
 * whichever is Ng4) or "@8... Ng4" (only Black's). Clicking one shows the board after that move.
 */
sealed interface TheoryText {
    data class Plain(val text: String) : TheoryText

    /** [ply]: number of moves played in the variant up to and including this one; null if the variant has no such move. */
    data class Ref(val label: String, val ply: Int?) : TheoryText
}

object MoveRefs {
    private val REF = Regex("""@(\d+)\s*(\.\.\.|…|\.)\s*([KQRBNOa-h0-9][A-Za-z0-9=+#-]*[A-Za-z0-9+#])""")

    fun split(text: String, variant: Variant): List<TheoryText> {
        val parts = mutableListOf<TheoryText>()
        var last = 0
        for (m in REF.findAll(text)) {
            if (m.range.first > last) parts += TheoryText.Plain(text.substring(last, m.range.first))
            val (number, dots, san) = m.destructured
            val ply = resolve(variant, number.toInt(), blackOnly = dots != ".", san)
            val label = if (ply != null) numberedSan(variant.positions[ply - 1], Move.fromUci(variant.moves[ply - 1])) else m.value.drop(1)
            parts += TheoryText.Ref(label, ply)
            last = m.range.last + 1
        }
        if (last < text.length) parts += TheoryText.Plain(text.substring(last))
        return parts
    }

    /** Markers in [text] that name no move of [variant], e.g. "8. Ng4" after a typo. */
    fun unresolved(text: String, variant: Variant): List<String> =
        split(text, variant).filterIsInstance<TheoryText.Ref>().filter { it.ply == null }.map { it.label }

    fun resolve(variant: Variant, number: Int, blackOnly: Boolean, san: String): Int? {
        val wanted = normalize(san)
        for (i in variant.moves.indices) {
            val before = variant.positions[i]
            if (before.fullmoveNumber != number || (blackOnly && before.whiteToMove)) continue
            if (normalize(before.san(Move.fromUci(variant.moves[i]))) == wanted) return i + 1
        }
        return null
    }

    /** Check signs are optional in a marker, and castling may be written with zeros. */
    private fun normalize(san: String) = san.trimEnd('+', '#').replace('0', 'O')

    /** A marker written right before the cursor, to switch between White's and Black's move. */
    private val TYPED_MARKER = Regex("""@(\d+)(\.\.\.|\.) ?[A-Za-z0-9=+#-]+$""")

    /** Just a move number ("7", "@7" or "7.") right before the cursor. */
    private val TYPED_NUMBER = Regex("""(?<![^\s(])@?(\d+)\.?$""")

    /**
     * Tab completion in the theory field: "7" becomes "@7. Bg5" (White's 7th move of [moves]),
     * a second Tab turns it into "@7... h6" and a third back into White's. [before] is the text up to
     * the cursor; returns where the replaced part starts and what replaces it, or null if nothing fits.
     */
    fun complete(before: String, moves: List<Move>): Pair<Int, String>? {
        val marker = TYPED_MARKER.find(before)
        // After White's marker comes Black's move, otherwise White's.
        val (match, number, wantBlack) = if (marker != null) {
            Triple(marker, marker.groupValues[1].toInt(), marker.groupValues[2] == ".")
        } else {
            val m = TYPED_NUMBER.find(before) ?: return null
            Triple(m, m.groupValues[1].toInt(), false)
        }
        if (number < 1) return null
        val white = (number - 1) * 2
        // If the line ends before the wanted side's move, stay on the other side.
        val index = (if (wantBlack) listOf(white + 1, white) else listOf(white)).firstOrNull { it < moves.size } ?: return null
        return match.range.first to "@" + numberedSan(moves.take(index).fold(Position.START) { p, m -> p.play(m) }, moves[index])
    }

    /** "@8. Ng4" for the last move of [moves], ready to paste into the theory. */
    fun markerFor(moves: List<Move>): String? {
        val last = moves.lastOrNull() ?: return null
        val before = moves.dropLast(1).fold(Position.START) { p, m -> p.play(m) }
        return "@" + numberedSan(before, last)
    }
}
