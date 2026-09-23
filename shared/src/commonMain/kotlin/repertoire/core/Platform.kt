package repertoire.core

/** Where the library and the learning progress are kept (a folder on desktop, app storage on Android). */
interface FileStore {
    /** Shown to the user, e.g. the folder path. */
    val location: String

    fun read(name: String): String?

    /** Must replace the file atomically so a crash never leaves half-written progress behind. */
    fun write(name: String, text: String)
}

expect fun currentTimeMillis(): Long

/** Epoch millis of [hour] o'clock, [daysAhead] local days after the day containing `now - hour`. */
expect fun dayStart(now: Long, daysAhead: Long, hour: Int): Long

/** Local calendar date of [epochMillis] as "yyyy-mm-dd". */
expect fun isoDate(epochMillis: Long): String

/** Local calendar date of [epochMillis] as "dd.MM.". */
expect fun shortDate(epochMillis: Long): String

expect fun newId(): String
