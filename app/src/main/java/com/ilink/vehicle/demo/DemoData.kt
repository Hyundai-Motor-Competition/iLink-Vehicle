package com.ilink.vehicle.demo

import com.ilink.vehicle.data.model.CallGuard
import com.ilink.vehicle.data.model.CallProgress
import com.ilink.vehicle.data.model.LatLng
import com.ilink.vehicle.data.model.LinkCodeResponse
import com.ilink.vehicle.data.model.PickupSpots
import com.ilink.vehicle.data.model.SessionBoarding
import com.ilink.vehicle.data.model.SessionCall
import com.ilink.vehicle.data.model.SessionChild
import com.ilink.vehicle.data.model.SessionEta
import com.ilink.vehicle.data.model.SessionIncident
import com.ilink.vehicle.data.model.SessionRoute
import com.ilink.vehicle.data.model.SessionSpot
import com.ilink.vehicle.data.model.SessionStatus
import com.ilink.vehicle.data.model.SessionView
import com.ilink.vehicle.data.model.VehicleSettingsSummary
import com.ilink.vehicle.data.model.WeeklyPickups
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 데모 서버와 화면 캡처·미리보기가 함께 쓰는 예시 데이터.
 * 모바일 앱 예시(김현대 보호자, 김현지·김현우, 대치동수학임팩트 학원)와 맞췄다.
 */
object DemoData {
    private val SEOUL = ZoneId.of("Asia/Seoul")

    val HYUNJI = SessionChild(childUserId = 2, name = "김현지", phase = "WAITING")
    val HYUNWOO = SessionChild(childUserId = 1, name = "김현우", phase = "WAITING")

    fun linkCode(now: Instant = Instant.now()) = LinkCodeResponse(
        codeId = 1,
        code = "482915",
        qrPayload = "pickup://link?code=482915",
        expiresAt = now.plusSeconds(300).toString(),
        reissuesLeft = 3,
    )

    /** 이번 주 월~일. 오늘은 김현지 18:10, 김현우 18:30 */
    fun weekly(today: LocalDate = LocalDate.now(SEOUL), weekStart: LocalDate? = null): WeeklyPickups {
        val monday = weekStart ?: today.minusDays((today.dayOfWeek.value - 1).toLong())
        val days = (0L until 7L).map { n ->
            val date = monday.plusDays(n)
            val dow = date.dayOfWeek.value // 1=월
            val pickups = buildList {
                if (dow <= 5 || date == today) add(WeeklyPickups.Pickup(101, "김현지", "대치동수학임팩트 학원", "18:00", "18:10"))
                if (dow in setOf(1, 3, 5) || date == today) add(WeeklyPickups.Pickup(102, "김현우", "대치동수학임팩트 영재반 캠퍼스", "18:20", "18:30"))
            }
            WeeklyPickups.Day(date.toString(), pickups)
        }
        return WeeklyPickups(monday.toString(), days, WeeklyPickups.Readiness(startable = true))
    }

    val settings = VehicleSettingsSummary(
        guardianNameMasked = "김현대",
        children = listOf(VehicleSettingsSummary.Child(2, "김현지"), VehicleSettingsSummary.Child(1, "김현우")),
        joinCallMode = "AUTO",
    )

    val spots = PickupSpots(
        listOf(
            PickupSpots.Academy(
                academyId = 11,
                academyName = "대치동수학임팩트 학원",
                spots = listOf(
                    PickupSpots.Spot(
                        201, "대치동수학임팩트 학원 정문 앞", LatLng(37.4995, 127.0602), LatLng(37.4996, 127.0601),
                        isDefault = true, walkMinutes = 2, etaSeconds = 720, reason = "정차 변동이 작고 2분 이상 걷지 않아도 돼요",
                    ),
                    PickupSpots.Spot(202, "학원 후문 승하차 구역", LatLng(37.4999, 127.0598), LatLng(37.4998, 127.0599), walkMinutes = 3, etaSeconds = 840),
                    PickupSpots.Spot(203, "학원 건너편 횡단보도", LatLng(37.4990, 127.0610), LatLng(37.4992, 127.0607), walkMinutes = 6, etaSeconds = 900),
                ),
            ),
        ),
    )

    // ── 화면 캡처·미리보기용 세션 단계 ──

    private fun base(now: Instant) = SessionView(
        sessionId = 7001,
        stateVersion = 1,
        status = SessionStatus(session = "ACTIVE", vehicle = "ROUTING", call = "NONE", spot = "SELECTED"),
        call = SessionCall(mode = "AUTO", guard = CallGuard(met = false, unmet = listOf("ETA"))),
        children = listOf(HYUNJI),
    )

    /** V02 장소 선택 직후 */
    fun sessionSelecting(now: Instant = Instant.now()) = base(now)

    /** V03 길안내 중 (정차 예상 12분 뒤, 남은 거리 4.2km — 디자인과 같은 값) */
    fun sessionRouting(now: Instant = Instant.now()) = base(now).copy(
        stateVersion = 3,
        status = SessionStatus("ACTIVE", "APPROACHING", "NONE", "CONFIRMED"),
        spot = SessionSpot(201, "대치동수학임팩트 학원 정문 앞", spotVersion = 1, locked = true),
        eta = SessionEta(now.plusSeconds(720).toString(), 720, now.toString()),
        route = SessionRoute(1, 4200),
        call = SessionCall(mode = "AUTO", scheduledAt = now.plusSeconds(540).toString(), guard = CallGuard(met = true)),
    )

    /** V04 합류 안내 전달됨 */
    fun sessionCalled(now: Instant = Instant.now()) = sessionRouting(now).copy(
        stateVersion = 5,
        status = SessionStatus("ACTIVE", "APPROACHING", "DELIVERED", "CONFIRMED"),
        eta = SessionEta(now.plusSeconds(170).toString(), 170, now.toString()),
        route = SessionRoute(1, 650),
        call = SessionCall(
            mode = "AUTO",
            callId = 9001,
            guard = CallGuard(met = true),
            progress = CallProgress(acceptedAt = now.toString(), sentAt = now.toString(), deliveredAt = now.toString()),
        ),
        children = listOf(HYUNJI.copy(phase = "CALLED", callNotBefore = now.toString())),
    )

    /** V05 도어로 탑승 감지 (10초 동안 취소 가능) */
    fun sessionBoarding(now: Instant = Instant.now()) = sessionCalled(now).copy(
        stateVersion = 8,
        status = SessionStatus("ACTIVE", "STOPPED", "ACKNOWLEDGED", "CONFIRMED"),
        boarding = SessionBoarding(now.toString(), now.plusSeconds(10).toString(), "REAR_RIGHT"),
        children = listOf(HYUNJI.copy(phase = "MEETING", acknowledgedAt = now.toString())),
    )

    /** V05 완료 */
    fun sessionCompleted(now: Instant = Instant.now()) = sessionBoarding(now).copy(
        stateVersion = 9,
        status = SessionStatus("COMPLETED", "STOPPED", "ACKNOWLEDGED", "CONFIRMED"),
        children = listOf(HYUNJI.copy(phase = "MEETING", boardedAt = now.toString())),
    )

    /** V06 만날 장소를 쓸 수 없음 */
    fun sessionSpotInvalid(now: Instant = Instant.now()) = sessionRouting(now).copy(
        stateVersion = 6,
        status = SessionStatus("ACTIVE", "APPROACHING", "NONE", "INVALIDATED"),
    )

    /** V06 아이가 타지 않음 */
    fun sessionNotBoarded(now: Instant = Instant.now()) = sessionCalled(now).copy(
        stateVersion = 10,
        status = SessionStatus("ON_HOLD", "STOPPED", "ACKNOWLEDGED", "CONFIRMED"),
        incident = SessionIncident("CHILD_NOT_ON_BOARD", 2, now.toString()),
        children = listOf(HYUNJI.copy(phase = "NOT_BOARDED")),
    )
}
