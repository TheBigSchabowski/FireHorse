package repertoire

import repertoire.core.FileStore
import repertoire.core.Library
import repertoire.core.Move
import repertoire.core.Store
import repertoire.ui.AppViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class InMemoryFiles : FileStore {
    val files = mutableMapOf<String, String>()
    override val location = "Speicher"
    override fun read(name: String): String? = files[name]
    override fun write(name: String, text: String) {
        files[name] = text
    }
}

class PlaylistTest {
    private val files = InMemoryFiles()

    private fun app() = AppViewModel(Store(files))

    private fun AppViewModel.add(opening: String, name: String, san: String) {
        newVariant()
        val e = editor!!
        e.opening = opening
        e.name = name
        for (token in san.split(" ")) editorPlay(e.position.parseSan(token)!!)
        saveEditor()
    }

    /** Four Knights with three variants of 2, 3 and 4 moves, and Sicilian with one. */
    private fun sample(): AppViewModel = app().apply {
        add("Sizilianisch", "Najdorf", "e4 c5")
        add("Four Knights", "Eins", "e4 e5")
        add("Four Knights", "Zwei", "e4 e5 Nf3")
        add("Four Knights", "Drei", "e4 e5 Nf3 Nc6")
    }

    private fun AppViewModel.openings() = library.groups().map { it.opening }

    @Test
    fun pinnedOpeningsComeFirstInTheOrderTheyWerePinned() {
        val vm = sample()
        vm.add("Caro-Kann", "Vorstoß", "e4 c6")
        assertEquals(listOf("Caro-Kann", "Four Knights", "Sizilianisch"), vm.openings())
        vm.togglePin("Sizilianisch")
        vm.togglePin("Four Knights")
        assertEquals(listOf("Sizilianisch", "Four Knights", "Caro-Kann"), vm.openings())
        assertEquals(listOf(true, true, false), vm.library.groups().map { it.pinned })
        // Saved with the library, so the pins are still there next time.
        assertEquals(listOf("Sizilianisch", "Four Knights"), app().library.pinned)
        vm.togglePin("Sizilianisch")
        assertEquals(listOf("Four Knights", "Caro-Kann", "Sizilianisch"), vm.openings())
    }

    @Test
    fun atMostThreeOpeningsArePinned() {
        val vm = sample()
        vm.add("Caro-Kann", "Vorstoß", "e4 c6")
        vm.add("Französisch", "Vorstoß", "e4 e6")
        for (o in listOf("Sizilianisch", "Four Knights", "Caro-Kann")) vm.togglePin(o)
        vm.togglePin("Französisch")
        assertEquals(listOf("Sizilianisch", "Four Knights", "Caro-Kann"), vm.library.pinned)
        assertNotNull(vm.flash)
        // A pinned opening without variants any more does not take a place.
        vm.deleteVariant(vm.library.variants.first { it.opening == "Caro-Kann" }.id)
        vm.togglePin("Französisch")
        assertEquals(listOf("Sizilianisch", "Four Knights", "Französisch"), vm.library.pinned)
    }

    @Test
    fun renamingThePinnedOpeningKeepsItPinned() {
        val vm = sample()
        vm.togglePin("Sizilianisch")
        vm.editVariant(vm.library.variants.first { it.opening == "Sizilianisch" })
        vm.editor!!.opening = "Sizilianische Verteidigung"
        vm.saveEditor()
        assertEquals(listOf("Sizilianische Verteidigung"), vm.library.pinned)
        assertTrue(vm.library.groups().first().pinned)
    }

    @Test
    fun thePlaylistPlaysEveryVariantOfTheOpeningInTurn() {
        val vm = sample()
        vm.playPlaylist("Four Knights")
        assertEquals("Eins", vm.editor!!.name)
        assertEquals(0, vm.editor!!.cursor)
        assertTrue(vm.editor!!.playing)
        repeat(2) { vm.editorPlayTick() }
        assertTrue(vm.editor!!.atEnd)
        // At the end of a line comes the next variant, from its start, still playing.
        vm.editorPlayTick()
        assertEquals("Zwei", vm.editor!!.name)
        assertEquals(1, vm.editor!!.playlist!!.index)
        assertEquals(0, vm.editor!!.cursor)
        assertTrue(vm.editor!!.playing)
        repeat(4) { vm.editorPlayTick() }
        assertEquals("Drei", vm.editor!!.name)
        // After the last one it stops, nothing was saved or changed.
        repeat(5) { vm.editorPlayTick() }
        assertEquals("Drei", vm.editor!!.name)
        assertFalse(vm.editor!!.playing)
        assertEquals(4, vm.library.variants.size)
        // Play once more: from the first variant again.
        vm.editorTogglePlay()
        assertEquals("Eins", vm.editor!!.name)
        assertTrue(vm.editor!!.playing)
    }

    @Test
    fun withRepeatThePlaylistStartsOverAfterTheLastVariant() {
        val vm = sample()
        vm.setPlayLoop(true)
        vm.playPlaylist("Four Knights")
        repeat(3 + 4 + 5) { vm.editorPlayTick() }
        assertEquals("Eins", vm.editor!!.name)
        assertTrue(vm.editor!!.playing)
    }

    @Test
    fun stepsBetweenVariantsKeepThePlayerAsItWas() {
        val vm = sample()
        vm.playPlaylist("Four Knights")
        vm.editorTogglePlay()
        vm.playlistStep(1)
        assertEquals("Zwei", vm.editor!!.name)
        assertFalse(vm.editor!!.playing)
        vm.playlistStep(1)
        vm.playlistStep(1)
        assertEquals("Drei", vm.editor!!.name)
        vm.playlistStep(-2)
        assertEquals("Eins", vm.editor!!.name)
    }

    @Test
    fun changesStopThePlaylistInsteadOfGettingLost() {
        val vm = sample()
        vm.playPlaylist("Four Knights")
        vm.editor!!.name = "Eins, umbenannt"
        repeat(3) { vm.editorPlayTick() }
        assertEquals("Eins, umbenannt", vm.editor!!.name)
        assertFalse(vm.editor!!.playing)
        vm.playlistStep(1)
        assertEquals("Eins, umbenannt", vm.editor!!.name)
        assertNotNull(vm.flash)
    }

    @Test
    fun practisingThePlaylistAsksEveryVariantInOrderWithoutGrading() {
        val vm = sample()
        vm.practicePlaylist("Four Knights")
        val s = vm.session!!
        assertFalse(s.graded)
        val names = mutableListOf<String>()
        while (s.attempt != null) {
            val attempt = s.attempt!!
            names += attempt.quiz.variant.name
            for (uci in attempt.quiz.variant.moves) vm.submitMove(Move.fromUci(uci))
            vm.grade()
        }
        assertEquals(listOf("Eins", "Zwei", "Drei"), names)
        assertTrue(vm.progress.cards.isEmpty())
        assertNull(vm.editor)
    }

    @Test
    fun theOpeningWithoutANameIsAGroupOfItsOwn() {
        val vm = app()
        vm.add("", "Irgendwas", "d4 d5")
        assertEquals(listOf(Library.NO_OPENING), vm.openings())
        vm.practicePlaylist(Library.NO_OPENING)
        assertEquals("Irgendwas", vm.session!!.attempt!!.quiz.variant.name)
    }

    @Test
    fun deletingAnOpeningDeletesAllItsVariantsAndItsPin() {
        val vm = sample()
        vm.togglePin("Four Knights")
        // Every variant gets a learning state, here by pausing it.
        vm.startLearning()
        repeat(4) { vm.suspendCurrent() }
        assertEquals(4, vm.progress.cards.size)
        vm.deleteOpening("Four Knights")
        assertEquals(listOf("Sizilianisch"), vm.openings())
        assertEquals(listOf("Najdorf"), vm.library.variants.map { it.name })
        assertTrue(vm.library.pinned.isEmpty())
        assertEquals(vm.library.variants.map { it.id }.toSet(), vm.progress.cards.keys)
    }

    @Test
    fun selectedVariantsAreDeletedTogether() {
        val vm = sample()
        vm.startSelecting()
        assertEquals(emptySet(), vm.selected)
        val ids = vm.library.variants.filter { it.name in setOf("Najdorf", "Zwei") }.map { it.id }
        ids.forEach(vm::toggleSelected)
        vm.deleteSelected()
        assertEquals(listOf("Eins", "Drei"), vm.library.variants.map { it.name })
        // Still selecting, with nothing ticked.
        assertEquals(emptySet(), vm.selected)
        vm.endSelecting()
        assertNull(vm.selected)
    }

    @Test
    fun aWholeOpeningIsTickedAndUntickedAtOnce() {
        val vm = sample()
        vm.startSelecting()
        vm.toggleSelectedOpening("Four Knights")
        assertEquals(3, vm.selected!!.size)
        vm.toggleSelected(vm.library.variants.first { it.name == "Zwei" }.id)
        // Not all of them ticked: ticks the rest.
        vm.toggleSelectedOpening("Four Knights")
        assertEquals(3, vm.selected!!.size)
        vm.toggleSelectedOpening("Four Knights")
        assertEquals(emptySet(), vm.selected)
    }

    @Test
    fun theSelectionPlaysInTheOrderOfTheList() {
        val vm = sample()
        vm.togglePin("Sizilianisch")
        vm.startSelecting()
        // Ticked in another order than they are shown: pinned Sicilian first, then Four Knights.
        for (name in listOf("Drei", "Najdorf", "Eins")) vm.toggleSelected(vm.library.variants.first { it.name == name }.id)
        vm.playSelected()
        val p = vm.editor!!.playlist!!
        assertEquals("Auswahl", p.title)
        assertEquals(listOf("Najdorf", "Eins", "Drei"), p.ids.map { id -> vm.library.variants.first { it.id == id }.name })
        vm.cancelEditor()
        vm.practiceSelected()
        assertEquals("Najdorf", vm.session!!.attempt!!.quiz.variant.name)
        assertEquals(2, vm.session!!.queue.size)
    }
}
