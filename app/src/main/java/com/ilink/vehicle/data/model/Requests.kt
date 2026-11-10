package com.ilink.vehicle.data.model

import kotlinx.serialization.Serializable

@Serializable data class LatLng(val lat: Double, val lng: Double)

@Serializable data class RegisterVehicleDeviceRequest(val model: String, val vin: String? = null)
@Serializable data class VehicleDeviceResponse(val deviceId: Long? = null, val vehicleId: Long? = null, val deviceToken: String)

@Serializable data class LinkCodeResponse(val codeId: Long, val code: String, val qrPayload: String, val expiresAt: String, val reissuesLeft: Int? = null)

@Serializable
data class LinkStatusResponse(
    val status: String, // WAITING | LINKED | RELINKED | EXPIRED
    val vehicleId: Long? = null,
    val familyId: Long? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val pollAfterSeconds: Int? = null,
)

@Serializable data class RefreshTokenRequest(val refreshToken: String)
@Serializable data class TokenResponse(val accessToken: String, val refreshToken: String, val accessTokenExpiresAt: String? = null)
@Serializable data class UpdatePushTokenRequest(val platform: String = "VEHICLE", val pushToken: String)

@Serializable data class WeeklyPickups(val weekStart: String? = null, val days: List<Day> = emptyList(), val readiness: Readiness? = null) {
    @Serializable data class Day(val date: String, val pickups: List<Pickup> = emptyList())
    @Serializable data class Pickup(val scheduleId: Long, val childName: String? = null, val academyName: String? = null, val classEndTime: String? = null, val targetPickupTime: String? = null)
    @Serializable data class Readiness(val startable: Boolean = false, val missing: List<String> = emptyList())
}

@Serializable data class VehicleSettingsSummary(val guardianNameMasked: String? = null, val children: List<Child> = emptyList(), val joinCallMode: String? = null) {
    @Serializable data class Child(val userId: Long, val name: String? = null)
}

@Serializable data class PickupSpots(val academies: List<Academy> = emptyList()) {
    @Serializable data class Academy(val academyId: Long, val academyName: String? = null, val spots: List<Spot> = emptyList())
    @Serializable data class Spot(
        val spotId: Long,
        val name: String? = null,
        val stopPoint: LatLng? = null,
        val meetingPoint: LatLng? = null,
        val isDefault: Boolean = false,
        val walkMinutes: Int? = null,
        // 기획서 API-04 recommend-spot 결과(정차 예상 · 추천 이유). Swagger SPOT-01에는 아직 없어 없으면 화면에서 숨긴다
        val etaSeconds: Int? = null,
        val reason: String? = null,
    )
}

@Serializable data class StartPickupRequest(val scheduleIds: List<Long>, val date: String)
@Serializable data class ConfirmRouteRequest(val spotId: Long, val navRouteId: String? = null, val stateVersion: Int)
@Serializable data class ChangeSpotRequest(val spotId: Long, val stateVersion: Int, val spotVersion: Int)
@Serializable data class StateVersionRequest(val stateVersion: Int, val reason: String? = null)
@Serializable data class CompletePickupRequest(val mode: String, val stateVersion: Int, val notBoardedChildUserIds: List<Long>? = null)
@Serializable data class RejectBoardingRequest(val stateVersion: Int, val childUserId: Long? = null)
@Serializable data class OpenIncidentRequest(val type: String, val childUserId: Long, val stateVersion: Int)

@Serializable
data class VehicleTelemetryRequest(
    val observedAt: String,
    val navEtaSeconds: Int,
    val remainingMeters: Int,
    val location: LatLng? = null,
    val headingDeg: Int? = null,
    val speedKmh: Double? = null,
    val gear: String? = null, // P | R | N | D
)

@Serializable
data class VehicleEventRequest(
    val eventId: String,
    val type: String, // STOPPED | DEPARTED | STOP_FAILED | DOOR_CYCLE
    val occurredAt: String,
    val location: LatLng? = null,
    val door: String? = null, // FRONT_RIGHT | REAR_LEFT | REAR_RIGHT
)

@Serializable data class SendCallResponse(val callId: Long? = null, val status: String? = null)
@Serializable data class Contacts(val items: List<Contact> = emptyList()) {
    @Serializable data class Contact(val name: String? = null, val role: String? = null, val phone: String? = null)
}
@Serializable data class SessionEventPage(val items: List<SessionEvent> = emptyList(), val hasMore: Boolean = false)
