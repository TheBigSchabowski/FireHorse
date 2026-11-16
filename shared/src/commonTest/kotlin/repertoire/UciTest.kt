package repertoire

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import repertoire.core.EngineLine
import repertoire.core.EngineSession
import repertoire.core.Position
import repertoire.core.Score
import repertoire.core.Uci
import repertoire.core.UciIo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** An engine that answers from a script: what it is sent is kept, what it says comes from [say]. */
private class FakeEngine : UciIo {
    val sent = mutableListOf<String>()
    val out = Channel<String>(Channel.UNLIMITED)

    fun say(vararg lines: String) = lines.forEach { out.trySend(it) }

    override fun send(line: String) {
        sent += line
        when (line) {
            "uci" -> say("id name Fakefish 1", "option name Threads type spin default 1", "option name MultiPV type spin default 1", "uciok")
            "isready" -> say("readyok")
            "stop" -> say("bestmove e2e4")
        }
    }

    override suspend fun readLine(): String? = out.receiveCatching().getOrNull()

    override fun close() {
        out.close()
    }
}

class UciTest {
    private val afterE4 = Position.START.play(Position.START.parseSan("e4")!!)

    @Test
    fun theRankOfALineIsKnownEvenForABound() {
        assertEquals(2, Uci.rank("info depth 12 multipv 2 score cp 20 lowerbound pv e2e4"))
        assertEquals(1, Uci.rank("info depth 12 score cp 20 pv e2e4"))
        assertNull(Uci.rank("info depth 12 currmove e2e4 currmovenumber 1"))
        assertNull(Uci.rank("bestmove e2e4"))
    }

    @Test
    fun infoLinesBecomeLinesFromWhitesView() {
        val line = Uci.parseInfo("info depth 12 seldepth 15 multipv 1 score cp -31 nodes 22841 pv c7c5 g1f3", whiteToMove = false)
        assertEquals(EngineLine(1, 12, Score(cp = 31), listOf("c7c5", "g1f3")), line)
        assertEquals(Score(mate = -2), Uci.parseInfo("info depth 9 multipv 2 score mate 2 pv d8h4", whiteToMove = false)!!.score)
        assertNull(Uci.parseInfo("info depth 12 currmove e2e4 currmovenumber 1", whiteToMove = true))
        assertNull(Uci.parseInfo("info depth 12 multipv 1 score cp 20 lowerbound pv e2e4", whiteToMove = true))
    }

    @Test
    fun scoresAreWrittenTheGermanWay() {
        assertEquals("+0,31", Score(cp = 31).label)
        assertEquals("−1,05", Score(cp = -105).label)
        assertEquals("0,00", Score(cp = 0).label)
        assertEquals("#3", Score(mate = 3).label)
        assertEquals("#−2", Score(mate = -2).label)
    }

    @Test
    fun theLineIsWrittenInSan() {
        assertEquals("1... c5 2. Nf3 Nc6", Uci.sanLine(afterE4, listOf("c7c5", "g1f3", "b8c6")))
        // An illegal move ends it.
        assertEquals("1... c5", Uci.sanLine(afterE4, listOf("c7c5", "a1a8")))
    }

    @Test
    fun aSessionGreetsSetsWhatItKnowsAndSearches() = runTest {
        val engine = FakeEngine()
        val session = EngineSession(engine)
        session.start(mapOf("Threads" to "4", "Hash" to "256", "MultiPV" to "3"))
        assertEquals("Fakefish 1", session.name)
        // Hash is not among its options, so it is not set.
        assertEquals(listOf("uci", "setoption name Threads value 4", "setoption name MultiPV value 3", "isready"), engine.sent)

        var seen = emptyList<EngineLine>()
        val search = launch { session.analyse(afterE4) { seen = it } }
        yield()
        engine.say(
            "info depth 10 multipv 1 score cp -30 pv c7c5",
            "info depth 10 multipv 2 score cp -35 pv e7e5",
            "info depth 11 multipv 1 score cp -28 pv c7c5 g1f3",
        )
        yield()
        assertEquals(listOf("position fen ${afterE4.fen}", "go infinite"), engine.sent.takeLast(2))
        // A deeper first line starts the round again.
        assertEquals(listOf(EngineLine(1, 11, Score(cp = 28), listOf("c7c5", "g1f3"))), seen)
        search.cancel()

        // The next position stops the old search first and waits for its bestmove.
        val next = launch { session.analyse(Position.START) { seen = it } }
        yield()
        assertTrue(engine.sent.takeLast(3) == listOf("stop", "position fen ${Position.START.fen}", "go infinite"), engine.sent.toString())
        next.cancel()
    }

    @Test
    fun withADepthTheSearchEndsThere() = runTest {
        val engine = FakeEngine()
        val session = EngineSession(engine)
        session.start(emptyMap())
        var seen = emptyList<EngineLine>()
        val search = launch { session.analyse(afterE4, depth = 12) { seen = it } }
        yield()
        assertEquals("go depth 12", engine.sent.last())
        engine.say("info depth 12 multipv 1 score cp -30 pv c7c5", "bestmove c7c5")
        search.join()
        // Done by itself: the lines stay, and the next search needs no stop.
        assertEquals(12, seen.single().depth)
        val next = launch { session.analyse(Position.START, depth = 12) { } }
        yield()
        assertEquals(listOf("position fen ${Position.START.fen}", "go depth 12"), engine.sent.takeLast(2))
        next.cancel()
    }

    @Test
    fun anEngineThatEndsShortOfTheDepthSearchesOn() = runTest {
        // Reckless with several threads: "go depth 12" ends while the reported lines are at 10.
        val engine = FakeEngine()
        val session = EngineSession(engine)
        session.start(emptyMap())
        var seen = emptyList<EngineLine>()
        var finished: Boolean? = null
        val search = launch { finished = session.analyse(afterE4, depth = 12) { seen = it } }
        yield()
        engine.say(
            "info depth 10 multipv 1 score cp -30 pv c7c5",
            "info depth 10 multipv 2 score cp -35 pv e7e5",
            "bestmove c7c5",
        )
        yield()
        // Two short, so it asks for a good deal more than the two that are missing.
        assertEquals("go depth 22", engine.sent.last())
        assertNull(finished)
        engine.say(
            // From the start again: not shown, the board has depth 10 already.
            "info depth 3 multipv 1 score cp -10 pv e7e5",
            "info depth 11 multipv 1 score cp -29 pv c7c5",
            "info depth 11 multipv 2 score cp -33 pv e7e5",
            "info depth 12 multipv 1 score cp -28 pv c7c5",
        )
        yield()
        // The first line is there, the second not yet.
        assertEquals(listOf(12), seen.map { it.depth })
        assertEquals("go depth 22", engine.sent.last())
        engine.say("info depth 12 multipv 2 score cp -32 pv e7e5", "info depth 13 multipv 1 score cp -27 pv c7c5")
        search.join()
        // Both at the depth: stopped here, and what came after is not shown.
        assertEquals("stop", engine.sent.last())
        assertEquals(listOf(12, 12), seen.map { it.depth })
        assertEquals(true, finished)
        // The next position starts without another stop, and asks for more at once: one search instead of two.
        seen = emptyList()
        val next = launch { session.analyse(Position.START, depth = 12) { seen = it } }
        yield()
        assertEquals(listOf("stop", "position fen ${Position.START.fen}", "go depth 22"), engine.sent.takeLast(3))
        engine.say(
            "info depth 11 multipv 1 score cp 28 pv e2e4",
            "info depth 11 multipv 2 score cp 25 pv d2d4",
            "info depth 12 multipv 1 score cp 30 pv e2e4",
            // The second line is not there yet at this depth: not the end.
            "info depth 11 multipv 2 score cp 25 pv d2d4",
            "info depth 12 multipv 1 score cp 30 pv e2e4",
            "info depth 12 multipv 2 score cp 26 pv d2d4",
        )
        next.join()
        assertEquals("stop", engine.sent.last())
        assertEquals(listOf(12, 12), seen.map { it.depth })
    }

    @Test
    fun anEngineThatKeepsEndingShortIsBelievedInTheEnd() = runTest {
        val engine = FakeEngine()
        val session = EngineSession(engine)
        session.start(emptyMap())
        var seen = emptyList<EngineLine>()
        val search = launch { session.analyse(afterE4, depth = 12) { seen = it } }
        yield()
        repeat(3) {
            engine.say("info depth 4 multipv 1 score cp -30 pv c7c5", "bestmove c7c5")
            yield()
        }
        search.join()
        assertEquals(3, engine.sent.count { it.startsWith("go depth") }, engine.sent.toString())
        assertEquals(4, seen.single().depth)
    }

    @Test
    fun aMateEndsTheSearchBeforeTheDepth() = runTest {
        val engine = FakeEngine()
        val session = EngineSession(engine)
        session.start(emptyMap())
        val search = launch { session.analyse(afterE4, depth = 12) { } }
        yield()
        engine.say("info depth 5 multipv 1 score mate 3 pv d8h4", "bestmove d8h4")
        search.join()
        assertEquals(1, engine.sent.count { it.startsWith("go depth") }, engine.sent.toString())
    }

    @Test
    fun endingInTheMiddleOfADepthKeepsTheLastWholeOne() = runTest {
        val engine = FakeEngine()
        val session = EngineSession(engine)
        session.start(emptyMap())
        var seen = emptyList<EngineLine>()
        val search = launch { session.analyse(afterE4, depth = 10) { seen = it } }
        yield()
        engine.say(
            "info depth 10 multipv 1 score cp -30 pv c7c5",
            "info depth 10 multipv 2 score cp -35 pv e7e5",
            "info depth 10 multipv 3 score cp -40 pv e7e6",
            // Stopped on the way to 11: the second line is only a bound, the third still the old one.
            "info depth 11 multipv 1 score cp -29 pv c7c5",
            "info depth 11 multipv 2 score cp -31 lowerbound pv e7e6",
            "info depth 10 multipv 3 score cp -35 pv e7e5",
            "bestmove c7c5",
        )
        search.join()
        assertEquals(listOf(1, 2, 3), seen.map { it.multipv })
        assertEquals(listOf("c7c5", "e7e5", "e7e6"), seen.map { it.pv.first() })
        assertTrue(seen.all { it.depth == 10 })
        assertEquals(1, engine.sent.count { it.startsWith("go depth") }, engine.sent.toString())
    }
}
