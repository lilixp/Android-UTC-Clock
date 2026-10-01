package io.github.lilixp.utcradioclock.data.propagation

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Downloads a small text file. Blocking: call it off the main thread. Replaced by a fake in tests. */
fun interface HttpClient {
    /** The body of a successful (2xx) response; [IOException] for no network, a timeout or an HTTP error. */
    @Throws(IOException::class)
    fun get(url: String): String
}

/**
 * [HttpClient] with Android's own [HttpURLConnection]: no extra library for one small file an hour.
 * Short timeouts, so a bad connection never keeps the screen waiting long, and a size limit.
 */
class UrlConnectionHttpClient(
    private val userAgent: String,
    private val timeoutMillis: Int = 10_000,
    private val maxBytes: Int = 64 * 1024,
) : HttpClient {

    override fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = timeoutMillis
            connection.readTimeout = timeoutMillis
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", userAgent)
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            connection.inputStream.use { input ->
                val bytes = input.readNBytesCompat(maxBytes + 1)
                if (bytes.size > maxBytes) throw IOException("Response larger than $maxBytes bytes")
                return String(bytes, Charsets.UTF_8)
            }
        } finally {
            connection.disconnect()
        }
    }

    /** Reads at most [limit] bytes (InputStream.readNBytes exists only from Android 13). */
    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (out.size() < limit) {
            val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }
}
