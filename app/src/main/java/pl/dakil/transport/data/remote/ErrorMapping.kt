package pl.dakil.transport.data.remote

import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlinx.serialization.SerializationException
import pl.dakil.transport.domain.model.AppError
import retrofit2.HttpException

/**
 * Classifies a repository failure into an [AppError] the UI can phrase for a person.
 *
 * Retrofit throws [HttpException] for non-2xx responses (the API interface returns a raw
 * `ResponseBody`, so there is no converter in between), OkHttp throws [IOException] subtypes for
 * transport problems, and manual decoding (`Json.decode`) throws [SerializationException].
 *
 * Cancellation is deliberately not handled here: `runCatching` in the repositories also catches
 * `CancellationException`, so callers must skip it *before* mapping — a cancelled refresh is not
 * an error worth showing.
 */
fun Throwable.toAppError(): AppError {
    // The interceptor's wrapper only says where; what went wrong is the exception it wraps. It may
    // also sit further down the chain — OkHttp's call timeout rethrows as its own
    // InterruptedIOException with the original failure as the cause.
    val failure = (this as? ApiRequestException)?.cause ?: this
    val request = generateSequence(this) { it.cause }
        .firstNotNullOfOrNull { it as? ApiRequestException }
        ?.let { "${it.method} ${it.url}" }
    val detail = listOfNotNull(
        failure.message?.takeIf { it.isNotBlank() } ?: failure::class.simpleName,
        request,
    ).joinToString("\n")
    return when (failure) {
        is HttpException -> {
            val code = failure.code()
            val httpDetail = failure.httpDetail()
            when (code) {
                404 -> AppError.NotFound(httpDetail)
                429 -> AppError.RateLimited(httpDetail)
                in 500..599 -> AppError.ServerError(code, httpDetail)
                in 400..499 -> AppError.BadRequest(code, httpDetail)
                else -> AppError.Unknown(httpDetail)
            }
        }
        // SocketTimeoutException first: it is an InterruptedIOException, as is OkHttp's own
        // whole-call timeout, which surfaces as a plain InterruptedIOException("timeout").
        is SocketTimeoutException, is InterruptedIOException -> AppError.Timeout(detail)
        is UnknownHostException -> AppError.NoConnection(detail)
        is SSLException -> AppError.NoConnection(detail)
        is IOException -> AppError.NoConnection(detail)
        is SerializationException -> AppError.MalformedResponse(detail)
        else -> AppError.Unknown(detail)
    }
}

/**
 * Status line, the request that got it and whatever the server said about it — "HTTP 404" alone
 * cannot tell a stop dropped from the feed from a malformed id or an endpoint that moved.
 */
private fun HttpException.httpDetail(): String {
    val response = response()
    val status = "HTTP ${code()} ${message()}".trim()
    val request = response?.raw()?.request?.let { "${it.method} ${it.url}" }
    // Retrofit buffers an error body in memory; peeking reads it without consuming it, so the
    // body is still there for anything else that wants it.
    val body = runCatching { response?.errorBody()?.source()?.peek()?.readUtf8() }
        .getOrNull()
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { if (it.length > MAX_BODY_CHARS) it.take(MAX_BODY_CHARS) + "…" else it }
    return listOfNotNull(listOfNotNull(status, request).joinToString("\n"), body)
        .joinToString("\n\n")
}

/** Enough for any JSON error MOTIS sends; an HTML error page past this is noise. */
internal const val MAX_BODY_CHARS = 2000
