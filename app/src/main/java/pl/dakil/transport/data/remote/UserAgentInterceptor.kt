package pl.dakil.transport.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import pl.dakil.transport.BuildConfig

/**
 * Transitous API usage guidelines require a User-Agent identifying the app,
 * its version, and a contact point on every request.
 * See https://transitous.org/sources/
 */
class UserAgentInterceptor : Interceptor {
    private val userAgent =
        "Transport/${BuildConfig.VERSION_NAME} (Android; pogromca.ap@gmail.com)"

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("User-Agent", userAgent)
            .build()
        return chain.proceed(request)
    }
}
