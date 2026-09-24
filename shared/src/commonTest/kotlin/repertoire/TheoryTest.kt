package repertoire

import repertoire.core.Cloze
import repertoire.core.Exchange
import repertoire.core.Fsrs
import repertoire.core.Grade
import repertoire.core.Library
import repertoire.core.Progress
import repertoire.core.TheoryPart
import repertoire.core.Variant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TheoryTest {
    @Test
    fun parsesGapsWithAlternativesAndHints() {
        val parts = Cloze.parse("Wenn g4, dann ((Nfd7|Sfd7::Springer)). Sonst ((e5)).")
        assertEquals(
            listOf(
                TheoryPart.Text("Wenn g4, dann "),
                TheoryPart.Gap(0, listOf("Nfd7", "Sfd7"), "Springer"),
                TheoryPart.Text(". Sonst "),
                TheoryPart.Gap(1, listOf("e5"), null),
                TheoryPart.Text("."),
            ),
            parts,
        )
        assertEquals("Wenn g4, dann Nfd7. Sonst e5.", Cloze.solved("Wenn g4, dann ((Nfd7|Sfd7::Springer)). Sonst ((e5))."))
    }

    @Test
    fun checksAnswersStrictly() {
        val move = Cloze.gaps("((Nxd4))").single()
        assertTrue(Cloze.isCorrect(" Nxd4 ", move))
        assertFalse(Cloze.isCorrect("Nd4", move))
        assertFalse(Cloze.isCorrect("Nxd4+", move))
        assertFalse(Cloze.isCorrect("nxd4", move))
        assertFalse(Cloze.isCorrect("Sxd4", move))
        assertTrue(Cloze.isCorrect("Sxd4", Cloze.gaps("((Nxd4|Sxd4))").single()))

        // Check and mate are different things.
        val mate = Cloze.gaps("((Qxf7#))").single()
        assertFalse(Cloze.isCorrect("Qxf7+", mate))
        assertTrue(Cloze.isCorrect("Qxf7#", mate))

        // No chess rules: the text between the brackets is the answer, whatever it is about.
        assertTrue(Cloze.isCorrect("O-O", Cloze.gaps("((O-O))").single()))
        assertFalse(Cloze.isCorrect("0-0", Cloze.gaps("((O-O))").single()))
        assertTrue(Cloze.isCorrect("Fis-Dur", Cloze.gaps("((Fis-Dur))").single()))
        assertFalse(Cloze.isCorrect("damenflügel", Cloze.gaps("((Damenflügel))").single()))
    }

    @Test
    fun exportAndImportRoundTrip() {
        val moves = Exchange.parseMoves("1. e4 c5 2.Nf3 d6 3. d4 cxd4")
        assertEquals(listOf("e2e4", "c7c5", "g1f3", "d7d6", "d2d4", "c5d4"), moves)
        val v = Variant("abc", "Sizilianisch", "Offen", moves, playWhite = false, theory = "Nun ((Nxd4)).")
        val progress = Progress(cards = mapOf("abc" to Fsrs.review(null, Grade.GOOD)))

        val text = Exchange.export(listOf(v), progress)
        assertTrue("\"moves\": \"1. e4 c5 2. Nf3 d6 3. d4 cxd4\"" in text)

        val fresh = Exchange.import(text, Library(), Progress())
        assertEquals(listOf(v), fresh.library.variants)
        assertEquals(progress.cards, fresh.progress.cards)
        assertEquals(1, fresh.added)

        // Same id again: replaced, not duplicated.
        val again = Exchange.import(text, fresh.library, fresh.progress)
        assertEquals(1, again.library.variants.size)
        assertEquals(1, again.updated)
    }

    @Test
    fun importSkipsBrokenVariantsAndDropsStaleProgress() {
        val old = Variant("abc", "Sizilianisch", "Offen", Exchange.parseMoves("e4 c5"))
        val progress = Progress(cards = mapOf("abc" to Fsrs.review(null, Grade.GOOD)))
        val text = """
            {"format": "eroeffnungs-puzzle", "variants": [
              {"id": "abc", "name": "Offen", "moves": "e4 c5 Nf3"},
              {"name": "Kaputt", "moves": "e4 e4"},
              {"name": "UCI", "moves": "d2d4 d7d5"}
            ]}
        """
        val result = Exchange.import(text, Library(listOf(old)), progress)
        assertEquals(listOf("Offen", "UCI"), result.library.variants.map { it.name })
        assertNull(result.progress.cards["abc"])
        assertEquals(1, result.errors.size)
        assertTrue("e4" in result.errors.single())

        assertFailsWith<IllegalArgumentException> { Exchange.import("{\"format\": \"anki\"}", Library(), Progress()) }
    }
}
