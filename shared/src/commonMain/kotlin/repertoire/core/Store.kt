package repertoire.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Settings(
    val newPerDay: Int = 20,
    /** How long the horse has to be held per step on a phone: one step for drawing, two for a new variant, … */
    val holdStepMs: Int = 1000,
    /** Double taps on a free spot of the phone's screen run a shortcut, by where they land. */
    val tapZonesOn: Boolean = true,
    /** The screen is cut into [tapCols] columns and [tapRows] rows of zones. */
    val tapCols: Int = 2,
    val tapRows: Int = 3,
    /**
     * The shortcut (a `Command` name, "" for none) of each zone, [tapCols] per row, read like a
     * book – top left, top right, middle left, …
     */
    val tapZones: List<String> = listOf("SHORTCUTS", "DRAW", "NEW_VARIANT", "SETTINGS", "PASTE_PGN", "PASTE_FEN"),
    /** The letter that goes with Ctrl to start a shortcut on the keyboard. */
    val prefixKey: String = "b",
    /** Keys changed from their default, by `Command` name; the rest keep theirs. */
    val keys: Map<String, String> = emptyMap(),
    /** Time between two moves when a variant is played. */
    val playDelayMs: Int = 1500,
    /** The player starts over after the last move. */
    val playLoop: Boolean = false,
    /** The horse button on a phone; off: shortcuts only by double tap. */
    val horseOn: Boolean = true,
    /** The horse sits at the bottom left instead of the bottom right. */
    val horseLeft: Boolean = false,
    /** Name of a `BoardTheme`. */
    val boardTheme: String = "classic",
)

@Serializable
data class Library(
    val variants: List<Variant> = emptyList(),
    val settings: Settings = Settings(),
    /** Openings (as [openingOf] names them) shown first on the home screen, in the order they were pinned. */
    val pinned: List<String> = emptyList(),
    /** Playlists put together by hand, from any openings. */
    val playlists: List<SavedPlaylist> = emptyList(),
) {
    /** The variants of [playlist] in its order; ones deleted in the meantime are left out. */
    fun variantsOf(playlist: SavedPlaylist): List<Variant> =
        playlist.variantIds.mapNotNull { id -> variants.firstOrNull { it.id == id } }

    /** The variants by opening: the pinned openings first, then the others alphabetically; each in the order it was created. */
    fun groups(): List<OpeningGroup> {
        val byOpening = variants.groupBy(::openingOf)
        val pins = pinned.filter { it in byOpening }
        val rest = (byOpening.keys - pins.toSet()).sortedWith(String.CASE_INSENSITIVE_ORDER)
        return pins.map { OpeningGroup(it, byOpening.getValue(it), pinned = true) } +
            rest.map { OpeningGroup(it, byOpening.getValue(it), pinned = false) }
    }

    companion object {
        const val MAX_PINNED = 3
        const val NO_OPENING = "Ohne Eröffnung"

        fun openingOf(v: Variant): String = v.opening.ifBlank { NO_OPENING }
    }
}

/** A playlist of its own: any variants, from any openings, in the order given. */
@Serializable
data class SavedPlaylist(val id: String, val name: String, val variantIds: List<String>)

/** One opening on the home screen, also the playlist that goes through all of its variants. */
data class OpeningGroup(val opening: String, val variants: List<Variant>, val pinned: Boolean)

@Serializable
data class Progress(
    val cards: Map<String, ReviewState> = emptyMap(),
    /** Learning day (yyyy-mm-dd) → number of new cards started that day. */
    val newIntroduced: Map<String, Int> = emptyMap(),
)

class Store(private val files: FileStore) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val location: String get() = files.location

    fun loadLibrary(): Library = read("library.json") ?: Library()

    fun saveLibrary(library: Library) = files.write("library.json", json.encodeToString(library))

    fun loadProgress(): Progress = read("progress.json") ?: Progress()

    fun saveProgress(progress: Progress) = files.write("progress.json", json.encodeToString(progress))

    private inline fun <reified T> read(name: String): T? {
        val text = files.read(name) ?: return null
        return try {
            json.decodeFromString<T>(text)
        } catch (e: Exception) {
            // Keep the unreadable file instead of silently overwriting the user's progress.
            files.write("$name.broken-${currentTimeMillis()}", text)
            null
        }
    }

    companion object {
        fun learningDay(now: Long = currentTimeMillis()): String = isoDate(Fsrs.startOfDay(now))
    }
}
