package repertoire.core

/**
 * What a pasted text turned out to be: a line of moves, possibly starting from a set-up position.
 * A bare FEN gives no [moves] at all — the continuation is then played in the editor.
 */
data class PastedLine(
    /** Position the line starts from; [Position.START_FEN] unless the text brought its own FEN. */
    val startFen: String,
    /** The main line's moves in UCI, counted from [startFen]. */
    val moves: List<String>,
    val opening: String,
    val name: String,
    val playWhite: Boolean,
    /** Games in the text after the first one; those are not taken over. */
    val otherGames: Int = 0,
) {
    val setUp: Boolean get() = startFen != Position.START_FEN
}

/**
 * Reads what people copy out of lichess, chess.com, a database or a book: a whole PGN with headers,
 * comments, side lines and a result, a bare move list ("1.e4 c5 2.Nf3"), a FEN, or a FEN followed by
 * moves. Only the main line is kept; side lines in brackets and comments are thrown away.
 */
object Pgn {
    private val HEADER = Regex("""\[\s*([A-Za-z0-9_]+)\s*"([^"]*)"\s*\]""")

    /** A "{ … }" comment. PGN does not nest them. Android's regex engine needs the closing brace escaped. */
    private val BRACE_COMMENT = Regex("""\{[^\}]*\}""")

    /** ";" to the end of the line, and a "%" escape taking up a whole line. */
    private val REST_OF_LINE = Regex("""(?m)(;|^[ \t]*%).*$""")

    /** A side line without another one inside it, so repeated replacing peels off nested brackets. */
    private val INNERMOST_VARIATION = Regex("""\([^()]*\)""")

    private val NAG = Regex("""\$\d+""")

    /** Each game of a multi-game PGN starts with its own [Event] tag. */
    private val GAME_START = Regex("""(?m)^[ \t]*\[Event[ \t]""")

    /** A "FEN:" label in front of a pasted position. */
    private val FEN_LABEL = Regex("""^(?i)fen\s*[:=]?\s*""")

    /** Event names that say nothing about the opening. */
    private val USELESS_EVENT = Regex("""(?i)^(live chess|(rated|casual|unrated).*|\?+|-+|)$""")

    private val FEN_FIELDS = listOf(
        Regex("^[wb]$"),
        Regex("^(-|[KQkq]{1,4})$"),
        Regex("^(-|[a-h][1-8])$"),
        Regex("^\\d+$"),
        Regex("^\\d+$"),
    )
    /**
     * What to assume for the fields a shortened FEN leaves out. Castling stays on because a pasted
     * position is usually an opening one; rights that do not fit the board are ignored anyway, since
     * castling is only generated with king and rook at home.
     */
    private val FEN_DEFAULTS = listOf("w", "KQkq", "-", "0", "1")

    /**
     * The line as PGN text, for the clipboard: the moves up to some position, numbered, and a
     * FEN header when it does not begin at the start of a game. Reads back with [parse].
     */
    fun export(startFen: String, numberedMoves: List<String>): String {
        val moves = compactMoves(numberedMoves)
        val header = if (startFen == Position.START_FEN) "" else "[SetUp \"1\"]\n[FEN \"$startFen\"]\n\n"
        return header + moves
    }

    /** lichess's four arrow colors, which are also the first four pens by default. */
    private val LICHESS_COLORS = listOf('G' to 0x15781B, 'R' to 0x882020, 'B' to 0x003088, 'Y' to 0xE68F00)

    /** The lichess color closest to [pen]: lichess (and ChessBase) only know these four. */
    private fun lichessColor(pen: PenColor): Char = LICHESS_COLORS.minBy { (_, rgb) ->
        val dr = (rgb shr 16 and 0xFF) - (pen.rgb shr 16 and 0xFF)
        val dg = (rgb shr 8 and 0xFF) - (pen.rgb shr 8 and 0xFF)
        val db = (rgb and 0xFF) - (pen.rgb and 0xFF)
        dr * dr + dg * dg + db * db
    }.first

    /** A comment may not close itself early, and a header value may not end in the middle. */
    private fun commentText(text: String) = text.replace('{', '(').replace('}', ')').trim()

    /** A drawing as lichess writes it into a comment: its question, then "[%csl Gd5][%cal Ge2e4]". */
    private fun drawingComment(d: Drawing, pens: List<PenColor>): String {
        fun code(m: Mark) = lichessColor(pens[m.color]) + squareName(m.from) + if (m.from == m.to) "" else squareName(m.to)
        val squares = d.marks.filter { it.from == it.to }.joinToString(",", transform = ::code)
        val arrows = d.marks.filter { it.from != it.to }.joinToString(",", transform = ::code)
        return listOf(
            commentText(d.question),
            if (squares.isEmpty()) "" else "[%csl $squares]",
            if (arrows.isEmpty()) "" else "[%cal $arrows]",
        ).filter { it.isNotEmpty() }.joinToString(" ")
    }

    /**
     * All of [variant] as PGN: opening and name as headers, every move, each drawing as a comment
     * after its move (arrows and circles as lichess writes them, so they show up there), and the
     * theory at the end with its gaps filled in. [pens] are what the pens look like, for the
     * nearest of lichess's colors. Reads back with [parse], which takes the moves and names.
     */
    fun exportAll(variant: Variant, pens: List<PenColor> = Ink.DEFAULT): String {
        fun header(value: String) = value.replace('"', '\'').trim()
        val title = listOf(variant.opening, variant.name).map(::header).filter { it.isNotEmpty() }
        val headers = buildList {
            add("Event" to title.joinToString(": ").ifEmpty { "?" })
            if (variant.opening.isNotBlank()) add("Opening" to header(variant.opening))
            if (variant.name.isNotBlank()) add("Variation" to header(variant.name))
            add("Result" to "*")
            if (variant.setUp) {
                add("SetUp" to "1")
                add("FEN" to variant.startFen)
            }
        }

        val parts = mutableListOf<String>()
        // After a comment, a black move needs its number again ("3... Nf6").
        var numberNext = true
        fun comment(text: String) {
            if (text.isEmpty()) return
            parts += "{ $text }"
            numberNext = true
        }
        fun drawingsAt(ply: Int) = comment(variant.drawings.filter { it.ply == ply }.joinToString(" ") { drawingComment(it, pens) })

        drawingsAt(0)
        variant.numberedSan().forEachIndexed { i, numbered ->
            val black = numbered.substringBefore(' ').endsWith("...")
            parts += if (black && !numberNext) numbered.substringAfter(' ') else numbered
            numberNext = false
            drawingsAt(i + 1)
        }
        comment(commentText(Cloze.solved(variant.theory)))
        parts += "*"
        return headers.joinToString("\n") { (k, v) -> "[$k \"$v\"]" } + "\n\n" + parts.joinToString(" ")
    }

    /** Reads [text]; throws [IllegalArgumentException] with a German explanation if it does not work out. */
    fun parse(text: String): PastedLine {
        // Web pages hand out non-breaking spaces, and some editors a byte-order mark.
        val cleaned = text.replace('\u00A0', ' ').replace('\u202F', ' ').replace("\uFEFF", "")
        require(cleaned.isNotBlank()) { "Da ist kein Text zum Einlesen." }
        val games = split(cleaned)
        return parseGame(games.first()).copy(otherGames = games.size - 1)
    }

    private fun split(text: String): List<String> {
        val starts = GAME_START.findAll(text).map { it.range.first }.toList()
        if (starts.size <= 1) return listOf(text)
        return starts.mapIndexed { i, from -> text.substring(from, starts.getOrNull(i + 1) ?: text.length) }
    }

    private fun parseGame(text: String): PastedLine {
        val headers = HEADER.findAll(text).associate { it.groupValues[1] to it.groupValues[2].trim() }
        var body = HEADER.replace(text, " ")
        body = REST_OF_LINE.replace(body, " ")
        body = BRACE_COMMENT.replace(body, " ")
        while (INNERMOST_VARIATION.containsMatchIn(body)) body = INNERMOST_VARIATION.replace(body, " ")
        body = NAG.replace(body, " ")

        val header = headers["FEN"]?.takeIf { it.isNotBlank() }
        val (startFen, movesText) = when {
            header != null -> normalizeFen(header) to body
            else -> splitOffFen(body) ?: (Position.START_FEN to body)
        }
        val start = try {
            Position.fromFen(startFen)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException(e.message ?: "Ungültige FEN: $startFen")
        }
        start.problem()?.let { throw IllegalArgumentException(it) }

        val moves = Exchange.parseMoves(movesText, start)
        require(moves.isNotEmpty() || start.fen != Position.START_FEN) {
            "Darin sind weder Züge noch eine Stellung zu finden. Erwartet wird eine PGN " +
                "(„1. e4 c5 2. Nf3“) oder eine FEN („rnbqkbnr/pp1ppppp/… w KQkq - 0 2“)."
        }
        val (opening, name) = namesFrom(headers)
        return PastedLine(
            startFen = start.fen,
            moves = moves,
            opening = opening,
            name = name,
            // Whoever played the last move is the side you want to be: a Najdorf line ends on Black's a6.
            playWhite = if (moves.isEmpty()) start.whiteToMove else start.whiteToMove == (moves.size % 2 == 1),
        )
    }

    /**
     * A FEN at the start of [body], with the fields it leaves out filled in, plus whatever follows it.
     * Null if it does not start with a position.
     */
    private fun splitOffFen(body: String): Pair<String, String>? {
        val tokens = FEN_LABEL.replace(body.trim(), "").split(Regex("\\s+")).filter { it.isNotEmpty() }
        val placement = tokens.firstOrNull() ?: return null
        // The one part every FEN has and no move ever looks like: eight ranks separated by slashes.
        if (placement.count { it == '/' } != 7) return null
        val fields = mutableListOf(placement)
        for (field in FEN_FIELDS) {
            if (!field.matches(tokens.getOrNull(fields.size) ?: break)) break
            fields += tokens[fields.size]
        }
        val used = fields.size
        while (fields.size < 6) fields += FEN_DEFAULTS[fields.size - 1]
        return fields.joinToString(" ") to tokens.drop(used).joinToString(" ")
    }

    /** Fills in the fields a FEN from a PGN header may leave out. */
    private fun normalizeFen(fen: String): String {
        val fields = fen.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.toMutableList()
        while (fields.size in 1..5) fields += FEN_DEFAULTS[fields.size - 1]
        return fields.joinToString(" ")
    }

    /** "Sicilian Defense: Najdorf" → „Sicilian Defense“ and „Najdorf“; whatever the headers give away. */
    private fun namesFrom(headers: Map<String, String>): Pair<String, String> {
        val variation = headers["Variation"]?.takeUnless { it.isBlank() || it == "?" }
        val title = headers["Opening"]?.takeUnless { it.isBlank() || it == "?" }
            ?: headers["Event"]?.takeUnless { USELESS_EVENT.matches(it) }
            ?: return "" to (variation ?: "")
        if (variation != null) return title to variation
        val cut = title.indexOf(": ")
        return if (cut > 0) title.take(cut).trim() to title.substring(cut + 2).trim() else title to ""
    }
}
