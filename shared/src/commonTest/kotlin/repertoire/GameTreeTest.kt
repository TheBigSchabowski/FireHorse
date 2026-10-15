package repertoire

import repertoire.core.GameSite
import repertoire.core.GameSources
import repertoire.core.GameSpeed
import repertoire.core.GameTree
import repertoire.core.OpeningTreeFile
import repertoire.core.Pgn
import repertoire.core.Position
import repertoire.core.TreeGame
import repertoire.core.TreeRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameTreeTest {
    private fun after(vararg san: String): Position =
        san.fold(Position.START) { p, m -> p.play(assertNotNull(p.parseSan(m), m)) }

    private fun game(result: String, opponentElo: Int, date: String = "2026.01.01", url: String = "") =
        TreeGame(white = "ich", black = "du", whiteElo = 1500, blackElo = opponentElo, result = result, date = date, url = url)

    /** Three games as White: two Sicilians (won, lost), one 1.e4 e5 (drawn). */
    private fun tree() = GameTree(TreeRequest(player = "ich", white = true)).apply {
        add(game("1-0", 1600, "2026.03.01", "https://lichess.org/a"), "e4 c5 Nf3 d6".split(' '))
        add(game("0-1", 1400, "2026.02.01"), "e4 c5 Nc3".split(' '))
        add(game("1/2-1/2", 1700, "2026.04.01"), "e4 e5 Nf3 Nc6".split(' '))
    }

    @Test
    fun theMovesOfAPositionAreCountedWithTheirResults() {
        val t = tree()
        val root = t.lookup(Position.START)
        assertEquals(listOf("e4"), root.moves.map { it.san })
        assertEquals(3L, root.moves.single().played)
        assertEquals(3L, root.games)

        val afterE4 = t.lookup(after("e4"))
        assertEquals(listOf("c5", "e5"), afterE4.moves.map { it.san })
        val c5 = afterE4.moves.first()
        assertEquals(2L, c5.played)
        assertEquals(1L to 1L, c5.white to c5.black)
        assertEquals(1500, c5.averageRating, "average of the opponents 1600 and 1400")
    }

    @Test
    fun theGamesThatStandOutAreKeptPerPosition() {
        val t = tree()
        val node = assertNotNull(t.nodeAt(after("e4")))
        assertEquals("2026.04.01", t.games[node.last].date)
        assertEquals(1600, t.games[node.bestWin].blackElo)
        assertEquals(1400, t.games[node.worstLoss].blackElo)
        assertEquals(50, t.score(node), "a win, a loss and a draw")
        assertEquals(listOf(1), assertNotNull(t.nodeAt(after("e4", "c5", "Nc3"))).ended)
    }

    @Test
    fun moveOrdersThatTransposeMeet() {
        val t = GameTree(TreeRequest(white = true)).apply {
            add(game("1-0", 1500), "e4 c5 Nf3".split(' '))
            add(game("1-0", 1500), "Nf3 c5 e4".split(' '))
        }
        val node = assertNotNull(t.nodeAt(after("e4", "c5", "Nf3")))
        assertEquals(2, node.games)
        // How often a move was played comes from its own position, the results from all games after it.
        val nf3 = t.lookup(after("e4", "c5")).moves.single()
        assertEquals(1L, nf3.played)
        assertEquals(2L, nf3.white)
    }

    @Test
    fun aMoveThatDoesNotFitEndsTheGameThere() {
        val t = GameTree().apply { add(game("1-0", 1500), listOf("e4", "e5", "Ke3")) }
        assertEquals(listOf("e5"), t.lookup(after("e4")).moves.map { it.san })
        assertTrue(t.lookup(after("e4", "e5")).moves.isEmpty())
    }

    @Test
    fun onlyTheOpeningGoesIntoTheTree() {
        val shuffle = List(40) { listOf("Nf3", "Nf6", "Ng1", "Ng8") }.flatten()
        val t = GameTree().apply { add(game("1/2-1/2", 1500), shuffle) }
        assertEquals(shuffle.size, t.games.single().plies)
        // Only four positions come round again and again, each counted once for the game.
        assertEquals(4, t.nodes.size)
        assertEquals(1, t.nodeAt(Position.START)?.games)
        assertTrue(t.nodeAt(Position.START)!!.ended.isEmpty(), "the game went on past ${GameTree.MAX_PLIES} plies")
    }

    @Test
    fun positionsAreKeyedAsOpeningtreeDoes() {
        assertEquals("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq", GameTree.keyOf(Position.START_FEN))
        assertEquals("8/8/8/8/8/8/8/K6k b -", GameTree.keyOf("8/8/8/8/8/8/8/K6k b - - 3 40"))
    }

    @Test
    fun speedsAreToldFromTheTimeControl() {
        assertEquals(GameSpeed.BULLET, GameSpeed.ofTimeControl("60+0"))
        assertEquals(GameSpeed.BLITZ, GameSpeed.ofTimeControl("180+2"))
        assertEquals(GameSpeed.RAPID, GameSpeed.ofTimeControl("600"))
        assertEquals(GameSpeed.CLASSICAL, GameSpeed.ofTimeControl("1800+30"))
        assertEquals(GameSpeed.CORRESPONDENCE, GameSpeed.ofTimeControl("1/259200"))
        assertEquals(GameSpeed.CORRESPONDENCE, GameSpeed.ofTimeControl("-"))
        assertNull(GameSpeed.ofTimeControl(null))
    }

    private val pgnFile = """
        [Event "Rated Blitz game"]
        [Site "https://lichess.org/abc"]
        [White "Ich"]
        [Black "Gegner"]
        [Result "1-0"]
        [UTCDate "2026.05.01"]
        [WhiteElo "1500"]
        [BlackElo "1550"]
        [TimeControl "180+2"]

        1. e4 { [%clk 0:03:00] } c5 (1... e5 2. Nf3) 2. Nf3 $1 d6 1-0

        [Event "Live Chess"]
        [White "Jemand"]
        [Black "Ich"]
        [Result "0-1"]
        [TimeControl "600"]

        1. d4 Nf6 2. c4 e6 0-1

        [Event "Casual"]
        [White "Ich"]
        [Black "Bot"]
        [Result "1/2-1/2"]
        [SetUp "1"]
        [FEN "4k3/8/8/8/8/8/8/4K2R w K - 0 1"]

        1. O-O Kd7 1/2-1/2
    """.trimIndent()

    @Test
    fun aPgnFileGivesEveryGameWithItsHeaders() {
        val games = Pgn.games(pgnFile)
        assertEquals(3, games.size)
        assertEquals(listOf("e4", "c5", "Nf3", "d6"), games[0].moves)
        assertEquals("Gegner", games[0].headers["Black"])
        assertTrue(games[2].setUp)
    }

    @Test
    fun aPgnFileBecomesTheTreeOfOnePlayer() {
        val white = GameSources.fromPgn(TreeRequest(player = "ich", white = true), pgnFile) {}
        assertEquals(1, white.games.size, "the game from a set-up position does not count")
        assertEquals("https://lichess.org/abc", white.games.single().url)
        assertEquals(GameSite.PGN_FILE, white.request.site)

        val black = GameSources.fromPgn(TreeRequest(player = "ich", white = false), pgnFile) {}
        assertEquals(listOf("d4"), black.lookup(Position.START).moves.map { it.san })

        val blitzOnly = TreeRequest(player = "", white = true, speeds = setOf(GameSpeed.BLITZ))
        assertEquals(1, GameSources.fromPgn(blitzOnly, pgnFile) {}.games.size)
        assertFailsWith<IllegalArgumentException> { GameSources.fromPgn(TreeRequest(player = "niemand"), pgnFile) {} }
    }

    @Test
    fun lichessIsAskedForTheGamesOfOneColorAndSpeed() {
        val url = GameSources.lichessUrl(TreeRequest(player = "Some One", white = false, speeds = setOf(GameSpeed.BLITZ, GameSpeed.RAPID), ratedOnly = true, max = 300))
        assertTrue(url.startsWith("https://lichess.org/api/games/user/Some%20One?max=300&color=black&perfType=blitz,rapid&rated=true"), url)
    }

    @Test
    fun anOpeningtreeFileReadsBackAsTheSameTree() {
        val t = tree()
        val bytes = OpeningTreeFile.write(t, now = 1_700_000_000_000)
        // The control words of openingtree: 0x1337, the format version 1, 0xC0D3.
        assertEquals(listOf(0x13, 0x37, 0, 0, 0, 1, 0xC0, 0xD3), bytes.take(8).map { it.toInt() and 0xFF })

        val back = OpeningTreeFile.read(bytes)
        assertEquals(t.request, back.request)
        assertEquals(t.games, back.games)
        assertEquals(t.nodes.keys, back.nodes.keys)
        for ((key, node) in t.nodes) {
            val other = back.nodes.getValue(key)
            assertEquals(node.moves, other.moves, key)
            assertEquals(listOf(node.white, node.draws, node.black), listOf(other.white, other.draws, other.black), key)
            assertEquals(listOf(node.last, node.bestWin, node.worstLoss, node.shortest, node.longest, node.ended),
                listOf(other.last, other.bestWin, other.worstLoss, other.shortest, other.longest, other.ended), key)
        }
        assertEquals(t.lookup(after("e4")), back.lookup(after("e4")))
    }

    @Test
    fun aBigTreeIsSplitIntoChunksOfAThousand() {
        val t = GameTree()
        val first = listOf("a3", "a4", "b3", "b4", "c3", "c4", "d3", "d4", "e3", "e4", "f3", "f4", "g3", "g4", "h3", "h4", "Na3", "Nc3", "Nf3", "Nh3")
        val second = listOf("a6", "a5", "b6", "b5", "c6", "c5", "d6", "d5", "e6", "e5", "f6", "f5", "g6", "g5", "h6", "h5", "Na6", "Nc6", "Nf6", "Nh6")
        for (w in first) for (b in second) for (w2 in listOf("Kf2", "Ra2", "Rb1")) t.add(game("1-0", 1500), listOf(w, b, w2))
        assertTrue(t.games.size > 1000)
        val back = OpeningTreeFile.read(OpeningTreeFile.write(t))
        assertEquals(t.nodes.keys, back.nodes.keys)
        assertEquals(t.games.size, back.games.size)
    }

    @Test
    fun otherFilesAreNotTakenForATree() {
        assertFailsWith<IllegalArgumentException> { OpeningTreeFile.read("{\"variants\":[]}".encodeToByteArray()) }
        assertFailsWith<IllegalArgumentException> { OpeningTreeFile.read(OpeningTreeFile.write(tree()).copyOf(30)) }
    }
}
