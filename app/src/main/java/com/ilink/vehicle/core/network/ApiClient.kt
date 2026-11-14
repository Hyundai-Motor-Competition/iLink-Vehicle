package com.ilink.vehicle.core.network

import com.ilink.vehicle.core.auth.TokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

class ApiClient(
    private val baseUrl: String,
    private val tokens: TokenStore,
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build(),
) {
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }
    private val refreshLock = Mutex()

    suspend inline fun <reified T> get(path: String, query: Map<String, Any?> = emptyMap(), auth: Boolean = true): T =
        execute("GET", path, query, null, null, auth, serializer())

    suspend inline fun <reified B, reified T> post(path: String, body: B, idempotencyKey: String? = null, auth: Boolean = true): T =
        execute("POST", path, emptyMap(), json.encodeToString(serializer<B>(), body), idempotencyKey, auth, serializer())

    suspend inline fun <reified T> postEmpty(path: String, idempotencyKey: String? = null): T =
        execute("POST", path, emptyMap(), "", idempotencyKey, true, serializer())

    suspend inline fun <reified B, reified T> put(path: String, body: B, idempotencyKey: String? = null): T =
        execute("PUT", path, emptyMap(), json.encodeToString(serializer<B>(), body), idempotencyKey, true, serializer())

    suspend inline fun <reified T> delete(path: String, query: Map<String, Any?> = emptyMap(), idempotencyKey: String? = null): T =
        execute("DELETE", path, query, null, idempotencyKey, true, serializer())

    fun buildUrl(path: String, query: Map<String, Any?> = emptyMap()): String {
        val b = (baseUrl + path).toHttpUrl().newBuilder()
        query.forEach { (k, v) -> if (v != null) b.addQueryParameter(k, v.toString()) }
        return b.build().toString()
    }

    suspend fun <T> execute(
        method: String,
        path: String,
        query: Map<String, Any?>,
        body: String?,
        idempotencyKey: String?,
        auth: Boolean,
        resSerializer: KSerializer<T>,
    ): T = withContext(Dispatchers.IO) {
        var token = if (auth) tokens.accessToken() else null
        var res = send(method, path, query, body, idempotencyKey, token)
        if (res.first == 401 && auth) {
            token = refresh(token)
            if (token != null) res = send(method, path, query, body, idempotencyKey, token)
        }
        val (status, text) = res
        if (status !in 200..299) throw toError(status, text)
        @Suppress("UNCHECKED_CAST")
        when {
            text.isNotBlank() -> json.decodeFromString(resSerializer, text)
            // 202 · 204: 본문 없음. Unit 응답이면 Unit, nullable 응답이면 null
            resSerializer.descriptor.serialName == "kotlin.Unit" -> Unit as T
            else -> null as T
        }
    }

    private fun send(method: String, path: String, query: Map<String, Any?>, body: String?, key: String?, token: String?): Pair<Int, String> {
        val req = Request.Builder().url(buildUrl(path, query)).header("Accept", "application/json").apply {
            token?.let { header("Authorization", "Bearer $it") }
            key?.let { header("Idempotency-Key", it) }
            val rb = body?.toRequestBody(JSON)
            method(method, if (rb == null && method in BODY_METHODS) "".toRequestBody(JSON) else rb)
        }.build()
        return try {
            http.newCall(req).execute().use { it.code to (it.body?.string() ?: "") }
        } catch (e: IOException) {
            // 화면에는 이 문구가 그대로 나온다 (원래 예외 메시지는 영어)
            throw ApiException(0, "NETWORK", "인터넷 연결을 확인하고 다시 시도해 주세요")
        }
    }

    /** 동시에 여러 요청이 401을 받아도 refresh는 한 번만 */
    private suspend fun refresh(failedToken: String?): String? = refreshLock.withLock {
        val current = tokens.accessToken()
        if (current != null && current != failedToken) return current
        val refreshToken = tokens.refreshToken() ?: return null
        val (status, text) = send("POST", "/v1/auth/refresh", emptyMap(), json.encodeToString(RefreshBody.serializer(), RefreshBody(refreshToken)), null, null)
        if (status != 200) {
            tokens.clearUserTokens()
            return null
        }
        val t = json.decodeFromString(RefreshResult.serializer(), text)
        tokens.saveUserTokens(t.accessToken, t.refreshToken)
        t.accessToken
    }

    private fun toError(status: Int, text: String): ApiException {
        val body = runCatching { json.decodeFromString(ErrorEnvelope.serializer(), text).error }.getOrNull()
        return ApiException(status, body?.code ?: "HTTP_$status", body?.message ?: "HTTP $status", body?.retryAfterSeconds)
    }

    @kotlinx.serialization.Serializable private data class RefreshBody(val refreshToken: String)
    @kotlinx.serialization.Serializable private data class RefreshResult(val accessToken: String, val refreshToken: String)

    companion object {
        private val JSON = "application/json".toMediaType()
        private val BODY_METHODS = setOf("POST", "PUT", "PATCH")
        fun newIdempotencyKey(): String = UUID.randomUUID().toString()
    }
}
