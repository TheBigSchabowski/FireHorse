package repertoire.core

import kotlinx.serialization.Serializable

/** Where the games of a tree came from; [code] is what openingtree writes into its files for it. */
enum class GameSite(val label: String, val code: String) {
    LICHESS("lichess", "lichess"),
    CHESS_COM("chess.com", "chesscom"),
    PGN_FILE("PGN-Datei", "pgnfile"),
    ;

    companion object {
        /** The site of an openingtree file; anything FireHorse cannot download from again counts as a file. */
        fun of(code: String): GameSite = entries.firstOrNull { it.code == code } ?: PGN_FILE
    }
}

/** How fast a game was played, by lichess's limits on the estimated time (base + 40 × increment). */
enum class GameSpeed(val label: String, val lichess: String) {
    BULLET("Bullet", "bullet"),
    BLITZ("Blitz", "blitz"),
    RAPID("Rapid", "rapid"),
    CLASSICAL("Klassisch", "classical"),
    CORRESPONDENCE("Fern", "correspondence"),
    ;

    companion object {
        /**
         * The speed of a PGN "TimeControl" ("180+2", "600", "1/259200" for chess.com's daily games,
         * "-" for none); null if it cannot be told.
         */
        fun ofTimeControl(timeControl: String?): GameSpeed? {
            val tc = timeControl?.trim().orEmpty()
            if (tc == "-" || tc.contains('/')) return CORRESPONDENCE
            val base = tc.substringBefore('+').toIntOrNull() ?: return null
            val increment = tc.substringAfter('+', "0").toIntOrNull() ?: 0
            val estimate = base + 40 * increment
            return when {
                estimate < 180 -> BULLET
                estimate < 480 -> BLITZ
                estimate < 1500 -> RAPID
                else -> CLASSICAL
            }
        }
    }
}

/** Which games a tree is built from: whose, as which color, how fast, how many. */
@Serializable
data class TreeRequest(
    val site: GameSite = GameSite.LICHESS,
    /** The account on lichess or chess.com; in a PGN file the player whose games count, or "" for all games. */
    val player: String = "",
    val white: Boolean = true,
    val speeds: Set<GameSpeed> = GameSpeed.entries.toSet(),
    val ratedOnly: Boolean = false,
    /** At most this many games, the newest first. */
    val max: Int = 500,
) {
    companion object {
        const val MAX = 5000
    }
}

/** One game the tree was built from, as openingtree keeps it: who played, how it ended, where to find it. */
@Serializable
data class TreeGame(
    val white: String = "",
    val black: String = "",
    val whiteElo: Int? = null,
    val blackElo: Int? = null,
    /** "1-0", "0-1", "1/2-1/2". */
    val result: String = "*",
    /** "2026.09.12", as PGN writes it, so it sorts as text. */
    val date: String = "",
    val url: String = "",
    val plies: Int = 0,
) {
    val whiteWon: Boolean get() = result == "1-0"
    val blackWon: Boolean get() = result == "0-1"
}

/**
 * The games through one position. [moves] are the moves played from here (SAN → how often); the
 * results are those of all games that reached it, by whatever move order. The game indices point
 * into [GameTree.games], -1 for none.
 */
@Serializable
class TreeNode(
    val moves: MutableMap<String, Int> = mutableMapOf(),
    var white: Int = 0,
    var draws: Int = 0,
    var black: Int = 0,
    /** Sum of the opponents' ratings and how many of them were known. */
    var opponentElo: Long = 0,
    var rated: Int = 0,
    var last: Int = -1,
    var bestWin: Int = -1,
    var worstLoss: Int = -1,
    var shortest: Int = -1,
    var longest: Int = -1,
    /** Games that ended right here. */
    val ended: MutableList<Int> = mutableListOf(),
) {
    val games: Int get() = white + draws + black

    val averageOpponent: Int? get() = if (rated == 0) null else (opponentElo / rated).toInt()
}

/**
 * The games of one player as a tree of positions, as on openingtree.com: at each position the moves
 * played there, how often, and how the games went on. Positions are keyed without the move counters
 * and the en-passant square (as openingtree does), so move orders that transpose meet.
 */
@Serializable
class GameTree(
    val request: TreeRequest = TreeRequest(),
    val games: MutableList<TreeGame> = mutableListOf(),
    val nodes: MutableMap<String, TreeNode> = mutableMapOf(),
    /** When it was built or loaded, epoch millis. */
    val built: Long = 0,
) {
    val white: Boolean get() = request.white

    fun nodeAt(position: Position): TreeNode? = nodes[keyOf(position.fen)]

    /**
     * Adds a game from the starting position: its first [MAX_PLIES] moves (SAN or UCI) go into the
     * tree. A move that is not legal ends the game there; what came before still counts.
     */
    fun add(game: TreeGame, moves: List<String>) {
        val index = games.size
        games += game.copy(plies = if (game.plies > 0) game.plies else moves.size)
        var position = Position.START
        val seen = mutableSetOf<String>()
        fun reach(p: Position) {
            val key = keyOf(p.fen)
            // A position that comes round again counts only once per game.
            if (seen.add(key)) count(nodes.getOrPut(key) { TreeNode() }, index)
        }
        reach(position)
        var complete = true
        for ((i, text) in moves.withIndex()) {
            if (i >= MAX_PLIES) {
                complete = false
                break
            }
            val move = position.parseSan(text) ?: runCatching { Move.fromUci(text) }.getOrNull()?.takeIf(position::isLegal)
            if (move == null) {
                complete = false
                break
            }
            val san = position.san(move)
            val node = nodes.getValue(keyOf(position.fen))
            node.moves[san] = (node.moves[san] ?: 0) + 1
            position = position.play(move)
            reach(position)
        }
        if (complete) nodes.getValue(keyOf(position.fen)).ended += index
    }

    private fun opponentElo(game: TreeGame): Int? = if (white) game.blackElo else game.whiteElo

    /** The result of game [index] put into [node]: the counts and the games that stand out. */
    internal fun count(node: TreeNode, index: Int) {
        val game = games[index]
        when {
            game.whiteWon -> node.white++
            game.blackWon -> node.black++
            else -> node.draws++
        }
        val elo = opponentElo(game)
        if (elo != null) {
            node.opponentElo += elo
            node.rated++
        }
        val won = if (white) game.whiteWon else game.blackWon
        val lost = if (white) game.blackWon else game.whiteWon
        fun other(i: Int) = games.getOrNull(i)
        if (won && (node.bestWin < 0 || (elo ?: 0) > (other(node.bestWin)?.let(::opponentElo) ?: 0))) node.bestWin = index
        if (lost && (node.worstLoss < 0 || (elo ?: Int.MAX_VALUE) < (other(node.worstLoss)?.let(::opponentElo) ?: Int.MAX_VALUE))) node.worstLoss = index
        if (node.last < 0 || game.date > (other(node.last)?.date ?: "")) node.last = index
        if (node.shortest < 0 || game.plies < (other(node.shortest)?.plies ?: Int.MAX_VALUE)) node.shortest = index
        if (node.longest < 0 || game.plies > (other(node.longest)?.plies ?: 0)) node.longest = index
    }

    /**
     * The tree at [position] in the form of the lichess database: the moves played there, most
     * often first, each with how often it was played here ([ExplorerMove.played]) and how the games
     * that reached the position after it went on.
     */
    fun lookup(position: Position): ExplorerResult {
        val node = nodeAt(position) ?: return ExplorerResult()
        val moves = node.moves.entries.mapNotNull { (san, count) ->
            val move = position.parseSan(san) ?: return@mapNotNull null
            val after = nodeAt(position.play(move))
            ExplorerMove(
                uci = move.uci,
                san = position.san(move),
                white = (after?.white ?: 0).toLong(),
                draws = (after?.draws ?: 0).toLong(),
                black = (after?.black ?: 0).toLong(),
                averageRating = after?.averageOpponent,
                played = count.toLong(),
            )
        }.sortedByDescending { it.played }
        return ExplorerResult(node.white.toLong(), node.draws.toLong(), node.black.toLong(), moves)
    }

    /** The player's score at [node] in percent: a win counts 1, a draw ½. */
    fun score(node: TreeNode): Int {
        if (node.games == 0) return 0
        val wins = if (white) node.white else node.black
        return ((wins * 2 + node.draws) * 100 + node.games) / (node.games * 2)
    }

    /** "Linus (Weiß) · lichess · 812 Partien". */
    val title: String
        get() = listOf(
            request.player.ifBlank { "Alle Partien" } + if (white) " (Weiß)" else " (Schwarz)",
            request.site.label,
            "${games.size} Partien",
        ).joinToString(" · ")

    companion object {
        /** Only the opening matters to FireHorse: the first 30 moves of each game go into the tree. */
        const val MAX_PLIES = 60

        /** "placement side castling", as openingtree keys its positions. */
        fun keyOf(fen: String): String {
            var spaces = 0
            for ((i, c) in fen.withIndex()) if (c == ' ' && ++spaces == 3) return fen.substring(0, i)
            return fen
        }
    }
}
