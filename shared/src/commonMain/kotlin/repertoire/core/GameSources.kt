package repertoire.core

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Builds a [GameTree] from one player's games, the way openingtree.com does, but here in the app:
 * downloaded from lichess or chess.com (both answer without an account), or read from a PGN file.
 * The newest games come first; [onProgress] hears how many have gone into the tree so far.
 */
object GameSources {
    private val json = Json { ignoreUnknownKeys = true }

    private const val LICHESS = "https://lichess.org"
    private const val CHESS_COM = "https://api.chess.com/pub/player"

    suspend fun download(request: TreeRequest, lichessToken: String?, onProgress: (Int) -> Unit): GameTree {
        require(request.player.isNotBlank()) { "Bitte den Namen des Kontos eintragen." }
        require(request.speeds.isNotEmpty()) { "Bitte mindestens ein Tempo wählen." }
        val tree = GameTree(request, built = currentTimeMillis())
        try {
            when (request.site) {
                GameSite.LICHESS -> lichess(request, lichessToken, tree, onProgress)
                GameSite.CHESS_COM -> chessCom(request, tree, onProgress)
                GameSite.PGN_FILE -> throw IllegalArgumentException("Eine PGN-Datei wird geöffnet, nicht geladen.")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw IllegalArgumentException("Keine Verbindung zu ${request.site.label} (${e.message ?: e::class.simpleName}).")
        }
        require(tree.games.isNotEmpty()) { "Keine passenden Partien gefunden – stimmen Name, Farbe und Tempo?" }
        return tree
    }

    /** The games of a PGN file: those of [TreeRequest.player] with the chosen color, or all if no name is given. */
    fun fromPgn(request: TreeRequest, text: String, onProgress: (Int) -> Unit): GameTree {
        val tree = GameTree(request.copy(site = GameSite.PGN_FILE), built = currentTimeMillis())
        val player = request.player.trim()
        for (game in Pgn.games(text)) {
            if (tree.games.size >= request.max) break
            if (game.setUp || game.moves.isEmpty()) continue
            val h = game.headers
            if (player.isNotEmpty() && !player.equals(h[if (request.white) "White" else "Black"]?.trim(), ignoreCase = true)) continue
            val speed = GameSpeed.ofTimeControl(h["TimeControl"])
            if (speed != null && speed !in request.speeds) continue
            tree.add(
                TreeGame(
                    white = h["White"].orEmpty(),
                    black = h["Black"].orEmpty(),
                    whiteElo = h["WhiteElo"]?.toIntOrNull(),
                    blackElo = h["BlackElo"]?.toIntOrNull(),
                    result = h["Result"].orEmpty().ifEmpty { "*" },
                    date = h["UTCDate"] ?: h["Date"].orEmpty(),
                    url = (h["Link"] ?: h["Site"])?.takeIf { it.startsWith("http") }.orEmpty(),
                    plies = game.moves.size,
                ),
                game.moves,
            )
            onProgress(tree.games.size)
        }
        require(tree.games.isNotEmpty()) {
            if (player.isEmpty()) "In der Datei ist keine Partie ab der Grundstellung." else "In der Datei ist keine Partie von „$player“ mit dieser Farbe."
        }
        return tree
    }

    // ---- lichess ----------------------------------------------------------------------------

    @Serializable
    private class LichessUser(val name: String = "")

    @Serializable
    private class LichessPlayer(val user: LichessUser? = null, val rating: Int? = null, val aiLevel: Int? = null) {
        val name: String get() = user?.name ?: aiLevel?.let { "Stockfish Stufe $it" } ?: "?"
    }

    @Serializable
    private class LichessPlayers(val white: LichessPlayer = LichessPlayer(), val black: LichessPlayer = LichessPlayer())

    @Serializable
    private class LichessGame(
        val id: String = "",
        val variant: String = "standard",
        val status: String = "",
        val createdAt: Long = 0,
        val players: LichessPlayers = LichessPlayers(),
        val winner: String? = null,
        val moves: String = "",
    )

    /** lichess's export of a user's games, one game of JSON per line, newest first. */
    fun lichessUrl(r: TreeRequest): String =
        "$LICHESS/api/games/user/${Explorer.encode(r.player.trim())}?max=${r.max}&color=${if (r.white) "white" else "black"}" +
            "&perfType=${r.speeds.joinToString(",") { it.lichess }}" + (if (r.ratedOnly) "&rated=true" else "") +
            "&moves=true&tags=false&clocks=false&evals=false&opening=false&finished=true"

    private suspend fun lichess(r: TreeRequest, token: String?, tree: GameTree, onProgress: (Int) -> Unit) {
        val status = httpLines(lichessUrl(r), token?.takeIf { it.isNotBlank() }?.trim(), "application/x-ndjson") { line ->
            if (line.isBlank()) return@httpLines
            val g = runCatching { json.decodeFromString(LichessGame.serializer(), line) }.getOrNull() ?: return@httpLines
            if (g.variant != "standard" || g.status in setOf("aborted", "noStart", "created", "started") || g.moves.isBlank()) return@httpLines
            val moves = g.moves.trim().split(' ')
            tree.add(
                TreeGame(
                    white = g.players.white.name,
                    black = g.players.black.name,
                    whiteElo = g.players.white.rating,
                    blackElo = g.players.black.rating,
                    result = when (g.winner) {
                        "white" -> "1-0"
                        "black" -> "0-1"
                        else -> "1/2-1/2"
                    },
                    date = pgnDate(g.createdAt),
                    url = "$LICHESS/${g.id}",
                    plies = moves.size,
                ),
                moves,
            )
            onProgress(tree.games.size)
        }
        when (status) {
            in 200..299 -> {}
            404 -> throw IllegalArgumentException("Bei lichess gibt es kein Konto „${r.player.trim()}“.")
            401, 403 -> throw IllegalArgumentException("lichess nimmt den API-Schlüssel nicht an – ist er richtig eingetragen?")
            429 -> throw IllegalArgumentException("lichess bittet um eine Pause (zu viele Anfragen). In einer Minute nochmal versuchen.")
            else -> throw IllegalArgumentException("lichess antwortet mit Fehler $status.")
        }
    }

    // ---- chess.com --------------------------------------------------------------------------

    @Serializable
    private class Archives(val archives: List<String> = emptyList())

    @Serializable
    private class ChessComPlayer(val username: String = "", val rating: Int? = null)

    @Serializable
    private class ChessComGame(
        val url: String = "",
        val pgn: String = "",
        val time_control: String = "",
        val rated: Boolean = true,
        val rules: String = "chess",
        val white: ChessComPlayer = ChessComPlayer(),
        val black: ChessComPlayer = ChessComPlayer(),
    )

    @Serializable
    private class Month(val games: List<ChessComGame> = emptyList())

    /** chess.com's public data: the list of months with games, then each month, from the newest back. */
    private suspend fun chessCom(r: TreeRequest, tree: GameTree, onProgress: (Int) -> Unit) {
        val player = r.player.trim()
        val list = httpGet("$CHESS_COM/${Explorer.encode(player.lowercase())}/games/archives", null)
        when (list.status) {
            200 -> {}
            404 -> throw IllegalArgumentException("Bei chess.com gibt es kein Konto „$player“.")
            429 -> throw IllegalArgumentException("chess.com bittet um eine Pause (zu viele Anfragen). Gleich nochmal versuchen.")
            else -> throw IllegalArgumentException("chess.com antwortet mit Fehler ${list.status}.")
        }
        val months = json.decodeFromString(Archives.serializer(), list.body).archives
        for (month in months.asReversed()) {
            if (tree.games.size >= r.max) break
            val answer = httpGet(month, null)
            if (answer.status != 200) throw IllegalArgumentException("chess.com antwortet mit Fehler ${answer.status}.")
            val games = runCatching { json.decodeFromString(Month.serializer(), answer.body).games }.getOrDefault(emptyList())
            for (g in games.asReversed()) {
                if (tree.games.size >= r.max) break
                val mine = if (r.white) g.white else g.black
                if (g.rules != "chess" || !mine.username.equals(player, ignoreCase = true)) continue
                if (r.ratedOnly && !g.rated) continue
                if (GameSpeed.ofTimeControl(g.time_control) !in r.speeds) continue
                val parsed = Pgn.games(g.pgn).firstOrNull() ?: continue
                if (parsed.setUp || parsed.moves.isEmpty()) continue
                val h = parsed.headers
                tree.add(
                    TreeGame(
                        white = g.white.username,
                        black = g.black.username,
                        whiteElo = g.white.rating,
                        blackElo = g.black.rating,
                        result = h["Result"].orEmpty().ifEmpty { "*" },
                        date = h["Date"].orEmpty(),
                        url = g.url,
                        plies = parsed.moves.size,
                    ),
                    parsed.moves,
                )
                onProgress(tree.games.size)
            }
        }
    }

    /** "2026.09.12", as PGN writes a date. */
    private fun pgnDate(epochMillis: Long): String = if (epochMillis <= 0) "" else isoDate(epochMillis).replace('-', '.')
}
