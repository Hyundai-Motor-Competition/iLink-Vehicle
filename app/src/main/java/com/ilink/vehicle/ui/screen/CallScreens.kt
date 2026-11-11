package com.ilink.vehicle.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.ilink.vehicle.data.model.PickupSpots
import com.ilink.vehicle.data.model.SessionView
import com.ilink.vehicle.ui.UiState
import com.ilink.vehicle.ui.component.ButtonTone
import com.ilink.vehicle.ui.component.CarIcon
import com.ilink.vehicle.ui.component.ChevronIcon
import com.ilink.vehicle.ui.component.IconCircle
import com.ilink.vehicle.ui.component.InfoIcon
import com.ilink.vehicle.ui.component.Label
import com.ilink.vehicle.ui.component.PinIcon
import com.ilink.vehicle.ui.component.PleosButton
import com.ilink.vehicle.ui.component.PleosCard
import com.ilink.vehicle.ui.component.PleosWindow
import com.ilink.vehicle.ui.component.SendIcon
import com.ilink.vehicle.ui.component.SpinnerIcon
import com.ilink.vehicle.ui.component.StatusCard
import com.ilink.vehicle.ui.component.StepRow
import com.ilink.vehicle.ui.component.StepState
import com.ilink.vehicle.ui.component.WarningIcon
import com.ilink.vehicle.ui.component.u
import com.ilink.vehicle.ui.component.us
import com.ilink.vehicle.ui.theme.Pleos
import kotlinx.coroutines.delay
import java.time.Instant

/*
 * V04 합류 진행 상태 · V05 탑승 자동 확인 · V06 안전 확인 필요 (assets/V04~V06 Driving · Parking).
 * 주행 중에는 읽기와 1탭 조작만, 정차 중에만 고르기·완료·취소 같은 조작을 연다.
 */

private const val GAP = 19

/** V04 합류 진행 상태. 디자인은 주행 중만 있어 정차 중에도 같은 틀을 쓰고 정차 전용 버튼만 더한다 */
@Composable
fun V04Call(s: UiState, onHold: () -> Unit, onRelease: () -> Unit, onManual: () -> Unit = {}, onContact: () -> Unit = {}) {
    val v = s.session ?: return
    val c = Pleos.colors
    val call = v.call
    val p = call?.progress
    val stopped = v.status?.vehicle == "STOPPED"
    val leftMin = v.eta?.remainingSeconds?.let { (it + 59) / 60 }

    PleosWindow("합류 진행 상태") {
        when {
            stopped && !s.doorSignalAvailable -> StatusCard("문 열림 신호를 받을 수 없어요", "아이가 타면 직접 완료해 주세요") {
                IconCircle(c.iconCircle) { InfoIcon(it, c.text) }
            }
            stopped -> StatusCard("정차를 확인했어요", "아이가 타면 자동으로 확인해요") { IconCircle(c.meet) { CarIcon(it, Color.White) } }
            call?.held == true -> StatusCard("합류 안내를 보류했어요", "보류를 풀면 바로 보내요") { IconCircle(c.iconCircle) { SpinnerIcon(it, c.text) } }
            call?.callId != null -> StatusCard(
                if (call.mode == "GUARDIAN_CONFIRM") "보호자 확인 후 합류 안내를 보냈어요" else "합류 안내를 자동으로 보냈어요",
                listOfNotNull(
                    leftMin?.takeIf { it > 0 }?.let { "도착 ${it}분 전" },
                    if (p?.acknowledgedAt != null) "아이가 만남 장소로 이동 중" else "자녀 열람 전",
                ).joinToString(" · "),
            ) { IconCircle(c.meet) { SendIcon(it, Color.White) } }
            else -> StatusCard("합류 안내를 준비하고 있어요", "도착 3분 전에 자동으로 보내요") { IconCircle(c.iconCircle) { SpinnerIcon(it, c.text) } }
        }
        Spacer(Modifier.height(u(GAP)))
        PleosCard(Modifier.fillMaxWidth(), padding = 22) {
            StepRow(
                if (p?.sentAt != null) "합류 안내 자동 발송 · ${clock(p.sentAt)}" else "합류 안내 자동 발송",
                if (p?.sentAt == null) "도착 3분 전 또는 700m에 보내요" else null,
                if (p?.sentAt != null) StepState.Done else StepState.Current, green = true, markSize = 34,
            )
            StepRow(
                if (p?.acknowledgedAt != null) "자녀 열람 · 만남 장소로 이동 중" else "자녀 열람",
                if (p?.acknowledgedAt == null && p?.sentAt != null) "열람하면 바로 알려 드려요" else null,
                when {
                    p?.acknowledgedAt != null -> StepState.Done
                    p?.sentAt != null -> StepState.Current
                    else -> StepState.Pending
                },
                green = true, markSize = 34,
            )
            StepRow(
                if (stopped) "정차 확인 · 아이를 기다리는 중" else "정차 확인",
                if (stopped) null else "도착하면 차량 신호 + 장소 일치로 확인",
                if (stopped) StepState.Done else StepState.Pending, green = true, markSize = 34,
            )
        }
        Spacer(Modifier.weight(1f))
        s.error?.let { ErrorLine(it) }
        if (!s.driving && stopped && !s.doorSignalAvailable) {
            PleosButton("탑승 확인 및 완료", enabled = !s.busy, onClick = onManual)
            Spacer(Modifier.height(u(GAP)))
        }
        if (!s.driving && stopped) {
            PleosButton("직접 연락", ButtonTone.Secondary, onClick = onContact)
        } else if (call?.held == true) {
            PleosButton("호출 보류 해제", ButtonTone.Secondary, onClick = onRelease)
        } else {
            PleosButton("호출 보류", ButtonTone.Secondary, enabled = !s.busy, onClick = onHold)
        }
    }
}

/**
 * V05 탑승 자동 확인. 문이 열렸다 닫히면 탑승으로 보고 10초 뒤 자동 완료 (그 사이 '아이가 타지 않았어요'로 취소).
 * 주행 중: assets/V05 Driving (완료 카드), 정차 중: assets/V05 Parking (합류 상태 + 지금 완료).
 */
@Composable
fun V05Complete(s: UiState, onReject: (Long?) -> Unit, onComplete: () -> Unit, onDone: () -> Unit) {
    val v = s.session ?: return
    val c = Pleos.colors
    val completed = v.status?.session == "COMPLETED"
    val left = secondsUntil(v.boarding?.cancelableUntil)
    val single = v.children.singleOrNull()?.childUserId

    PleosWindow("탑승 자동 확인") {
        if (s.driving) {
            StatusCard(
                if (completed) "픽업을 완료했어요" else "탑승을 확인했어요",
                if (completed) "탑승 자동 인식 · 위치 공유 종료" else "${left}초 후 자동으로 완료돼요",
            ) { IconCircle(c.meet) { CarIcon(it, Color.White) } }
            Spacer(Modifier.weight(1f))
            s.error?.let { ErrorLine(it) }
            PleosButton(if (completed) "완료됨" else "${left}초 후 자동 완료", ButtonTone.Secondary, enabled = false) {}
            Spacer(Modifier.height(u(GAP)))
            PleosButton("아이가 타지 않았어요", ButtonTone.Secondary, enabled = !completed && !s.busy) { onReject(single) }
        } else {
            Text(
                if (completed) "픽업을 완료했어요" else "탑승을 확인했어요",
                color = c.text, fontSize = us(34), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = u(4)),
            )
            Text(
                if (completed) "위치 공유를 종료했어요. 안전하게 출발하세요."
                else "문이 열렸다 닫혀 탑승으로 인식했어요. ${left}초 후 자동으로 완료돼요.",
                color = c.subText, fontSize = us(20), modifier = Modifier.padding(top = u(10), bottom = u(22)),
            )
            PleosCard(Modifier.fillMaxWidth(), padding = 22) {
                Label("합류 상태", Modifier.padding(bottom = u(6)))
                val p = v.call?.progress
                StepRow("합류 안내 자동 발송", p?.sentAt?.let { clock(it) }, if (p?.sentAt != null) StepState.Done else StepState.Pending, green = true, markSize = 34)
                StepRow("자녀 열람", p?.acknowledgedAt?.let { clock(it) }, if (p?.acknowledgedAt != null) StepState.Done else StepState.Pending, green = true, markSize = 34)
                StepRow(
                    "탑승 자동 인식 · ${clock(v.boarding?.detectedAt)}",
                    "문 열림→닫힘 (운전석 제외)",
                    if (completed) StepState.Done else StepState.Current, green = true, markSize = 34,
                )
            }
            Spacer(Modifier.weight(1f))
            s.error?.let { ErrorLine(it) }
            if (completed) {
                PleosButton("확인", onClick = onDone)
            } else {
                PleosButton("지금 완료 (${left}초 후 자동)", enabled = !s.busy, onClick = onComplete)
                Spacer(Modifier.height(u(GAP)))
                PleosButton("아이가 타지 않았어요", ButtonTone.Secondary, enabled = !s.busy) { onReject(single) }
            }
        }
    }
}

/**
 * V06 안전 확인 필요: 오류 코드보다 아이가 지금 어떻게 하고 있는지와 다음 행동을 먼저.
 * 주행 중: 직접 연락만, 대안은 정차 후. 정차 중: 승인된 대안 · 직접 연락 · 보류·취소.
 */
@Composable
fun V06Exception(
    s: UiState,
    onAlternative: (PickupSpots.Spot) -> Unit = {},
    onContact: () -> Unit = {},
    onCancel: () -> Unit = {},
    onResume: () -> Unit = {},
) {
    val v = s.session ?: return
    val c = Pleos.colors
    val notBoarded = v.incident?.type == "CHILD_NOT_ON_BOARD"
    val alts = alternatives(s, v)
    var pick by remember(alts) { mutableStateOf(alts.firstOrNull()?.spotId) }
    val chosen = alts.firstOrNull { it.spotId == pick }

    PleosWindow("안전 확인 필요") {
        StatusCard(
            if (notBoarded) "아이가 타지 않았어요" else "안전하게 정차할 수 없어요",
            when {
                notBoarded -> "보호자에게 알리고 픽업을 잠시 멈췄어요"
                s.driving -> "자녀에게 실내 대기를 안내했어요"
                else -> "자녀에게는 실내 대기를 안내했어요"
            },
        ) { IconCircle(c.danger) { WarningIcon(it, Color.White) } }

        if (s.driving) {
            Spacer(Modifier.height(u(GAP)))
            if (notBoarded) {
                StatusCard("다시 확인은 정차 후에 할 수 있어요", "아이와 직접 연락해 주세요") { IconCircle(c.iconCircle) { InfoIcon(it, c.text) } }
            } else {
                StatusCard("대안 장소는 정차 후 선택할 수 있어요", "그 전까지 자녀는 실내에서 기다려요") { IconCircle(c.iconCircle) { InfoIcon(it, c.text) } }
            }
            Spacer(Modifier.weight(1f))
            s.error?.let { ErrorLine(it) }
            PleosButton("직접 연락", onClick = onContact)
        } else if (notBoarded) {
            Spacer(Modifier.weight(1f))
            s.error?.let { ErrorLine(it) }
            PleosButton("다시 기다리기", enabled = !s.busy, onClick = onResume)
            Spacer(Modifier.height(u(GAP)))
            PleosButton("직접 연락", ButtonTone.Secondary, onClick = onContact)
            Spacer(Modifier.height(u(GAP)))
            PleosButton("픽업 취소", ButtonTone.Secondary, enabled = !s.busy, onClick = onCancel)
        } else {
            Label("승인된 대안", Modifier.padding(top = u(24), bottom = u(12)))
            if (alts.isEmpty()) {
                Text("다른 승인된 장소가 없어요", color = c.subText, fontSize = us(19))
            }
            alts.forEach { alt ->
                AlternativeRow(alt, selected = alts.size > 1 && alt.spotId == pick) { pick = alt.spotId }
                Spacer(Modifier.height(u(12)))
            }
            Spacer(Modifier.weight(1f))
            s.error?.let { ErrorLine(it) }
            PleosButton("대안 확인", enabled = chosen != null && !s.busy) { chosen?.let(onAlternative) }
            Spacer(Modifier.height(u(GAP)))
            PleosButton("직접 연락", ButtonTone.Secondary, onClick = onContact)
            Spacer(Modifier.height(u(GAP)))
            PleosButton("보류·취소", ButtonTone.Secondary, enabled = !s.busy, onClick = onCancel)
        }
    }
}

/** 지금 장소를 뺀 승인 장소 (기본 장소 먼저, 최대 2곳) */
private fun alternatives(s: UiState, v: SessionView): List<PickupSpots.Spot> =
    s.spots?.academies?.flatMap { it.spots }.orEmpty()
        .filter { it.spotId != v.spot?.spotId }
        .sortedWith(compareByDescending<PickupSpots.Spot> { it.isDefault }.thenBy { it.walkMinutes ?: 99 })
        .take(2)

@Composable
private fun AlternativeRow(spot: PickupSpots.Spot, selected: Boolean, onClick: () -> Unit) {
    val c = Pleos.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(u(22))).background(c.card)
            .border(u(2), if (selected) c.stepDone else Color.Transparent, RoundedCornerShape(u(22)))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = u(24), vertical = u(16)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconCircle(c.iconCircle, 44) { PinIcon(it, c.text) }
        Spacer(Modifier.width(u(16)))
        Column(Modifier.weight(1f)) {
            Text(spot.name ?: "-", color = c.text, fontSize = us(20), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull("승인된 장소", spot.walkMinutes?.let { "보행 ${it}분" }).joinToString(" · "),
                color = c.subText, fontSize = us(17),
            )
        }
        ChevronIcon(u(30), c.subText)
    }
}

@Composable
internal fun ErrorLine(text: String) {
    Text(text, color = Pleos.colors.danger, fontSize = us(18), modifier = Modifier.padding(bottom = u(10)))
}

/** ISO 시각까지 남은 초. 1초마다 줄어든다 */
@Composable
internal fun secondsUntil(iso: String?): Long {
    val end = remember(iso) { iso?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(end) {
        while (end != null && now < end) {
            delay(250)
            now = System.currentTimeMillis()
        }
    }
    return ((end ?: now) - now).coerceAtLeast(0).let { (it + 999) / 1000 }
}
