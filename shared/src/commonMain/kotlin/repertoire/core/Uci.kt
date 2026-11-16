package repertoire.core

import kotlinx.serialization.Serializable

/**
 * A chess engine set up in the configuration: what it calls itself, the program file, and how it
 * works, each engine for itself (as in En Croissant): [lines] shown (MultiPV), [threads], [hashMb],
 * and the [depth] it stops at (0: on and on until the position changes).
 */
@Serializable
data class EngineEntry(
    val name: String,
    val path: String,
    val lines: Int = 3,
    val threads: Int = 2,
    val hashMb: Int = 256,
    val depth: Int = 0,
)

/** Talking to a running engine program: one line at a time each way. */
interface UciIo {
    fun send(line: String)

    /** The next line the engine writes; null once it has quit. */
    suspend fun readLine(): String?

    fun close()
}

/** Starts the engine program at [path]; throws [IllegalStateException] with a German explanation if it cannot. */
expect fun launchEngine(path: String): UciIo

/** The program files in [dir] (and one folder down) that could be engines; the folder is made if it is missing. */
expect fun engineFilesIn(dir: String): List<String>

/** How many processor cores (hardware threads) this computer has, for the engine's threads. */
expect fun processorCount(): Int

/** A score as the engine gives it, from White's point of view: centipawns, or a mate in so many moves (negative: Black mates). */
data class Score(val cp: Int? = null, val mate: Int? = null) {
    /** "+0,31", "−1,20", "#3", "#−2" */
    val label: String
        get() = when {
            mate != null -> if (mate >= 0) "#$mate" else "#−${-mate}"
            cp != null -> {
                val sign = if (cp > 0) "+" else if (cp < 0) "−" else ""
                val abs = kotlin.math.abs(cp)
                "$sign${abs / 100},${(abs % 100).toString().padStart(2, '0')}"
            }
            else -> "?"
        }
}

/** One line of the analysis: its rank ([multipv], from 1), how deep, the score and the moves in UCI. */
data class EngineLine(val multipv: Int, val depth: Int, val score: Score, val pv: List<String>)

object Uci {
    /**
     * An "info … multipv 2 score cp -31 … pv c7c5 g1f3" line as an [EngineLine], with the score
     * turned to White's view ([whiteToMove] says whose move it is). Null for info without a line.
     */
    fun parseInfo(line: String, whiteToMove: Boolean): EngineLine? {
        if (!line.startsWith("info ")) return null
        val words = line.split(' ').filter { it.isNotEmpty() }
        val pvAt = words.indexOf("pv").takeIf { it >= 0 } ?: return null
        fun intAfter(key: String) = words.indexOf(key).takeIf { it in 0 until pvAt - 1 }?.let { words[it + 1].toIntOrNull() }
        // Bounds during a search are not the score yet.
        if ("lowerbound" in words || "upperbound" in words) return null
        val scoreAt = words.indexOf("score").takeIf { it >= 0 && it + 2 < words.size } ?: return null
        val value = words[scoreAt + 2].toIntOrNull() ?: return null
        val sign = if (whiteToMove) 1 else -1
        val score = when (words[scoreAt + 1]) {
            "cp" -> Score(cp = value * sign)
            "mate" -> Score(mate = value * sign)
            else -> return null
        }
        return EngineLine(intAfter("multipv") ?: 1, intAfter("depth") ?: 0, score, words.drop(pvAt + 1))
    }

    /**
     * Which line (MultiPV, from 1) an "info … pv …" line is about, also if its score is only a bound
     * and [parseInfo] leaves it out. Null for info without a line.
     */
    fun rank(line: String): Int? {
        if (!line.startsWith("info ")) return null
        val words = line.split(' ').filter { it.isNotEmpty() }
        val pvAt = words.indexOf("pv").takeIf { it >= 0 } ?: return null
        return words.indexOf("multipv").takeIf { it in 0 until pvAt - 1 }?.let { words[it + 1].toIntOrNull() } ?: 1
    }

    /** [pv] from [from] in SAN, numbered ("12... Nf6 13. Bd3"), as far as the moves are legal. */
    fun sanLine(from: Position, pv: List<String>, max: Int = 10): String {
        var position = from
        val out = mutableListOf<String>()
        for (uci in pv.take(max)) {
            val move = runCatching { Move.fromUci(uci) }.getOrNull()?.takeIf(position::isLegal) ?: break
            out += numberedSan(position, move)
            position = position.play(move)
        }
        return compactMoves(out)
    }
}

/**
 * A running engine, asked about one position after another. [start] does the UCI greeting and
 * sets the options; [analyse] searches a position until the next one comes, reporting each
 * improvement to [onLines]. One reader takes all the engine's lines, so a search that is stopped
 * never leaves a second reader behind.
 */
class EngineSession(private val io: UciIo) {
    var name: String = ""
        private set

    private companion object {
        /** How many searches one position gets at most, if the engine keeps ending short of the depth. */
        const val DEPTH_ROUNDS = 3

        /** The most that is asked for beyond the depth wanted. */
        const val MAX_AHEAD = 20
    }

    private val options = mutableSetOf<String>()
    private var searching = false

    /** How much deeper than wanted this engine is asked to go, once it has ended a search short of the depth. */
    private var ahead = 0

    /** "uci", the name and options it knows, then [settings] where it has them. */
    suspend fun start(settings: Map<String, String>) {
        io.send("uci")
        while (true) {
            val line = io.readLine() ?: throw IllegalStateException("Das Programm hat sich beendet, bevor es sich als UCI-Engine gemeldet hat.")
            when {
                line.startsWith("id name ") -> name = line.removePrefix("id name ").trim()
                line.startsWith("option name ") -> options += line.removePrefix("option name ").substringBefore(" type ").trim().lowercase()
                line.trim() == "uciok" -> break
            }
        }
        for ((option, value) in settings) {
            if (option.lowercase() in options) io.send("setoption name $option value $value")
        }
        ready()
    }

    private suspend fun ready() {
        io.send("isready")
        while (true) {
            val line = io.readLine() ?: throw IllegalStateException("Die Engine hat sich beendet.")
            if (line.trim() == "readyok") return
        }
    }

    /**
     * Searches [position] to [depth] (null: without end, until cancelled). Returns when the depth is
     * reached, when cancelled, or when the engine quits; the lines it found stay with [onLines].
     * True if the search is finished, false if the engine quit.
     *
     * Some engines end "go depth" before the lines they report are that deep: Reckless with several
     * threads stops once most of its threads are there, while only the first one reports, which may
     * be a depth or two behind (or in the middle of the next). Then the position is searched again,
     * further than asked, and stopped here as soon as all lines are at [depth]; from then on this
     * engine is asked for more from the start, so it is one search again. An engine that keeps
     * ending short is believed after [DEPTH_ROUNDS] searches, and at once if it has found a mate.
     *
     * What stays at the end are lines the engine wrote together, all at one depth. Taken one by one
     * from a search that ended in the middle of a depth, a line can be missing or a move be there twice.
     */
    suspend fun analyse(position: Position, depth: Int? = null, onLines: (List<EngineLine>) -> Unit): Boolean {
        if (searching) {
            io.send("stop")
            // What the old search still says is about the old position.
            while (true) {
                val line = io.readLine() ?: return false
                if (line.startsWith("bestmove")) break
            }
            searching = false
        }
        val limit = depth?.takeIf { it > 0 }
        val lines = mutableMapOf<Int, EngineLine>()
        // How many lines the engine gives, and how deep the search before this one got.
        var width = 0
        var floor = 0
        // The lines the engine is writing in one go (null: only a bound yet), and the last time they were all there at one depth.
        val batch = mutableMapOf<Int, EngineLine?>()
        var whole = emptyMap<Int, EngineLine>()
        fun show() = onLines(lines.values.sortedBy { it.multipv })
        io.send("position fen ${position.fen}")
        for (round in 1..DEPTH_ROUNDS) {
            val goTo = limit?.let { it + ahead }
            io.send(if (goTo != null) "go depth $goTo" else "go infinite")
            searching = true
            var stopping = false
            while (true) {
                val line = io.readLine() ?: return false
                if (line.startsWith("bestmove")) {
                    searching = false
                    break
                }
                // Stopped at the depth: what the engine still says is from the middle of the next.
                if (stopping) continue
                val rank = Uci.rank(line) ?: continue
                val info = Uci.parseInfo(line, position.whiteToMove)
                if (rank == 1) batch.clear()
                batch[rank] = info
                width = maxOf(width, rank)
                // Bounds are not the score yet. And a search that goes on starts from depth 1 again; what is shown is deeper already.
                if (info == null || info.depth < floor) continue
                // A deeper first line starts a new round; the others follow it.
                if (rank == 1 && lines[1]?.let { it.depth < info.depth } == true) lines.keys.removeAll { it > 1 }
                lines[rank] = info
                if (batch.size >= width && batch.values.all { it?.depth == info.depth }) {
                    whole = batch.mapValues { it.value!! }
                    // Asked for more than wanted: this is the depth, all lines are there.
                    if (limit != null && goTo != limit && info.depth >= limit) {
                        lines.clear()
                        lines.putAll(whole)
                        io.send("stop")
                        stopping = true
                    }
                }
                show()
            }
            // Ended in the middle of a depth: back to the last one that had all lines.
            val shallowest = lines.values.minOfOrNull { it.depth } ?: return true
            if (whole.isNotEmpty() && whole != lines && whole.values.first().depth >= shallowest) {
                lines.clear()
                lines.putAll(whole)
                show()
            }
            val best = lines[1] ?: return true
            if (limit == null || best.depth >= limit || best.score.mate != null) return true
            // Short of the depth: again from here, asking for more than is missing so it does not end short again.
            floor = best.depth
            ahead = maxOf(ahead, minOf(3 * (limit - best.depth) + 4, MAX_AHEAD))
        }
        return true
    }

    fun close() {
        runCatching { io.send("quit") }
        io.close()
    }
}
