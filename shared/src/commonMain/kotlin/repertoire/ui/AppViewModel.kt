package repertoire.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import repertoire.core.Cloze
import repertoire.core.Exchange
import repertoire.core.FilePicker
import repertoire.core.Fsrs
import repertoire.core.Grade
import repertoire.core.TheoryPart
import repertoire.core.Library
import repertoire.core.Move
import repertoire.core.MoveRefs
import repertoire.core.Outcome
import repertoire.core.PastedLine
import repertoire.core.Pgn
import repertoire.core.Position
import repertoire.core.Progress
import repertoire.core.Quiz
import repertoire.core.QuizAttempt
import repertoire.core.QuizBuilder
import repertoire.core.ReviewState
import repertoire.core.Scheduler
import repertoire.core.Stats
import repertoire.core.Store
import repertoire.core.Variant
import repertoire.core.newId
import repertoire.core.numberedSan

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
) {
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

    /** Single moves do not ask the theory. */
    val gaps: List<TheoryPart.Gap>
        get() = attempt?.takeIf { !it.singleMove }?.quiz?.variant?.theory?.let(Cloze::gaps) ?: emptyList()

    /** The position is reached and every gap is checked: ready to be graded. */
    val done: Boolean get() = attempt?.finished == true && (gapResults != null || gaps.isEmpty())
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
) {
    var opening by mutableStateOf(opening)
    var name by mutableStateOf(name)
    var moves by mutableStateOf(moves)
    var playWhite by mutableStateOf(playWhite)
    var theory by mutableStateOf(TextFieldValue(theory))

    /** Where the line begins; not the start of a game when it came from a FEN. */
    var startFen by mutableStateOf(startFen)

    val setUp: Boolean get() = startFen != Position.START_FEN

    val start: Position get() = if (setUp) Position.fromFen(startFen) else Position.START

    /** The theory field is open (Cmd/Ctrl+T). */
    var showTheory by mutableStateOf(theory.isNotBlank())

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

    /** Position shown on the board. */
    val position: Position get() = shown.fold(start) { p, m -> p.play(m) }

    /** Position the whole line leads to, the one that is trained. */
    val target: Position get() = moves.fold(start) { p, m -> p.play(m) }

    /** Start position followed by the position after each move. */
    val positions: List<Position> get() = moves.runningFold(start) { p, m -> p.play(m) }

    /** The variant as it would be saved, for checks that work on a [Variant]. */
    fun asVariant(): Variant =
        Variant(editingId ?: "", opening, name, moves.map { it.uci }, playWhite, theory.text, startFen)
}

/** The "PGN/FEN einfügen" dialog: paste the text, read it, then carry on in the editor. */
class PasteState(
    /** Only for the title and example: what was asked for with Ctrl+B p / f; both are read either way. */
    val kind: Command? = null,
) {
    var text by mutableStateOf("")

    /** Why the text could not be read, shown under the field. */
    var error by mutableStateOf<String?>(null)
}

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

    /** A short note that disappears by itself, e.g. why a shortcut did nothing. */
    var flash by mutableStateOf<String?>(null)

    /** Drawing mode of the board on screen: the editor's if one is open, else the puzzle's. */
    private val drawModeOnScreen get() = editor?.drawMode ?: session?.takeIf { it.attempt != null }?.drawMode

    val drawMode: Boolean get() = drawModeOnScreen?.value == true

    /** An editor with something in it, which a new variant would throw away. */
    private val editorInUse: Boolean
        get() = editor?.let { it.moves.isNotEmpty() || it.name.isNotBlank() || it.theory.text.isNotBlank() } == true

    /** Runs a shortcut. It works on every screen; a puzzle stays open underneath the new variant. */
    fun run(command: Command) {
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
            Command.SETTINGS -> prefs = if (prefs == null) Prefs.Config else null
            Command.DRAW -> drawModeOnScreen?.let { it.value = !it.value }
                ?: run { flash = "Zeichnen geht, sobald ein Brett zu sehen ist." }
            Command.COPY_FEN, Command.COPY_PGN -> copyBoard(command)
            Command.NEW_VARIANT, Command.PASTE_PGN, Command.PASTE_FEN -> when {
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
            val numbered = e.positions.zip(e.moves).take(e.cursor).map { (before, move) -> numberedSan(before, move) }
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

    /** Ctrl+B 2 / 3: the FEN of the board, or the PGN of the moves that led there, on the clipboard. */
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
                },
            )
        }
    }

    fun resetKeys() {
        keyCapture = null
        updateLibrary { it.copy(settings = it.settings.copy(prefixKey = "b", keys = emptyMap())) }
    }

    fun setTapZonesOn(on: Boolean) = updateLibrary { it.copy(settings = it.settings.copy(tapZonesOn = on)) }

    fun setTapZone(zone: TapZone, command: Command?) = updateLibrary { it.copy(settings = it.settings.withZone(zone, command)) }

    /** Shown in the editor when it was opened on top of a puzzle. */
    private val backToPuzzle: String?
        get() = if (session != null) "Das Puzzle wartet: nach Speichern oder Abbrechen geht es dort weiter." else null

    /** Result of the last export or import, shown on the home screen. */
    var message by mutableStateOf<String?>(null)

    val canTransfer: Boolean get() = files != null

    private val quizzes: List<Quiz> get() = QuizBuilder.build(library.variants)

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
        val last = library.variants.lastOrNull()
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
    fun editVariant(v: Variant, fromStart: Boolean = false) {
        editor = EditorState(v.id, v.opening, v.name, v.moves.map(Move::fromUci), v.playWhite, v.theory, fromStart, v.startFen)
    }

    /**
     * A move on the board. At the end of the line it is appended; in the middle, the same move just
     * walks on, and a different one replaces the rest of the line from here.
     */
    fun editorPlay(move: Move) {
        val e = editor ?: return
        if (e.moves.getOrNull(e.cursor) != move) e.moves = e.shown + move
        e.cursor++
    }

    /** Puts the board [index] moves into the line; the moves themselves stay. */
    fun editorGoTo(index: Int) {
        val e = editor ?: return
        e.cursor = index.coerceIn(0, e.moves.size)
    }

    fun editorStepBack() = editorGoTo((editor?.cursor ?: 0) - 1)

    fun editorStepForward() = editorGoTo((editor?.cursor ?: 0) + 1)

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
        return library.variants.firstOrNull { it.id != e.editingId && it.moves.isNotEmpty() && it.target.key == key }
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
        val variant = Variant(
            id, e.opening.trim(), e.name.trim(), e.moves.map { it.uci }, e.playWhite, e.theory.text.trim(), e.startFen,
        )
        val old = library.variants.firstOrNull { it.id == id }
        updateLibrary { lib ->
            lib.copy(variants = if (old != null) lib.variants.map { if (it.id == id) variant else it } else lib.variants + variant)
        }
        // Different moves means a different puzzle: start learning it from scratch.
        if (old != null && !old.sameLine(variant)) saveProgress(progress.copy(cards = progress.cards - id))
        editor = null
    }

    fun cancelEditor() {
        editor = null
    }

    fun deleteVariant(id: String) {
        updateLibrary { lib -> lib.copy(variants = lib.variants.filterNot { it.id == id }) }
        saveProgress(progress.copy(cards = progress.cards - id))
    }

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
        s.attempt = s.queue.removeFirstOrNull()?.attempt()
        s.preview = null
        s.feedback = null
        s.marks = emptyMap()
        s.showTarget = false
        s.earlierMistakes = 0
        s.gapInputs = List(s.gaps.size) { "" }
        s.gapResults = null
        s.revision++
    }

    /** Start the current puzzle again from the initial position. */
    fun restartCurrent() {
        val s = session ?: return
        val attempt = s.attempt ?: return
        s.earlierMistakes += attempt.mistakes
        s.attempt = attempt.drill.attempt()
        s.preview = null
        s.gapInputs = List(s.gaps.size) { "" }
        s.gapResults = null
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
        s.feedback = when {
            right == results.size && (s.attempt?.mistakes ?: 0) + s.earlierMistakes == 0 -> Feedback("Alles richtig!", FeedbackTone.GOOD)
            right == results.size -> Feedback("Alle Lücken richtig.", FeedbackTone.INFO)
            else -> Feedback("$right von ${results.size} Lücken richtig.", FeedbackTone.BAD)
        }
    }

    /** Enter: check the gaps first, then grade. */
    fun confirm() {
        val s = session ?: return
        if (s.done) grade() else checkGaps()
    }

    /** Mistakes on the board (all tries) plus wrong gaps. */
    fun mistakes(s: TrainingSession): Int =
        (s.attempt?.mistakes ?: 0) + s.earlierMistakes + (s.gapResults?.count { !it } ?: 0)

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
        if (g == Grade.AGAIN) s.queue.add(minOf(3, s.queue.size), attempt.drill)
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
        val line = try {
            Pgn.parse(p.text)
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
            // At move zero, so the line can be walked through and cut where it gets interesting.
            fromStart = true,
            startFen = line.startFen,
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
        e.cursor = 0
    }

    // ---- Export / import -----------------------------------------------------------------

    /** Saves [opening]'s variants (all if null), with their learning state, as a JSON file. */
    fun export(opening: String? = null) {
        val picker = files ?: return
        val variants = library.variants.filter { opening == null || it.opening.ifBlank { "Ohne Eröffnung" } == opening }
        val name = "eroeffnungen-" + (opening?.let(::fileSafe) ?: "alle") + ".json"
        picker.save(name, Exchange.export(variants, progress)) { file ->
            if (file != null) message = "${variants.size} Variante(n) exportiert: $file"
        }
    }

    /** Adds the variants of an exported file; same id means the same variant, which is replaced. */
    fun import() {
        val picker = files ?: return
        picker.open { text ->
            if (text == null) return@open
            message = try {
                val result = Exchange.import(text, library, progress)
                updateLibrary { result.library }
                saveProgress(result.progress)
                "Importiert: " + result.summary()
            } catch (e: IllegalArgumentException) {
                "Import fehlgeschlagen: ${e.message}"
            }
        }
    }

    private fun fileSafe(s: String) =
        s.lowercase().replace(Regex("[^a-z0-9äöüß]+"), "-").trim('-').ifEmpty { "eroeffnung" }
}
