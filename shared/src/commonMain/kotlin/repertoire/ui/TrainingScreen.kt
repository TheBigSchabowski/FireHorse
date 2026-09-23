package repertoire.ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import repertoire.core.Grade
import repertoire.core.compactMoves

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
                "erledigt ${session.answered} · noch ${session.queue.size + if (attempt != null) 1 else 0}",
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
        BoardWithPanel(
            board = { modifier ->
                ChessBoard(
                    position = attempt.position,
                    whiteBottom = variant.playWhite,
                    lastMove = attempt.lastMove,
                    marks = session.marks,
                    interactive = !attempt.finished,
                    onMove = vm::submitMove,
                    modifier = modifier,
                )
            },
            top = { Chalkboard(variant.opening, variant.name) },
        ) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Spiele beide Seiten vom Start, bis diese Stellung auf dem Brett steht.",
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
                Spacer(Modifier.height(16.dp))
                if (session.graded && attempt.mistakes + session.earlierMistakes == 0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { vm.grade(Grade.HARD) }) { Text("Schwer") }
                        Button(onClick = { vm.grade(Grade.GOOD) }) { Text("Gut ⏎") }
                        OutlinedButton(onClick = { vm.grade(Grade.EASY) }) { Text("Leicht") }
                    }
                } else {
                    Button(onClick = { vm.grade() }) { Text("Weiter ⏎") }
                }
            } else {
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
            OutlinedButton(onClick = vm::restartCurrent, enabled = attempt.played.isNotEmpty()) { Text("↺ Nochmal von vorn") }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = vm::suspendCurrent) { Text("Variante nicht mehr abfragen") }
        }
    }
}
