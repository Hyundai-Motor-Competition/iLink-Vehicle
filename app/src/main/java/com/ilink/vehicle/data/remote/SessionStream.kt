package com.ilink.vehicle.data.remote

import com.ilink.vehicle.core.auth.TokenStore
import com.ilink.vehicle.core.network.ApiClient
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit

data class StreamEvent(val id: String?, val type: String?, val data: String)

/**
 * SES-08 세션 실시간 이벤트(SSE, 차량 토큰 전용).
 * 끊기면 호출 측에서 SES-03 재조회 → lastEventId로 재구독 (F4-06, SEQ 7).
 */
interface SessionEventSource {
    fun open(sessionId: Long, lastEventId: String?): Flow<StreamEvent>
}

class SessionStream(private val client: ApiClient, private val tokens: TokenStore) : SessionEventSource {

    private val sseHttp = client.http.newBuilder().readTimeout(0, TimeUnit.MILLISECONDS).build()

    override fun open(sessionId: Long, lastEventId: String?): Flow<StreamEvent> = callbackFlow {
        val token = tokens.accessToken()
        val req = Request.Builder()
            .url(client.buildUrl("/v1/pickup-sessions/$sessionId/stream"))
            .header("Accept", "text/event-stream")
            .apply {
                token?.let { header("Authorization", "Bearer $it") }
                lastEventId?.let { header("Last-Event-ID", it) }
            }
            .build()

        val source = EventSources.createFactory(sseHttp).newEventSource(req, object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                trySend(StreamEvent(id, type, data))
            }

            override fun onClosed(eventSource: EventSource) {
                close()
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                close(t ?: IllegalStateException("SSE failed: ${response?.code}"))
            }
        })
        awaitClose { source.cancel() }
    }
}
