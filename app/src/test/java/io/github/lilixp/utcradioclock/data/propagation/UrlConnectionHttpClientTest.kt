package io.github.lilixp.utcradioclock.data.propagation

import com.sun.net.httpserver.HttpServer
import io.github.lilixp.utcradioclock.testing.REAL_FEED
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket

/**
 * The real HTTP client against a small server on this PC (the JDK's own), never the Internet:
 * a good answer, HTTP errors, a server that does not answer in time, no server at all.
 */
class UrlConnectionHttpClientTest {

    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply { start() }
    private val base = "http://127.0.0.1:${server.address.port}"
    private var userAgent: String? = null

    private fun answer(path: String, code: Int, body: String, delayMillis: Long = 0) {
        server.createContext(path) { exchange ->
            userAgent = exchange.requestHeaders.getFirst("User-Agent")
            Thread.sleep(delayMillis)
            val bytes = body.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "text/xml;charset=UTF-8")
            exchange.sendResponseHeaders(code, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
    }

    @After
    fun stop() = server.stop(0)

    @Test
    fun validResponse_isReturnedWithOurUserAgent() {
        answer("/solarxml.php", 200, REAL_FEED)
        val body = UrlConnectionHttpClient(userAgent = "UTCRadioClock/test").get("$base/solarxml.php")
        assertEquals(REAL_FEED, body)
        assertEquals("UTCRadioClock/test", userAgent)
    }

    @Test
    fun httpErrors_areFailures() {
        answer("/down", 503, "Service Unavailable")
        answer("/missing", 404, "Not Found")
        val http = UrlConnectionHttpClient(userAgent = "test")
        assertThrows(IOException::class.java) { http.get("$base/down") }
        assertThrows(IOException::class.java) { http.get("$base/missing") }
    }

    @Test
    fun timeout_isAFailure_andDoesNotHang() {
        answer("/slow", 200, REAL_FEED, delayMillis = 3_000)
        val started = System.nanoTime()
        assertThrows(IOException::class.java) { UrlConnectionHttpClient(userAgent = "test", timeoutMillis = 300).get("$base/slow") }
        assertTrue("gave up after the timeout", (System.nanoTime() - started) / 1_000_000 < 2_500)
    }

    @Test
    fun noServer_likeNoInternet_isAFailure() {
        val freePort = ServerSocket(0).use { it.localPort } // nothing listens there any more
        assertThrows(IOException::class.java) { UrlConnectionHttpClient(userAgent = "test").get("http://127.0.0.1:$freePort/solarxml.php") }
    }

    @Test
    fun tooLargeResponse_isAFailure() {
        answer("/huge", 200, "x".repeat(10_000))
        assertThrows(IOException::class.java) { UrlConnectionHttpClient(userAgent = "test", maxBytes = 2_000).get("$base/huge") }
    }
}
