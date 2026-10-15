package repertoire.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** [data] packed with zlib (with its header, as Node's zlib.deflate writes it). */
expect fun zlibDeflate(data: ByteArray): ByteArray

/** The reverse of [zlibDeflate]; throws on data that is not zlib. */
expect fun zlibInflate(data: ByteArray): ByteArray

/**
 * The ".tree" files that openingtree.com saves and loads, so a tree can go back and forth between
 * it and FireHorse. Written from the file format alone; no code of openingtree (GPL-3.0) is in here.
 *
 * The file is a row of 8-byte control words (0x1337, a 32-bit number, 0xC0D3; big-endian): the format
 * version 1, the number of chunks, then each chunk's length and the chunk, which is zlib-packed JSON
 * `{"chunk": …, "index": n}`. Chunk 0 holds the header (with `arraySizes`), the others hold up to
 * 1000 entries each of two arrays: the positions as `[fen, node]` and the games.
 */
object OpeningTreeFile {
    const val EXTENSION = "tree"

    /** The version inside the header that openingtree writes and accepts. */
    private const val HEADER_VERSION = 2
    private const val FORMAT_VERSION = 1
    private const val CHUNK = 1000

    /** openingtree's highest download limit; more would not fit its slider. */
    private const val MAX_LIMIT = 2000

    private val json = Json { ignoreUnknownKeys = true }

    fun fileName(tree: GameTree): String {
        val who = tree.request.player.lowercase().replace(Regex("[^a-z0-9_-]+"), "-").trim('-').ifEmpty { "partien" }
        return "$who-${if (tree.white) "weiss" else "schwarz"}.$EXTENSION"
    }

    fun write(tree: GameTree, now: Long = currentTimeMillis()): ByteArray {
        val color = if (tree.white) "white" else "black"
        val r = tree.request
        val header = buildJsonObject {
            put("version", HEADER_VERSION)
            put("timestamp", now / 1000)
            put("gamesProcessed", tree.games.size)
            putJsonObject("settings") {
                put("playerName", r.player)
                put("orientation", color)
                put("playerColor", color)
                put("darkMode", false)
            }
            put("playerName", r.player)
            put("site", r.site.code)
            putJsonObject("advancedFilters") {
                put("ultraBullet", GameSpeed.BULLET in r.speeds)
                for (speed in GameSpeed.entries) put(speed.lichess, speed in r.speeds)
                put("daily", GameSpeed.CORRESPONDENCE in r.speeds)
                put("rated", if (r.ratedOnly) "rated" else "all")
                put("downloadLimit", r.max.coerceAtMost(MAX_LIMIT))
                putJsonArray("eloRange") {
                    add(JsonPrimitive(0))
                    add(JsonPrimitive(3000))
                }
                put("opponent", "")
            }
            put("variant", "standard")
        }
        val nodes = tree.nodes.map { (key, node) -> buildJsonArray {
            add(JsonPrimitive(key))
            add(nodeJson(key, node))
        } }
        val games = tree.games.mapIndexed { i, g -> gameJson(g, i) }

        val chunks = mutableListOf<JsonObject>()
        chunks += buildJsonObject {
            put("chunk", header)
            put("index", 0)
            putJsonArray("arraySizes") {
                add(JsonPrimitive(nodes.size))
                add(JsonPrimitive(games.size))
            }
        }
        for (array in listOf(nodes, games)) {
            for (part in array.chunked(CHUNK)) {
                chunks += buildJsonObject {
                    put("chunk", JsonArray(part))
                    put("index", chunks.size)
                }
            }
        }
        val out = ByteBuilder()
        out.control(FORMAT_VERSION.toLong())
        out.control(chunks.size.toLong())
        for (chunk in chunks) {
            val packed = zlibDeflate(chunk.toString().encodeToByteArray())
            out.control(packed.size.toLong())
            out.add(packed)
        }
        return out.bytes()
    }

    private fun nodeJson(key: String, node: TreeNode): JsonObject = buildJsonObject {
        put("fen", key)
        put("playedByMax", node.moves.values.maxOrNull() ?: 0)
        if (node.moves.isNotEmpty()) putJsonObject("playedBy") { for ((san, n) in node.moves) put(san, n) }
        if (node.games > 0) {
            putJsonObject("details") {
                put("hasData", true)
                put("blackWins", node.black)
                put("whiteWins", node.white)
                put("draws", node.draws)
                put("totalOpponentElo", node.opponentElo)
                fun index(name: String, i: Int) = put(name, if (i >= 0) JsonPrimitive(i) else JsonNull)
                index("shortestGame", node.shortest)
                index("longestGame", node.longest)
                index("lastPlayed", node.last)
                if (node.bestWin >= 0) put("bestWin", node.bestWin)
                if (node.worstLoss >= 0) put("worstLoss", node.worstLoss)
            }
        }
        if (node.ended.isNotEmpty()) putJsonArray("gameResults") { node.ended.forEach { add(JsonPrimitive(it)) } }
    }

    private fun gameJson(g: TreeGame, index: Int): JsonObject = buildJsonObject {
        put("result", g.result)
        put("white", g.white)
        put("black", g.black)
        g.whiteElo?.let { put("whiteElo", it.toString()) }
        g.blackElo?.let { put("blackElo", it.toString()) }
        put("url", g.url.ifEmpty { null })
        put("date", g.date)
        // Without a link, openingtree shows the players from the headers.
        if (g.url.isEmpty()) {
            putJsonObject("headers") {
                put("White", g.white)
                put("Black", g.black)
                put("Result", g.result)
                put("Date", g.date)
                g.whiteElo?.let { put("WhiteElo", it.toString()) }
                g.blackElo?.let { put("BlackElo", it.toString()) }
            }
        } else {
            put("headers", JsonNull)
        }
        put("numberOfPlys", g.plies)
        put("index", index)
    }

    /** Reads a saved openingtree; throws [IllegalArgumentException] with a German explanation. */
    fun read(bytes: ByteArray, now: Long = currentTimeMillis()): GameTree {
        var at = 0
        fun control(): Long {
            require(at + 8 <= bytes.size) { "Die Datei ist zu kurz – ist sie vollständig?" }
            val word = bytes.copyOfRange(at, at + 8)
            at += 8
            fun u16(i: Int) = (word[i].toInt() and 0xFF shl 8) or (word[i + 1].toInt() and 0xFF)
            require(u16(0) == 0x1337 && u16(6) == 0xC0D3) { "Das ist keine openingtree-Datei (.tree)." }
            return (2..5).fold(0L) { acc, i -> acc shl 8 or (word[i].toLong() and 0xFF) }
        }
        require(control() == FORMAT_VERSION.toLong()) { "Diese openingtree-Datei hat ein Format, das FireHorse nicht kennt." }
        val count = control().toInt()
        require(count > 0) { "Die openingtree-Datei ist leer." }
        val chunks = (0 until count).map {
            val size = control().toInt()
            require(size >= 0 && at + size <= bytes.size) { "Die Datei ist zu kurz – ist sie vollständig?" }
            val packed = bytes.copyOfRange(at, at + size)
            at += size
            val text = try {
                zlibInflate(packed).decodeToString()
            } catch (e: Exception) {
                throw IllegalArgumentException("Die openingtree-Datei ist beschädigt.")
            }
            try {
                json.parseToJsonElement(text).jsonObject
            } catch (e: Exception) {
                throw IllegalArgumentException("Die openingtree-Datei ist beschädigt.")
            }
        }.sortedBy { it["index"]?.jsonPrimitive?.intOrNull ?: 0 }

        val first = chunks.first()
        val header = first["chunk"]?.let { it as? JsonObject } ?: throw IllegalArgumentException("Der openingtree-Datei fehlt ihr Kopf.")
        val version = header.int("version") ?: 1
        require(version >= HEADER_VERSION) {
            "Das ist eine openingtree-Datei im alten Format; die liest auch openingtree selbst nicht mehr."
        }
        val variant = header.string("variant")
        require(variant == null || variant == "standard") { "Der Baum ist für eine Schachvariante ($variant); FireHorse kennt nur normales Schach." }

        val rest = chunks.drop(1).flatMap { it["chunk"]?.jsonArray.orEmpty() }
        val sizes = first["arraySizes"]?.jsonArray?.mapNotNull { it.jsonPrimitive.intOrNull } ?: listOf(rest.size)
        val arrays = mutableListOf<List<JsonElement>>()
        var from = 0
        for (size in sizes) {
            arrays += rest.subList(from.coerceAtMost(rest.size), (from + size).coerceAtMost(rest.size))
            from += size
        }
        val nodeEntries = arrays.getOrElse(0) { emptyList() }
        val gameEntries = arrays.getOrElse(1) { emptyList() }

        val settings = header["settings"] as? JsonObject
        val filters = header["advancedFilters"] as? JsonObject
        fun on(name: String) = filters?.get(name)?.jsonPrimitive?.booleanOrNull != false
        val speeds = GameSpeed.entries.filter { speed ->
            when (speed) {
                GameSpeed.BULLET -> on("bullet") || on("ultraBullet")
                GameSpeed.CORRESPONDENCE -> on("correspondence") || on("daily")
                else -> on(speed.lichess)
            }
        }.toSet()
        val games = gameEntries.map { e ->
            val g = e as? JsonObject ?: JsonObject(emptyMap())
            val headers = g["headers"] as? JsonObject
            TreeGame(
                white = g.string("white") ?: headers?.string("White") ?: "",
                black = g.string("black") ?: headers?.string("Black") ?: "",
                whiteElo = g.string("whiteElo")?.toIntOrNull(),
                blackElo = g.string("blackElo")?.toIntOrNull(),
                result = g.string("result") ?: headers?.string("Result") ?: "*",
                date = g.string("date") ?: headers?.string("Date") ?: "",
                url = g.string("url") ?: "",
                plies = g.int("numberOfPlys") ?: 0,
            )
        }
        val tree = GameTree(
            request = TreeRequest(
                site = GameSite.of(header.string("site") ?: ""),
                player = settings?.string("playerName") ?: header.string("playerName") ?: "",
                white = (settings?.string("playerColor") ?: "white") != "black",
                speeds = speeds.ifEmpty { GameSpeed.entries.toSet() },
                ratedOnly = filters?.string("rated") == "rated",
                max = (filters?.int("downloadLimit") ?: games.size).coerceIn(1, TreeRequest.MAX),
            ),
            games = games.toMutableList(),
            built = now,
        )
        for (entry in nodeEntries) {
            val pair = entry as? JsonArray ?: continue
            val fen = pair.getOrNull(0)?.jsonPrimitive?.contentOrNull ?: continue
            val n = pair.getOrNull(1) as? JsonObject ?: continue
            val node = TreeNode()
            (n["playedBy"] as? JsonObject)?.forEach { (san, count) -> count.jsonPrimitive.intOrNull?.let { node.moves[san] = it } }
            (n["gameResults"] as? JsonArray)?.mapNotNullTo(node.ended) { it.jsonPrimitive.intOrNull?.takeIf { i -> i in games.indices } }
            when (val details = n["details"]) {
                is JsonPrimitive -> details.intOrNull?.takeIf { it in games.indices }?.let { tree.count(node, it) }
                is JsonObject -> {
                    node.white = details.int("whiteWins") ?: 0
                    node.draws = details.int("draws") ?: 0
                    node.black = details.int("blackWins") ?: 0
                    // openingtree adds up the opponents' ratings over all games there.
                    node.opponentElo = details["totalOpponentElo"]?.jsonPrimitive?.longOrNull ?: 0
                    node.rated = if (node.opponentElo > 0) node.games else 0
                    fun index(name: String) = details.int(name)?.takeIf { it in games.indices } ?: -1
                    node.last = index("lastPlayed")
                    node.bestWin = index("bestWin")
                    node.worstLoss = index("worstLoss")
                    node.shortest = index("shortestGame")
                    node.longest = index("longestGame")
                }
                else -> {}
            }
            tree.nodes[GameTree.keyOf(fen)] = node
        }
        require(tree.nodes.isNotEmpty()) { "In der openingtree-Datei ist kein einziger Zug." }
        return tree
    }

    private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull

    private fun JsonObject.int(name: String): Int? = (this[name] as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.toIntOrNull() }

    /** Bytes collected in order, with openingtree's control words. */
    private class ByteBuilder {
        private val parts = mutableListOf<ByteArray>()

        fun add(bytes: ByteArray) {
            parts += bytes
        }

        fun control(value: Long) {
            val word = ByteArray(8)
            word[0] = 0x13
            word[1] = 0x37
            for (i in 0 until 4) word[2 + i] = (value shr (24 - 8 * i) and 0xFF).toByte()
            word[6] = 0xC0.toByte()
            word[7] = 0xD3.toByte()
            parts += word
        }

        fun bytes(): ByteArray {
            val out = ByteArray(parts.sumOf { it.size })
            var at = 0
            for (p in parts) {
                p.copyInto(out, at)
                at += p.size
            }
            return out
        }
    }
}
