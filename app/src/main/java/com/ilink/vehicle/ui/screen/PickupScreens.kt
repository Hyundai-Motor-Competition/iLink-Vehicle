package com.ilink.vehicle.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.ilink.vehicle.data.model.PickupSpots
import com.ilink.vehicle.data.model.SessionView
import com.ilink.vehicle.ui.UiState
import com.ilink.vehicle.ui.component.AdaptiveScreen
import com.ilink.vehicle.ui.component.Headline
import com.ilink.vehicle.ui.component.PrimaryAction
import com.ilink.vehicle.ui.component.SecondaryAction
import com.ilink.vehicle.ui.component.ButtonTone
import com.ilink.vehicle.ui.component.ChevronIcon
import com.ilink.vehicle.ui.component.ClockIcon
import com.ilink.vehicle.ui.component.Divider
import com.ilink.vehicle.ui.component.InfoIcon
import com.ilink.vehicle.ui.component.Label
import com.ilink.vehicle.ui.component.Pill
import com.ilink.vehicle.ui.component.PinIcon
import com.ilink.vehicle.ui.component.PleosButton
import com.ilink.vehicle.ui.component.PleosCard
import com.ilink.vehicle.ui.component.PleosWindow
import com.ilink.vehicle.ui.component.RouteMap
import com.ilink.vehicle.ui.component.SpotMap
import com.ilink.vehicle.ui.component.StepRow
import com.ilink.vehicle.ui.component.StepState
import com.ilink.vehicle.ui.component.u
import com.ilink.vehicle.ui.component.us
import com.ilink.vehicle.ui.theme.Pleos
import java.time.LocalTime
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")
private fun clock(iso: String?): String = runCatching {
    // 기기 시간대와 상관없이 한국 시간으로 보여 준다 (V02 정차 예상과 같은 기준)
    OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.of("Asia/Seoul")).format(CLOCK)
}.getOrDefault("--:--")

/**
 * V01 오늘의 픽업. 일정은 하루 최대 3개, 필수 설정 누락 시 시작 비활성.
 * assets에 V01 디자인이 아직 없어(V01 파일이 V03과 같음) V00·V02·V03과 같은 카드·버튼 체계로 만들었다.
 */
@Composable
fun V01Today(s: UiState, onStart: (List<Long>) -> Unit, onSettings: () -> Unit) {
    val c = Pleos.colors
    val today = LocalDate.now(SEOUL)
    val days = s.weekly?.days.orEmpty()
    val pickups = days.firstOrNull { it.date == today.toString() }?.pickups.orEmpty()
    val readiness = s.weekly?.readiness
    var selected by remember(pickups) { mutableStateOf(pickups.firstOrNull()?.scheduleId) }
    val chosen = pickups.firstOrNull { it.scheduleId == selected }

    PleosWindow("오늘의 픽업") {
        Row(Modifier.weight(1f)) {
            Column(Modifier.weight(0.62f), verticalArrangement = Arrangement.spacedBy(u(16))) {
                if (readiness?.startable == false) {
                    PleosCard {
                        Text("시작하기 전에 설정이 필요해요", color = c.danger, fontSize = us(22), fontWeight = FontWeight.Bold)
                        Text(readiness.missing.joinToString(" · "), color = c.subText, fontSize = us(19), modifier = Modifier.padding(top = u(6)))
                        Text("휴대전화 앱에서 설정해 주세요", color = c.subText, fontSize = us(17))
                    }
                }
                if (pickups.isEmpty()) {
                    PleosCard(Modifier.fillMaxWidth()) {
                        Text("오늘은 픽업 일정이 없어요", color = c.text, fontSize = us(24), fontWeight = FontWeight.Bold)
                        Text("일정은 휴대전화 앱에서 등록할 수 있어요", color = c.subText, fontSize = us(19), modifier = Modifier.padding(top = u(6)))
                    }
                }
                pickups.forEach { p ->
                    val on = p.scheduleId == selected
                    PleosCard(
                        Modifier.fillMaxWidth()
                            .border(u(2), if (on) c.stepDone else Color.Transparent, RoundedCornerShape(u(22)))
                            .clickable(role = Role.RadioButton) { selected = p.scheduleId },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Label("픽업 시각")
                                Text(p.targetPickupTime ?: p.classEndTime ?: "--:--", color = c.text, fontSize = us(48), fontWeight = FontWeight.Bold)
                                Text(
                                    listOfNotNull(p.childName, p.academyName).joinToString(" · "),
                                    color = c.text, fontSize = us(20), fontWeight = FontWeight.SemiBold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                p.classEndTime?.let { Text("수업 종료 $it", color = c.subText, fontSize = us(17)) }
                            }
                            StepRowMarker(on)
                        }
                    }
                }
            }
            Spacer(Modifier.width(u(26)))
            Column(Modifier.weight(0.38f)) {
                Label("이번 주", Modifier.padding(start = u(4), bottom = u(12)))
                PleosCard(Modifier.fillMaxWidth(), padding = 20) {
                    days.forEach { d ->
                        val date = runCatching { LocalDate.parse(d.date) }.getOrNull()
                        val isToday = date == today
                        Row(Modifier.fillMaxWidth().padding(vertical = u(7)), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                date?.let { "${it.monthValue}/${it.dayOfMonth} (${WEEK_KO[it.dayOfWeek.value - 1]})" } ?: d.date,
                                color = if (isToday) c.stepDone else c.text, fontSize = us(19),
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                if (d.pickups.isEmpty()) "없음" else "${d.pickups.size}건",
                                color = if (d.pickups.isEmpty()) c.subText else c.text, fontSize = us(19),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(u(18)))
        Row {
            PleosButton(
                chosen?.let { "${it.targetPickupTime ?: ""} ${it.childName ?: ""} 픽업 시작".trim() } ?: "픽업 시작",
                enabled = chosen != null && readiness?.startable != false && !s.busy,
                modifier = Modifier.weight(1f),
            ) { chosen?.let { onStart(listOf(it.scheduleId)) } }
            Spacer(Modifier.width(u(20)))
            PleosButton("설정", ButtonTone.Secondary, enabled = !s.drivingRestricted, modifier = Modifier.width(u(300)), onClick = onSettings)
        }
        s.error?.let { Text(it, color = c.danger, fontSize = us(18), modifier = Modifier.padding(top = u(8))) }
    }
}

/** 선택 표시 (라디오) */
@Composable
private fun StepRowMarker(on: Boolean) {
    val c = Pleos.colors
    Box(
        Modifier.size(u(32)).clip(CircleShape)
            .border(u(if (on) 9 else 2), if (on) c.stepDone else c.stepPending, CircleShape),
    )
}

/**
 * V02 추천 픽업 장소 (assets/V02). 대안을 누르면 그 장소가 위로 올라와 길안내 대상이 된다.
 * 내비 적용 성공 후에만 장소 확정 (F1-06, ERR-03).
 */
@Composable
fun V02Recommend(s: UiState, onRoute: (PickupSpots.Spot) -> Unit) {
    val c = Pleos.colors
    val spots = s.spots?.academies?.flatMap { it.spots }.orEmpty().sortedByDescending { it.isDefault }
    var selectedId by remember(spots) { mutableStateOf(spots.firstOrNull()?.spotId) }
    val best = spots.firstOrNull { it.spotId == selectedId } ?: spots.firstOrNull()
    val alts = spots.filter { it.spotId != best?.spotId }

    PleosWindow("추천 픽업 장소") {
        Row(Modifier.weight(1f)) {
            Column(Modifier.weight(771f)) {
                SpotMap(mapLabel(s, best), alts.size, Modifier.fillMaxWidth().height(u(322)))
                Spacer(Modifier.height(u(20)))
                if (best == null) {
                    PleosCard(Modifier.fillMaxWidth()) {
                        Text("안전하게 만날 장소가 없어요", color = c.text, fontSize = us(26), fontWeight = FontWeight.Bold)
                        Text("휴대전화 앱에서 픽업 장소를 등록해 주세요", color = c.subText, fontSize = us(19))
                    }
                } else {
                    PleosCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                best.name ?: "-", color = c.text, fontSize = us(26), fontWeight = FontWeight.Bold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                            )
                            // SPOT-01은 보호자가 승인한 장소만 준다
                            Pill("승인됨", accent = true)
                        }
                        best.etaSeconds?.let { eta ->
                            InfoLine({ ClockIcon(u(30), c.subText) }, "정차 예상", "약 ${(eta + 59) / 60}분 후 도착 · ${clockIn(eta)}")
                        }
                        best.walkMinutes?.let { w ->
                            if (best.reason == null) InfoLine({ InfoIcon(u(30), c.subText) }, "아이 보행 ${w}분", null)
                        }
                        best.reason?.let { InfoLine({ InfoIcon(u(30), c.subText) }, it, null) }
                    }
                }
                s.error?.let { Text(it, color = c.danger, fontSize = us(18), modifier = Modifier.padding(top = u(10))) }
            }
            Spacer(Modifier.width(u(34)))
            Column(Modifier.weight(460f)) {
                Label("대안 장소", Modifier.padding(start = u(4), top = u(4), bottom = u(14)))
                if (s.drivingRestricted) Text("주행 중에는 다른 장소를 고를 수 없어요", color = c.subText, fontSize = us(17), modifier = Modifier.padding(bottom = u(10)))
                if (alts.isEmpty()) Text("다른 후보가 없어요", color = c.subText, fontSize = us(19), modifier = Modifier.padding(start = u(4)))
                alts.forEach { alt ->
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = u(18)).clip(RoundedCornerShape(u(22))).background(c.card)
                            .clickable(enabled = !s.drivingRestricted, role = Role.Button) { selectedId = alt.spotId }
                            .padding(horizontal = u(26), vertical = u(20)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(u(56)).clip(CircleShape).background(c.iconCircle), contentAlignment = Alignment.Center) {
                            PinIcon(u(30), c.text)
                        }
                        Spacer(Modifier.width(u(22)))
                        Column(Modifier.weight(1f)) {
                            Text(alt.name ?: "-", color = c.text, fontSize = us(22), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(
                                    alt.etaSeconds?.let { "약 ${(it + 59) / 60}분 후 도착" },
                                    alt.walkMinutes?.let { "보행 ${it}분" },
                                ).joinToString(" · ").ifEmpty { "-" },
                                color = c.subText, fontSize = us(18),
                            )
                        }
                        ChevronIcon(u(34), c.subText)
                    }
                }
            }
        }
        Spacer(Modifier.height(u(18)))
        PleosButton("이 장소로 길안내", enabled = best != null && !s.busy) { best?.let(onRoute) }
    }
}

/** 지도 위 짧은 이름: 학원 이름을 떼고 '정문 앞'이면 '정문 앞 승차 지점' */
private fun mapLabel(s: UiState, spot: PickupSpots.Spot?): String {
    val name = spot?.name ?: return "승차 지점"
    val academy = s.spots?.academies?.firstOrNull { a -> a.spots.any { it.spotId == spot.spotId } }?.academyName
    val short = academy?.let { name.removePrefix(it).trim() }?.ifEmpty { null } ?: name
    return if (short.endsWith("앞")) "$short 승차 지점" else short
}

@Composable
private fun InfoLine(icon: @Composable () -> Unit, label: String, value: String?) {
    val c = Pleos.colors
    Row(Modifier.fillMaxWidth().padding(top = u(20)), verticalAlignment = Alignment.CenterVertically) {
        icon()
        Spacer(Modifier.width(u(18)))
        Text(label, color = c.subText, fontSize = us(20), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        value?.let { Text(it, color = c.text, fontSize = us(22), fontWeight = FontWeight.Bold) }
    }
}

/** V03 픽업 진행·예측 (assets/V03, 주행 중 사용 가능 DO). 주행 중 조작은 '자동 호출 보류' 1탭만 */
@Composable
fun V03Progress(s: UiState, onHold: () -> Unit, onRelease: () -> Unit) {
    val v = s.session ?: return
    val c = Pleos.colors
    val call = v.call
    val child = v.children.firstOrNull()
    val remainMin = v.eta?.remainingSeconds?.let { (it + 59) / 60 }
    val meters = v.route?.remainingMeters

    PleosWindow("픽업 진행 중") {
        Row(Modifier.weight(1f)) {
            Column(Modifier.weight(584f)) {
                // 정차 예상
                PleosCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Label("정차 예상")
                            Text(
                                if (v.eta?.stale == true) "--:--" else clock(v.eta?.stopEtaAt),
                                color = c.text, fontSize = us(76), fontWeight = FontWeight.Bold,
                                // 큰 숫자는 줄 높이를 글자 크기에 맞춰 카드가 디자인보다 커지지 않게
                                style = TextStyle(lineHeight = us(80), platformStyle = PlatformTextStyle(includeFontPadding = false)),
                            )
                            Text(
                                when {
                                    v.eta?.stale == true -> "도착 시간을 다시 확인하고 있어요"
                                    remainMin != null -> "약 ${remainMin}분 남음"
                                    else -> "길안내를 시작하면 알려줘요"
                                },
                                color = c.subText, fontSize = us(19),
                            )
                        }
                        if (v.eta != null) Pill(if (v.eta.stale) "확인 중" else "예측 안정", accent = !v.eta.stale)
                    }
                }
                Spacer(Modifier.height(u(18)))
                // 확정 장소 · 합류 안내
                PleosCard(Modifier.fillMaxWidth()) {
                    Text("확정 장소", color = c.text, fontSize = us(22), fontWeight = FontWeight.Bold)
                    Text(v.spot?.name ?: "-", color = c.subText, fontSize = us(19), modifier = Modifier.padding(top = u(2), bottom = u(12)))
                    Divider()
                    val (callTitle, callSub) = callTexts(v)
                    Text(callTitle, color = c.text, fontSize = us(22), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = u(12)))
                    Text(callSub, color = c.subText, fontSize = us(19), modifier = Modifier.padding(top = u(2)))
                }
                Spacer(Modifier.height(u(18)))
                // 아이 준비 상태
                PleosCard(Modifier.fillMaxWidth(), padding = 22) {
                    val more = if (v.children.size > 1) " · ${child?.name ?: "아이"} 외 ${v.children.size - 1}명" else ""
                    Label("아이 준비 상태$more", Modifier.padding(bottom = u(6)))
                    StepRow("준비 알림 발송", null, StepState.Done)
                    val prep = child?.extraPrepMinutes?.takeIf { it > 0 }?.let { "준비 시간 +${it}분 요청" }
                    StepRow("실내에서 준비 중", prep, StepState.Current)
                    StepRow(
                        "합류 안내 자동 발송",
                        when {
                            call?.held == true -> "보류 중"
                            call?.scheduledAt != null -> "${clock(call.scheduledAt)} 예정"
                            else -> "도착 3분 전"
                        },
                        StepState.Pending,
                    )
                }
                Spacer(Modifier.weight(1f).height(u(18)))
                if (call?.held == true) PleosButton("자동 호출 보류 해제", ButtonTone.Secondary, onClick = onRelease)
                else PleosButton("자동 호출 보류", ButtonTone.Secondary, onClick = onHold)
            }
            Spacer(Modifier.width(u(25)))
            RouteMap(
                listOfNotNull("길안내 중", meters?.let { "남은 거리 ${distance(it)}" }).joinToString(" · "),
                Modifier.weight(633f).fillMaxHeight(),
            )
        }
        // 디자인 V03은 아래 여백이 V02보다 크다 (창 아래 47)
        Spacer(Modifier.height(u(23)))
    }
}

private fun callTexts(v: SessionView): Pair<String, String> {
    val c = v.call
    return when {
        c?.held == true -> "합류 안내 보류 중" to "보류를 풀면 다시 자동으로 보내요"
        c?.mode == "GUARDIAN_CONFIRM" -> "합류 안내 보호자 확인" to "도착 3분 전에 보호자 확인 후 보내요"
        c?.scheduledAt != null -> "합류 안내 자동 발송" to "도착 3분 전 또는 700m · ${clock(c.scheduledAt)} 예정"
        else -> "합류 안내 자동 발송" to "도착 3분 전 또는 700m에 보내요"
    }
}

private fun distance(m: Int) = if (m >= 1000) "%.1fkm".format(m / 1000.0) else "${m}m"

private val SEOUL = ZoneId.of("Asia/Seoul")
private val WEEK_KO = listOf("월", "화", "수", "목", "금", "토", "일")
private fun clockIn(seconds: Int): String = LocalTime.now(SEOUL).plusSeconds(seconds.toLong()).format(CLOCK)

/** V04 정차·호출 상태. 주행 중에는 읽기와 '보류'만 (QA-28) */
@Composable
fun V04Call(s: UiState, onHold: () -> Unit, onRelease: () -> Unit) {
    val v = s.session ?: return
    val p = v.call?.progress
    AdaptiveScreen("V04", "합류 안내", secondary = { ChildList(v) }) {
        Headline(
            when {
                p?.acknowledgedAt != null -> "아이가 확인했어요"
                p?.deliveredAt != null -> "아이에게 전달됐어요"
                p?.sentAt != null -> "보냈어요"
                else -> "보내는 중"
            },
        )
        Text("차량 ${v.status?.vehicle ?: "-"}", style = MaterialTheme.typography.bodyLarge)
        HoldButton(v, onHold, onRelease)
    }
}

/** V05 픽업 완료. 도어 신호가 없으면 정차 중 수동 완료 (ERR-14) */
@Composable
fun V05Complete(s: UiState, onReject: (Long?) -> Unit, onManual: () -> Unit, onDone: () -> Unit) {
    val v = s.session ?: return
    AdaptiveScreen("V05", "픽업 완료") {
        when {
            v.status?.session == "COMPLETED" -> {
                Headline("픽업을 완료했어요")
                PrimaryAction("확인", onClick = onDone)
            }
            v.boarding?.detectedAt != null -> {
                Headline("탑승을 확인했어요", "${clock(v.boarding.cancelableUntil)}까지 취소할 수 있어요")
                val single = v.children.singleOrNull()?.childUserId
                SecondaryAction("아이가 타지 않았어요", enabled = !s.busy) { onReject(single) }
            }
            !s.doorSignalAvailable -> {
                Text("문 열림 신호를 받을 수 없어요", style = MaterialTheme.typography.bodyLarge)
                PrimaryAction("탑승 확인 및 완료", enabled = !s.drivingRestricted && !s.busy, onClick = onManual)
            }
            else -> Text("아이가 타면 자동으로 확인해요", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** V06 예외·복구: 오류 코드보다 다음 안전 행동을 먼저 */
@Composable
fun V06Exception(s: UiState, onCancel: () -> Unit) {
    val v = s.session ?: return
    AdaptiveScreen("V06", "확인이 필요해요", secondary = { ChildList(v) }) {
        Text(
            when {
                v.incident?.type == "CHILD_NOT_ON_BOARD" -> "아이가 아직 타지 않았어요"
                v.status?.spot == "INVALIDATED" -> "만날 장소를 쓸 수 없어요. 아이는 안전한 곳에서 기다려요"
                else -> "픽업을 잠시 멈췄어요"
            },
            style = MaterialTheme.typography.titleMedium,
        )
        Text("아이에게 직접 연락해 주세요", style = MaterialTheme.typography.bodyLarge)
        SecondaryAction("픽업 취소", enabled = !s.drivingRestricted && !s.busy, onClick = onCancel)
    }
}

@Composable
private fun HoldButton(v: SessionView, onHold: () -> Unit, onRelease: () -> Unit) {
    if (v.call?.held == true) SecondaryAction("보류 해제", onClick = onRelease) else SecondaryAction("합류 안내 보류", onClick = onHold)
}

@Composable
private fun ChildList(v: SessionView) {
    v.children.forEach { c ->
        val extra = if (c.extraPrepMinutes > 0) " · +${c.extraPrepMinutes}분" else ""
        Text("${c.name ?: "아이"}  ${phaseLabel(c.phase)}$extra", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth())
    }
}

private fun phaseLabel(phase: String?) = when (phase) {
    "WAITING" -> "실내 대기"
    "CALLED" -> "합류 안내 받음"
    "MEETING" -> "만나는 중"
    "NOT_BOARDED" -> "탑승 안 함"
    else -> "-"
}

private fun callLine(v: SessionView): String {
    val c = v.call ?: return "합류 안내 준비 중"
    return when {
        c.held -> "합류 안내를 보류했어요"
        c.guard?.met == false && c.scheduledAt == null -> "합류 안내 대기 중"
        c.scheduledAt != null -> "${clock(c.scheduledAt)} 합류 안내 자동 발송 예정"
        else -> "도착 3분 전에 합류 안내를 보내요"
    }
}
