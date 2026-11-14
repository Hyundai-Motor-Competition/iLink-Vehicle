package com.ilink.vehicle.demo

import com.ilink.vehicle.data.model.ActiveSession
import com.ilink.vehicle.data.model.ChangeSpotRequest
import com.ilink.vehicle.data.model.CallGuard
import com.ilink.vehicle.data.model.CallProgress
import com.ilink.vehicle.data.model.ConfirmRouteRequest
import com.ilink.vehicle.data.model.Contacts
import com.ilink.vehicle.data.model.LinkStatusResponse
import com.ilink.vehicle.data.model.RejectBoardingRequest
import com.ilink.vehicle.data.model.SendCallResponse
import com.ilink.vehicle.data.model.SessionBoarding
import com.ilink.vehicle.data.model.SessionCall
import com.ilink.vehicle.data.model.SessionEta
import com.ilink.vehicle.data.model.SessionIncident
import com.ilink.vehicle.data.model.SessionRoute
import com.ilink.vehicle.data.model.SessionSpot
import com.ilink.vehicle.data.model.SessionStatus
import com.ilink.vehicle.data.model.SessionView
import com.ilink.vehicle.data.model.StartPickupRequest
import com.ilink.vehicle.data.model.VehicleDeviceResponse
import com.ilink.vehicle.data.model.VehicleEventRequest
import com.ilink.vehicle.data.model.VehicleTelemetryRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.time.Instant

/**
 * 데모 빌드에서 실제 서버 대신 응답하는 가짜 Pickup Service.
 * OkHttp 인터셉터로 끼워서 앱의 ApiClient · 저장소 · 리듀서 로직은 그대로 탄다.
 *
 * 흐름(서버 규칙을 단순화):
 *  연결: 코드 발급 → 약 12초 뒤 LINKED (V00 화면을 볼 수 있게)
 *  세션: 시작 → 경로 확정(CONFIRMED) → 달리는 동안은 가드 G6(정차) 미충족
 *        → STOPPED 이벤트면 합류 안내(callId) 발송 → 4초 뒤 자녀 열람(MEETING)
 *        → DOOR_CYCLE이면 탑승 감지, 10초 뒤 COMPLETED
 */
/** @param approveAfterPolls 연결 상태를 이만큼 조회하면 보호자가 승인한 것으로 본다 (2초 간격, 기본 6번 ≈ 12초) */
class DemoServer(private val scope: CoroutineScope, private val approveAfterPolls: Int = 6) : Interceptor {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }
    private val lock = Any()

    private var linkPolls = 0
    private var weekStartQuery: String? = null
    private var session: SessionView? = null
    private var nextSessionId = 7001L
    private var completeJob: kotlinx.coroutines.Job? = null

    /** 세션이 바뀔 때마다 올라가는 번호. 가짜 SSE가 이 값을 보고 이벤트를 보낸다 */
    private val _changes = MutableStateFlow(0L)
    val changes: StateFlow<Long> = _changes

    fun reset() = synchronized(lock) {
        linkPolls = 0
        session = null
        completeJob?.cancel()
        bump()
    }

    /** 서버가 정차 실패를 판정한 것처럼: 세션 보류, 아이는 실내 대기 */
    fun injectStopFailed() = synchronized(lock) {
        if (session?.isTerminal == false) {
            update {
                it.copy(
                    status = it.status?.copy(session = "ON_HOLD"),
                    incident = SessionIncident("STOP_FAILED", it.children.firstOrNull()?.childUserId, now()),
                    children = it.children.map { c -> c.copy(phase = "WAITING") },
                )
            }
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        val path = req.url.encodedPath
        val body = req.body?.let { Buffer().also(it::writeTo).readUtf8() }.orEmpty()
        Thread.sleep(120) // 네트워크처럼 약간 늦게
        val (code, text) = runCatching {
            synchronized(lock) {
                weekStartQuery = req.url.queryParameter("weekStart")
                route(req.method, path, body)
            }
        }
            .getOrElse { 500 to error("DEMO_ERROR", it.message ?: "demo error") }
        return Response.Builder()
            .request(req)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code in 200..299) "OK" else "ERROR")
            .body(text.toResponseBody(JSON))
            .build()
    }

    private fun route(method: String, path: String, body: String): Pair<Int, String> = synchronized(lock) {
        val seg = path.trim('/').split('/')
        when {
            // 인증 · 연결
            method == "POST" && path == "/v1/vehicle-devices" -> ok(VehicleDeviceResponse(1, 1, "demo-device-token"))
            method == "POST" && path == "/v1/vehicle-link-codes" -> {
                linkPolls = 0
                ok(DemoData.linkCode())
            }
            method == "GET" && path.matches(Regex("/v1/vehicle-link-codes/\\d+/status")) -> {
                linkPolls++
                if (linkPolls < approveAfterPolls) ok(LinkStatusResponse("WAITING", pollAfterSeconds = 2))
                else ok(LinkStatusResponse("LINKED", 1, 1, "demo-access", "demo-refresh"))
            }
            method == "POST" && path == "/v1/auth/refresh" -> 200 to """{"accessToken":"demo-access","refreshToken":"demo-refresh"}"""
            method == "DELETE" && path == "/v1/vehicle-devices/me/session" -> {
                session = null
                bump()
                204 to ""
            }
            path == "/v1/devices/me/push-token" -> 204 to ""

            // 일정 · 설정
            path == "/v1/vehicles/me/weekly-pickups" -> ok(DemoData.weekly(weekStart = weekStartQuery?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }))
            path == "/v1/vehicles/me/settings-summary" -> ok(DemoData.settings)
            path == "/v1/families/me/pickup-spots" -> ok(DemoData.spots)
            path == "/v1/pickup-spot-recommendations" -> ok(DemoData.recommendations)

            // 세션
            method == "GET" && path == "/v1/pickup-sessions/active" -> {
                val s = session
                if (s == null || s.isTerminal) 204 to "" else ok(ActiveSession(s.sessionId, s.status?.session, s.stateVersion))
            }
            method == "POST" && path == "/v1/pickup-sessions" -> start(decode(body))
            seg.size >= 3 && seg[0] == "v1" && seg[1] == "pickup-sessions" -> sessionRoute(method, seg.drop(3), body)
            seg.size == 4 && seg[1] == "calls" && seg[3] == "revocation" ->
                update { it.copy(call = it.call?.copy(callId = null, progress = null), status = it.status?.copy(call = "REVOKED")) }

            else -> 404 to error("NOT_FOUND", "데모 서버에 없는 API: $method $path")
        }
    }

    private fun sessionRoute(method: String, rest: List<String>, body: String): Pair<Int, String> {
        val s = session ?: return 404 to error("SESSION_NOT_FOUND", "진행 중인 픽업이 없어요")
        if (s.isTerminal && rest.isNotEmpty()) return 409 to error("SESSION_CLOSED", "이미 끝난 픽업이에요")
        return when (method to rest.firstOrNull()) {
            "GET" to null -> ok(s)
            "PUT" to "route" -> {
                val r = decode<ConfirmRouteRequest>(body)
                val spot = DemoData.spots.academies.flatMap { it.spots }.firstOrNull { it.spotId == r.spotId }
                update {
                    it.copy(
                        spot = SessionSpot(r.spotId, spot?.name, spotVersion = 1, locked = true),
                        status = it.status?.copy(spot = "CONFIRMED", vehicle = "ROUTING"),
                        route = SessionRoute(1, null),
                    )
                }
            }
            "POST" to "spot-changes" -> {
                val r = decode<ChangeSpotRequest>(body)
                // here(true): 지금 차 위치를 임시 장소로 확정하고 바로 합류 안내 (실제 서버와 같게)
                if (r.here == true) {
                    val now = Instant.now()
                    return update {
                        it.copy(
                            spot = SessionSpot(null, "차량 정차 위치", spotVersion = r.spotVersion + 1, locked = true),
                            status = it.status?.copy(spot = "CONFIRMED", call = "DELIVERED"),
                            call = (it.call ?: SessionCall(mode = "AUTO")).copy(
                                callId = (it.call?.callId ?: 9000) + 1, held = false, scheduledAt = null,
                                progress = CallProgress(now.toString(), now.toString(), now.plusSeconds(1).toString()),
                            ),
                            children = it.children.map { c -> c.copy(phase = "CALLED", callNotBefore = now.toString()) },
                        )
                    }
                }
                val spot = DemoData.spots.academies.flatMap { it.spots }.firstOrNull { it.spotId == r.spotId }
                update {
                    it.copy(
                        spot = SessionSpot(r.spotId, spot?.name, spotVersion = r.spotVersion + 1, locked = false),
                        status = it.status?.copy(session = "ACTIVE", spot = "SELECTED", call = "NONE"),
                        incident = null,
                        eta = null,
                        call = SessionCall(mode = "AUTO", guard = CallGuard(met = false, unmet = listOf("ETA"))),
                        children = it.children.map { c -> c.copy(phase = "WAITING") },
                    )
                }
            }
            // 보호자가 차에서 '여기로 부르기': 지금 차 위치로 합류 안내를 바로 다시 보낸다
            "POST" to "calls" -> {
                val now = Instant.now()
                update {
                    it.copy(
                        call = it.call?.copy(
                            callId = (it.call.callId ?: 9000) + 1, held = false, scheduledAt = null,
                            progress = CallProgress(now.toString(), now.toString(), now.plusSeconds(1).toString()),
                        ),
                        status = it.status?.copy(call = "DELIVERED"),
                        children = it.children.map { c -> c.copy(phase = "CALLED", callNotBefore = now.toString()) },
                    )
                }
                ok(SendCallResponse(session?.call?.callId, "DELIVERED"))
            }
            "PUT" to "call-hold" -> update { it.copy(call = it.call?.copy(held = true)) }
            // 보류를 풀 때 이미 정차해 있으면 바로 보낸다
            "DELETE" to "call-hold" -> update { callIfReady(it.copy(call = it.call?.copy(held = false)), Instant.now()) }
            "PUT" to "hold" -> update { it.copy(status = it.status?.copy(session = "ON_HOLD")) }
            "DELETE" to "hold" -> update { it.copy(status = it.status?.copy(session = "ACTIVE"), incident = null) }
            "POST" to "cancellation" -> {
                completeJob?.cancel()
                update { it.copy(status = it.status?.copy(session = "CANCELLED")) }
            }
            "POST" to "completion" -> {
                completeJob?.cancel()
                complete()
            }
            "POST" to "boarding-rejection" -> {
                completeJob?.cancel()
                val r = decode<RejectBoardingRequest>(body)
                val childId = r.childUserId ?: s.children.firstOrNull()?.childUserId
                // 기획 QA-37: 취소 창 안의 '아이가 타지 않았어요'는 완료만 취소하고 MEETING을 유지한다 (V04로 복귀).
                // 실제 서버도 세션을 ACTIVE로 둔다. 미탑승 예외(SES-13)는 따로 연다
                update {
                    it.copy(
                        boarding = null,
                        children = it.children.map { c -> if (c.childUserId == childId) c.copy(phase = "MEETING", boardedAt = null) else c },
                    )
                }
            }
            "POST" to "vehicle-telemetry" -> telemetry(decode(body))
            "POST" to "vehicle-events" -> vehicleEvent(decode(body))
            "GET" to "contacts" -> ok(Contacts(listOf(Contacts.Contact("김현지", "CHILD", "010-0000-0002"))))
            "GET" to "events" -> 200 to """{"items":[],"hasMore":false}"""
            else -> 404 to error("NOT_FOUND", "데모 서버에 없는 세션 API: $method ${rest.joinToString("/")}")
        }
    }

    private fun start(req: StartPickupRequest): Pair<Int, String> {
        session?.takeIf { !it.isTerminal }?.let { return ok(it) } // 같은 날 진행 중 세션은 그대로 돌려준다
        val children = buildList {
            if (101L in req.scheduleIds) add(DemoData.HYUNJI)
            if (102L in req.scheduleIds) add(DemoData.HYUNWOO)
        }.ifEmpty { listOf(DemoData.HYUNJI) }
        session = SessionView(
            sessionId = nextSessionId++,
            stateVersion = 1,
            status = SessionStatus("ACTIVE", "ROUTING", "NONE", "SELECTED"),
            call = SessionCall(mode = "AUTO", guard = CallGuard(met = false, unmet = listOf("ETA"))),
            children = children,
        )
        bump()
        return 201 to json.encodeToString(serializer<SessionView>(), session!!)
    }

    /** 내비 ETA를 반영하고, 3분 이내면 합류 안내를 만든다 (실서버의 3분·700m 규칙 단순화) */
    private fun telemetry(t: VehicleTelemetryRequest): Pair<Int, String> {
        val now = Instant.now()
        update { s ->
            var next = s.copy(
                eta = SessionEta(now.plusSeconds(t.navEtaSeconds.toLong()).toString(), t.navEtaSeconds, now.toString()),
                route = SessionRoute(s.route?.routeVersion ?: 1, t.remainingMeters),
                status = s.status?.copy(vehicle = if (s.status.vehicle == "STOPPED") "STOPPED" else "APPROACHING"),
            )
            val call = next.call
            // 합류 안내를 보낸 지 4초가 지나면 자녀가 열람한 것으로 본다
            val sent = call?.progress?.sentAt?.let { Instant.parse(it) }
            if (call?.callId != null && call.progress?.acknowledgedAt == null && sent != null && sent.plusSeconds(4) <= now) {
                next = next.copy(
                    call = call.copy(progress = call.progress.copy(acknowledgedAt = now.toString())),
                    status = next.status?.copy(call = "ACKNOWLEDGED"),
                    children = next.children.map { c -> c.copy(acknowledgedAt = now.toString(), phase = "MEETING") },
                )
            }
            // 가드 G6(2026.11.13~): 합류 안내는 정차 뒤에만. 달리는 동안은 G6 미충족으로 보인다.
            // 도착 3분 전(또는 700m)에는 자녀에게 실내 준비 알림만 간다 (화면은 ETA로 판단)
            if (call != null && call.callId == null && next.status?.vehicle != "STOPPED") {
                next = next.copy(call = call.copy(guard = CallGuard(met = false, unmet = listOf("G6")), scheduledAt = null))
            }
            next
        }
        return 202 to ""
    }

    private fun vehicleEvent(e: VehicleEventRequest): Pair<Int, String> {
        val now = Instant.now()
        when (e.type) {
            // 정차하면 합류 안내 판정을 다시 한다: 자동 방식이고 보류가 아니면 이때 보낸다
            "STOPPED" -> update {
                callIfReady(
                    it.copy(
                        status = it.status?.copy(vehicle = "STOPPED"),
                        eta = it.eta?.copy(remainingSeconds = 0, stopEtaAt = now.toString()),
                    ),
                    now,
                )
            }
            "DEPARTED" -> update { it.copy(status = it.status?.copy(vehicle = "DEPARTED")) }
            "DOOR_CYCLE" -> {
                update {
                    it.copy(boarding = SessionBoarding(now.toString(), now.plusSeconds(CANCEL_WINDOW_S).toString(), e.door))
                }
                completeJob?.cancel()
                completeJob = scope.launch {
                    delay(CANCEL_WINDOW_S * 1000)
                    synchronized(lock) { if (session?.boarding != null && session?.isTerminal == false) complete() }
                }
            }
        }
        return 202 to ""
    }

    /** 정차 중 · 자동 방식 · 보류 아님 · 아직 안 보냄이면 합류 안내를 보낸다 (가드 G1~G6 통과로 본다) */
    private fun callIfReady(s: SessionView, now: Instant): SessionView {
        val call = s.call ?: return s
        if (s.status?.vehicle != "STOPPED" || call.callId != null || call.held || call.mode == "GUARDIAN_CONFIRM") return s
        return s.copy(
            call = call.copy(
                callId = 9001,
                scheduledAt = null,
                guard = CallGuard(met = true),
                progress = CallProgress(now.toString(), now.toString(), now.plusSeconds(1).toString()),
            ),
            status = s.status?.copy(call = "DELIVERED"),
            children = s.children.map { it.copy(phase = "CALLED", callNotBefore = now.toString()) },
        )
    }

    private fun complete(): Pair<Int, String> = update {
        val now = now()
        it.copy(
            status = it.status?.copy(session = "COMPLETED"),
            children = it.children.map { c -> if (c.phase == "NOT_BOARDED") c else c.copy(boardedAt = now) },
        )
    }

    // ── 공통 ──

    private fun update(f: (SessionView) -> SessionView): Pair<Int, String> {
        val cur = session ?: return 404 to error("SESSION_NOT_FOUND", "진행 중인 픽업이 없어요")
        val next = f(cur).copy(stateVersion = cur.stateVersion + 1)
        session = next
        bump()
        return ok(next)
    }

    private fun bump() {
        _changes.value = _changes.value + 1
    }

    private fun now() = Instant.now().toString()

    private inline fun <reified T> ok(v: T): Pair<Int, String> = 200 to json.encodeToString(serializer<T>(), v)
    private inline fun <reified T> decode(body: String): T = json.decodeFromString(serializer<T>(), body)
    private fun error(code: String, message: String) =
        """{"error":{"code":"$code","message":"${message.replace("\"", "'")}"}}"""

    private companion object {
        val JSON = "application/json".toMediaType()
        const val CANCEL_WINDOW_S = 10L
    }
}
