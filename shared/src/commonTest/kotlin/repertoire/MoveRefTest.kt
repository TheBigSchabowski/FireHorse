package repertoire

import repertoire.core.Move
import repertoire.core.MoveRefs
import repertoire.core.Outcome
import repertoire.core.Position
import repertoire.core.QuizAttempt
import repertoire.core.QuizBuilder
import repertoire.core.TheoryText
import repertoire.core.Variant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MoveRefTest {
    private fun variant(san: String, theory: String = ""): Variant {
        var p = Position.START
        val moves = san.split(" ").filter { it.isNotBlank() }.map { token ->
            val move = p.parseSan(token) ?: error("illegal $token")
            p = p.play(move)
            move.uci
        }
        return Variant("n", "Sizilianisch", "Najdorf", moves, playWhite = false, theory = theory)
    }

    private val najdorf = variant("e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 a6 Be3 Ng4 Bg5 h6 Bh4 g5 Bg3 Bg7 Be2 O-O")

    @Test
    fun markerFindsWhiteOrBlackMoveByItsSan() {
        assertEquals(12, MoveRefs.resolve(najdorf, 6, blackOnly = false, "Ng4"))
        assertEquals(11, MoveRefs.resolve(najdorf, 6, blackOnly = false, "Be3"))
        assertNull(MoveRefs.resolve(najdorf, 6, blackOnly = true, "Be3"))
        assertEquals(20, MoveRefs.resolve(najdorf, 10, blackOnly = false, "0-0"))
        assertNull(MoveRefs.resolve(najdorf, 8, blackOnly = false, "Ng4"))
    }

    @Test
    fun splitsTheoryIntoTextAndLinks() {
        val parts = MoveRefs.split("Nach @6. Ng4, aber nicht @6... Be3 oder @7.Bg5.", najdorf)
        assertEquals(
            listOf(
                TheoryText.Plain("Nach "),
                TheoryText.Ref("6... Ng4", 12),
                TheoryText.Plain(", aber nicht "),
                TheoryText.Ref("6... Be3", null),
                TheoryText.Plain(" oder "),
                TheoryText.Ref("7. Bg5", 13),
                TheoryText.Plain("."),
            ),
            parts,
        )
        assertEquals(listOf("6... Be3"), MoveRefs.unresolved("@6... Be3", najdorf))
        assertEquals("@6... Ng4", MoveRefs.markerFor(najdorf.moves.take(12).map(Move::fromUci)))
    }

    @Test
    fun tabCompletesNumberThenSwitchesSides() {
        val moves = najdorf.moves.map(Move::fromUci)
        fun tab(text: String) = MoveRefs.complete(text, moves)?.let { (start, marker) -> text.substring(0, start) + marker }

        assertEquals("Nach @7. Bg5", tab("Nach 7"))
        assertEquals("Nach @7... h6", tab("Nach @7. Bg5"))
        assertEquals("Nach @7. Bg5", tab("Nach @7... h6"))
        assertEquals("(@7. Bg5", tab("(@7"))
        assertEquals("@1. e4", tab("1."))
        assertNull(tab("a7"))
        assertNull(tab("Nach 11"))
        // The line ends with Black's 10th move; White's 10th exists, Black's 11th does not.
        assertEquals("@10... O-O", tab("@10. Be2"))
        assertEquals("@10. Be2", tab(tab("@10. Be2")!!))
        assertEquals("@10. Be2", MoveRefs.complete("@10. Be2", moves.take(19))?.second)
    }

    @Test
    fun singleMoveStartsMidwayAndEndsAfterOneMove() {
        val quiz = QuizBuilder.build(listOf(najdorf)).first()
        val right = QuizAttempt(quiz, startPly = 11, singleMove = true)
        assertEquals(najdorf.positions[11].key, right.position.key)
        assertEquals(11, right.played.size)
        val ok = right.submit(right.position.parseSan("Ng4")!!)
        assertIs<Outcome.Correct>(ok)
        assertTrue(ok.finished)

        val wrong = QuizAttempt(quiz, startPly = 11, singleMove = true)
        val miss = wrong.submit(wrong.position.parseSan("e5")!!)
        assertIs<Outcome.Wrong>(miss)
        assertTrue(miss.finished)
        assertEquals(1, wrong.mistakes)
    }
}
