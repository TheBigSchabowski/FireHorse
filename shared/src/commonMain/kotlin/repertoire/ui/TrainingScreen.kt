package repertoire.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import repertoire.core.Cloze
import repertoire.core.Grade
import repertoire.core.Move
import repertoire.core.MoveRefs
import repertoire.core.TheoryText
import repertoire.core.Variant
import repertoire.core.TheoryPart
import repertoire.core.compactMoves
import repertoire.core.numberedSan

private val GOOD_COLOR = Color(0xFF7BC67B)
private val BAD_COLOR = Color(0xFFE57373)
private val INFO_COLOR = Color(0xFF90CAF9)

@Composable
fun TrainingScreen(vm: AppViewModel, session: TrainingSession, modifier: Modifier = Modifier) {
    // Read so that moves inside the (non-observable) attempt trigger recomposition.
    session.revision
    val attempt = session.attempt

    Column(modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(session.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Text(
                if (session.untilClean) {
                    "Puzzle ${minOf(session.solved + 1, session.total)} von ${session.total}"
                } else {
                    "erledigt ${session.answered} · noch ${session.queue.size + if (attempt != null) 1 else 0}"
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(end = 16.dp),
            )
            OutlinedButton(onClick = vm::endSession) { Text("Beenden") }
        }
        Spacer(Modifier.height(12.dp))

        if (attempt == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (session.answered == 0) "Gerade nichts fällig." else "Fertig! 🎉",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = vm::endSession) { Text("Zurück") }
                }
            }
            return@Column
        }

        val variant = attempt.quiz.variant
        val preview = session.preview
        // A drawing asked from memory puts the board at its position; a "@8. Ng4" link still goes first.
        val drawing = session.drawing
        val check = session.drawingCheck
        val shownPly = preview ?: drawing?.ply
        BoardWithPanel(
            board = { modifier ->
                ChessBoard(
                    position = shownPly?.let { variant.positions[it] } ?: attempt.position,
                    whiteBottom = variant.playWhite,
                    lastMove = if (shownPly != null) variant.moves.getOrNull(shownPly - 1)?.let(Move::fromUci) else attempt.lastMove,
                    marks = if (shownPly != null) emptyMap() else session.marks,
                    interactive = !attempt.finished && preview == null,
                    onMove = vm::submitMove,
                    modifier = modifier,
                    drawModeState = session.drawMode,
                    kept = when {
                        preview != null || drawing == null -> emptyList()
                        check == null -> session.drawingInput.map(::KeptMark)
                        else -> check.right.map { KeptMark(it, KEPT_RIGHT) } + check.missing.map { KeptMark(it, KEPT_MISSING) } +
                            check.extra.map { KeptMark(it, KEPT_EXTRA) }
                    },
                    penState = vm.pen,
                    clears = vm.boardClears,
                    hiddenState = vm.drawingsHidden,
                    keeping = if (preview == null && drawing != null && check == null) {
                        Keeping("✎ Aus dem Gedächtnis", vm::drawingMark, vm::clearDrawingInput)
                    } else {
                        null
                    },
                )
            },
            top = { Chalkboard(variant.opening, variant.name) },
        ) {
            Spacer(Modifier.height(10.dp))
            Text(
                when {
                    !attempt.singleMove && variant.setUp ->
                        "Spiele beide Seiten ab dieser Stellung weiter, bis die Zielstellung auf dem Brett steht."
                    !attempt.singleMove -> "Spiele beide Seiten vom Start, bis diese Stellung auf dem Brett steht."
                    variant.positions[attempt.startPly].whiteToMove -> "Finde den nächsten Zug für Weiß."
                    else -> "Finde den nächsten Zug für Schwarz."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (attempt.played.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(compactMoves(attempt.played), style = MaterialTheme.typography.bodyMedium)
            }

            session.feedback?.let { fb ->
                Spacer(Modifier.height(16.dp))
                Text(
                    fb.text,
                    color = when (fb.tone) {
                        FeedbackTone.GOOD -> GOOD_COLOR
                        FeedbackTone.BAD -> BAD_COLOR
                        FeedbackTone.INFO -> INFO_COLOR
                    },
                    fontWeight = FontWeight.SemiBold,
                )
            }

            if (attempt.finished) {
                Spacer(Modifier.height(12.dp))
                Text(
                    compactMoves(variant.numberedSan()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                )
                if (variant.theory.isNotBlank() && !attempt.singleMove) {
                    Spacer(Modifier.height(12.dp))
                    TheoryQuiz(vm, session, variant)
                }
                if (drawing != null) {
                    Spacer(Modifier.height(12.dp))
                    DrawingQuiz(session, variant)
                }
                if (preview != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Brett: nach ${numberedSan(variant.positions[preview - 1], Move.fromUci(variant.moves[preview - 1]))}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = INFO_COLOR,
                        )
                        TextButton(onClick = { vm.showPly(null) }) { Text("Zurück zur Stellung") }
                    }
                }
                Spacer(Modifier.height(16.dp))
                if (!session.done) {
                    Button(onClick = vm::confirm) { Text(if (check != null) "Nächste Zeichnung ⏎" else "Prüfen ⏎") }
                } else if (session.graded && vm.mistakes(session) == 0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { vm.grade(Grade.HARD) }) { Text("Schwer") }
                        Button(onClick = { vm.grade(Grade.GOOD) }) { Text("Gut ⏎") }
                        OutlinedButton(onClick = { vm.grade(Grade.EASY) }) { Text("Leicht") }
                    }
                } else if (session.untilClean) {
                    // Learning a playlist: on only after a clean solve.
                    Button(onClick = { vm.grade() }) {
                        Text(
                            when {
                                vm.mistakes(session) > 0 -> "Nochmal ⏎"
                                session.queue.isEmpty() -> "Gut – fertig ⏎"
                                else -> "Gut – nächstes Puzzle ⏎"
                            },
                        )
                    }
                } else {
                    Button(onClick = { vm.grade() }) { Text("Weiter ⏎") }
                }
            } else if (!attempt.singleMove) {
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = vm::toggleTarget) {
                    Text(if (session.showTarget) "Zielstellung ausblenden" else "Zielstellung zeigen (Hilfe)")
                }
                if (session.showTarget) {
                    ChessBoard(
                        position = variant.target,
                        whiteBottom = variant.playWhite,
                        lastMove = null,
                        marks = emptyMap(),
                        interactive = false,
                        onMove = {},
                        modifier = Modifier.size(260.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = vm::restartCurrent, enabled = attempt.started) {
                Text(if (attempt.singleMove) "↺ Nochmal" else "↺ Nochmal von vorn")
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = vm::suspendCurrent) { Text("Variante nicht mehr abfragen") }
        }
    }
}

/** The question of the drawing being asked, and once checked, what the colors on the board mean. */
@Composable
private fun DrawingQuiz(session: TrainingSession, variant: Variant) {
    val drawing = session.drawing ?: return
    val check = session.drawingCheck
    Column(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(12.dp),
    ) {
        Text(
            (if (session.drawings.size > 1) "Zeichnung ${session.drawingIndex + 1} von ${session.drawings.size} · " else "Zeichnung · ") +
                "Stellung ${variant.plyLabel(drawing.ply)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            drawing.question.ifBlank { "Zeichne, was du hier markiert hast." },
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        if (check == null) {
            Text(
                "Auf dem Brett: Antippen kreist ein Feld ein, Ziehen malt einen Pfeil. ${drawing.marks.size} Markierung(en) gesucht.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (!check.correct) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("richtig", style = MaterialTheme.typography.bodySmall, color = GOOD_COLOR, fontWeight = FontWeight.SemiBold)
                Text("fehlte", style = MaterialTheme.typography.bodySmall, color = KEPT_MISSING.copy(alpha = 1f), fontWeight = FontWeight.SemiBold)
                Text("zu viel", style = MaterialTheme.typography.bodySmall, color = BAD_COLOR, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private sealed interface Piece {
    data class Word(val text: String) : Piece

    /** [suffix]: punctuation right after the gap, kept on the same line as it. */
    data class Blank(val gap: TheoryPart.Gap, val suffix: String = "") : Piece

    /** A "@8. Ng4" marker: click to see the board after that move. */
    data class Ref(val ref: TheoryText.Ref, val suffix: String = "") : Piece
}

/** The theory text line by line, cut into words so gaps and move links can sit inside the flowing text. */
private fun lines(variant: Variant): List<List<Piece>> {
    val lines = mutableListOf(mutableListOf<Piece>())
    fun addText(text: String) = text.split("\n").forEachIndexed { i, chunk ->
        if (i > 0) lines += mutableListOf<Piece>()
        val words = chunk.split(" ").toMutableList()
        val previous = lines.last().lastOrNull()
        if (i == 0 && words.first().isNotEmpty()) {
            val glued = when (previous) {
                is Piece.Blank -> previous.copy(suffix = words.first())
                is Piece.Ref -> previous.copy(suffix = words.first())
                else -> null
            }
            if (glued != null) {
                lines.last()[lines.last().lastIndex] = glued
                words.removeAt(0)
            }
        }
        words.filter(String::isNotEmpty).forEach { lines.last() += Piece.Word(it) }
    }
    for (part in Cloze.parse(variant.theory)) {
        when (part) {
            is TheoryPart.Gap -> lines.last() += Piece.Blank(part)
            is TheoryPart.Text -> for (t in MoveRefs.split(part.text, variant)) when (t) {
                is TheoryText.Plain -> addText(t.text)
                is TheoryText.Ref -> lines.last() += Piece.Ref(t)
            }
        }
    }
    return lines
}

/** Fill in the blanks: every "((…))" of the theory becomes a small text field. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TheoryQuiz(vm: AppViewModel, session: TrainingSession, variant: Variant) {
    val theory = variant.theory
    val lines = remember(variant) { lines(variant) }
    val gapCount = remember(theory) { Cloze.gaps(theory).size }
    val focusers = remember(theory) { List(gapCount) { FocusRequester() } }
    val focusManager = LocalFocusManager.current
    val results = session.gapResults
    LaunchedEffect(session.attempt) { focusers.firstOrNull()?.requestFocus() }

    Column(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(12.dp),
    ) {
        for (line in lines) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (piece in line) when (piece) {
                    is Piece.Word -> Text(piece.text, style = MaterialTheme.typography.bodyMedium)
                    is Piece.Ref -> Row {
                        val ply = piece.ref.ply
                        val selected = ply != null && ply == session.preview
                        Text(
                            piece.ref.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (ply == null) MaterialTheme.colorScheme.onSurfaceVariant else INFO_COLOR,
                            textDecoration = if (ply != null && !selected) TextDecoration.Underline else null,
                            modifier = if (ply == null) Modifier else Modifier
                                .background(if (selected) INFO_COLOR.copy(alpha = 0.25f) else Color.Transparent, RoundedCornerShape(4.dp))
                                .clickable { vm.showPly(ply) }
                                .padding(horizontal = 2.dp),
                        )
                        if (piece.suffix.isNotEmpty()) Text(piece.suffix, style = MaterialTheme.typography.bodyMedium)
                    }
                    is Piece.Blank -> Row(verticalAlignment = Alignment.CenterVertically) {
                        val gap = piece.gap
                        val right = results?.get(gap.index)
                        val color = when (right) {
                            null -> MaterialTheme.colorScheme.onSurface
                            true -> GOOD_COLOR
                            false -> BAD_COLOR
                        }
                        val last = gap.index == gapCount - 1
                        val chars = maxOf(gap.answer.length, gap.hint?.length ?: 0, 3)
                        BasicTextField(
                            value = session.gapInputs.getOrElse(gap.index) { "" },
                            onValueChange = { vm.setGapInput(gap.index, it) },
                            readOnly = results != null,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = color, fontWeight = FontWeight.SemiBold),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = if (last) ImeAction.Done else ImeAction.Next),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Next) },
                                onDone = { vm.confirm() },
                            ),
                            modifier = Modifier.width((chars * 9 + 14).dp)
                                .focusRequester(focusers[gap.index])
                                .onPreviewKeyEvent {
                                    if (it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                    when (it.key) {
                                        Key.Enter, Key.NumPadEnter -> vm.confirm().let { true }
                                        Key.Tab -> focusManager.moveFocus(FocusDirection.Next).let { true }
                                        else -> false
                                    }
                                },
                            decorationBox = { field ->
                                Box(
                                    Modifier.border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                ) {
                                    if (session.gapInputs.getOrElse(gap.index) { "" }.isEmpty() && gap.hint != null) {
                                        Text(gap.hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    field()
                                }
                            },
                        )
                        if (right == false) Text(" → ${gap.answers.joinToString(" / ")}", color = GOOD_COLOR, fontWeight = FontWeight.SemiBold)
                        if (piece.suffix.isNotEmpty()) Text(piece.suffix, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
