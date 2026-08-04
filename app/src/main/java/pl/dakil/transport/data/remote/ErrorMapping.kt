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
    val detail = message?.takeIf { it.isNotBlank() } ?: this::class.simpleName
    return when (this) {
        is HttpException -> when (val code = code()) {
            404 -> AppError.NotFound("HTTP 404 ${message()}".trim())
            429 -> AppError.RateLimited("HTTP 429 ${message()}".trim())
            in 500..599 -> AppError.ServerError(code, "HTTP $code ${message()}".trim())
            in 400..499 -> AppError.BadRequest(code, "HTTP $code ${message()}".trim())
            else -> AppError.Unknown("HTTP $code ${message()}".trim())
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
