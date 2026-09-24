package repertoire.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The export file: readable and editable by hand, e.g.
 *
 * ```json
 * {
 *   "format": "eroeffnungs-puzzle",
 *   "version": 1,
 *   "variants": [
 *     {
 *       "opening": "Sizilianisch",
 *       "name": "Scheveningen",
 *       "playWhite": false,
 *       "moves": "1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. f3 e6",
 *       "theory": "Gegen g4 hilft ((Nfd7))."
 *     }
 *   ]
 * }
 * ```
 *
 * "id" and "progress" are optional; without an id a variant is always added as new.
 */
@Serializable
data class ExportFile(
    val format: String = FORMAT,
    val version: Int = 1,
    val exportedAt: String = "",
    val variants: List<ExportedVariant> = emptyList(),
) {
    companion object {
        const val FORMAT = "eroeffnungs-puzzle"
    }
}

@Serializable
data class ExportedVariant(
    val id: String? = null,
    val opening: String = "",
    val name: String,
    val playWhite: Boolean = true,
    /** SAN with or without move numbers ("1. e4 c5 2. Nf3"); UCI ("e2e4") works as well. */
    val moves: String,
    val theory: String = "",
    /** Learning state; left out, the variant starts as new. */
    val progress: ReviewState? = null,
)

data class ImportResult(val library: Library, val progress: Progress, val added: Int, val updated: Int, val errors: List<String>) {
    fun summary(): String = buildString {
        append("$added neu, $updated aktualisiert")
        if (errors.isNotEmpty()) append(", ${errors.size} übersprungen:\n" + errors.joinToString("\n"))
    }
}

object Exchange {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun export(variants: List<Variant>, progress: Progress, withProgress: Boolean = true): String =
        json.encodeToString(
            ExportFile(
                exportedAt = isoDate(currentTimeMillis()),
                variants = variants.map { v ->
                    ExportedVariant(
                        id = v.id,
                        opening = v.opening,
                        name = v.name,
                        playWhite = v.playWhite,
                        moves = compactMoves(v.numberedSan()),
                        theory = v.theory,
                        progress = if (withProgress) progress.cards[v.id] else null,
                    )
                },
            ),
        )

    /**
     * Adds the file's variants to [library]. A variant whose id already exists replaces it; its
     * learning state is taken from the file, or dropped if the file has none and the moves changed.
     */
    fun import(text: String, library: Library, progress: Progress): ImportResult {
        val file = try {
            json.decodeFromString<ExportFile>(text)
        } catch (e: Exception) {
            throw IllegalArgumentException("Keine gültige Export-Datei: ${e.message?.lineSequence()?.first()}")
        }
        require(file.format == ExportFile.FORMAT) { "Unbekanntes Format „${file.format}“" }

        var variants = library.variants
        var cards = progress.cards
        var added = 0
        var updated = 0
        val errors = mutableListOf<String>()
        for (e in file.variants) {
            val label = listOf(e.opening, e.name).filter(String::isNotBlank).joinToString(" › ")
            val moves = try {
                parseMoves(e.moves)
            } catch (ex: IllegalArgumentException) {
                errors += "$label: ${ex.message}"
                continue
            }
            if (moves.isEmpty() || e.name.isBlank()) {
                errors += "${label.ifEmpty { "(ohne Namen)" }}: Name oder Züge fehlen"
                continue
            }
            val id = e.id?.takeIf(String::isNotBlank) ?: newId()
            val variant = Variant(id, e.opening.trim(), e.name.trim(), moves, e.playWhite, e.theory)
            val old = variants.firstOrNull { it.id == id }
            if (old != null) {
                variants = variants.map { if (it.id == id) variant else it }
                updated++
            } else {
                variants = variants + variant
                added++
            }
            cards = when {
                e.progress != null -> cards + (id to e.progress)
                old != null && old.moves != moves -> cards - id
                else -> cards
            }
        }
        return ImportResult(library.copy(variants = variants), progress.copy(cards = cards), added, updated, errors)
    }

    /** "1. e4 c5 2. Nf3", "1.e4 c5", "e2e4 c7c5" → UCI moves. */
    fun parseMoves(text: String): List<String> {
        var position = Position.START
        val result = mutableListOf<String>()
        val tokens = text.split(Regex("\\s+"))
            .map { it.replace(Regex("^\\d+\\.+"), "") }
            .filter { it.isNotEmpty() && it !in setOf("*", "1-0", "0-1", "1/2-1/2") }
        for (token in tokens) {
            val move = position.parseSan(token)
                ?: runCatching { Move.fromUci(token) }.getOrNull()?.takeIf(position::isLegal)
                ?: throw IllegalArgumentException("„$token“ ist nach ${compactMoves(numbered(result))} nicht möglich")
            result += move.uci
            position = position.play(move)
        }
        return result
    }

    private fun numbered(uci: List<String>): List<String> =
        Variant("", "", "", uci).numberedSan().ifEmpty { listOf("Start") }
}
