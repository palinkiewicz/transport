package pl.dakil.transport.data.remote

import java.io.InterruptedIOException
import java.net.UnknownHostException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.dakil.transport.domain.model.AppError
import retrofit2.HttpException
import retrofit2.Response

class ErrorMappingTest {

    private val url = "https://api.transitous.org/api/v5/stoptimes?stopId=abc&n=10"

    private fun httpError(code: Int, body: String): HttpException {
        val raw = okhttp3.Response.Builder()
            .request(Request.Builder().url(url).build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("Not Found")
            .build()
        return HttpException(
            Response.error<Any>(body.toResponseBody("application/json".toMediaType()), raw),
        )
    }

    @Test
    fun `http error detail carries status, request and body`() {
        val error = httpError(404, """{"error":"stop not found"}""").toAppError()

        assertTrue(error is AppError.NotFound)
        val detail = requireNotNull(error.detail)
        assertTrue(detail, detail.startsWith("HTTP 404 Not Found"))
        assertTrue(detail, "GET $url" in detail)
        assertTrue(detail, """{"error":"stop not found"}""" in detail)
    }

    @Test
    fun `long bodies are truncated`() {
        val detail = requireNotNull(httpError(500, "x".repeat(MAX_BODY_CHARS * 2)).toAppError().detail)

        assertTrue(detail.length < MAX_BODY_CHARS + 200)
        assertTrue(detail.endsWith("…"))
    }

    @Test
    fun `an empty body adds nothing`() {
        assertEquals("HTTP 404 Not Found\nGET $url", httpError(404, "").toAppError().detail)
    }

    @Test
    fun `a wrapped transport failure keeps its category and names the request`() {
        val error = ApiRequestException("GET", url, UnknownHostException("api.transitous.org")).toAppError()

        assertTrue(error is AppError.NoConnection)
        assertEquals("api.transitous.org\nGET $url", error.detail)
    }

    @Test
    fun `a call timeout wrapping the request still names it`() {
        val timeout = InterruptedIOException("timeout").apply {
            initCause(ApiRequestException("GET", url, java.io.IOException("Canceled")))
        }
        val error = timeout.toAppError()

        assertTrue(error is AppError.Timeout)
        assertEquals("timeout\nGET $url", error.detail)
    }
}
