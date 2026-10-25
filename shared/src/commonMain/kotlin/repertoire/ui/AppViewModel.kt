package repertoire.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import repertoire.core.Cloze
import repertoire.core.Drawing
import repertoire.core.DrawingCheck
import repertoire.core.EngineEntry
import repertoire.core.EngineSession
import repertoire.core.Explorer
import repertoire.core.ExplorerResult
import repertoire.core.ExplorerSource
import repertoire.core.Exchange
import repertoire.core.FilePicker
import repertoire.core.GameSite
import repertoire.core.GameSources
import repertoire.core.GameTree
import repertoire.core.OpeningTreeFile
import repertoire.core.TreeRequest
import repertoire.core.Fsrs
import repertoire.core.Ink
import repertoire.core.Grade
import repertoire.core.TheoryPart
import repertoire.core.Library
import repertoire.core.Mark
import repertoire.core.Move
import repertoire.core.MoveRefs
import repertoire.core.Outcome
import repertoire.core.PastedLine
import repertoire.core.PenColor
import repertoire.core.Pgn
import repertoire.core.Position
import repertoire.core.Progress
import repertoire.core.Quiz
import repertoire.core.QuizAttempt
import repertoire.core.OpeningGroup
import repertoire.core.QuizBuilder
import repertoire.core.VariantSearch
import repertoire.core.ReviewState
import repertoire.core.SavedPlaylist
import repertoire.core.Scheduler
import repertoire.core.Stats
import repertoire.core.Store
import repertoire.core.Variant
import repertoire.core.engineFilesIn
import repertoire.core.launchEngine
import repertoire.core.processorCount
import repertoire.core.newId
import repertoire.core.numberedSan
import repertoire.core.toggle

enum class FeedbackTone { GOOD, BAD, INFO }

data class Feedback(val text: String, val tone: FeedbackTone)

/** One task in a session: a whole variant from the start, or with [singleMove] just the move after [startPly]. */
data class Drill(val quiz: Quiz, val startPly: Int = 0, val singleMove: Boolean = false) {
    fun attempt() = QuizAttempt(quiz, startPly, singleMove)
}

private val QuizAttempt.drill get() = Drill(quiz, startPly, singleMove)

class TrainingSession(
    val title: String,
    val graded: Boolean,
    val queue: ArrayDeque<Drill>,
    /** A playlist being learned: a puzzle with a mistake comes again right away, the next one only after a clean solve. */
    val untilClean: Boolean = false,
) {
    /** Puzzles at the start, for "Puzzle 2 von 5". */
    val total: Int = queue.size

    /** Puzzles solved without a mistake, so far. */
    var solved by mutableIntStateOf(0)

    var attempt by mutableStateOf<QuizAttempt?>(null)

    /** Bumped after every move because [QuizAttempt] itself is not observable. */
    var revision by mutableIntStateOf(0)
    var feedback by mutableStateOf<Feedback?>(null)

    /** Squares to tint: square → true (right) / false (wrong). */
    var marks by mutableStateOf<Map<Int, Boolean>>(emptyMap())
    var showTarget by mutableStateOf(false)
    var answered by mutableIntStateOf(0)

    /** Mistakes from earlier tries of the current puzzle, so restarting does not hide them from grading. */
    var earlierMistakes by mutableIntStateOf(0)

    /** What has been typed into the theory gaps of the current puzzle, by gap index. */
    var gapInputs by mutableStateOf<List<String>>(emptyList())

    /** Gap index → answered right, once "Prüfen" was pressed; null before. */
    var gapResults by mutableStateOf<List<Boolean>?>(null)

    /** Board shown after this many moves of the variant, from a "@8. Ng4" in the theory; null shows the attempt. */
    var preview by mutableStateOf<Int?>(null)

    /** Drawing mode of the board (three taps or Ctrl+B m); ends with the session. */
    val drawMode = mutableStateOf(false)

    /** The board turned round with 8, until the next variant comes up. */
    var flipped by mutableStateOf(false)

    /** Single moves do not ask the theory. */
    val gaps: List<TheoryPart.Gap>
        get() = attempt?.takeIf { !it.singleMove }?.quiz?.variant?.theory?.let(Cloze::gaps) ?: emptyList()

    val gapsChecked: Boolean get() = gapResults != null || gaps.isEmpty()

    /** The variant's saved drawings, drawn from memory after the gaps; single moves do not ask them either. */
    val drawings: List<Drawing>
        get() = attempt?.takeIf { !it.singleMove }?.quiz?.variant?.drawings ?: emptyList()

    /** Which of [drawings] is asked; they come one after the other. */
    var drawingIndex by mutableIntStateOf(0)

    /** What has been drawn for it so far. */
    var drawingInput by mutableStateOf<List<Mark>>(emptyList())

    /** The drawings checked so far, in order. */
    var drawingResults by mutableStateOf<List<DrawingCheck>>(emptyList())

    /** The drawing being asked: once the position is reached and the gaps are checked; null before and without drawings. */
    val drawing: Drawing? get() = if (attempt?.finished == true && gapsChecked) drawings.getOrNull(drawingIndex) else null

    /** How [drawing] went, once checked. */
    val drawingCheck: DrawingCheck? get() = drawingResults.getOrNull(drawingIndex)

    /** The position is reached, every gap and every drawing is checked: ready to be graded. */
    val done: Boolean get() = attempt?.finished == true && gapsChecked && drawingResults.size == drawings.size
}

/** Variants played one after the other in the editor: an opening's, or the selected ones; [index] is the one that is open. */
data class Playlist(val title: String, val ids: List<String>, val index: Int = 0) {
    val last: Boolean get() = index == ids.lastIndex
}

/** A variant being entered: play the moves, then name it. */
class EditorState(
    val editingId: String?,
    opening: String,
    name: String,
    moves: List<Move>,
    playWhite: Boolean,
    theory: String = "",
    /** Open the board at the start instead of at the end of the line, to step through it. */
    fromStart: Boolean = false,
    startFen: String = Position.START_FEN,
    /** Shown above the board, e.g. what a pasted PGN turned into. */
    val note: String? = null,
    /** Set while a playlist runs: at the end of the line the player goes on with the next variant. */
    val playlist: Playlist? = null,
    drawings: List<Drawing> = emptyList(),
    /** Durchgehen: the line stays as it is; a different move on the board opens a [side] line instead. */
    browse: Boolean = false,
) {
    var opening by mutableStateOf(opening)

    /** Going through the line (Durchgehen) rather than changing it (Bearbeiten). */
    var browsing by mutableStateOf(browse)

    /**
     * While going through: moves tried on the board that leave the line, from the position at
     * [cursor] on; the line itself is untouched. Empty: on the line.
     */
    var side by mutableStateOf<List<Move>>(emptyList())

    /** How many moves of [side] are on the board. */
    var sideCursor by mutableIntStateOf(0)

    val inSide: Boolean get() = side.isNotEmpty()
    var name by mutableStateOf(name)

    /** Arrows and circles saved with the variant; shown at their position, asked after the puzzle. */
    var drawings by mutableStateOf(drawings)

    /** The saved drawing that the board draws into, by index in [drawings]; null while the board plays moves. */
    var drawingAt by mutableStateOf<Int?>(null)

    private var movesState by mutableStateOf(moves)

    var moves: List<Move>
        get() = movesState
        set(value) {
            // A drawing belongs to its position: where the line changes, the drawings after it go with it.
            val same = movesState.zip(value).takeWhile { (a, b) -> a == b }.size
            finishDrawing()
            drawings = drawings.filter { it.ply <= same }
            movesState = value
        }
    var playWhite by mutableStateOf(playWhite)
    var theory by mutableStateOf(TextFieldValue(theory))

    /** Where the line begins; not the start of a game when it came from a FEN. */
    var startFen by mutableStateOf(startFen)

    val setUp: Boolean get() = startFen != Position.START_FEN

    val start: Position get() = if (setUp) Position.fromFen(startFen) else Position.START

    /** The theory field is open (Cmd/Ctrl+T). */
    var showTheory by mutableStateOf(theory.isNotBlank())

    /** The player is running: the cursor moves on by itself. */
    var playing by mutableStateOf(false)

    /** The engine plays one side from here on (Ctrl+B Shift+R); null: both sides are played by hand. */
    var opponent by mutableStateOf<EngineOpponent?>(null)

    /** The board is at the tip of what has been played: the end of the line, or of the side line when in one. */
    val atTip: Boolean get() = if (inSide) sideCursor >= side.size else atEnd

    /** Drawing mode of the board (three taps or Ctrl+B m). */
    val drawMode = mutableStateOf(false)

    /** Bumped to move the keyboard focus into the theory field. */
    var focusTheory by mutableIntStateOf(0)

    /**
     * How many moves are on the board. Everything after it stays part of the variant, so the line
     * can be walked through without losing it; only playing a different move there cuts it off.
     */
    var cursor by mutableIntStateOf(if (fromStart) 0 else moves.size)

    /** The moves up to [cursor], i.e. the ones on the board. */
    val shown: List<Move> get() = moves.take(cursor)

    val atEnd: Boolean get() = cursor >= moves.size

    /** The moves on the board: the line up to [cursor], then those of a side line. */
    val boardMoves: List<Move> get() = shown + side.take(sideCursor)

    /** Position shown on the board. */
    val position: Position get() = boardMoves.fold(start) { p, m -> p.play(m) }

    /** Position the whole line leads to, the one that is trained. */
    val target: Position get() = moves.fold(start) { p, m -> p.play(m) }

    /** Start position followed by the position after each move. */
    val positions: List<Position> get() = moves.runningFold(start) { p, m -> p.play(m) }

    /** The variant as it would be saved, for checks that work on a [Variant]. */
    fun asVariant(): Variant =
        Variant(editingId ?: "", opening, name, moves.map { it.uci }, playWhite, theory.text, startFen, keptDrawings)

    /** Drawings with something in them; an empty one is not saved. */
    val keptDrawings: List<Drawing> get() = drawings.filter { it.marks.isNotEmpty() }

    val editedDrawing: Drawing? get() = drawingAt?.let(drawings::getOrNull)

    /** The saved drawings at the position on the board; none in a side line, they belong to the line. */
    val drawingsHere: List<Drawing> get() = if (inSide) emptyList() else drawings.filter { it.ply == cursor }

    /** Back on the line, where the side line left it. */
    fun leaveSide() {
        side = emptyList()
        sideCursor = 0
    }

    /** The board plays moves again; a drawing left empty is dropped. */
    fun finishDrawing() {
        drawingAt = null
        drawings = keptDrawings
    }

    /** "Ausgangsstellung" or "nach 8. Ng4": where a drawing at [ply] is. */
    fun plyLabel(ply: Int): String = asVariant().plyLabel(ply)
}

/** "Ausgangsstellung" or "nach 8. Ng4": the position after the first [ply] moves. */
fun Variant.plyLabel(ply: Int): String =
    if (ply == 0) "Ausgangsstellung" else "nach " + numberedSan(positions[ply - 1], Move.fromUci(moves[ply - 1]))

/** The dialog that asks for a playlist's name: a new one from the selection ([playlistId] null), or a new name. */
class PlaylistNaming(val playlistId: String?, name: String = "") {
    var text by mutableStateOf(name)
}

/** The "PGN/FEN einfügen" dialog: paste the text, read it, then carry on in the editor. */
class PasteState(
    /** Only for the title and example: what was asked for with Ctrl+B x / f; both are read either way. */
    val kind: Command? = null,
) {
    var text by mutableStateOf("")

    /** Why the text could not be read, shown under the field. */
    var error by mutableStateOf<String?>(null)
}

/** The depths the − and + buttons step through, 0 first: no limit. Any other depth can be typed. */
val ENGINE_DEPTHS = listOf(0, 10, 12, 14, 16, 18, 20, 22, 25, 30, 35, 40, 50)

/** What can be set for an engine, by button or typed. Depth 0 is no limit. */
val ENGINE_LINES = 1..5
val ENGINE_DEPTH = 0..99
val ENGINE_HASH_MB = 16..65536

/** Threads: one up to the cores of this computer, or more if an engine already had more set. */
fun engineThreads(current: Int = 1): IntRange = 1..maxOf(processorCount(), current)

/** Which move the engine as opponent plays: the best, second best or third best. */
val OPPONENT_RANKS = 1..3

/**
 * The engine as opponent in the editor: it plays [white] or Black, searching [depth] deep for each
 * move, and plays its [rank]-best move (1 the best); with fewer moves to choose from, the worst of them.
 */
data class EngineOpponent(val white: Boolean, val depth: Int, val rank: Int = 1)

class AppViewModel(private val store: Store, private val files: FilePicker? = null) {
    var library by mutableStateOf(store.loadLibrary())
        private set
    var progress by mutableStateOf(store.loadProgress())
        private set
    var session by mutableStateOf<TrainingSession?>(null)
        private set
    var editor by mutableStateOf<EditorState?>(null)
        private set
    var paste by mutableStateOf<PasteState?>(null)
        private set

    val dataLocation: String get() = store.location

    // ---- Shortcuts -----------------------------------------------------------------------

    val shortcutKeys = ShortcutKeys(this)

    /** Ctrl+B was pressed; the next key picks the command. */
    var prefix by mutableStateOf(false)

    var showShortcuts by mutableStateOf(false)

    /** The text fields that have the focus right now (one at most, but leaving one and entering the next may overlap). */
    val typingIn = mutableSetOf<Any>()

    /** A text field has the focus: keys without Ctrl+B are typed, not commands. */
    val typing: Boolean get() = typingIn.isNotEmpty()

    /** Ctrl+N was pressed; a digit picks the opening whose playlist is learned. */
    var picking by mutableStateOf(false)

    /** A short note that disappears by itself, e.g. why a shortcut did nothing. */
    var flash by mutableStateOf<String?>(null)

    /** Drawing mode of the board on screen: the editor's if one is open, else the puzzle's. */
    private val drawModeOnScreen get() = editor?.drawMode ?: session?.takeIf { it.attempt != null }?.drawMode

    val drawMode: Boolean get() = drawModeOnScreen?.value == true

    /**
     * Shift+A: what is drawn on the board is out of sight (kept, not wiped) until the same again,
     * or until something is drawn: the board shows it all again then.
     */
    val drawingsHidden = mutableStateOf(false)

    /** Color and shape to draw with, the same on every board until changed. */
    val pen = mutableStateOf(Pen())

    /**
     * A board is drawing right now: drawing mode, a saved drawing, or one from memory in a quiz.
     * Then ← → switch the pen and ↑ ↓ circle or field, instead of stepping through the moves.
     */
    val penActive: Boolean
        get() = editor?.let { it.drawMode.value || it.editedDrawing != null }
            ?: session?.let { s -> s.attempt != null && (s.drawMode.value || (s.drawing != null && s.drawingCheck == null && s.preview == null)) }
            ?: false

    /** The next pen ([by] 1) or the one before (-1). */
    fun penStep(by: Int) {
        pen.value = pen.value.step(by)
    }

    /** Circle or filled field for a tap on a single square. */
    fun penToggleField() {
        pen.value = pen.value.copy(field = !pen.value.field)
    }

    /** A key while drawing ([DrawKey]): pick a pen, circle or field, wipe, colors, done. */
    fun runDrawKey(key: DrawKey) {
        if (!penActive) return
        key.pen?.let {
            pen.value = pen.value.copy(color = it)
            return
        }
        when (key) {
            DrawKey.SHAPE -> penToggleField()
            DrawKey.CLEAR -> clearBoard()
            DrawKey.COLORS -> colorsOpen = !colorsOpen
            DrawKey.DONE -> endDrawing()
            else -> Unit
        }
    }

    /** Leaves drawing mode, or finishes the saved drawing; a drawing from memory waits for „Prüfen“. */
    private fun endDrawing() {
        val e = editor
        when {
            e != null && e.editedDrawing != null -> editorFinishDrawing()
            e != null -> e.drawMode.value = false
            else -> session?.drawMode?.value = false
        }
    }

    /**
     * 8. In the editor: the variant is played from the other side (saved with "Speichern"; the
     * learning state stays, the moves are the same), so one opening can have variants for White and
     * for Black. In a puzzle: only the board on screen turns round, until the next variant.
     */
    fun flipBoard() {
        val s = session
        val e = editor
        when {
            e != null -> {
                e.playWhite = !e.playWhite
                flash = "Du spielst diese Variante jetzt mit " + (if (e.playWhite) "Weiß" else "Schwarz") + " – mit „Speichern“ übernehmen."
            }
            s?.attempt != null -> s.flipped = !s.flipped
            else -> flash = "Drehen geht, sobald ein Brett zu sehen ist."
        }
    }

    /** Shift+A: the drawings on the board out of sight, or back again. */
    fun toggleDrawingsHidden() {
        flash = when {
            drawModeOnScreen == null -> "Ausblenden geht, sobald ein Brett zu sehen ist."
            penActive -> "Beim Zeichnen bleibt alles zu sehen."
            else -> {
                drawingsHidden.value = !drawingsHidden.value
                if (drawingsHidden.value) "Zeichnungen ausgeblendet – Shift+A zeigt sie wieder." else "Zeichnungen wieder zu sehen."
            }
        }
    }

    /** Bumped by Ctrl+B w: the board wipes its scribbles. */
    var boardClears by mutableIntStateOf(0)
        private set

    /** An editor with something in it, which a new variant would throw away. */
    private val editorInUse: Boolean
        get() = editor?.let { it.moves.isNotEmpty() || it.name.isNotBlank() || it.theory.text.isNotBlank() } == true

    /**
     * Runs a shortcut. It works on every screen; a puzzle stays open underneath the new variant.
     * [fromKeyboard]: by Ctrl+B, so the settings open at the keys.
     */
    fun run(command: Command, fromKeyboard: Boolean = false) {
        prefix = false
        if (command == Command.SHORTCUTS) {
            showShortcuts = !showShortcuts
            return
        }
        showShortcuts = false
        // Everything else happens on the screen underneath, so the preferences make way.
        if (command != Command.SETTINGS) prefs = null
        when (command) {
            Command.SHORTCUTS -> Unit
            Command.SETTINGS -> if (prefs == null) {
                prefs = Prefs.Config
                showKeys = fromKeyboard
            } else {
                prefs = null
            }
            Command.DRAW -> drawModeOnScreen?.let { it.value = !it.value }
                ?: run { flash = "Zeichnen geht, sobald ein Brett zu sehen ist." }
            Command.DRAW_KEPT -> editor?.let { toggleKeptDrawing(it) }
                ?: run { flash = "Zeichnungen zum Abfragen entstehen im Editor (Durchgehen oder Bearbeiten)." }
            Command.CLEAR -> if (drawModeOnScreen != null) clearBoard() else flash = "Wegwischen geht, sobald ein Brett zu sehen ist."
            Command.COLORS -> colorsOpen = !colorsOpen
            Command.EXPLORER -> explorerOpen = !explorerOpen
            Command.ENGINE -> engineOpen = !engineOpen
            Command.NOTES -> if (notesOf != null) notesOf = null else openNotes()
            Command.SEARCH -> toggleSearch()
            Command.PLAY -> editor?.let { editorTogglePlay() }
                ?: run { flash = "Abspielen geht in einer geöffneten Variante (Durchgehen)." }
            Command.COPY_FEN, Command.COPY_PGN -> copyBoard(command)
            Command.COPY_ALL -> copyAll(here = false)
            Command.COPY_ALL_HERE -> copyAll(here = true)
            Command.NEW_VARIANT, Command.PASTE_PGN, Command.PASTE_FEN, Command.PASTE_ALL -> when {
                editorInUse -> flash = "Erst die offene Variante speichern oder abbrechen."
                command == Command.NEW_VARIANT -> {
                    paste = null
                    newVariant()
                }
                else -> paste = PasteState(command)
            }
        }
    }

    /** Puts text on the system clipboard; set by the app window. */
    var copyToClipboard: ((String) -> Unit)? = null

    /** Where the line on the board began, and the numbered moves ("1. e4", "1... c5") up to the board's position. */
    private class BoardLine(val startFen: String, val numbered: List<String>, val position: Position)

    /** The position on the board, wherever it is: in the editor (at the cursor) or in a puzzle (with a "@8." preview). */
    private fun boardLine(): BoardLine? {
        editor?.let { e ->
            val moves = e.boardMoves
            val numbered = moves.runningFold(e.start) { p, m -> p.play(m) }.zip(moves).map { (before, move) -> numberedSan(before, move) }
            return BoardLine(e.startFen, numbered, e.position)
        }
        val attempt = session?.attempt ?: return null
        val variant = attempt.quiz.variant
        val preview = session?.preview
        return if (preview != null) {
            BoardLine(variant.startFen, variant.numberedSan().take(preview), variant.positions[preview])
        } else {
            BoardLine(variant.startFen, attempt.played.toList(), attempt.position)
        }
    }

    /** Ctrl+B y / v: the FEN of the board, or the PGN of the moves that led there, on the clipboard. */
    private fun copyBoard(command: Command) {
        val line = boardLine()
        val copy = copyToClipboard
        when {
            line == null -> flash = "Kopieren geht, sobald ein Brett zu sehen ist."
            copy == null -> flash = "Hier gibt es keine Zwischenablage."
            command == Command.COPY_FEN -> {
                copy(line.position.fen)
                flash = "FEN kopiert."
            }
            line.numbered.isEmpty() && line.startFen == Position.START_FEN -> flash = "Noch kein Zug auf dem Brett."
            else -> {
                copy(Pgn.export(line.startFen, line.numbered))
                flash = "PGN kopiert (${line.numbered.size} Halbzüge)."
            }
        }
    }

    /**
     * Ctrl+B p: the whole variant on the board as PGN, with names, theory and drawings: the one in
     * the editor as it is right now, or the puzzle's. Ctrl+B o ([here]): only the moves up to the
     * position on the board and the drawings up to there; the theory is whole either way.
     */
    private fun copyAll(here: Boolean) {
        val e = editor
        val s = session
        val variant = e?.asVariant() ?: s?.attempt?.quiz?.variant
        val ply = when {
            !here -> null
            e != null -> e.cursor
            else -> s?.preview ?: s?.attempt?.played?.size
        }
        val copy = copyToClipboard
        when {
            variant == null -> flash = "Alles kopieren geht in einer Variante oder einem Puzzle."
            copy == null -> flash = "Hier gibt es keine Zwischenablage."
            (ply ?: variant.moves.size) == 0 && !variant.setUp -> flash = "Noch kein Zug auf dem Brett."
            else -> {
                val plies = ply?.coerceAtMost(variant.moves.size) ?: variant.moves.size
                copy(Pgn.exportAll(variant, library.settings.penColors, upTo = plies))
                val drawings = variant.drawings.count { it.ply <= plies }
                val extras = listOfNotNull(
                    "Theorie".takeIf { variant.theory.isNotBlank() },
                    drawings.takeIf { it > 0 }?.let { if (it == 1) "1 Zeichnung" else "$it Zeichnungen" },
                )
                flash = (if (here) "Bis hier mit allem kopiert" else "PGN mit allem kopiert") +
                    " ($plies Halbzüge" + extras.joinToString("") { ", $it" } + ")."
            }
        }
    }

    /** The position on the board right now, wherever it is; null without a board. */
    val boardPosition: Position? get() = boardLine()?.position

    // ---- lichess opening database (Ctrl+B l) ---------------------------------------------

    /** The database window, beside whatever is open; it follows the board. */
    var explorerOpen by mutableStateOf(false)

    var explorerSource by mutableStateOf(ExplorerSource.MASTERS)

    /** Where the database window was dragged to, from its place at the bottom right. */
    val explorerAt = mutableStateOf(Offset.Zero)

    /** Answers already fetched, so stepping back and forth asks lichess only once per position. */
    private val explorerCache = mutableMapOf<Pair<ExplorerSource, String>, ExplorerResult>()

    /** What the database knows about [fen]; throws [IllegalStateException] with a German explanation. */
    suspend fun explore(source: ExplorerSource, fen: String): ExplorerResult =
        explorerCache[source to fen] ?: Explorer.fetch(source, fen, library.settings.lichessToken).also { explorerCache[source to fen] = it }

    /** A move clicked in the database or the engine's lines: played in the editor; a puzzle is not answered for you. */
    fun playSuggested(uci: String) = playSuggested(runCatching { Move.fromUci(uci) }.getOrNull())

    /** A suggested [move]; null or not legal on the board (e.g. for a position that is gone): nothing is played. */
    fun playSuggested(move: Move?) {
        val e = editor
        when {
            e == null -> flash = "Vorgeschlagene Züge spielen geht im Editor (Durchgehen oder Bearbeiten)."
            e.editedDrawing != null || e.drawMode.value -> flash = "Erst das Zeichnen beenden."
            move == null || !e.position.isLegal(move) -> flash = "Dieser Zug passt nicht zur Stellung auf dem Brett."
            else -> editorPlay(move)
        }
    }

    /** Shift+click on an engine line: all its moves in the editor, as far as they are legal on the board. */
    fun playSuggestedLine(pv: List<String>) {
        val e = editor
        when {
            e == null -> flash = "Vorgeschlagene Züge spielen geht im Editor (Durchgehen oder Bearbeiten)."
            e.editedDrawing != null || e.drawMode.value -> flash = "Erst das Zeichnen beenden."
            else -> for (uci in pv) {
                val move = runCatching { Move.fromUci(uci) }.getOrNull()?.takeIf(e.position::isLegal) ?: break
                editorPlay(move)
            }
        }
    }

    // ---- One's own games as a tree, like openingtree.com ("Partien" in the database window) ----

    /** The games of one player as a tree of positions; null until one is loaded. Kept in "partien.json". */
    var gameTree by mutableStateOf(store.loadGameTree())
        private set

    /** What the form for a new tree says; it stays filled in while the window is closed. */
    var treeRequest by mutableStateOf(gameTree?.request ?: TreeRequest())

    /** The form for other games is shown instead of the tree. */
    var treeFormOpen by mutableStateOf(false)

    /** How many games are in the tree being built; null while nothing is loading. */
    var treeLoading by mutableStateOf<Int?>(null)
        private set

    /** Why the last tree could not be built. */
    var treeError by mutableStateOf<String?>(null)

    /** Downloads go on when the window is closed; only [cancelTree] stops them. */
    private val scope by lazy { MainScope() }
    private var treeJob: Job? = null

    /** Builds the tree [treeRequest] asks for: downloads from lichess or chess.com, or opens a PGN file for it. */
    fun loadTree() {
        val request = treeRequest.copy(player = treeRequest.player.trim())
        if (request.site == GameSite.PGN_FILE) {
            val picker = files ?: return
            picker.openBytes(listOf("pgn")) { bytes ->
                if (bytes != null) buildTree { progress -> GameSources.fromPgn(request, bytes.decodeToString(), progress) }
            }
        } else {
            val token = library.settings.lichessToken
            buildTree { progress -> GameSources.download(request, token, progress) }
        }
    }

    /** Opens a tree saved by openingtree.com (or by FireHorse). */
    fun importTree() {
        val picker = files ?: return
        picker.openBytes(listOf(OpeningTreeFile.EXTENSION)) { bytes ->
            if (bytes != null) buildTree { OpeningTreeFile.read(bytes) }
        }
    }

    /** Saves the tree as a ".tree" file that openingtree.com can load. */
    fun exportTree() {
        val tree = gameTree ?: return
        val picker = files ?: return
        scope.launch {
            val bytes = withContext(Dispatchers.Default) { OpeningTreeFile.write(tree) }
            picker.saveBytes(OpeningTreeFile.fileName(tree), bytes) { file ->
                if (file != null) flash = "Baum gespeichert: $file"
            }
        }
    }

    fun cancelTree() {
        treeJob?.cancel()
    }

    /** Forgets the tree; the games stay where they came from. */
    fun clearTree() {
        cancelTree()
        gameTree = null
        treeFormOpen = false
        store.saveGameTree(null)
    }

    private fun buildTree(work: suspend (progress: (Int) -> Unit) -> GameTree) {
        treeJob?.cancel()
        treeError = null
        treeLoading = 0
        treeJob = scope.launch {
            try {
                val tree = withContext(Dispatchers.Default) {
                    work { n -> if (n % 10 == 0) treeLoading = n }.also { store.saveGameTree(it) }
                }
                gameTree = tree
                treeRequest = tree.request
                treeFormOpen = false
                flash = "${tree.games.size} Partien im Baum."
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                treeError = e.message ?: "Das hat nicht geklappt."
            } finally {
                // A newer tree may already be loading in its place.
                if (treeJob === coroutineContext[Job]) treeLoading = null
            }
        }
    }

    fun setLichessToken(token: String) {
        explorerCache.clear()
        updateLibrary { it.copy(settings = it.settings.copy(lichessToken = token.trim())) }
    }

    // ---- Engines (Ctrl+B r) --------------------------------------------------------------

    /** The engine window, beside whatever is open; it analyses the board as it changes. */
    var engineOpen by mutableStateOf(false)

    /** Where the engine window was dragged to, from its place at the bottom right. */
    val engineAt = mutableStateOf(Offset.Zero)

    /** Where engines can be put to be found: "Engines" in the data folder. */
    val engineFolder: String get() = store.location.trimEnd('/', '\\') + "/Engines"

    /** Programs in [engineFolder] that are not set up yet. */
    fun enginesInFolder(): List<String> {
        val known = library.settings.engines.map { it.path }.toSet()
        return runCatching { engineFilesIn(engineFolder) }.getOrDefault(emptyList()).filter { it !in known }
    }

    /**
     * Sets up the engine at [path]: it is started once to learn its name, which also proves it is
     * a UCI engine. Throws [IllegalStateException] with a German explanation if not.
     */
    suspend fun addEngine(path: String) {
        val clean = path.trim().removeSurrounding("\"").removeSurrounding("'")
        check(clean.isNotEmpty()) { "Erst den Pfad zur Engine eintragen." }
        check(library.settings.engines.none { it.path == clean }) { "Diese Engine ist schon eingerichtet." }
        val session = EngineSession(launchEngine(clean))
        val name = try {
            withTimeout(10_000) { session.start(emptyMap()) }
            session.name.ifBlank { clean.substringAfterLast('/') }
        } catch (e: TimeoutCancellationException) {
            throw IllegalStateException("Das Programm hat sich nicht als UCI-Engine gemeldet.")
        } finally {
            session.close()
        }
        updateLibrary { lib ->
            val s = lib.settings
            lib.copy(settings = s.copy(engines = s.engines + EngineEntry(name, clean), engine = s.engine.ifEmpty { clean }))
        }
    }

    fun removeEngine(path: String) = updateLibrary { lib ->
        val s = lib.settings
        lib.copy(settings = s.copy(engines = s.engines.filter { it.path != path }, engine = if (s.engine == path) "" else s.engine))
    }

    fun chooseEngine(path: String) = updateLibrary { it.copy(settings = it.settings.copy(engine = path)) }

    private fun updateEngine(path: String, transform: (EngineEntry) -> EngineEntry) = updateLibrary { lib ->
        lib.copy(settings = lib.settings.copy(engines = lib.settings.engines.map { if (it.path == path) transform(it) else it }))
    }

    fun setEngineLines(path: String, n: Int) = updateEngine(path) { it.copy(lines = n.coerceIn(ENGINE_LINES)) }

    fun setEngineThreads(path: String, n: Int) = updateEngine(path) { it.copy(threads = n.coerceIn(engineThreads(it.threads))) }

    fun setEngineHash(path: String, mb: Int) = updateEngine(path) { it.copy(hashMb = mb.coerceIn(ENGINE_HASH_MB)) }

    /** A depth typed in; 0 is no limit. */
    fun setEngineDepth(path: String, depth: Int) = updateEngine(path) { it.copy(depth = depth.coerceIn(ENGINE_DEPTH)) }

    /** [ENGINE_DEPTHS] step by step, from a typed depth to its neighbour; 0 is no limit. */
    fun stepEngineDepth(path: String, by: Int) = updateEngine(path) { e -> e.copy(depth = stepIn(ENGINE_DEPTHS, e.depth, by)) }

    // ---- The engine as opponent (Ctrl+B Shift+R) ------------------------------------------

    /** The dialog that picks the engine's side and depth. */
    var opponentDialog by mutableStateOf(false)

    fun openOpponent() {
        prefix = false
        showShortcuts = false
        prefs = null
        if (editor == null) flash = "Die Engine als Gegner gibt es im Editor (neue Variante, Durchgehen oder Bearbeiten)." else opponentDialog = true
    }

    /** The engine plays [opponent] from now on in the open editor; null: off again. Depth and rank are kept for next time. */
    fun setOpponent(opponent: EngineOpponent?) {
        opponentDialog = false
        val e = editor ?: return
        e.opponent = opponent
        val settings = library.settings
        if (opponent != null && (opponent.depth != settings.opponentDepth || opponent.rank != settings.opponentRank)) {
            updateLibrary { it.copy(settings = it.settings.copy(opponentDepth = opponent.depth, opponentRank = opponent.rank)) }
        }
    }

    /**
     * It is the engine's move: its side is to move at the tip of the line, and nothing else has the
     * board (the player, a drawing). Stepping back through the line never makes it move, so what
     * comes after stays.
     */
    val opponentToMove: Boolean
        get() {
            val e = editor ?: return false
            val o = e.opponent ?: return false
            if (e.playing || e.drawingAt != null || e.drawMode.value || !e.atTip) return false
            val position = e.position
            return position.whiteToMove == o.white && position.legalMoves().isNotEmpty()
        }

    /** The engine's move [uci], found for [fen]: played if the board is still there and it is still its move. */
    fun opponentPlay(fen: String, uci: String) {
        val e = editor ?: return
        if (!opponentToMove || e.position.fen != fen) return
        val move = runCatching { Move.fromUci(uci) }.getOrNull()?.takeIf(e.position::isLegal) ?: return
        editorPlay(move)
    }

    // ---- Opening notes (Ctrl+B e) --------------------------------------------------------

    /** The opening whose notes are open ([Library.GENERAL_NOTES] for the general ones); null when closed. */
    var notesOf by mutableStateOf<String?>(null)

    /** Opens the notes of the opening on screen: the editor's, the puzzle's, otherwise the general ones. */
    fun openNotes() {
        val opening = editor?.opening?.trim()?.takeIf { it.isNotEmpty() }
            ?: session?.attempt?.quiz?.variant?.let(Library::openingOf)
        notesOf = opening ?: Library.GENERAL_NOTES
    }

    /** What to pick from in the notes: the general ones, every opening, and those with notes but no variants any more. */
    val notesOpenings: List<String>
        get() = (listOf(Library.GENERAL_NOTES) + library.groups().map { it.opening } + library.notes.keys + listOfNotNull(notesOf)).distinct()

    fun saveNotes(opening: String, text: String) {
        if ((library.notes[opening] ?: "") == text.trimEnd()) return
        updateLibrary { lib ->
            lib.copy(notes = if (text.isBlank()) lib.notes - opening else lib.notes + (opening to text.trimEnd()))
        }
    }

    /** A double tap into [zone] on a phone. */
    fun tapZone(zone: TapZone) {
        library.settings.commandAt(zone)?.let(::run)
    }

    // ---- Preferences ---------------------------------------------------------------------

    /** The preferences, on top of whatever is open underneath; null when closed. */
    var prefs: Prefs?
        get() = prefsState
        set(value) {
            if (value != Prefs.Config) keyCapture = null
            prefsState = value
        }
    private var prefsState by mutableStateOf<Prefs?>(null)

    fun openConfig() {
        prefs = Prefs.Config
    }

    /** Set to scroll the configuration down to the keys; the page clears it once it is there. */
    var showKeys by mutableStateOf(false)

    /**
     * Ctrl+B and a key without a command ([free], if it is one that can be given one): Ctrl+B
     * navigates, so it goes to the configuration, at the keys, where it can get a command.
     */
    fun openKeys(free: Char?) {
        showShortcuts = false
        prefs = Prefs.Config
        showKeys = true
        val settings = library.settings
        flash = if (free != null) {
            "${settings.prefixLabel} $free ist noch frei – hier unter „Tastenkürzel“ kannst du es belegen."
        } else {
            "Kein Kürzel – hier die Tastenkürzel; ${settings.shortcutLabel(Command.SHORTCUTS)} zeigt alle."
        }
    }

    /** The help: the list of topics, or with [topic] its page. */
    fun openHelp(topic: HelpTopic? = null) {
        prefs = Prefs.Help(topic, fromConfig = prefs == Prefs.Config)
    }

    /** One step back: from a help page to where it was opened from, otherwise closed. */
    fun prefsBack() {
        val p = prefs
        prefs = when {
            p is Prefs.Help && p.topic != null -> if (p.fromConfig) Prefs.Config else Prefs.Help()
            else -> null
        }
    }

    /** Waiting for a key press in the configuration, to set it as the new key of this. */
    var keyCapture by mutableStateOf<KeyTarget?>(null)

    fun setKey(target: KeyTarget, key: Char) {
        keyCapture = null
        updateLibrary { lib ->
            lib.copy(
                settings = when (target) {
                    KeyTarget.Prefix -> lib.settings.copy(prefixKey = key.toString())
                    is KeyTarget.Of -> lib.settings.withKey(target.command, key)
                    is KeyTarget.Draw -> lib.settings.withDrawKey(target.key, key)
                },
            )
        }
    }

    fun resetKeys() {
        keyCapture = null
        updateLibrary { it.copy(settings = it.settings.copy(prefixKey = "b", keys = emptyMap(), drawKeys = emptyMap())) }
    }

    fun setTapZonesOn(on: Boolean) = updateLibrary { it.copy(settings = it.settings.copy(tapZonesOn = on)) }

    fun setTapZone(zone: TapZone, command: Command?) = updateLibrary { it.copy(settings = it.settings.withZone(zone, command)) }

    fun setTapGrid(cols: Int, rows: Int) = updateLibrary { it.copy(settings = it.settings.withGrid(cols, rows)) }

    fun setPlayDelay(ms: Int) = updateLibrary { it.copy(settings = it.settings.copy(playDelayMs = ms.coerceIn(PLAY_DELAY_MIN, PLAY_DELAY_MAX))) }

    fun setPlayLoop(on: Boolean) = updateLibrary { it.copy(settings = it.settings.copy(playLoop = on)) }

    fun setHorseOn(on: Boolean) = updateLibrary { it.copy(settings = it.settings.copy(horseOn = on)) }

    fun setHorseLeft(left: Boolean) = updateLibrary { it.copy(settings = it.settings.copy(horseLeft = left)) }

    fun setBoardTheme(theme: BoardTheme) = updateLibrary { it.copy(settings = it.settings.copy(boardTheme = theme.name)) }

    /** The colors of the pens in a window of their own, over whatever is open (Ctrl+B k). */
    var colorsOpen by mutableStateOf(false)

    /** Gives pen [index] (0 to 5) the color [color]; what is drawn with it, saved drawings too, takes the new color. */
    fun setPenColor(index: Int, color: PenColor) = updateLibrary { lib ->
        lib.copy(settings = lib.settings.copy(pens = lib.settings.penColors.mapIndexed { i, old -> if (i == index) color else old }.map { it.saved }))
    }

    /** A color typed in, like "#7FFFD4" with the name "Aquamarin"; false if the code is not one. */
    fun setPenHex(index: Int, code: String, name: String): Boolean {
        val color = PenColor.parse(code.trim().substringBefore(' ')) ?: return false
        setPenColor(index, if (name.isBlank()) color else color.copy(label = name.trim()))
        return true
    }

    fun resetPens() = updateLibrary { it.copy(settings = it.settings.copy(pens = Ink.DEFAULT.map { c -> c.saved })) }

    /** Shown in the editor when it was opened on top of a puzzle. */
    private val backToPuzzle: String?
        get() = if (session != null) "Das Puzzle wartet: nach Speichern oder Abbrechen geht es dort weiter." else null

    /** Result of the last export or import, shown on the home screen. */
    var message by mutableStateOf<String?>(null)

    val canTransfer: Boolean get() = files != null

    /** The puzzles of the repertoire; archived variants are not among them, nor do their positions count in the others. */
    private val quizzes: List<Quiz> get() = QuizBuilder.build(library.active)

    fun stats(): Stats = Scheduler.stats(quizzes, progress)

    fun reviewOf(variantId: String): ReviewState? = progress.cards[variantId]

    private fun updateLibrary(transform: (Library) -> Library) {
        library = transform(library)
        store.saveLibrary(library)
    }

    private fun saveProgress(p: Progress) {
        progress = p
        store.saveProgress(p)
    }

    fun setNewPerDay(value: Int) = updateLibrary { it.copy(settings = it.settings.copy(newPerDay = value.coerceIn(0, 500))) }

    fun setHoldStep(ms: Int) = updateLibrary { it.copy(settings = it.settings.copy(holdStepMs = ms.coerceIn(HOLD_STEP_MIN, HOLD_STEP_MAX))) }

    // ---- Editor --------------------------------------------------------------------------

    /** New variant; with [template] its moves are taken over so you only add what is different. */
    fun newVariant(template: Variant? = null) {
        val last = library.active.lastOrNull()
        editor = EditorState(
            editingId = null,
            opening = template?.opening ?: last?.opening ?: "",
            name = "",
            moves = template?.moves?.map(Move::fromUci) ?: emptyList(),
            playWhite = template?.playWhite ?: last?.playWhite ?: true,
            startFen = template?.startFen ?: Position.START_FEN,
            note = backToPuzzle,
        )
    }

    /** Opens a saved variant; [fromStart] puts the board at move zero to step through the line. */
    /**
     * Opens a saved variant; [fromStart] puts the board at move zero to step through the line.
     * [browse] (Durchgehen, the default from the start or playing): the moves stay as they are.
     */
    fun editVariant(v: Variant, fromStart: Boolean = false, play: Boolean = false, playlist: Playlist? = null, browse: Boolean = fromStart || play) {
        editor = EditorState(
            v.id, v.opening, v.name, v.moves.map(Move::fromUci), v.playWhite, v.theory, fromStart || play, v.startFen,
            playlist = playlist, drawings = v.drawings, browse = browse,
        )
        if (play) editor?.playing = v.moves.isNotEmpty()
    }

    /** Starts or stops the player; at the end of the line it starts over from the beginning. */
    fun editorTogglePlay() {
        val e = editor ?: return
        val p = e.playlist
        e.finishDrawing()
        // The player plays the line, not a side line.
        e.leaveSide()
        when {
            e.playing -> e.playing = false
            e.moves.isEmpty() -> Unit
            // The playlist is through: once more from its first variant.
            p != null && p.last && e.atEnd && !editorChanged() -> openPlaylist(p.copy(index = 0), play = true)
            else -> {
                if (e.atEnd) e.cursor = 0
                e.playing = true
            }
        }
    }

    /** One step of the player, after the delay: the next move, or the end: the next variant of a playlist, start over or stop. */
    fun editorPlayTick() {
        val e = editor ?: return
        val p = e.playlist
        when {
            !e.playing -> Unit
            !e.atEnd -> e.cursor++
            // Changes would be thrown away by the next variant, so the player waits for them to be saved.
            p != null && editorChanged() -> e.playing = false
            p != null && !p.last -> openPlaylist(p.copy(index = p.index + 1), play = true)
            p != null && library.settings.playLoop -> openPlaylist(p.copy(index = 0), play = true)
            p == null && library.settings.playLoop -> e.cursor = 0
            else -> e.playing = false
        }
    }

    /** The open variant differs from the saved one, or is not saved at all. */
    private fun editorChanged(): Boolean {
        val e = editor ?: return false
        val saved = library.variants.firstOrNull { it.id == e.editingId } ?: return true
        return saved != e.asVariant()
    }

    /**
     * A move on the board. Bearbeiten: at the end of the line it is appended; in the middle, the same
     * move just walks on, and a different one replaces the rest of the line from here. Durchgehen:
     * the same move walks on, a different one goes into a side line and the line stays.
     */
    /** Plays [move] on the board; one that is not legal there is dropped, as it would break the line. */
    fun editorPlay(move: Move) {
        val e = editor ?: return
        if (!e.position.isLegal(move)) return
        e.playing = false
        if (e.browsing) {
            when {
                // Back onto the line from the start of a side line.
                e.sideCursor == 0 && e.moves.getOrNull(e.cursor) == move -> {
                    e.leaveSide()
                    e.cursor++
                }
                e.side.getOrNull(e.sideCursor) == move -> e.sideCursor++
                else -> {
                    e.finishDrawing()
                    e.side = e.side.take(e.sideCursor) + move
                    e.sideCursor++
                }
            }
            return
        }
        if (e.moves.getOrNull(e.cursor) != move) e.moves = e.shown + move
        e.cursor++
    }

    /** Puts the board [index] moves into the line; the moves themselves stay, a side line goes. */
    fun editorGoTo(index: Int) {
        val e = editor ?: return
        e.playing = false
        e.finishDrawing()
        e.leaveSide()
        e.cursor = index.coerceIn(0, e.moves.size)
    }

    /** One move back: in a side line within it, and from its first move back onto the line. */
    fun editorStepBack() {
        val e = editor ?: return
        if (e.inSide && e.sideCursor > 0) {
            e.playing = false
            e.sideCursor--
        } else {
            editorGoTo(e.cursor - if (e.inSide) 0 else 1)
        }
    }

    /** One move on: in a side line along it, otherwise along the line. */
    fun editorStepForward() {
        val e = editor ?: return
        if (e.inSide) {
            e.playing = false
            e.sideCursor = (e.sideCursor + 1).coerceAtMost(e.side.size)
        } else {
            editorGoTo(e.cursor + 1)
        }
    }

    /** Ctrl+B Enter: from a side line back to the line, where it was left. */
    fun backToLine() {
        val e = editor
        when {
            e == null || !e.inSide -> flash = "Kein Nebenstrang offen – du bist auf der Variante."
            else -> {
                e.leaveSide()
                flash = "Zurück auf der Variante."
            }
        }
    }

    /** From Durchgehen to Bearbeiten: the moves can be changed from now on; a side line is dropped. */
    fun editorStartEditing() {
        val e = editor ?: return
        e.leaveSide()
        e.browsing = false
    }

    fun editorToEnd() = editorGoTo(editor?.moves?.size ?: 0)

    /** Throws away the moves after the board's position. */
    fun editorTruncate() {
        val e = editor ?: return
        e.moves = e.shown
    }

    /** Takes back the last move of the line. */
    fun editorUndo() {
        val e = editor ?: return
        e.moves = e.moves.dropLast(1)
        e.cursor = e.cursor.coerceAtMost(e.moves.size)
    }

    fun editorClear() {
        val e = editor ?: return
        e.moves = emptyList()
        e.cursor = 0
    }

    // ---- Saved drawings ------------------------------------------------------------------

    /**
     * Ctrl+B w: wipes what is drawn on the board right now, like „Löschen“ in its bar: the scribbles,
     * and the drawing being drawn into (a saved one in the editor, one from memory in a quiz). Saved
     * drawings that are only shown stay.
     */
    private fun clearBoard() {
        boardClears++
        if (editor?.editedDrawing != null) editorClearDrawing() else clearDrawingInput()
    }

    /** Ctrl+B n: draws into the drawing of this position (a new one if there is none), or finishes it. */
    private fun toggleKeptDrawing(e: EditorState) {
        when {
            e.drawingAt != null -> editorFinishDrawing()
            e.drawingsHere.isNotEmpty() -> editorEditDrawing(e.drawings.indexOf(e.drawingsHere.first()))
            else -> editorNewDrawing()
        }
    }

    /** A new drawing at the position on the board; the board draws into it until [editorFinishDrawing]. */
    fun editorNewDrawing() {
        val e = editor ?: return
        if (e.inSide) {
            flash = "Zeichnungen gehören zur Variante – erst mit Strg+B Enter zurück."
            return
        }
        e.playing = false
        e.finishDrawing()
        e.drawMode.value = false
        e.drawings = e.drawings + Drawing(e.cursor, emptyList())
        e.drawingAt = e.drawings.lastIndex
    }

    /** Goes to the position of drawing [index] and draws into it. */
    fun editorEditDrawing(index: Int) {
        val e = editor ?: return
        val d = e.drawings.getOrNull(index) ?: return
        // Finishing the one being drawn may drop it, which shifts the others.
        e.finishDrawing()
        e.leaveSide()
        e.playing = false
        e.drawMode.value = false
        e.cursor = d.ply
        e.drawingAt = e.drawings.indexOf(d).takeIf { it >= 0 } ?: return
    }

    private fun updateDrawing(transform: (Drawing) -> Drawing) {
        val e = editor ?: return
        val i = e.drawingAt ?: return
        e.drawings = e.drawings.mapIndexed { j, d -> if (j == i) transform(d) else d }
    }

    /** A circle or arrow drawn on the board goes into the drawing; the same one again takes it away. */
    fun editorDrawingMark(mark: Mark) = updateDrawing { it.copy(marks = it.marks.toggle(mark)) }

    fun editorClearDrawing() = updateDrawing { it.copy(marks = emptyList()) }

    fun editorDrawingQuestion(text: String) = updateDrawing { it.copy(question = text) }

    fun editorFinishDrawing() {
        editor?.finishDrawing()
    }

    fun editorDeleteDrawing() {
        val e = editor ?: return
        val i = e.drawingAt ?: return
        e.drawingAt = null
        e.drawings = e.drawings.filterIndexed { j, _ -> j != i }
    }

    /** Opens the theory field and puts the cursor into it, or closes it again. */
    fun toggleTheory() {
        val e = editor ?: return
        e.showTheory = !e.showTheory
        if (e.showTheory) e.focusTheory++
    }

    /** Wraps the selected text in "((…))"; without a selection, inserts the last move as a gap. */
    fun theoryGap() {
        val e = editor ?: return
        val t = e.theory
        val sel = t.selection
        val inner = if (sel.collapsed) {
            val last = e.shown.lastOrNull() ?: return
            e.shown.dropLast(1).fold(e.start) { p, m -> p.play(m) }.san(last)
        } else {
            t.text.substring(sel.min, sel.max)
        }
        val gap = "(($inner))"
        val text = t.text.substring(0, sel.min) + gap + t.text.substring(sel.max)
        e.theory = TextFieldValue(text, TextRange(sel.min + gap.length))
    }

    /** Inserts "@8. Ng4" for the move on the board at the text cursor: in training it links to the board after that move. */
    fun theoryMarker() {
        val e = editor ?: return
        val marker = MoveRefs.markerFor(e.start, e.shown) ?: return
        val t = e.theory
        val sel = t.selection
        val text = t.text.substring(0, sel.min) + marker + t.text.substring(sel.max)
        e.theory = TextFieldValue(text, TextRange(sel.min + marker.length))
    }

    /** Tab in the theory field: "7" → "@7. Bg5" → "@7... h6" → "@7. Bg5" …; false if there is nothing to complete. */
    fun theoryComplete(): Boolean {
        val e = editor ?: return false
        val t = e.theory
        if (!t.selection.collapsed) return false
        val cursor = t.selection.start
        val (start, marker) = MoveRefs.complete(t.text.substring(0, cursor), e.start, e.moves) ?: return false
        e.theory = TextFieldValue(t.text.substring(0, start) + marker + t.text.substring(cursor), TextRange(start + marker.length))
        return true
    }

    /** "@…" markers in the theory that name no move of the variant being edited. */
    fun editorBrokenMarkers(): List<String> {
        val e = editor ?: return emptyList()
        return MoveRefs.unresolved(e.theory.text, e.asVariant())
    }

    /** Title of another saved variant that already ends in the editor's position. */
    fun editorDuplicate(): String? {
        val e = editor ?: return null
        if (e.moves.isEmpty()) return null
        val key = e.target.key
        return library.active.firstOrNull { it.id != e.editingId && it.moves.isNotEmpty() && it.target.key == key }
            ?.let(QuizBuilder::title)
    }

    /** Editing a variant that is already being learned, with moves that no longer match it. */
    fun editorRestartsProgress(): Boolean {
        val e = editor ?: return false
        val id = e.editingId ?: return false
        if (!progress.cards.containsKey(id)) return false
        val old = library.variants.firstOrNull { it.id == id } ?: return false
        return !old.sameLine(e.asVariant())
    }

    fun saveEditor() {
        val e = editor ?: return
        if (e.name.isBlank() || e.moves.isEmpty()) return
        val id = e.editingId ?: newId()
        var variant = Variant(
            id, e.opening.trim(), e.name.trim(), e.moves.map { it.uci }, e.playWhite, e.theory.text.trim(), e.startFen,
            e.keptDrawings.map { it.copy(question = it.question.trim()) },
        )
        val old = library.variants.firstOrNull { it.id == id }
        // Edited in the archive, it stays there.
        if (old?.archived == true) variant = variant.copy(archived = true)
        updateLibrary { lib ->
            lib.copy(variants = if (old != null) lib.variants.map { if (it.id == id) variant else it } else lib.variants + variant)
        }
        // A pinned opening whose last variant got a new opening name stays pinned under that name.
        val oldOpening = old?.let(Library::openingOf)
        if (oldOpening != null && oldOpening in library.pinned && library.variants.none { Library.openingOf(it) == oldOpening }) {
            val newOpening = Library.openingOf(variant)
            updateLibrary { lib -> lib.copy(pinned = lib.pinned.map { if (it == oldOpening) newOpening else it }.distinct()) }
        }
        // Different moves means a different puzzle: start learning it from scratch.
        if (old != null && !old.sameLine(variant)) saveProgress(progress.copy(cards = progress.cards - id))
        editor = null
    }

    fun cancelEditor() {
        editor = null
    }

    // ---- Openings: pinning and playlists -------------------------------------------------

    /** Pins [opening] to the top of the home screen, or unpins it; at most [Library.MAX_PINNED] at a time. */
    fun togglePin(opening: String) {
        if (opening in library.pinned) {
            updateLibrary { it.copy(pinned = it.pinned - opening) }
            return
        }
        // Pins of openings without variants (renamed or deleted) do not count.
        val live = library.groups().filter { it.pinned }.map { it.opening }
        if (live.size >= Library.MAX_PINNED) {
            flash = "Höchstens ${Library.MAX_PINNED} Eröffnungen lassen sich anpinnen – löse erst eine."
            return
        }
        updateLibrary { it.copy(pinned = live + opening) }
    }

    private fun variantsOf(opening: String): List<Variant> =
        library.groups().firstOrNull { it.opening == opening }?.variants ?: emptyList()

    /** Plays every variant of [opening] in the editor, one after the other, in the order of the list. */
    fun playPlaylist(opening: String) = playVariants(opening, variantsOf(opening))

    private fun playVariants(title: String, variants: List<Variant>) {
        if (variants.isNotEmpty()) openPlaylist(Playlist(title, variants.map { it.id }), play = true)
    }

    /** The previous or next variant of the running playlist; the player keeps running if it was. */
    fun playlistStep(by: Int) {
        val e = editor ?: return
        val p = e.playlist ?: return
        val index = p.index + by
        if (index !in p.ids.indices) return
        if (editorChanged()) {
            flash = "Erst speichern oder abbrechen – sonst gehen die Änderungen verloren."
            return
        }
        openPlaylist(p.copy(index = index), play = e.playing)
    }

    private fun openPlaylist(p: Playlist, play: Boolean) {
        val v = library.variants.firstOrNull { it.id == p.ids[p.index] }
        if (v == null) {
            // Deleted in the meantime: the player just stops.
            editor?.playing = false
            return
        }
        editVariant(v, fromStart = true, play = play, playlist = p)
    }

    /**
     * Ctrl+N and [number]: „Playlist lernen“ of the opening at that place on the home screen, counted
     * from 1 as listed there (pinned ones first). Works from any screen; a changed variant in the
     * editor is not thrown away.
     */
    fun learnOpeningAt(number: Int) {
        val group = library.groups().getOrNull(number - 1)
        if (group == null) flash = "Keine Eröffnung Nummer $number." else fromAnywhere { learnPlaylist(group.opening) }
    }

    /** A widget or shortcut on the phone: „Playlist lernen“ of [opening], from any screen. */
    fun learnOpening(opening: String) {
        if (variantsOf(opening).isEmpty()) flash = "Keine Eröffnung „$opening“ mehr – umbenannt, archiviert oder gelöscht?"
        else fromAnywhere { learnPlaylist(opening) }
    }

    /** A widget on the phone: „Lernen“ of a playlist of one's own, from any screen. */
    fun learnSavedPlaylistFromAnywhere(id: String) {
        val p = savedPlaylist(id)
        if (p == null || library.variantsOf(p).isEmpty()) flash = "Die Playlist ist leer oder gelöscht."
        else fromAnywhere { learnSavedPlaylist(id) }
    }

    /** A widget or shortcut on the phone: „Lernen“ with spaced repetition, from any screen. */
    fun startLearningFromAnywhere() = fromAnywhere(::startLearning)

    /** Whatever is open makes way for [start]; a changed variant in the editor is not thrown away. */
    private fun fromAnywhere(start: () -> Unit) {
        if (editorChanged() && editorInUse) {
            flash = "Erst die offene Variante speichern oder abbrechen."
            return
        }
        editor = null
        paste = null
        prefs = null
        showShortcuts = false
        start()
    }

    /**
     * Every variant of [opening] as a puzzle, one after the other in the order of the list: the next
     * one comes only once the current one is solved without a mistake. Not graded.
     */
    fun learnPlaylist(opening: String) = learnVariants(variantsOf(opening))

    private fun learnVariants(variants: List<Variant>) {
        val all = quizzes
        val drills = variants.mapNotNull { v -> all.firstOrNull { it.variant.id == v.id } }.map(::Drill)
        if (drills.isNotEmpty()) start(TrainingSession("Playlist lernen", graded = false, ArrayDeque(drills), untilClean = true))
    }

    // ---- Playlists of one's own ----------------------------------------------------------

    /** The name dialog for a playlist; null when closed. */
    var naming by mutableStateOf<PlaylistNaming?>(null)
        private set

    private fun savedPlaylist(id: String): SavedPlaylist? = library.playlists.firstOrNull { it.id == id }

    private fun updatePlaylist(id: String, transform: (SavedPlaylist) -> SavedPlaylist) =
        updateLibrary { lib -> lib.copy(playlists = lib.playlists.map { if (it.id == id) transform(it) else it }) }

    fun playSavedPlaylist(id: String) {
        val p = savedPlaylist(id) ?: return
        playVariants(p.name, library.variantsOf(p))
    }

    fun learnSavedPlaylist(id: String) {
        val p = savedPlaylist(id) ?: return
        learnVariants(library.variantsOf(p))
    }

    /** "+ Neue Playlist": pick the variants first, the name comes when saving. */
    fun newPlaylist() {
        startSelecting()
    }

    /** Asks for the name of a new playlist made of the ticked variants. */
    fun saveSelectionAsPlaylist() {
        if (selected.isNullOrEmpty()) return
        naming = PlaylistNaming(null)
    }

    fun renamePlaylist(id: String) {
        naming = PlaylistNaming(id, savedPlaylist(id)?.name ?: return)
    }

    fun cancelNaming() {
        naming = null
    }

    /** Saves the name: a new playlist of the ticked variants, in the order of the home screen, or the new name of one. */
    fun confirmNaming() {
        val n = naming ?: return
        val name = n.text.trim()
        if (name.isEmpty()) return
        naming = null
        val id = n.playlistId
        if (id != null) {
            updatePlaylist(id) { it.copy(name = name) }
            return
        }
        val ids = selectedVariants.map { it.id }
        updateLibrary { it.copy(playlists = it.playlists + SavedPlaylist(newId(), name, ids)) }
        endSelecting()
        flash = "Playlist „$name“ mit ${ids.size} Variante(n) gespeichert."
    }

    /** The saved playlist whose variants are being picked on the home screen; null otherwise. */
    var choosingFor by mutableStateOf<String?>(null)
        private set

    /** Ticks the variants of the playlist, to add or take away some with the tick boxes. */
    fun changePlaylistVariants(id: String) {
        val p = savedPlaylist(id) ?: return
        selected = p.variantIds.toSet()
        choosingFor = id
    }

    /** Takes the ticks over into the playlist: its order stays, new variants go to the end in the order of the home screen. */
    fun applyPlaylistVariants() {
        val id = choosingFor ?: return
        val ticked = selected ?: return
        val added = selectedVariants.map { it.id }
        updatePlaylist(id) { p -> p.copy(variantIds = p.variantIds.filter { it in ticked } + added.filterNot { it in p.variantIds }) }
        endSelecting()
    }

    /** Moves the variant at [index] of the playlist up (-1) or down (+1). */
    fun movePlaylistVariant(id: String, index: Int, by: Int) = updatePlaylist(id) { p ->
        val to = index + by
        if (index !in p.variantIds.indices || to !in p.variantIds.indices) {
            p
        } else {
            p.copy(variantIds = p.variantIds.toMutableList().apply { add(to, removeAt(index)) })
        }
    }

    fun removeFromPlaylist(id: String, variantId: String) = updatePlaylist(id) { it.copy(variantIds = it.variantIds - variantId) }

    /** Deletes the playlist only; its variants stay. */
    fun deletePlaylist(id: String) {
        updateLibrary { lib -> lib.copy(playlists = lib.playlists.filterNot { it.id == id }) }
        if (choosingFor == id) endSelecting()
    }

    /** All variants of [opening] with their learning state, and its pin; with [archived], those of it in the archive. */
    fun deleteOpening(opening: String, archived: Boolean = false) {
        val group = if (archived) library.archivedGroups() else library.groups()
        deleteVariants(group.firstOrNull { it.opening == opening }?.variants.orEmpty().map { it.id }.toSet())
    }

    fun deleteVariant(id: String) = deleteVariants(setOf(id))

    // ---- Searching the home screen --------------------------------------------------------

    /** What is typed into the search on the home screen, see [VariantSearch]; empty: everything is shown. */
    var search by mutableStateOf("")

    /** Goes up each time the search field is to take the focus. */
    var searchFocus by mutableIntStateOf(0)
        private set

    /**
     * Ctrl+B s: the search field on the home screen out of sight, its search with it, or back with
     * the cursor in it. The choice is kept.
     */
    fun toggleSearch() {
        if (editor != null || session != null) {
            flash = "Die Suche ist auf der Startseite."
            return
        }
        val show = !library.settings.showSearch
        updateLibrary { it.copy(settings = it.settings.copy(showSearch = show)) }
        if (show) searchFocus++ else search = ""
    }

    private fun shown(groups: List<OpeningGroup>): List<OpeningGroup> {
        val s = VariantSearch(search)
        if (!s.active) return groups
        return groups.mapNotNull { g -> g.variants.filter(s::matches).takeIf { it.isNotEmpty() }?.let { g.copy(variants = it) } }
    }

    /** The openings with the variants the search finds; all of them without a search. */
    val shownGroups: List<OpeningGroup> get() = shown(library.groups())

    /** The same in the archive. */
    val shownArchive: List<OpeningGroup> get() = shown(library.archivedGroups())

    /** The variants the search finds outside the archive, in the order of the home screen. */
    val searchHits: List<Variant> get() = shownGroups.flatMap { it.variants }

    fun playHits() = playVariants("Suche „${search.trim()}“", searchHits)

    fun learnHits() = learnVariants(searchHits)

    /** Selecting, with what the search found ticked. */
    fun selectHits() {
        selected = searchHits.map { it.id }.toSet()
    }

    // ---- The archive ---------------------------------------------------------------------

    /** The archive at the bottom of the home screen is open. */
    var archiveOpen by mutableStateOf(false)

    /** The playlists of one's own, below the archive, are open. */
    var playlistsOpen by mutableStateOf(false)

    /** All variants of [opening] into the archive; the opening is no longer pinned. */
    fun archiveOpening(opening: String) = archive(variantsOf(opening).map { it.id }.toSet())

    fun archiveVariant(id: String) = archive(setOf(id))

    fun archiveSelected() {
        archive(selected ?: return)
        endSelecting()
    }

    /** Back from the archive into the repertoire, with the learning state they had. */
    fun restoreOpening(opening: String) =
        setArchived(library.archivedGroups().firstOrNull { it.opening == opening }?.variants.orEmpty().map { it.id }.toSet(), false)

    fun restoreVariant(id: String) = setArchived(setOf(id), false)

    /** Back from the archive: these [variants], e.g. those of an opening the search shows. */
    fun restoreVariants(variants: List<Variant>) = setArchived(variants.map { it.id }.toSet(), false)

    private fun archive(ids: Set<String>) {
        if (ids.isEmpty()) return
        setArchived(ids, true)
        val n = ids.size
        flash = (if (n == 1) "Ins Archiv gelegt" else "$n Varianten ins Archiv gelegt") + " – unten auf der Startseite, von dort zurückzuholen."
    }

    private fun setArchived(ids: Set<String>, archived: Boolean) {
        if (ids.isEmpty()) return
        updateLibrary { lib ->
            val variants = lib.variants.map { if (it.id in ids) it.copy(archived = archived) else it }
            // An opening with nothing left outside the archive is not pinned any more.
            val live = variants.filterNot { it.archived }.map(Library::openingOf).toSet()
            lib.copy(variants = variants, pinned = lib.pinned.filter { it in live })
        }
        selected = selected?.minus(ids)
    }

    private fun deleteVariants(ids: Set<String>) {
        updateLibrary { lib ->
            val variants = lib.variants.filterNot { it.id in ids }
            val left = variants.map(Library::openingOf).toSet()
            lib.copy(
                variants = variants,
                pinned = lib.pinned.filter { it in left },
                playlists = lib.playlists.map { it.copy(variantIds = it.variantIds - ids) },
            )
        }
        saveProgress(progress.copy(cards = progress.cards - ids))
        selected = selected?.minus(ids)
    }

    // ---- Selecting variants on the home screen -------------------------------------------

    /** Ids of the ticked variants while selecting; null when not selecting. */
    var selected by mutableStateOf<Set<String>?>(null)
        private set

    /** The ticked variants in the order of the home screen. */
    private val selectedVariants: List<Variant>
        get() = selected?.let { ids -> library.groups().flatMap { it.variants }.filter { it.id in ids } } ?: emptyList()

    /** Shows a tick box at every variant; with [opening], its variants start out ticked. */
    fun startSelecting(opening: String? = null) {
        selected = opening?.let { o -> variantsOf(o).map { it.id }.toSet() } ?: emptySet()
    }

    /** A variant held down: selecting begins with it ticked. */
    fun startSelectingVariant(id: String) {
        selected = setOf(id)
    }

    fun endSelecting() {
        selected = null
        choosingFor = null
    }

    fun toggleSelected(id: String) {
        val s = selected ?: return
        selected = if (id in s) s - id else s + id
    }

    /** Ticks every variant of [opening] that the search shows, or unticks them all if they already are. */
    fun toggleSelectedOpening(opening: String) {
        val s = selected ?: return
        val ids = shownGroups.firstOrNull { it.opening == opening }?.variants.orEmpty().map { it.id }.toSet()
        selected = if (s.containsAll(ids)) s - ids else s + ids
    }

    fun deleteSelected() {
        deleteVariants(selected ?: return)
    }

    fun playSelected() = playVariants("Auswahl", selectedVariants)

    fun learnSelected() = learnVariants(selectedVariants)

    fun unsuspend(id: String) {
        val s = progress.cards[id] ?: return
        saveProgress(progress.copy(cards = if (s.reps == 0) progress.cards - id else progress.cards + (id to s.copy(suspended = false))))
    }

    // ---- Training ------------------------------------------------------------------------

    fun startLearning() {
        start(TrainingSession("Lernen", graded = true, ArrayDeque(Scheduler.dueQueue(quizzes, progress, library.settings).map(::Drill))))
    }

    fun startCram() {
        start(TrainingSession("Frei üben", graded = false, ArrayDeque(Scheduler.cramQueue(quizzes, progress).map(::Drill))))
    }

    fun practice(variantId: String) {
        val quiz = quizzes.firstOrNull { it.variant.id == variantId } ?: return
        start(TrainingSession("Üben", graded = false, ArrayDeque(listOf(Drill(quiz)))))
    }

    /** Every position of the variant in random order; each time only the next move is asked. */
    fun practiceMoves(variantId: String) {
        val quiz = quizzes.firstOrNull { it.variant.id == variantId } ?: return
        val drills = quiz.variant.moves.indices.map { Drill(quiz, startPly = it, singleMove = true) }.shuffled()
        start(TrainingSession("Züge üben", graded = false, ArrayDeque(drills)))
    }

    private fun start(s: TrainingSession) {
        session = s
        nextQuiz(s)
    }

    fun endSession() {
        session = null
    }

    private fun nextQuiz(s: TrainingSession) {
        val before = s.attempt?.quiz?.variant?.id
        s.attempt = s.queue.removeFirstOrNull()?.attempt()
        // A turned board stays turned for the same variant (again after a mistake), not for the next one.
        if (s.attempt?.quiz?.variant?.id != before) s.flipped = false
        s.feedback = null
        s.marks = emptyMap()
        s.showTarget = false
        s.earlierMistakes = 0
        clearAnswers(s)
        s.revision++
    }

    /** Nothing typed into the gaps, nothing drawn from memory. */
    private fun clearAnswers(s: TrainingSession) {
        s.preview = null
        s.gapInputs = List(s.gaps.size) { "" }
        s.gapResults = null
        s.drawingIndex = 0
        s.drawingInput = emptyList()
        s.drawingResults = emptyList()
    }

    /** Start the current puzzle again from the initial position. */
    fun restartCurrent() {
        val s = session ?: return
        val attempt = s.attempt ?: return
        s.earlierMistakes += attempt.mistakes
        s.attempt = attempt.drill.attempt()
        clearAnswers(s)
        s.feedback = null
        s.marks = emptyMap()
        s.revision++
    }

    fun submitMove(move: Move) {
        val s = session ?: return
        val attempt = s.attempt ?: return
        if (attempt.finished) return
        val before = attempt.position
        when (val outcome = attempt.submit(move)) {
            is Outcome.Correct -> {
                s.marks = if (outcome.finished) mapOf(move.from to true, move.to to true) else emptyMap()
                s.feedback = when {
                    !outcome.finished -> null
                    attempt.singleMove -> Feedback("Richtig!", FeedbackTone.GOOD)
                    attempt.mistakes > 0 -> Feedback("Stellung erreicht, aber mit ${attempt.mistakes} Fehler(n).", FeedbackTone.BAD)
                    else -> Feedback("Richtig! Das ist die Stellung.", FeedbackTone.GOOD)
                }
                // Without gaps the board goes straight on to the first drawing, at its own position.
                s.drawing?.let { d ->
                    s.feedback = s.feedback?.let { it.copy(text = it.text + " Jetzt die Zeichnung " + attempt.quiz.variant.plyLabel(d.ply) + ".") }
                }
            }
            is Outcome.OtherVariant -> s.feedback = Feedback(
                if (outcome.name != null) {
                    "${before.san(move)} führt zu „${outcome.name}“ – gesucht ist eine andere Stellung. Nochmal."
                } else {
                    "${before.san(move)} gehört zu einer anderen Variante. Nochmal."
                },
                FeedbackTone.INFO,
            )
            is Outcome.Wrong -> {
                s.marks = mapOf(move.to to false, outcome.expected.from to true, outcome.expected.to to true)
                s.feedback = Feedback(
                    "${before.san(move)} führt nicht dorthin. Richtig: ${before.san(outcome.expected)}" +
                        if (!outcome.finished) " – weiter geht's." else "",
                    FeedbackTone.BAD,
                )
            }
        }
        s.revision++
    }

    /** Shows the board after [ply] moves of the variant (a "@8. Ng4" in the theory), or the attempt again for null / the same ply. */
    fun showPly(ply: Int?) {
        val s = session ?: return
        s.preview = if (ply == s.preview) null else ply
    }

    fun toggleTarget() {
        session?.let { it.showTarget = !it.showTarget }
    }

    fun setGapInput(index: Int, text: String) {
        val s = session ?: return
        if (s.gapResults == null) s.gapInputs = s.gapInputs.toMutableList().also { it[index] = text }
    }

    fun checkGaps() {
        val s = session ?: return
        if (s.attempt?.finished != true || s.gapResults != null) return
        val results = s.gaps.map { Cloze.isCorrect(s.gapInputs[it.index], it) }
        s.gapResults = results
        val right = results.count { it }
        val clean = (s.attempt?.mistakes ?: 0) + s.earlierMistakes == 0
        val then = when (s.drawings.size) {
            0 -> ""
            1 -> " Jetzt die Zeichnung."
            else -> " Jetzt die ${s.drawings.size} Zeichnungen."
        }
        s.feedback = when {
            right == results.size && clean && s.drawings.isEmpty() -> Feedback("Alles richtig!", FeedbackTone.GOOD)
            right == results.size -> Feedback("Alle Lücken richtig.$then", if (clean) FeedbackTone.GOOD else FeedbackTone.INFO)
            else -> Feedback("$right von ${results.size} Lücken richtig.$then", FeedbackTone.BAD)
        }
    }

    /** A circle or arrow drawn from memory; the same one again takes it away. */
    fun drawingMark(mark: Mark) {
        val s = session ?: return
        if (s.drawing != null && s.drawingCheck == null) s.drawingInput = s.drawingInput.toggle(mark)
    }

    fun clearDrawingInput() {
        val s = session ?: return
        if (s.drawingCheck == null) s.drawingInput = emptyList()
    }

    /** Compares what was drawn with the saved drawing; the board then shows right, missing and too much. */
    fun checkDrawing() {
        val s = session ?: return
        val d = s.drawing ?: return
        if (s.drawingCheck != null) return
        val check = DrawingCheck.of(d, s.drawingInput)
        s.drawingResults = s.drawingResults + check
        s.preview = null
        s.feedback = when {
            check.correct && s.done && mistakes(s) == 0 -> Feedback("Alles richtig!", FeedbackTone.GOOD)
            check.correct -> Feedback("Zeichnung richtig.", FeedbackTone.GOOD)
            else -> Feedback(
                listOfNotNull(
                    check.missing.takeIf { it.isNotEmpty() }?.let { "Es fehlt: " + it.joinToString(", ") { m -> m.describe(library.settings.penColors) } + "." },
                    check.extra.takeIf { it.isNotEmpty() }?.let { "Zu viel: " + it.joinToString(", ") { m -> m.describe(library.settings.penColors) } + "." },
                ).joinToString(" "),
                FeedbackTone.BAD,
            )
        }
    }

    /** On to the next drawing, once this one is checked. */
    fun nextDrawing() {
        val s = session ?: return
        if (s.drawingCheck == null || s.drawingIndex >= s.drawings.lastIndex) return
        s.drawingIndex++
        s.drawingInput = emptyList()
        s.preview = null
        s.feedback = null
    }

    /** Enter: check the gaps first, then each drawing, then grade. */
    fun confirm() {
        val s = session ?: return
        when {
            s.done -> grade()
            !s.gapsChecked -> checkGaps()
            s.drawingCheck == null -> checkDrawing()
            else -> nextDrawing()
        }
    }

    /** Mistakes on the board (all tries) plus wrong gaps and drawings. */
    fun mistakes(s: TrainingSession): Int =
        (s.attempt?.mistakes ?: 0) + s.earlierMistakes + (s.gapResults?.count { !it } ?: 0) +
            s.drawingResults.count { !it.correct }

    /** Finishes the current puzzle. Without an explicit grade, a clean solve counts as GOOD, a mistake as AGAIN. */
    fun grade(grade: Grade? = null) {
        val s = session ?: return
        val attempt = s.attempt ?: return
        if (!s.done) return
        val mistakes = mistakes(s)
        val g = if (mistakes > 0) Grade.AGAIN else grade ?: Grade.GOOD
        if (s.graded) {
            val id = attempt.quiz.variant.id
            val old = progress.cards[id]
            val day = Store.learningDay()
            saveProgress(
                progress.copy(
                    cards = progress.cards + (id to Fsrs.review(old, g)),
                    newIntroduced = if (old == null) {
                        progress.newIntroduced + (day to (progress.newIntroduced[day] ?: 0) + 1)
                    } else {
                        progress.newIntroduced
                    },
                ),
            )
        }
        when {
            g != Grade.AGAIN -> s.solved++
            // Learning a playlist: the same puzzle again, until it goes through without a mistake.
            s.untilClean -> s.queue.addFirst(attempt.drill)
            else -> s.queue.add(minOf(3, s.queue.size), attempt.drill)
        }
        s.answered++
        nextQuiz(s)
    }

    /** "Ignore": the variant is not asked any more until reactivated in the list. */
    fun suspendCurrent() {
        val s = session ?: return
        val id = s.attempt?.quiz?.variant?.id ?: return
        val old = progress.cards[id] ?: ReviewState(0.0, 0.0, 0, 0)
        saveProgress(progress.copy(cards = progress.cards + (id to old.copy(suspended = true))))
        s.queue.removeAll { it.quiz.variant.id == id }
        nextQuiz(s)
    }

    // ---- Pasting a PGN or FEN ------------------------------------------------------------

    fun openPaste() {
        paste = PasteState()
    }

    fun cancelPaste() {
        paste = null
    }

    fun setPasteText(text: String) {
        paste?.let {
            it.text = text
            it.error = null
        }
    }

    /**
     * Reads the pasted PGN or FEN and opens the editor with it, so the line can be shortened and
     * named before it is saved. On failure the dialog stays open and says what went wrong.
     */
    fun applyPaste() {
        val p = paste ?: return
        val all = p.kind == Command.PASTE_ALL
        val line = try {
            if (all) Pgn.parseAll(p.text) else Pgn.parse(p.text)
        } catch (e: IllegalArgumentException) {
            p.error = e.message ?: "Der Text lässt sich nicht lesen."
            return
        }
        paste = null
        editor = EditorState(
            editingId = null,
            opening = line.opening.ifBlank { library.variants.lastOrNull()?.opening ?: "" },
            name = line.name,
            moves = line.moves.map(Move::fromUci),
            playWhite = line.playWhite,
            theory = line.theory,
            // At move zero, so the line can be walked through and cut where it gets interesting.
            fromStart = true,
            startFen = line.startFen,
            drawings = line.drawings,
            note = listOfNotNull(pasteNote(line), backToPuzzle).joinToString(" "),
        )
    }

    private fun pasteNote(line: PastedLine): String = buildString {
        when {
            line.moves.isEmpty() ->
                append("Stellung aus der FEN übernommen. Spiel von hier die Züge, die du üben willst.")
            line.setUp ->
                append("${line.moves.size} Züge ab der FEN-Stellung übernommen.")
            else ->
                append("${line.moves.size} Züge übernommen. Mit ▶ durchgehen, „Ab hier kürzen“ schneidet den Rest weg.")
        }
        if (line.theory.isNotBlank() || line.drawings.isNotEmpty()) {
            val n = line.drawings.size
            append(" Dazu " + listOfNotNull("die Theorie".takeIf { line.theory.isNotBlank() }, if (n == 0) null else if (n == 1) "1 Zeichnung" else "$n Zeichnungen").joinToString(" und ") + ".")
        }
        if (line.otherGames > 0) {
            append(" Im Text standen ${line.otherGames + 1} Partien – übernommen ist die erste.")
        }
    }

    /** Resets the line to the start of a game; the moves from the pasted position no longer fit. */
    fun editorResetStart() {
        val e = editor ?: return
        if (!e.setUp) return
        e.startFen = Position.START_FEN
        e.moves = emptyList()
        e.drawings = emptyList()
        e.cursor = 0
    }

    // ---- Export / import -----------------------------------------------------------------

    /**
     * Saves [opening]'s variants (all if null, the archive included), with their learning state, as
     * a JSON file; with [archived], those of [opening] in the archive.
     */
    fun export(opening: String? = null, archived: Boolean = false) {
        val picker = files ?: return
        val variants = library.variants.filter { opening == null || Library.openingOf(it) == opening && it.archived == archived }
        val name = "eroeffnungen-" + (opening?.let(::fileSafe) ?: "alle") + ".json"
        picker.save(name, Exchange.export(variants, progress)) { file ->
            if (file != null) message = "${variants.size} Variante(n) exportiert: $file"
        }
    }

    /** An export file that was opened, waiting for the choice to add it or to replace everything with it. */
    var importing by mutableStateOf<PendingImport?>(null)
        private set

    class PendingImport(val text: String, val variants: Int)

    /** Opens an exported file and asks how to take it in ([finishImport]). */
    fun import() {
        val picker = files ?: return
        picker.open { text ->
            if (text == null) return@open
            try {
                importing = PendingImport(text, Exchange.variantsIn(text))
            } catch (e: IllegalArgumentException) {
                message = "Import fehlgeschlagen: ${e.message}"
            }
        }
    }

    /**
     * Takes in the opened file. Normally its variants are added, and one with the same id replaces
     * the old one; with [replace], all variants there were are deleted and only the file's are left.
     */
    fun finishImport(replace: Boolean) {
        val text = importing?.text ?: return
        importing = null
        message = try {
            val result = Exchange.import(text, library, progress, replace)
            updateLibrary { result.library }
            saveProgress(result.progress)
            selected = selected?.filter { id -> library.variants.any { it.id == id } }?.toSet()
            (if (replace) "Ersetzt: " else "Importiert: ") + result.summary()
        } catch (e: IllegalArgumentException) {
            "Import fehlgeschlagen: ${e.message}"
        }
    }

    fun cancelImport() {
        importing = null
    }

    private fun fileSafe(s: String) =
        s.lowercase().replace(Regex("[^a-z0-9äöüß]+"), "-").trim('-').ifEmpty { "eroeffnung" }
}
