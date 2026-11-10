package com.ilink.vehicle.data.repository

import com.ilink.vehicle.core.network.ApiClient.Companion.newIdempotencyKey
import com.ilink.vehicle.data.model.*
import com.ilink.vehicle.data.remote.SessionEventSource
import com.ilink.vehicle.data.remote.VehicleApi
import com.ilink.vehicle.domain.SessionReducer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * 진행 중 픽업 세션의 단일 출처. 명령 응답·SSE 이벤트 모두 SessionReducer를 거쳐 반영한다.
 * 화면 회전·창 크기 변경·백그라운드 복귀 때도 이 상태를 그대로 쓴다 (QA-26).
 */
class SessionRepository(
    private val api: VehicleApi,
    private val stream: SessionEventSource,
    private val scope: CoroutineScope,
) {
    private val _session = MutableStateFlow<SessionView?>(null)
    val session: StateFlow<SessionView?> = _session
    private var streamJob: Job? = null
    private var lastEventId: String? = null

    private fun apply(next: SessionView): SessionView {
        val merged = SessionReducer.merge(_session.value, next)
        _session.value = merged
        return merged
    }

    suspend fun restoreActive(): SessionView? {
        val active = api.activeSession() ?: return null
        return refresh(active.sessionId).also { follow(active.sessionId) }
    }

    suspend fun refresh(id: Long = requireId()): SessionView = apply(api.session(id))

    suspend fun start(scheduleIds: List<Long>): SessionView {
        val today = LocalDate.now(ZoneId.of("Asia/Seoul")).toString()
        val v = apply(api.startSession(StartPickupRequest(scheduleIds, today), newIdempotencyKey()))
        follow(v.sessionId)
        return v
    }

    suspend fun confirmRoute(spotId: Long, navRouteId: String?) =
        apply(api.confirmRoute(requireId(), ConfirmRouteRequest(spotId, navRouteId, version()), newIdempotencyKey()))

    suspend fun changeSpot(spotId: Long) = cur().let {
        apply(api.changeSpot(it.sessionId, ChangeSpotRequest(spotId, it.stateVersion, it.spot?.spotVersion ?: 0), newIdempotencyKey()))
    }

    suspend fun holdCall() = apply(api.holdCall(requireId(), version(), newIdempotencyKey()))
    suspend fun releaseCallHold() = apply(api.releaseCallHold(requireId(), version(), newIdempotencyKey()))
    suspend fun cancel(reason: String?) = apply(api.cancel(requireId(), version(), reason, newIdempotencyKey()))
    suspend fun rejectBoarding(childUserId: Long?) =
        apply(api.rejectBoarding(requireId(), RejectBoardingRequest(version(), childUserId), newIdempotencyKey()))
    suspend fun completeManually() = apply(api.complete(requireId(), CompletePickupRequest("MANUAL", version()), newIdempotencyKey()))
    suspend fun sendCallManually() = api.sendCall(requireId(), version(), newIdempotencyKey()).also { refresh() }
    suspend fun contacts() = api.contacts(requireId())

    suspend fun sendTelemetry(req: VehicleTelemetryRequest) = api.telemetry(requireId(), req)
    suspend fun sendVehicleEvent(req: VehicleEventRequest) = api.vehicleEvent(requireId(), req)

    /** SES-08 구독. 끊기면 최신 세션을 다시 받고 lastEventId부터 재구독 (F4-06) */
    private fun follow(sessionId: Long) {
        streamJob?.cancel()
        streamJob = scope.launch {
            var backoff = 1_000L
            while (isActive && _session.value?.isTerminal != true) {
                runCatching {
                    stream.open(sessionId, lastEventId).collect { e ->
                        lastEventId = e.id ?: lastEventId
                        backoff = 1_000L
                        refresh(sessionId)
                    }
                }
                runCatching { refresh(sessionId) }
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(30_000L)
            }
        }
    }

    fun clear() {
        streamJob?.cancel()
        _session.value = null
        lastEventId = null
    }

    private fun cur() = checkNotNull(_session.value) { "no active session" }
    private fun requireId() = cur().sessionId
    private fun version() = cur().stateVersion
}
