package repertoire.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI

private fun open(url: String, token: String?, accept: String, readTimeout: Int): HttpURLConnection {
    val connection = URI(url).toURL().openConnection() as HttpURLConnection
    connection.connectTimeout = 8000
    connection.readTimeout = readTimeout
    connection.setRequestProperty("Accept", accept)
    // lichess and chess.com ask apps to say who they are, so they can get in touch instead of blocking.
    connection.setRequestProperty("User-Agent", "FireHorse (https://github.com/TheBigSchabowski/FireHorse)")
    if (!token.isNullOrBlank()) connection.setRequestProperty("Authorization", "Bearer $token")
    return connection
}

actual suspend fun httpGet(url: String, token: String?): HttpResponse = withContext(Dispatchers.IO) {
    val connection = open(url, token, "application/json", 15000)
    try {
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        HttpResponse(status, stream?.bufferedReader()?.use { it.readText() } ?: "")
    } finally {
        connection.disconnect()
    }
}

actual suspend fun httpLines(url: String, token: String?, accept: String, onLine: (String) -> Unit): Int = withContext(Dispatchers.IO) {
    // lichess sends a user's games as it finds them, with pauses in between.
    val connection = open(url, token, accept, 60000)
    try {
        val status = connection.responseCode
        if (status in 200..299) {
            connection.inputStream.bufferedReader().use { reader ->
                while (true) {
                    ensureActive()
                    onLine(reader.readLine() ?: break)
                }
            }
        }
        status
    } finally {
        connection.disconnect()
    }
}
