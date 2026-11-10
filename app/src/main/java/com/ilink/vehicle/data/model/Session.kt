package com.ilink.vehicle.data.model

import kotlinx.serialization.Serializable

// swagger SessionViewResponse. 서버는 주체(차량/보호자/자녀)마다 보이는 필드가 달라 전부 nullable.
@Serializable
data class SessionView(
    val sessionId: Long,
    val stateVersion: Int = 0,
    val status: SessionStatus? = null,
    val spot: SessionSpot? = null,
    val eta: SessionEta? = null,
    val call: SessionCall? = null,
    val children: List<SessionChild> = emptyList(),
    val route: SessionRoute? = null,
    val boarding: SessionBoarding? = null,
    val incident: SessionIncident? = null,
) {
    val isTerminal: Boolean get() = status?.session in TERMINAL

    companion object {
        private val TERMINAL = setOf("COMPLETED", "CANCELLED")
    }
}

@Serializable
data class SessionStatus(
    val session: String? = null, // ACTIVE | ON_HOLD | COMPLETED | CANCELLED
    val vehicle: String? = null, // ROUTING | APPROACHING | STOPPED | DEPARTED
    val call: String? = null,
    val spot: String? = null, // SELECTED | ROUTING | CONFIRMED | INVALIDATED
)

@Serializable
data class SessionSpot(val spotId: Long? = null, val name: String? = null, val spotVersion: Int = 0, val locked: Boolean = false)

@Serializable
data class SessionEta(val stopEtaAt: String? = null, val remainingSeconds: Int? = null, val updatedAt: String? = null, val stale: Boolean = false)

@Serializable
data class SessionCall(
    val mode: String? = null, // AUTO | GUARDIAN_CONFIRM
    val callId: Long? = null,
    val held: Boolean = false,
    val scheduledAt: String? = null,
    val guard: CallGuard? = null,
    val progress: CallProgress? = null,
)

@Serializable
data class CallGuard(val met: Boolean = false, val unmet: List<String> = emptyList())

@Serializable
data class CallProgress(val acceptedAt: String? = null, val sentAt: String? = null, val deliveredAt: String? = null, val acknowledgedAt: String? = null)

@Serializable
data class SessionChild(
    val childUserId: Long,
    val name: String? = null,
    val phase: String? = null, // WAITING | CALLED | MEETING | NOT_BOARDED
    val extraPrepMinutes: Int = 0,
    val callNotBefore: String? = null,
    val acknowledgedAt: String? = null,
    val boardedAt: String? = null,
)

@Serializable
data class SessionRoute(val routeVersion: Int = 0, val remainingMeters: Int? = null)

@Serializable
data class SessionBoarding(val detectedAt: String? = null, val cancelableUntil: String? = null, val door: String? = null)

@Serializable
data class SessionIncident(val type: String? = null, val childUserId: Long? = null, val openedAt: String? = null)

@Serializable
data class ActiveSession(val sessionId: Long, val status: String? = null, val stateVersion: Int = 0)

@Serializable
data class SessionEvent(
    val eventId: Long? = null,
    val seq: Long,
    val type: String,
    val stateVersion: Int? = null,
    val occurredAt: String? = null,
)
