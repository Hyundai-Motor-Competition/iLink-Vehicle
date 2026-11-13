package com.ilink.vehicle.domain

import com.ilink.vehicle.data.model.SessionView
import com.ilink.vehicle.data.model.WeeklyPickups
import com.ilink.vehicle.platform.voice.Banner
import com.ilink.vehicle.platform.voice.VoiceCue
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 세션이 바뀔 때 어떤 음성 안내(U01~U08)를 낼지 정한다.
 * 화면과 상관없이 상태 변화만 보고 판단하고, 같은 사건은 eventKey로 한 번만 말한다.
 */
object VoiceCues {
    data class Cue(val banner: Banner, val key: String)

    private val SEOUL = ZoneId.of("Asia/Seoul")
    private val HHMM = DateTimeFormatter.ofPattern("HH:mm")

    /** 서버가 실내 준비 알림을 보내는 기준: 도착 3분 전 또는 700m */
    const val PREPARE_SECONDS = 180
    const val PREPARE_METERS = 700

    /** 도착 3분 전 또는 700m 안 (서버의 실내 준비 알림 기준) */
    fun isNear(v: SessionView): Boolean {
        val left = v.eta?.remainingSeconds
        val meters = v.route?.remainingMeters
        return (left != null && left <= PREPARE_SECONDS) || (meters != null && meters <= PREPARE_METERS)
    }

    /** U01 오늘 픽업 일정 안내 (V01에 들어올 때 하루 한 번) */
    fun today(date: String, pickups: List<WeeklyPickups.Pickup>): Cue? {
        val first = pickups.firstOrNull() ?: return null
        val more = if (pickups.size > 1) " 외 ${pickups.size - 1}건" else ""
        return Cue(
            Banner(
                VoiceCue.U01,
                "오늘 ${first.classEndTime ?: first.targetPickupTime ?: ""} 픽업 일정$more".replace("  ", " "),
                listOfNotNull(first.academyName, first.childName).joinToString(" · "),
            ),
            "U01-$date",
        )
    }

    /** 이전 → 다음 세션 상태에서 새로 생긴 사건 */
    fun onSession(prev: SessionView?, next: SessionView): List<Cue> {
        val id = next.sessionId
        val out = mutableListOf<Cue>()
        val wasConfirmed = prev?.status?.spot == "CONFIRMED"
        val confirmed = next.status?.spot == "CONFIRMED"

        // U02 길안내 시작 (장소를 바꿔 다시 길안내해도 한 번 더)
        if (confirmed && !wasConfirmed) {
            out += Cue(
                Banner(VoiceCue.U02, "${next.spot?.name ?: "픽업 장소"}으로 길안내 시작", "다른 장소 선택은 정차 후"),
                "U02-$id-${next.spot?.spotId}-${next.spot?.spotVersion}",
            )
        }
        // U03 출발 알림: 길안내 뒤 첫 도착 예정 시각이 나오면 자녀에게 전달된 것으로 본다
        if (confirmed && next.eta?.stopEtaAt != null && (prev?.eta?.stopEtaAt == null || !wasConfirmed)) {
            out += Cue(
                Banner(VoiceCue.U03, "출발 알림 발송 접수", "${clock(next.eta.stopEtaAt)} 도착 예정 전달 · 수신·열람은 별도 표시"),
                "U03-$id-${next.spot?.spotVersion}",
            )
        }
        // U04 합류 안내 예고: 도착 3분 전(또는 700m)에 자녀에게 실내 준비 알림이 가고,
        // 합류 안내는 정차하면 보낸다 (가드 G6, 2026.11.13~). 아직 안 보냈고 보류도 아닐 때 한 번
        val call = next.call
        if (confirmed && call != null && call.callId == null && !call.held && call.mode != "GUARDIAN_CONFIRM" &&
            next.status?.vehicle != "STOPPED" && isNear(next) && !(prev != null && isNear(prev) && prev.status?.spot == "CONFIRMED")
        ) {
            out += Cue(Banner(VoiceCue.U04, "곧 도착 · 자녀에게 준비 알림", "정차하면 합류 안내를 보내요. '보류'라고 말하면 보내지 않아요"), "U04-$id-${next.spot?.spotVersion}")
        }
        // U05 합류 안내 발송 결과
        if (call?.callId != null && prev?.call?.callId == null) {
            out += Cue(
                Banner(
                    VoiceCue.U05, "합류 안내 자동 발송됨",
                    if (call.progress?.acknowledgedAt != null) "자녀가 안내를 확인했어요" else "자녀 열람 전 · 이동 확정 아님",
                ),
                "U05-$id-${call.callId}",
            )
        }
        // U06 예외
        val incident = next.incident?.type ?: if (next.status?.spot == "INVALIDATED") "SPOT_INVALIDATED" else null
        val prevIncident = prev?.incident?.type ?: if (prev?.status?.spot == "INVALIDATED") "SPOT_INVALIDATED" else null
        if (incident != null && incident != prevIncident) {
            val b = when (incident) {
                "CHILD_NOT_ON_BOARD" -> Banner(VoiceCue.U06, "아이 미탑승 확인", "보호자에게 알렸어요")
                else -> Banner(VoiceCue.U06, "안전한 정차 미확인", "자녀 실내 대기 안내됨")
            }
            out += Cue(b, "U06-$id-$incident-${next.incident?.openedAt}")
        }
        // U07 픽업 완료
        if (next.status?.session == "COMPLETED" && prev?.status?.session != "COMPLETED") {
            out += Cue(Banner(VoiceCue.U07, "탑승 자동 인식 · 완료", "위치 공유 종료"), "U07-$id")
        }
        return out
    }

    /** U08 '지금 픽업 상태 어때?'에 대한 대답 */
    fun status(v: SessionView?, query: String): Banner {
        if (v == null || v.isTerminal) return Banner(VoiceCue.U08, "진행 중인 픽업이 없어요", "오늘 일정은 화면에서 확인할 수 있어요", query)
        val spot = v.spot?.name?.let { shortSpot(it) }
        val title = when {
            v.incident != null || v.status?.session == "ON_HOLD" -> "픽업 잠시 멈춤 · 확인 필요"
            v.boarding?.detectedAt != null -> "탑승 확인 · 곧 완료"
            v.status?.vehicle == "STOPPED" -> "정차 중 · 아이를 기다리는 중"
            v.eta?.stopEtaAt != null -> "픽업 진행 중 · ${clock(v.eta.stopEtaAt)} 정차 예상"
            else -> "픽업 장소를 고르는 중"
        }
        val sub = when {
            v.call?.held == true -> "합류 안내 보류 중"
            spot != null -> "$spot 승차 지점"
            else -> null
        }
        return Banner(VoiceCue.U08, title, sub, query)
    }

    /** '대치동수학임팩트 학원 정문 앞' → '정문 앞' */
    private fun shortSpot(name: String): String {
        val i = name.indexOf("학원 ")
        return if (i >= 0) name.substring(i + 3).trim().ifEmpty { name } else name
    }

    fun clock(iso: String?): String = runCatching {
        OffsetDateTime.parse(iso).atZoneSameInstant(SEOUL).format(HHMM)
    }.getOrDefault("--:--")
}
