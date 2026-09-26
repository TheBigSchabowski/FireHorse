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
            ImageComposeScene(w * 2, h * 2, Density(2f)) { App(vm) }.use { scene ->
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
    render("single-move") { vm ->
        vm.sample()
        vm.practiceMoves(vm.library.variants.last().id)
    }
    println("Screens in ${out.absolutePath}")
}
