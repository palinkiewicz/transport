package pl.dakil.transport.domain.model

/**
 * A failed data load, classified into the cases the UI explains differently.
 *
 * Repositories keep returning `Result<T>`; ViewModels classify the throwable with
 * `Throwable.toAppError()` (`data/remote/ErrorMapping.kt`) so no screen ever has to render a
 * raw exception message. [detail] carries that raw text for the collapsible details line.
 */
sealed interface AppError {

    /** Raw exception text, shown only behind the "Technical details" toggle. */
    val detail: String?

    /** Whether trying again unchanged could plausibly succeed. */
    val isRetryable: Boolean get() = true

    /** Offline, DNS failure, or the host could not be reached at all. */
    data class NoConnection(override val detail: String? = null) : AppError

    /** The request was sent but the server did not answer in time. */
    data class Timeout(override val detail: String? = null) : AppError

    /** 4xx other than the cases below — the request itself is the problem. */
    data class BadRequest(val code: Int, override val detail: String? = null) : AppError {
        override val isRetryable: Boolean get() = false
    }

    /** 404 — the stop/trip/route is gone from the feed, or the id was never valid. */
    data class NotFound(override val detail: String? = null) : AppError {
        override val isRetryable: Boolean get() = false
    }

    /** 429 — the shared API is asking us to slow down. */
    data class RateLimited(override val detail: String? = null) : AppError

    /** 5xx — the routing service is down or broken. */
    data class ServerError(val code: Int, override val detail: String? = null) : AppError

    /** A 2xx response whose body did not parse as the expected schema. */
    data class MalformedResponse(override val detail: String? = null) : AppError

    data class Unknown(override val detail: String? = null) : AppError
}
