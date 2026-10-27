package pl.dakil.transport.data.remote

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Response

/**
 * A transport failure tagged with the request it broke. OkHttp's own exceptions say what went
 * wrong but not where, and "timeout" alone is no help when working out which endpoint is failing.
 * [toAppError] classifies on [cause], so wrapping never changes how an error is explained.
 */
class ApiRequestException(
    val method: String,
    val url: String,
    override val cause: IOException,
) : IOException("$method $url", cause)

/** Wraps every transport failure in an [ApiRequestException] carrying the request's method and URL. */
class RequestContextInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        return try {
            chain.proceed(request)
        } catch (e: IOException) {
            throw ApiRequestException(request.method, request.url.toString(), e)
        }
    }
}
