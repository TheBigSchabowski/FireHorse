package repertoire.desktop

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import org.jetbrains.skia.EncodedImageFormat
import repertoire.core.DirectoryStore
import repertoire.core.Move
import repertoire.core.Position
import repertoire.core.Store
import repertoire.ui.App
import repertoire.ui.AppViewModel
import repertoire.ui.BoardTheme
import repertoire.ui.Command
import repertoire.ui.HelpTopic
import repertoire.ui.KeyTarget
import java.io.File
import java.nio.file.Files

/**
 * Renders the main screens off-screen at phone and desktop size, without opening a window:
 *
 *   ./gradlew :desktopApp:renderScreens     → desktopApp/build/screens/
 */
fun main() {
    val out = File("build/screens").apply { mkdirs() }
    val sizes = mapOf("phone" to (393 to 852), "desktop" to (1200 to 820))

    fun render(name: String, setup: (AppViewModel) -> Unit) {
        for ((label, size) in sizes) {
            val dir = Files.createTempDirectory("puzzle").toFile()
            val vm = AppViewModel(Store(DirectoryStore(dir))).also(setup)
            val (w, h) = size
            ImageComposeScene(w * 2, h * 2, Density(2f)) { App(vm, touch = label == "phone") }.use { scene ->
                // Images load on the first frames; render a few before taking the picture.
                repeat(5) { scene.render(it * 20_000_000L) }
                val png = scene.render(200_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes
                File(out, "$name-$label.png").writeBytes(png)
            }
        }
    }

    fun AppViewModel.addVariant(opening: String, name: String, san: String) {
        newVariant()
        val e = editor!!
        e.opening = opening
        e.name = name
        e.playWhite = false
        var p = Position.START
        for (token in san.split(" ")) {
            val move: Move = p.parseSan(token)!!
            editorPlay(move)
            p = p.play(move)
        }
        saveEditor()
    }

    fun AppViewModel.sample() {
        addVariant("Sizilianisch", "English Attack", "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 a6 f3")
        addVariant("Sizilianisch", "Scheveningen", "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 a6 f3 e6")
    }

    render("home") { it.sample() }
    render("training") { vm ->
        vm.sample()
        vm.practice(vm.library.variants.last().id)
        var p = Position.START
        for (san in listOf("e4", "c5", "Nc3")) {
            val move = p.parseSan(san)!!
            vm.submitMove(move)
            p = vm.session!!.attempt!!.position
        }
    }
    render("editor") { vm ->
        vm.sample()
        vm.newVariant(vm.library.variants.first())
    }
    render("editor-durchgehen") { vm ->
        vm.sample()
        vm.editVariant(vm.library.variants.last(), fromStart = true)
        repeat(5) { vm.editorStepForward() }
    }
    val theory = "Gegen den Aufbau mit @6. f3 und g4 spielt Schwarz ((e6)), vorher kam @3... cxd4.\nWenn Weiß g4 zieht, dann ((Nfd7|Sfd7::Springer)) und später ((b5))."
    render("editor-theory") { vm ->
        vm.sample()
        vm.editVariant(vm.library.variants.last())
        vm.editor!!.theory = androidx.compose.ui.text.input.TextFieldValue(theory)
        vm.toggleTheory()
    }
    fun AppViewModel.solveWithTheory() {
        sample()
        editVariant(library.variants.last())
        editor!!.theory = androidx.compose.ui.text.input.TextFieldValue(theory)
        saveEditor()
        practice(library.variants.last().id)
        var p = Position.START
        for (san in "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 a6 f3 e6".split(" ")) {
            val move = p.parseSan(san)!!
            submitMove(move)
            p = p.play(move)
        }
        setGapInput(0, "e6")
        setGapInput(1, "Sfd7")
        setGapInput(2, "b4")
    }
    render("theory-quiz") { it.solveWithTheory() }
    render("theory-checked") { vm -> vm.solveWithTheory(); vm.checkGaps() }
    render("theory-link") { vm -> vm.solveWithTheory(); vm.showPly(6) }
    fun AppViewModel.withDrawing() {
        sample()
        editVariant(library.variants.last())
        editorGoTo(9)
        editorNewDrawing()
        for (m in listOf("4:#d5", "2:b7b5", "f3g4", "3:e4", "5:c6")) editorDrawingMark(repertoire.core.Mark.parse(m))
        editorDrawingQuestion("Was will Weiß, was Schwarz?")
    }
    render("drawing-edit") { it.withDrawing() }
    render("drawing-view") { vm -> vm.withDrawing(); vm.editorFinishDrawing() }
    fun AppViewModel.askDrawing() {
        withDrawing()
        saveEditor()
        practice(library.variants.last().id)
        for (uci in session!!.attempt!!.quiz.variant.moves) submitMove(Move.fromUci(uci))
        drawingMark(repertoire.core.Mark.parse("4:#d5"))
        drawingMark(repertoire.core.Mark.parse("g2g4"))
    }
    render("drawing-quiz") { it.askDrawing() }
    render("drawing-checked") { vm -> vm.askDrawing(); vm.confirm() }
    render("single-move") { vm ->
        vm.sample()
        vm.practiceMoves(vm.library.variants.last().id)
    }
    render("einfuegen") { vm ->
        vm.sample()
        vm.openPaste()
        vm.setPasteText("[Opening \"Sizilianisch: Najdorf\"]\n\n1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 *")
    }
    render("einfuegen-fehler") { vm ->
        vm.sample()
        vm.openPaste()
        vm.setPasteText("1. e4 c5 2. Nf6")
        vm.applyPaste()
    }
    render("einfuegen-pgn") { vm ->
        vm.sample()
        vm.openPaste()
        vm.setPasteText("[Opening \"Sizilianisch: Najdorf\"]\n\n1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 *")
        vm.applyPaste()
        repeat(6) { vm.editorStepForward() }
    }
    render("einfuegen-fen") { vm ->
        vm.sample()
        vm.openPaste()
        vm.setPasteText("r1bqkb1r/1p2pppp/p1np1n2/8/3NP3/2N1B3/PPP2PPP/R2QKB1R b KQkq - 1 7")
        vm.applyPaste()
    }
    render("kuerzel") { vm ->
        vm.sample()
        vm.run(Command.SHORTCUTS)
    }
    render("praefix") { vm ->
        vm.sample()
        vm.practice(vm.library.variants.last().id)
        vm.prefix = true
    }
    render("puzzle-neue-variante") { vm ->
        vm.sample()
        vm.practice(vm.library.variants.last().id)
        vm.run(Command.NEW_VARIANT)
        vm.run(Command.DRAW)
    }
    render("einstellungen") { vm ->
        vm.sample()
        vm.openConfig()
    }
    render("abspielen") { vm ->
        vm.sample()
        vm.setBoardTheme(BoardTheme.GREEN)
        vm.editVariant(vm.library.variants.last(), play = true)
        repeat(7) { vm.editorPlayTick() }
    }
    render("einstellungen-kacheln") { vm ->
        vm.sample()
        vm.setTapGrid(1, 2)
        vm.setHorseLeft(true)
        vm.openConfig()
    }
    render("einstellungen-tasten") { vm ->
        vm.sample()
        vm.setKey(KeyTarget.Of(Command.PASTE_PGN), 'g')
        vm.openConfig()
        vm.keyCapture = KeyTarget.Of(Command.PASTE_FEN)
    }
    render("hilfe") { vm ->
        vm.sample()
        vm.openHelp()
    }
    render("hilfe-pferd") { vm ->
        vm.sample()
        vm.openConfig()
        vm.openHelp(HelpTopic.HORSE)
    }
    render("hilfe-doppeltippen") { vm ->
        vm.sample()
        vm.openHelp(HelpTopic.TAP_ZONES)
    }
    fun AppViewModel.twoOpenings() {
        sample()
        addVariant("Four Knights", "Scotch Game Bb4 Main line", "e4 e5 Nf3 Nc6 Nc3 Nf6 d4 exd4 Nxd4 Bb4")
        addVariant("Four Knights", "Scotch Game Nxe5", "e4 e5 Nf3 Nc6 Nc3 Nf6 d4 Nxd4")
    }
    render("angepinnt") { vm ->
        vm.twoOpenings()
        vm.togglePin("Four Knights")
    }
    render("playlist") { vm ->
        vm.twoOpenings()
        vm.playPlaylist("Four Knights")
        repeat(11 + 3) { vm.editorPlayTick() }
    }
    render("playlist-ueben") { vm ->
        vm.twoOpenings()
        vm.learnPlaylist("Four Knights")
    }
    render("auswahl") { vm ->
        vm.twoOpenings()
        vm.togglePin("Four Knights")
        vm.startSelecting()
        vm.toggleSelectedOpening("Four Knights")
        vm.toggleSelected(vm.library.variants.first().id)
    }
    fun AppViewModel.ownPlaylist() {
        twoOpenings()
        newPlaylist()
        toggleSelected(library.variants.first { it.name == "Scotch Game Nxe5" }.id)
        toggleSelected(library.variants.first { it.name == "Scheveningen" }.id)
        saveSelectionAsPlaylist()
        naming!!.text = "Four Knights + Scheveningen"
        confirmNaming()
    }
    render("meine-playlists") { it.ownPlaylist() }
    render("playlist-name") { vm ->
        vm.twoOpenings()
        vm.newPlaylist()
        vm.toggleSelected(vm.library.variants.first().id)
        vm.saveSelectionAsPlaylist()
        vm.naming!!.text = "Four Knights + Scheveningen"
    }
    render("playlist-varianten-aendern") { vm ->
        vm.ownPlaylist()
        vm.changePlaylistVariants(vm.library.playlists.single().id)
    }
    render("playlist-lernen-fehler") { vm ->
        vm.twoOpenings()
        vm.learnPlaylist("Four Knights")
        val v = vm.session!!.attempt!!.quiz.variant
        vm.submitMove(v.start.parseSan("a3")!!)
        while (!vm.session!!.attempt!!.finished) vm.submitMove(Move.fromUci(v.moves[vm.session!!.attempt!!.played.size]))
    }
    render("hilfe-playlist") { vm ->
        vm.sample()
        vm.openHelp(HelpTopic.PLAYLIST)
    }
    println("Screens in ${out.absolutePath}")
}
