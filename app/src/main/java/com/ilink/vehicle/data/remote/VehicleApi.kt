package com.ilink.vehicle.data.remote

import com.ilink.vehicle.core.network.ApiClient
import com.ilink.vehicle.data.model.*

/** 차량 토큰으로 부르는 API (swagger 기준 ID 표기) */
class VehicleApi(private val c: ApiClient) {

    // 인증 · 연결
    suspend fun registerDevice(model: String, vin: String?, key: String): VehicleDeviceResponse =
        c.post("/v1/vehicle-devices", RegisterVehicleDeviceRequest(model, vin), key, auth = false) // AUTH-02
    suspend fun issueLinkCode(): LinkCodeResponse = c.postEmpty("/v1/vehicle-link-codes") // LINK-01
    suspend fun linkStatus(codeId: Long): LinkStatusResponse = c.get("/v1/vehicle-link-codes/$codeId/status") // LINK-02
    suspend fun updatePushToken(token: String): Unit = c.put("/v1/devices/me/push-token", UpdatePushTokenRequest(pushToken = token)) // AUTH-03
    suspend fun logout(): Unit = c.delete("/v1/vehicle-devices/me/session") // AUTH-04

    // 일정 · 설정
    suspend fun weeklyPickups(weekStart: String? = null): WeeklyPickups = c.get("/v1/vehicles/me/weekly-pickups", mapOf("weekStart" to weekStart)) // SET-09
    suspend fun settingsSummary(): VehicleSettingsSummary = c.get("/v1/vehicles/me/settings-summary") // SET-10
    suspend fun pickupSpots(academyId: Long? = null): PickupSpots = c.get("/v1/families/me/pickup-spots", mapOf("academyId" to academyId)) // SPOT-01

    // 세션
    suspend fun activeSession(): ActiveSession? = c.get("/v1/pickup-sessions/active") // SES-01 (204 = 없음)
    suspend fun startSession(req: StartPickupRequest, key: String): SessionView = c.post("/v1/pickup-sessions", req, key) // SES-02
    suspend fun session(id: Long): SessionView = c.get("/v1/pickup-sessions/$id") // SES-03
    suspend fun confirmRoute(id: Long, req: ConfirmRouteRequest, key: String): SessionView = c.put("/v1/pickup-sessions/$id/route", req, key) // SES-04
    suspend fun changeSpot(id: Long, req: ChangeSpotRequest, key: String): SessionView = c.post("/v1/pickup-sessions/$id/spot-changes", req, key) // SES-05
    suspend fun holdSession(id: Long, stateVersion: Int, reason: String?, key: String): SessionView =
        c.put("/v1/pickup-sessions/$id/hold", StateVersionRequest(stateVersion, reason), key) // SES-06
    suspend fun releaseSessionHold(id: Long, stateVersion: Int, key: String): SessionView =
        c.delete("/v1/pickup-sessions/$id/hold", mapOf("stateVersion" to stateVersion), key)
    suspend fun cancel(id: Long, stateVersion: Int, reason: String?, key: String): SessionView =
        c.post("/v1/pickup-sessions/$id/cancellation", StateVersionRequest(stateVersion, reason), key) // SES-07
    suspend fun events(id: Long, afterSeq: Long, limit: Int = 100): SessionEventPage =
        c.get("/v1/pickup-sessions/$id/events", mapOf("afterSeq" to afterSeq, "limit" to limit)) // SES-09
    suspend fun contacts(id: Long): Contacts = c.get("/v1/pickup-sessions/$id/contacts") // SES-10
    suspend fun complete(id: Long, req: CompletePickupRequest, key: String): SessionView = c.post("/v1/pickup-sessions/$id/completion", req, key) // SES-11
    suspend fun rejectBoarding(id: Long, req: RejectBoardingRequest, key: String): SessionView =
        c.post("/v1/pickup-sessions/$id/boarding-rejection", req, key) // SES-12
    suspend fun openIncident(id: Long, req: OpenIncidentRequest, key: String): SessionView = c.put("/v1/pickup-sessions/$id/incident", req, key) // SES-13

    // 신호 (백그라운드)
    suspend fun telemetry(id: Long, req: VehicleTelemetryRequest): Unit = c.post("/v1/pickup-sessions/$id/vehicle-telemetry", req) // SIG-01
    suspend fun vehicleEvent(id: Long, req: VehicleEventRequest): Unit = c.post("/v1/pickup-sessions/$id/vehicle-events", req) // SIG-02

    // 합류 호출
    suspend fun holdCall(id: Long, stateVersion: Int, key: String): SessionView = c.put("/v1/pickup-sessions/$id/call-hold", StateVersionRequest(stateVersion), key) // CALL-01
    suspend fun releaseCallHold(id: Long, stateVersion: Int, key: String): SessionView =
        c.delete("/v1/pickup-sessions/$id/call-hold", mapOf("stateVersion" to stateVersion), key)
    suspend fun sendCall(id: Long, stateVersion: Int, key: String): SendCallResponse = c.post("/v1/pickup-sessions/$id/calls", StateVersionRequest(stateVersion), key) // CALL-02 (보호자 확인형)
    suspend fun revokeCall(callId: Long, stateVersion: Int, reason: String?, key: String): SessionView =
        c.post("/v1/calls/$callId/revocation", StateVersionRequest(stateVersion, reason), key) // CALL-03
}
