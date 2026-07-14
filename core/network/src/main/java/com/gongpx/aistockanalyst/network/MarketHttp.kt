package com.gongpx.aistockanalyst.network

import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.HeaderMap
import retrofit2.http.Url

interface RawHttpService {
    @GET
    suspend fun get(
        @Url url: String,
        @HeaderMap headers: Map<String, String>,
    ): Response<ResponseBody>
}

object MarketNetworkFactory {
    fun createRawHttpService(client: OkHttpClient): RawHttpService =
        Retrofit.Builder()
            .baseUrl("https://localhost/")
            .client(client)
            .build()
            .create(RawHttpService::class.java)
}

sealed class ProviderException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

class ProviderHttpException(
    provider: String,
    val statusCode: Int,
) : ProviderException("$provider returned HTTP $statusCode")

class ProviderPayloadException(
    provider: String,
    message: String,
    cause: Throwable? = null,
) : ProviderException("$provider payload error: $message", cause)

internal fun Response<ResponseBody>.requireBody(provider: String): ResponseBody {
    if (!isSuccessful) {
        errorBody()?.close()
        throw ProviderHttpException(provider, code())
    }
    return body() ?: throw ProviderPayloadException(provider, "response body is empty")
}

class InMemoryCookieJar : CookieJar {
    private val cookies = ConcurrentHashMap<String, Cookie>()

    override fun saveFromResponse(
        url: HttpUrl,
        cookies: List<Cookie>,
    ) {
        cookies.forEach { cookie ->
            this.cookies[cookie.storageKey()] = cookie
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        return cookies.values.mapNotNull { cookie ->
            if (cookie.expiresAt <= now) {
                cookies.remove(cookie.storageKey(), cookie)
                null
            } else {
                cookie.takeIf { it.matches(url) }
            }
        }
    }

    private fun Cookie.storageKey(): String = "$name|$domain|$path"
}
