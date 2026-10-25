package repertoire.android

import android.content.Context
import android.content.Intent
import repertoire.core.DirectoryStore
import repertoire.core.Library
import repertoire.core.Progress
import repertoire.core.QuizBuilder
import repertoire.core.Scheduler
import repertoire.core.Store
import repertoire.ui.AppViewModel

/** What a widget or a shortcut on the phone starts: straight into a puzzle, without the home screen. */
sealed interface LearnTarget {
    /** „Lernen“: everything due today, with spaced repetition. */
    data object Due : LearnTarget

    /** „Playlist lernen“ of one opening. */
    data class Opening(val name: String) : LearnTarget

    /** „Lernen“ of a playlist of one's own. */
    data class Playlist(val id: String) : LearnTarget

    val saved: String
        get() = when (this) {
            Due -> "due"
            is Opening -> "opening:$name"
            is Playlist -> "playlist:$id"
        }

    fun start(vm: AppViewModel) = when (this) {
        Due -> vm.startLearningFromAnywhere()
        is Opening -> vm.learnOpening(name)
        is Playlist -> vm.learnSavedPlaylistFromAnywhere(id)
    }

    /** The name and the line underneath it, as the widget shows them. */
    fun label(library: Library, progress: Progress): Pair<String, String> = when (this) {
        Due -> {
            val n = Scheduler.dueQueue(QuizBuilder.build(library.active), progress, library.settings).size
            "Lernen" to if (n == 0) "nichts fällig" else "$n zu lernen"
        }
        is Opening -> name to variantCount(library.groups().firstOrNull { it.opening == name }?.variants?.size)
        is Playlist -> library.playlists.firstOrNull { it.id == id }
            .let { p -> (p?.name ?: "Playlist") to variantCount(p?.let { library.variantsOf(it).size }) }
    }

    fun intent(context: Context): Intent =
        Intent(context, MainActivity::class.java).setAction(ACTION).putExtra(EXTRA, saved)

    companion object {
        const val ACTION = "repertoire.android.LEARN"
        private const val EXTRA = "target"

        fun parse(saved: String?): LearnTarget? = when {
            saved == null -> null
            saved == "due" -> Due
            saved.startsWith("opening:") -> Opening(saved.removePrefix("opening:"))
            saved.startsWith("playlist:") -> Playlist(saved.removePrefix("playlist:"))
            else -> null
        }

        fun of(intent: Intent?): LearnTarget? =
            if (intent?.action == ACTION) parse(intent.getStringExtra(EXTRA)) else null

        private fun variantCount(n: Int?): String = if (n == null || n == 0) "nicht mehr da – Widget bearbeiten" else variants(n)

        fun variants(n: Int): String = if (n == 1) "1 Variante" else "$n Varianten"
    }
}

/** The app's own files, read by the widgets while the app itself may not be running. */
fun Context.store() = Store(DirectoryStore(filesDir))
