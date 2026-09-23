package repertoire.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Settings(
    val newPerDay: Int = 20,
)

@Serializable
data class Library(
    val variants: List<Variant> = emptyList(),
    val settings: Settings = Settings(),
)

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
