package repertoire.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import repertoire.core.Fsrs
import repertoire.core.Grade
import repertoire.core.Library
import repertoire.core.Move
import repertoire.core.Outcome
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

enum class FeedbackTone { GOOD, BAD, INFO }

data class Feedback(val text: String, val tone: FeedbackTone)

class TrainingSession(
    val title: String,
    val graded: Boolean,
    val queue: ArrayDeque<Quiz>,
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
}

/** A variant being entered: play the moves, then name it. */
class EditorState(val editingId: String?, opening: String, name: String, moves: List<Move>, playWhite: Boolean) {
    var opening by mutableStateOf(opening)
    var name by mutableStateOf(name)
    var moves by mutableStateOf(moves)
    var playWhite by mutableStateOf(playWhite)

    val position: Position get() = moves.fold(Position.START) { p, m -> p.play(m) }
}

class AppViewModel(private val store: Store) {
    var library by mutableStateOf(store.loadLibrary())
        private set
    var progress by mutableStateOf(store.loadProgress())
        private set
    var session by mutableStateOf<TrainingSession?>(null)
        private set
    var editor by mutableStateOf<EditorState?>(null)
        private set

    val dataLocation: String get() = store.location

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
        )
    }

    fun editVariant(v: Variant) {
        editor = EditorState(v.id, v.opening, v.name, v.moves.map(Move::fromUci), v.playWhite)
    }

    fun editorPlay(move: Move) {
        val e = editor ?: return
        e.moves = e.moves + move
    }

    fun editorUndo() {
        val e = editor ?: return
        e.moves = e.moves.dropLast(1)
    }

    /** Title of another saved variant that already ends in the editor's position. */
    fun editorDuplicate(): String? {
        val e = editor ?: return null
        if (e.moves.isEmpty()) return null
        val key = e.position.key
        return library.variants.firstOrNull { it.id != e.editingId && it.moves.isNotEmpty() && it.target.key == key }
            ?.let(QuizBuilder::title)
    }

    fun saveEditor() {
        val e = editor ?: return
        if (e.name.isBlank() || e.moves.isEmpty()) return
        val id = e.editingId ?: newId()
        val variant = Variant(id, e.opening.trim(), e.name.trim(), e.moves.map { it.uci }, e.playWhite)
        val old = library.variants.firstOrNull { it.id == id }
        updateLibrary { lib ->
            lib.copy(variants = if (old != null) lib.variants.map { if (it.id == id) variant else it } else lib.variants + variant)
        }
        // Different moves means a different puzzle: start learning it from scratch.
        if (old != null && old.moves != variant.moves) saveProgress(progress.copy(cards = progress.cards - id))
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
        start(TrainingSession("Lernen", graded = true, ArrayDeque(Scheduler.dueQueue(quizzes, progress, library.settings))))
    }

    fun startCram() {
        start(TrainingSession("Frei üben", graded = false, ArrayDeque(Scheduler.cramQueue(quizzes, progress))))
    }

    fun practice(variantId: String) {
        val quiz = quizzes.firstOrNull { it.variant.id == variantId } ?: return
        start(TrainingSession("Üben", graded = false, ArrayDeque(listOf(quiz))))
    }

    private fun start(s: TrainingSession) {
        session = s
        nextQuiz(s)
    }

    fun endSession() {
        session = null
    }

    private fun nextQuiz(s: TrainingSession) {
        s.attempt = s.queue.removeFirstOrNull()?.let(::QuizAttempt)
        s.feedback = null
        s.marks = emptyMap()
        s.showTarget = false
        s.earlierMistakes = 0
        s.revision++
    }

    /** Start the current puzzle again from the initial position. */
    fun restartCurrent() {
        val s = session ?: return
        val attempt = s.attempt ?: return
        s.earlierMistakes += attempt.mistakes
        s.attempt = QuizAttempt(attempt.quiz)
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

    fun toggleTarget() {
        session?.let { it.showTarget = !it.showTarget }
    }

    /** Finishes the current puzzle. Without an explicit grade, a clean solve counts as GOOD, a mistake as AGAIN. */
    fun grade(grade: Grade? = null) {
        val s = session ?: return
        val attempt = s.attempt ?: return
        if (!attempt.finished) return
        val mistakes = attempt.mistakes + s.earlierMistakes
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
        if (g == Grade.AGAIN) s.queue.add(minOf(3, s.queue.size), attempt.quiz)
        s.answered++
        nextQuiz(s)
    }

    /** "Ignore": the variant is not asked any more until reactivated in the list. */
    fun suspendCurrent() {
        val s = session ?: return
        val id = s.attempt?.quiz?.variant?.id ?: return
        val old = progress.cards[id] ?: ReviewState(0.0, 0.0, 0, 0)
        saveProgress(progress.copy(cards = progress.cards + (id to old.copy(suspended = true))))
        s.queue.removeAll { it.variant.id == id }
        nextQuiz(s)
    }
}
