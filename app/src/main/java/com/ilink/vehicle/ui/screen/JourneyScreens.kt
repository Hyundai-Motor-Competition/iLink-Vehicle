package com.ilink.vehicle.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.ilink.vehicle.R
import com.ilink.vehicle.data.model.PickupSpots
import com.ilink.vehicle.data.model.SessionView
import com.ilink.vehicle.ui.UiState
import com.ilink.vehicle.ui.component.DesignCanvas
import com.ilink.vehicle.ui.component.MapMark
import com.ilink.vehicle.ui.component.MiniMap
import com.ilink.vehicle.ui.component.u
import com.ilink.vehicle.ui.component.us
import com.ilink.vehicle.ui.theme.Pleos
import kotlinx.coroutines.delay

/*
 * 차량 2차 디자인 V03 ~ V06 (assets/차량용 화면 V/V03~V06 · Parking).
 * 좌표는 앱 창(1305 x 908) 기준 디자인 값 그대로다. V02 개선안과 같은 틀: 왼쪽 정보 · 오른쪽 작은 지도.
 *
 *  V03 목적지로 가는 길  : ① 출발 알림 발송(진행) → 1분 뒤 ② 목적지로 가는 중(진행). 레이아웃은 그대로, 단계 상태만 바뀐다.
 *  V04                  : ② 진행 중 아이가 준비 시간을 늘려도(보류) ③ 합류 안내 발송 시각은 그대로 둔다.
 *  V05 도착 · 정차 중    : ③ 합류 안내 발송(진행). '여기로 부르기' · '다른장소로 가기'.
 *  V06 다른 장소 고르기  : 대안 장소를 골라 출발하면 자녀에게 바뀐 위치로 안내가 간다.
 */

/** ① 출발 알림 → ② 목적지로 가는 중으로 넘어가는 시간 */
internal const val DEPARTURE_STEP_MS = 60_000L

private object J {
    val bg = V01.bg
    val card = Color(0xFF21211E)
    val button = Color(0xFF2E2E2A)
    val yellow = V01.yellow
    val pending = Color(0xFF767671)
    val time = Color(0xFF8C8C86)
    val timePending = Color(0xFF6E6E69)
    val loc = Color(0xFFCECECE)
    val sub = Color(0xFFCECECE)
    val caption = Color(0xFF8A8A82)
}

private enum class Step { Done, Active, Pending }

private data class TimelineRow(val title: String, val time: String?, val sub: String?, val step: Step)

/** 진행 단계: 0 출발 알림 · 1 가는 중 · 2 합류 안내 */
private fun timelineRows(v: SessionView, phase: Int, departedAt: Long?, calledHereAt: Long?): List<TimelineRow> {
    val call = v.call
    val sentAt = call?.progress?.sentAt
    // 합류 안내 예정 시각: 서버 예정값 → 없으면 정차 예상. 아이가 준비 시간을 늘려도(callNotBefore) 바꾸지 않는다
    val planned = call?.scheduledAt ?: v.eta?.stopEtaAt
    val child = v.children.firstOrNull()
    val prep = child?.extraPrepMinutes?.takeIf { it > 0 }
    val departTime = departedAt?.let { java.time.Instant.ofEpochMilli(it).atZone(SEOUL).format(CLOCK) }
    val stepOf = { i: Int -> when { i < phase -> Step.Done; i == phase -> Step.Active; else -> Step.Pending } }
    return listOf(
        TimelineRow("출발 알림 발송", departTime, null, stepOf(0)),
        TimelineRow(
            if (phase >= 2) "목적지 도착" else "목적지로 가는 중",
            null,
            if (phase == 1) (if (prep != null) "아이가 ${prep}분 뒤에 나온대요" else "아이는 실내에서 대기 중") else null,
            stepOf(1),
        ),
        TimelineRow(
            if (call?.held == true) "합류 안내 보류 중" else "합류 안내 발송",
            when {
                calledHereAt != null -> java.time.Instant.ofEpochMilli(calledHereAt).atZone(SEOUL).format(CLOCK)
                sentAt != null -> clock(sentAt)
                planned != null -> "${clock(planned)} 예정"
                else -> null
            },
            if (calledHereAt != null) "지금 차 위치로 안내했어요" else null,
            stepOf(2),
        ),
    )
}

/** 지금 몇 번째 단계인지. 출발 1분이 지나면 ②로 (정차하면 ③) */
@Composable
private fun rememberPhase(v: SessionView, departedAt: Long?): Int {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val arrived = v.status?.vehicle == "STOPPED"
    LaunchedEffect(departedAt, arrived) {
        while (!arrived) {
            now = System.currentTimeMillis()
            val left = (departedAt ?: now) + DEPARTURE_STEP_MS - now
            if (departedAt != null && left <= 0) break
            delay(left.coerceIn(200, 1_000))
        }
        now = System.currentTimeMillis()
    }
    return when {
        arrived -> 2
        departedAt != null && now - departedAt >= DEPARTURE_STEP_MS -> 1
        else -> 0
    }
}

/**
 * V03 · V04 정차 중 화면 (목적지로 가는 길). 주행 중이면 [JourneyDriving].
 */
@Composable
fun JourneyEnRoute(
    s: UiState,
    onHold: () -> Unit,
    onRelease: () -> Unit,
    onContact: () -> Unit,
    onMenu: () -> Unit,
) {
    val v = s.session ?: return
    val phase = rememberPhase(v, s.departedAt)
    val rows = timelineRows(v, phase, s.departedAt, null)
    val held = v.call?.held == true
    // V02 '여기로 출발하기'와 같은 자리라 두 번 누르면 보류가 걸린다. 화면이 바뀐 뒤 1.5초는 누름을 받지 않는다
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1_500)
        armed = true
    }
    if (s.driving) {
        JourneyDriving(s, v, rows, held, armed, onHold, onRelease)
        return
    }
    DesignCanvas(J.bg) {
        Box(Modifier.fillMaxSize()) {
            TopButtons(onContact, onMenu)
            EtaHeadline(v, Modifier.offset(u(38), u(187)))
            LocationLine(v, arrived = false, Modifier.offset(u(38), u(276)).width(u(698)))
            TimelineCard(rows, Modifier.offset(u(38), u(339)).size(u(698), u(252)), avatarsInCard = true)
            s.error?.let { ErrorAt(it, 760) }
            DarkButton(if (held) "합류 안내 보류 해제" else "합류 안내 보류", !s.busy && armed, Modifier.offset(u(38), u(789))) {
                if (held) onRelease() else onHold()
            }
            SpotMiniMap(s, v)
        }
    }
}

/** V03 주행 중 (assets/V03 Parking(주행 중)): 지도 · 위 버튼 없이 가운데 571 폭 */
@Composable
private fun JourneyDriving(
    s: UiState,
    v: SessionView,
    rows: List<TimelineRow>,
    held: Boolean,
    armed: Boolean,
    onHold: () -> Unit,
    onRelease: () -> Unit,
) {
    DesignCanvas(J.bg) {
        Box(Modifier.fillMaxSize()) {
            EtaHeadline(v, Modifier.offset(u(368), u(200)))
            LocationLine(v, arrived = false, Modifier.offset(u(368), u(289)).width(u(430)))
            Avatars(Modifier.offset(u(809), u(247)))
            TimelineCard(rows, Modifier.offset(u(368), u(352)).size(u(571), u(252)), avatarsInCard = false)
            DarkButton(if (held) "합류 안내 보류 해제" else "합류 안내 보류", !s.busy && armed, Modifier.offset(u(368), u(643)), width = 571) {
                if (held) onRelease() else onHold()
            }
        }
    }
}

/**
 * V05 도착 · 정차 중. 합류 안내 발송이 진행 단계.
 * '여기로 부르기': 미리 정한 곳이 아닌 근처에 섰을 때, 지금 차 위치로 아이를 부른다.
 * '다른장소로 가기': 대안 장소 고르기(V06)로.
 */
@Composable
fun JourneyArrived(
    s: UiState,
    onCallHere: () -> Unit,
    onOtherPlace: () -> Unit,
    onContact: () -> Unit,
    onMenu: () -> Unit,
    onManual: () -> Unit = {},
) {
    val v = s.session ?: return
    val rows = timelineRows(v, 2, s.departedAt, s.calledHereAt)
    DesignCanvas(J.bg) {
        Box(Modifier.fillMaxSize()) {
            TopButtons(onContact, onMenu)
            Row(Modifier.offset(u(38), u(187))) {
                Text("도착", color = Color.White, fontSize = us(56), fontWeight = FontWeight.ExtraBold, style = tight(), modifier = Modifier.alignByBaseline())
                Spacer(Modifier.width(u(12)))
                Text("정차 중이에요", color = Color.White, fontSize = us(32), style = tight(), modifier = Modifier.alignByBaseline())
            }
            LocationLine(v, arrived = true, Modifier.offset(u(38), u(276)).width(u(698)))
            TimelineCard(rows, Modifier.offset(u(38), u(339)).size(u(698), u(252)), avatarsInCard = true)
            s.error?.let { ErrorAt(it, 630) }
            if (!s.doorSignalAvailable && v.call?.callId != null) {
                // 기획 QA-38: 도어 신호를 못 받는 차량은 아이가 타면 직접 완료한다 (ERR-14)
                Text(
                    "문 열림 신호를 받을 수 없어요. 아이가 타면 눌러 주세요", color = J.caption, fontSize = us(17), textAlign = TextAlign.Center,
                    modifier = Modifier.offset(u(38), u(668)).width(u(698)),
                )
                StartButton(0, enabled = !s.busy, Modifier.offset(u(38), u(704)), label = "탑승 확인 및 완료", width = 698, ring = false, onClick = onManual)
            } else {
                Text(
                    "차량이 다른 곳에 정차했나요?", color = J.caption, fontSize = us(17), textAlign = TextAlign.Center,
                    modifier = Modifier.offset(u(38), u(668)).width(u(698)),
                )
                DarkButton(if (s.calledHereAt != null) "여기로 다시 부르기" else "여기로 부르기", !s.busy, Modifier.offset(u(38), u(704))) { onCallHere() }
            }
            DarkButton("다른장소로 가기", !s.busy, Modifier.offset(u(38), u(789))) { onOtherPlace() }
            SpotMiniMap(s, v)
        }
    }
}

/**
 * V06 다른 장소로 가기. V02 개선안과 같은 카드 · 버튼에 '추천 장소'만 묶었다.
 * 지금 장소를 뺀 후보 중 첫 번째가 골라진 채로 8초 뒤 자동 출발. 출발하면 장소 변경(SES-05) → 자녀에게 바뀐 위치 안내.
 */
@Composable
fun JourneyOtherPlace(
    s: UiState,
    onGo: (PickupSpots.Spot) -> Unit,
    onBack: () -> Unit,
    onContact: () -> Unit,
    onMenu: () -> Unit,
) {
    val current = s.session?.spot?.spotId
    val spots = s.spots?.academies?.flatMap { it.spots }.orEmpty().filter { it.spotId != current }.take(2)
    var selectedId by remember(spots) { mutableStateOf(spots.firstOrNull()?.spotId) }
    val best = spots.firstOrNull { it.spotId == selectedId } ?: spots.firstOrNull()
    val times = spots.associate { it.spotId to timeOf(it, s.recommendations) }

    var left by remember(best?.spotId) { mutableIntStateOf(AUTO_START_S) }
    var fired by remember(best?.spotId) { mutableStateOf(false) }
    LaunchedEffect(best?.spotId, s.busy) {
        while (best != null && !fired && left > 0 && !s.busy) {
            delay(1_000)
            left -= 1
        }
        if (best != null && !fired && left == 0 && !s.busy) {
            fired = true
            onGo(best)
        }
    }

    DesignCanvas(J.bg) {
        Box(Modifier.fillMaxSize()) {
            TopButtons(onContact, onMenu)
            Text("어디서 아이를 픽업할까요?", color = Color.White, fontSize = us(34), modifier = Modifier.offset(u(38), u(183)))
            if (best == null) {
                Text("다른 픽업 장소가 없어요", color = V01.sub, fontSize = us(20), modifier = Modifier.offset(u(38), u(290)))
                DarkButton("돌아가기", true, Modifier.offset(u(38), u(789)), onClick = onBack)
            } else {
                Text("추천 장소", color = J.caption, fontSize = us(17), modifier = Modifier.offset(u(37), u(269.5)))
                spots.forEachIndexed { i, spot ->
                    SpotCard(spot, times[spot.spotId], selected = spot.spotId == best.spotId, Modifier.offset(u(37), u(309 + i * 143))) {
                        left = AUTO_START_S
                        selectedId = spot.spotId
                    }
                }
                StartButton(left, enabled = !s.busy && !fired, Modifier.offset(u(37), u(787))) {
                    fired = true
                    onGo(best)
                }
            }
            s.error?.let { ErrorAt(it, 760) }
            val center = best?.stopPoint
            if (center != null) {
                MiniMap(
                    center = center,
                    marks = buildList {
                        spots.filter { it.spotId != best.spotId }.forEachIndexed { i, a -> a.stopPoint?.let { add(MapMark(it, selected = false, label = "${i + 1}")) } }
                        add(MapMark(center, selected = true))
                    },
                    modifier = Modifier.offset(u(772), u(182)).size(u(498), u(678)).clip(RoundedCornerShape(u(36))),
                )
            }
        }
    }
}

/**
 * 탑승 완료 (assets/탑승완료-정차중 · 탑승완료-주행중). 문 열림 · 닫힘으로 탑승을 인식했거나 완료된 뒤.
 * 정차 중: 제목 · 장소와 도착 시각 · 진행 단계 · '집으로 가기'(8초 뒤 자동) · '픽업 끝내기', 오른쪽 그림 카드.
 * 주행 중: 가운데 제목과 탑승 시각, 아래 두 버튼만.
 * 탑승 인식 뒤 10초 안에는 '아이가 타지 않았어요'로 취소할 수 있다 (SES-12).
 */
@Composable
fun JourneyBoarded(
    s: UiState,
    onGoHome: () -> Unit,
    onEnd: () -> Unit,
    onReject: (Long?) -> Unit,
    onMenu: () -> Unit,
) {
    val v = s.session ?: return
    val completed = v.status?.session == "COMPLETED"
    val cancelLeft = secondsUntil(v.boarding?.cancelableUntil)
    val canReject = !completed && cancelLeft > 0

    // 기획 QA-28: 주행 중에는 완료 전 탑승 확인 조작을 막는다 (완료된 뒤의 '집으로 가기' · '픽업 끝내기'만 허용)
    val locked = s.driving && !completed
    var left by remember { mutableIntStateOf(AUTO_START_S) }
    var fired by remember { mutableStateOf(false) }
    LaunchedEffect(s.busy, locked) {
        while (!fired && left > 0 && !s.busy && !locked) {
            delay(1_000)
            left -= 1
        }
        if (!fired && left == 0 && !s.busy && !locked) {
            fired = true
            onGoHome()
        }
    }
    val boardedAt = clock(v.boarding?.detectedAt ?: v.children.firstNotNullOfOrNull { it.boardedAt })
    val arrivedAt = clock(v.eta?.stopEtaAt)
    val rows = timelineRows(v, 2, s.departedAt, s.calledHereAt).mapIndexed { i, r -> if (i < 2) r.copy(step = Step.Done, sub = null) else r }

    DesignCanvas(J.bg) {
        Box(Modifier.fillMaxSize()) {
            MenuButton(onMenu)
            if (s.driving) {
                Text(
                    "탑승 완료", color = Color.White, fontSize = us(56), fontWeight = FontWeight.ExtraBold, style = tight(),
                    textAlign = TextAlign.Center, modifier = Modifier.offset(y = u(187)).fillMaxWidth(),
                )
                Row(Modifier.offset(y = u(276)).fillMaxWidth().height(u(25)), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    PlaceAndTime(v, "$boardedAt 탑승")
                }
                BoardedButtons(s, left, fired || locked, canReject = false, x = 37, width = 1231, onGoHome = { fired = true; onGoHome() }, onEnd = onEnd, onReject = { onReject(v.children.singleOrNull()?.childUserId) })
            } else {
                Text("탑승 완료", color = Color.White, fontSize = us(56), fontWeight = FontWeight.ExtraBold, style = tight(), modifier = Modifier.offset(u(38), u(187)))
                Row(Modifier.offset(u(38), u(276)).width(u(560)).height(u(25)), verticalAlignment = Alignment.CenterVertically) {
                    PlaceAndTime(v, "$arrivedAt 도착")
                }
                Avatars(Modifier.offset(u(603), u(234)))
                TimelineCard(rows, Modifier.offset(u(38), u(339)).size(u(698), u(252)), avatarsInCard = false)
                BoardedButtons(s, left, fired, canReject, x = 37, width = 698, onGoHome = { fired = true; onGoHome() }, onEnd = onEnd, onReject = { onReject(v.children.singleOrNull()?.childUserId) })
                // 오른쪽 그림 카드 498 x 678
                Box(Modifier.offset(u(772), u(182)).size(u(498), u(678)).clip(RoundedCornerShape(u(36)))
                    // 디자인 카드는 위에서 아래로 살짝 어두워진다. 그림 윗부분 색(#282824)과 이어지게
                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(0f to Color(0xFF2D2D29), 0.47f to Color(0xFF2A2A26)))) {
                    Text(
                        "아이와 함께 목적지까지\n안전한 운전 되세요", color = Color(0xFF8E8E88), fontSize = us(28), fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center, lineHeight = us(45), modifier = Modifier.offset(y = u(113)).fillMaxWidth(),
                    )
                    Image(
                        painterResource(R.drawable.boarded_car), contentDescription = null, contentScale = ContentScale.FillWidth,
                        modifier = Modifier.offset(y = u(318)).fillMaxWidth(),
                    )
                    // 그림 윗부분 바탕이 카드와 이어지도록 카드색 → 투명 그러데이션을 덮는다
                    Box(
                        Modifier.offset(y = u(318)).fillMaxWidth().height(u(110))
                            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF2A2A26), Color.Transparent))),
                    )
                }
            }
            s.error?.let { ErrorAt(it, 640) }
        }
    }
}

/** 핀 + 장소 · 노란 시각 ('20:07 도착' / '20:09 탑승') */
@Composable
private fun androidx.compose.foundation.layout.RowScope.PlaceAndTime(v: SessionView, yellow: String) {
    OutlinePin(u(19), J.loc)
    Spacer(Modifier.width(u(8)))
    Text(v.spot?.name ?: "-", color = J.loc, fontSize = us(21), maxLines = 1, overflow = TextOverflow.Ellipsis, style = tight(), modifier = Modifier.weight(1f, fill = false))
    Text("  ·  ", color = J.loc, fontSize = us(21), style = tight())
    Text(yellow, color = J.yellow, fontSize = us(21), maxLines = 1, style = tight())
}

/** '아이가 타지 않았어요'(취소 가능할 때만) · '집으로 가기'(8초 자동) · '픽업 끝내기' */
@Composable
private fun BoardedButtons(
    s: UiState, left: Int, fired: Boolean, canReject: Boolean, x: Int, width: Int,
    onGoHome: () -> Unit, onEnd: () -> Unit, onReject: () -> Unit,
) {
    if (canReject) {
        Text(
            "아이가 타지 않았어요", color = J.caption, fontSize = us(18), textAlign = TextAlign.Center,
            modifier = Modifier.offset(u(x), u(648)).width(u(width)).clickable(role = Role.Button, enabled = !s.busy, onClick = onReject),
        )
    }
    StartButton(left, enabled = !s.busy && !fired, Modifier.offset(u(x), u(703)), label = "집으로 가기", width = width, onClick = onGoHome)
    DarkButton("픽업 끝내기", !s.busy && !(fired && s.driving), Modifier.offset(u(x), u(788)), width = width, onClick = onEnd)
}

/** 오른쪽 위 메뉴 하나 (탑승 완료 화면) */
@Composable
private fun MenuButton(onMenu: () -> Unit) {
    Box(
        Modifier.offset(u(1196), u(49)).size(u(74)).clip(CircleShape).background(J.card)
            .clickable(role = Role.Button, onClickLabel = "메뉴", onClick = onMenu),
        contentAlignment = Alignment.Center,
    ) { MenuIcon(u(26), Color(0xFFE6E6E0)) }
}

/* ---------- 조각 ---------- */

private fun tight() = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

/** '12분 후 도착' */
@Composable
private fun EtaHeadline(v: SessionView, modifier: Modifier) {
    val min = v.eta?.remainingSeconds?.let { (it + 59) / 60 }
    Row(modifier) {
        Text(
            if (v.eta?.stale == true || min == null) "--분" else "${min}분",
            color = Color.White, fontSize = us(56), fontWeight = FontWeight.ExtraBold, style = tight(), modifier = Modifier.alignByBaseline(),
        )
        Spacer(Modifier.width(u(12)))
        Text("후 도착", color = Color.White, fontSize = us(32), style = tight(), modifier = Modifier.alignByBaseline())
    }
}

/** 핀 + '수학 임팩트 학원 · 20:10 도착 예정' */
@Composable
private fun LocationLine(v: SessionView, arrived: Boolean, modifier: Modifier) {
    val time = clock(v.eta?.stopEtaAt).takeIf { it != "--:--" }
    Row(modifier.height(u(25)), verticalAlignment = Alignment.CenterVertically) {
        OutlinePin(u(19), J.loc)
        Spacer(Modifier.width(u(8)))
        // 장소 이름이 길면 이름만 줄이고 시각은 남긴다
        Text(
            v.spot?.name ?: "-", color = J.loc, fontSize = us(21), maxLines = 1, overflow = TextOverflow.Ellipsis, style = tight(),
            modifier = Modifier.weight(1f, fill = false),
        )
        time?.let {
            Text("  ·  " + if (arrived) "$it 도착" else "$it 도착 예정", color = J.loc, fontSize = us(21), maxLines = 1, style = tight())
        }
    }
}

/** 단계 카드: 세로 점선 + 표시(완료 점 · 진행 고리 · 대기 회색 점), 줄 간격 74 */
@Composable
private fun TimelineCard(rows: List<TimelineRow>, modifier: Modifier, avatarsInCard: Boolean) {
    Box(modifier.clip(RoundedCornerShape(u(44))).background(J.card)) {
        val markerX = 55.6f
        val centers = listOf(54f, 128f, 202f)
        val active = rows.indexOfFirst { it.step == Step.Active }.let { if (it < 0) rows.size - 1 else it }
        // 점선: 진행 단계까지는 노랑, 그 뒤는 흐리게
        Canvas(Modifier.fillMaxSize()) {
            val k = size.height / 252f
            val x = markerX * k
            val dash = PathEffect.dashPathEffect(floatArrayOf(3f * k, 3f * k))
            val w = 1.4f * k
            drawLine(J.yellow.copy(alpha = 0.45f), Offset(x, centers[0] * k), Offset(x, centers[active] * k), w, pathEffect = dash)
            if (active < 2) drawLine(J.yellow.copy(alpha = 0.22f), Offset(x, centers[active] * k), Offset(x, centers[2] * k), w, pathEffect = dash)
        }
        rows.forEachIndexed { i, r ->
            val cy = centers[i]
            Marker(r.step, Modifier.offset(u(markerX - 12), u(cy - 12)))
            val titleColor = when (r.step) { Step.Done -> Color.White; Step.Active -> J.yellow; Step.Pending -> J.pending }
            val titleCenter = if (r.sub != null) cy - 13 else cy
            Row(Modifier.offset(u(93.8f), u(titleCenter - 17)).height(u(34)), verticalAlignment = Alignment.CenterVertically) {
                Text(r.title, color = titleColor, fontSize = us(24), style = tight())
                r.time?.let {
                    Spacer(Modifier.width(u(18)))
                    Text(it, color = if (r.step == Step.Pending) J.timePending else J.time, fontSize = us(18), style = tight())
                }
            }
            r.sub?.let {
                Text(it, color = J.sub, fontSize = us(17), style = tight(), modifier = Modifier.offset(u(94.3f), u(cy + 17.5f - 11)).height(u(22)))
            }
        }
        if (avatarsInCard) Avatars(Modifier.offset(u(523.6f), u(37)))
    }
}

@Composable
private fun Marker(step: Step, modifier: Modifier) {
    Box(modifier.size(u(24)), contentAlignment = Alignment.Center) {
        when (step) {
            Step.Active -> {
                Box(Modifier.size(u(24)).border(u(2), J.yellow, CircleShape))
                Box(Modifier.size(u(12)).clip(CircleShape).background(J.yellow))
            }
            Step.Done -> Box(Modifier.size(u(11)).clip(CircleShape).background(J.yellow))
            Step.Pending -> Box(Modifier.size(u(12)).clip(CircleShape).background(J.pending))
        }
    }
}

/** 보호자 · 아이 사진 (지름 67, 오른쪽이 위로 겹침) */
@Composable
private fun Avatars(modifier: Modifier) {
    Box(modifier.size(u(131), u(67))) {
        AvatarPhoto(R.drawable.avatar_guardian, Modifier)
        AvatarPhoto(R.drawable.avatar_child, Modifier.offset(x = u(63.5f)))
    }
}

@Composable
private fun AvatarPhoto(res: Int, modifier: Modifier) {
    Box(modifier.size(u(67)).clip(CircleShape).background(J.card), contentAlignment = Alignment.Center) {
        Image(
            painterResource(res), contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(u(67)).clip(CircleShape),
        )
    }
}

@Composable
private fun DarkButton(label: String, enabled: Boolean, modifier: Modifier, width: Number = 698, onClick: () -> Unit) {
    Box(
        modifier.size(u(width), u(71)).clip(RoundedCornerShape(u(18))).background(J.button)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (enabled) Color.White else Color.White.copy(alpha = 0.5f), fontSize = us(22), fontWeight = FontWeight.ExtraBold)
    }
}

/** 오른쪽 위 전화(연락처) · 메뉴 동그라미 */
@Composable
private fun TopButtons(onContact: () -> Unit, onMenu: () -> Unit) {
    Box(
        Modifier.offset(u(1107), u(49)).size(u(74)).clip(CircleShape).background(J.card)
            .clickable(role = Role.Button, onClickLabel = "직접 연락", onClick = onContact),
        contentAlignment = Alignment.Center,
    ) { PhoneIcon(u(27), Color(0xFFE6E6E0)) }
    Box(
        Modifier.offset(u(1196), u(49)).size(u(74)).clip(CircleShape).background(J.card)
            .clickable(role = Role.Button, onClickLabel = "메뉴", onClick = onMenu),
        contentAlignment = Alignment.Center,
    ) { MenuIcon(u(26), Color(0xFFE6E6E0)) }
}

/** 오른쪽 작은 지도 498 x 678: 확정 장소에 핀 */
@Composable
private fun SpotMiniMap(s: UiState, v: SessionView) {
    val spot = s.spots?.academies?.flatMap { it.spots }?.firstOrNull { it.spotId == v.spot?.spotId }
    val all = s.spots?.academies?.flatMap { it.spots }.orEmpty()
    val center = spot?.stopPoint ?: all.firstOrNull()?.stopPoint ?: return
    MiniMap(
        center = center,
        marks = buildList {
            all.filter { it.spotId != spot?.spotId }.take(2).forEachIndexed { i, a -> a.stopPoint?.let { add(MapMark(it, selected = false, label = "${i + 1}")) } }
            add(MapMark(center, selected = true))
        },
        modifier = Modifier.offset(u(772), u(182)).size(u(498), u(678)).clip(RoundedCornerShape(u(36))),
    )
}

@Composable
private fun ErrorAt(text: String, y: Int) {
    Text(text, color = Pleos.colors.danger, fontSize = us(17), modifier = Modifier.offset(u(38), u(y)))
}

/** 위치 핀 (외곽선) */
@Composable
private fun OutlinePin(size: Dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val st = w * 0.09f
        val p = Path().apply {
            moveTo(w * 0.5f, w * 0.95f)
            cubicTo(w * 0.2f, w * 0.62f, w * 0.12f, w * 0.48f, w * 0.12f, w * 0.38f)
            cubicTo(w * 0.12f, w * 0.17f, w * 0.29f, w * 0.03f, w * 0.5f, w * 0.03f)
            cubicTo(w * 0.71f, w * 0.03f, w * 0.88f, w * 0.17f, w * 0.88f, w * 0.38f)
            cubicTo(w * 0.88f, w * 0.48f, w * 0.8f, w * 0.62f, w * 0.5f, w * 0.95f)
            close()
        }
        drawPath(p, color, style = Stroke(st, join = StrokeJoin.Round))
        drawCircle(color, radius = w * 0.12f, center = Offset(w * 0.5f, w * 0.38f), style = Stroke(st))
    }
}

/** 전화기 (외곽선) */
@Composable
private fun PhoneIcon(size: Dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val p = Path().apply {
            moveTo(w * 0.22f, w * 0.08f)
            lineTo(w * 0.36f, w * 0.08f)
            lineTo(w * 0.44f, w * 0.3f)
            lineTo(w * 0.33f, w * 0.38f)
            cubicTo(w * 0.4f, w * 0.52f, w * 0.48f, w * 0.6f, w * 0.62f, w * 0.67f)
            lineTo(w * 0.7f, w * 0.56f)
            lineTo(w * 0.92f, w * 0.64f)
            lineTo(w * 0.92f, w * 0.78f)
            cubicTo(w * 0.92f, w * 0.87f, w * 0.85f, w * 0.93f, w * 0.76f, w * 0.92f)
            cubicTo(w * 0.42f, w * 0.88f, w * 0.12f, w * 0.58f, w * 0.08f, w * 0.24f)
            cubicTo(w * 0.07f, w * 0.15f, w * 0.13f, w * 0.08f, w * 0.22f, w * 0.08f)
            close()
        }
        drawPath(p, color, style = Stroke(w * 0.075f, join = StrokeJoin.Round, cap = StrokeCap.Round))
    }
}
