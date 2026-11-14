package com.ilink.vehicle.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.ilink.vehicle.data.model.PickupSpots
import com.ilink.vehicle.data.model.SpotRecommendations
import com.ilink.vehicle.ui.UiState
import com.ilink.vehicle.ui.component.DesignCanvas
import com.ilink.vehicle.ui.component.MapMark
import com.ilink.vehicle.ui.component.MiniMap
import com.ilink.vehicle.ui.component.u
import com.ilink.vehicle.ui.component.us
import com.ilink.vehicle.ui.theme.Pleos
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sqrt

/** 개선안의 '여기로 출발하기' 자동 출발까지 남은 초 */
internal const val AUTO_START_S = 8

/** 장소 1곳의 표시 값: 운전 시간(초) · 걷는 시간(분) */
internal data class SpotTime(val driveSeconds: Int?, val walkMinutes: Int?)

/**
 * V02 정차 중 — assets/V02 픽업 장소 선택 (개선안) 좌표 그대로.
 * 왼쪽: 고른 장소(노란 테두리) + 추천 장소 2곳 + '여기로 출발하기'(8초 뒤 자동 출발),
 * 오른쪽: 작은 실제 지도(OpenStreetMap)에 고른 장소 핀과 다른 후보 번호.
 * 운전 시간 · 도착 시각은 SPOT-04 추천 결과(같은 자리 후보)를 먼저 쓰고, 없으면 장소 목록 값을 쓴다.
 */
@Composable
internal fun V02Parking(
    s: UiState,
    spots: List<PickupSpots.Spot>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onRoute: (PickupSpots.Spot) -> Unit,
    onMenu: () -> Unit,
) {
    val best = spots.firstOrNull { it.spotId == selectedId } ?: spots.firstOrNull()
    val alts = spots.filter { it.spotId != best?.spotId }.take(2)
    val times = spots.associate { it.spotId to timeOf(it, s.recommendations) }

    // 고른 장소가 바뀌면 8초부터 다시. 0이 되면 한 번만 출발
    var left by remember(best?.spotId) { mutableIntStateOf(AUTO_START_S) }
    var fired by remember(best?.spotId) { mutableStateOf(false) }
    LaunchedEffect(best?.spotId, s.busy) {
        while (best != null && !fired && left > 0 && !s.busy) {
            delay(1_000)
            left -= 1
        }
        if (best != null && !fired && left == 0 && !s.busy) {
            fired = true
            onRoute(best)
        }
    }

    DesignCanvas(V01.bg) {
        Box(Modifier.fillMaxSize()) {
            // 오른쪽 위 메뉴 (지름 74)
            Box(
                Modifier.offset(u(1196), u(49)).size(u(74)).clip(CircleShape).background(V01.chip)
                    .clickable(role = Role.Button, onClickLabel = "메뉴", onClick = onMenu),
                contentAlignment = Alignment.Center,
            ) { MenuIcon(u(26), Color(0xFFE6E6E0)) }

            Text("어디서 아이를 픽업할까요?", color = Color.White, fontSize = us(34), modifier = Modifier.offset(u(38), u(183)))

            if (best == null) {
                Text("휴대전화 앱에서 픽업 장소를 등록해 주세요", color = V01.sub, fontSize = us(20), modifier = Modifier.offset(u(38), u(290)))
            } else {
                SpotCard(best, times[best.spotId], selected = true, Modifier.offset(u(37), u(268))) {}
                if (alts.isNotEmpty()) {
                    Text("추천 장소", color = Color(0xFF8A8A82), fontSize = us(17), modifier = Modifier.offset(u(37), u(428)))
                }
                alts.forEachIndexed { i, alt ->
                    SpotCard(alt, times[alt.spotId], selected = false, Modifier.offset(u(37), u(466 + i * 143))) {
                        left = AUTO_START_S
                        onSelect(alt.spotId)
                    }
                }
                StartButton(left, enabled = !s.busy && !fired, Modifier.offset(u(37), u(787))) {
                    fired = true
                    onRoute(best)
                }
            }
            s.error?.let { ErrorLineAt(it) }

            // 오른쪽 작은 지도 498 x 678, 모서리 36
            val center = best?.stopPoint
            if (center != null) {
                MiniMap(
                    center = center,
                    marks = buildList {
                        alts.forEachIndexed { i, a -> a.stopPoint?.let { add(MapMark(it, selected = false, label = "${i + 1}")) } }
                        add(MapMark(center, selected = true))
                    },
                    modifier = Modifier.offset(u(772), u(182)).size(u(498), u(678)).clip(RoundedCornerShape(u(36))),
                )
            }
        }
    }
}

@Composable
private fun ErrorLineAt(text: String) {
    Text(text, color = Pleos.colors.danger, fontSize = us(17), modifier = Modifier.offset(u(38), u(868)))
}

/** 장소 카드 698 x 127: [분] [기본 배지 · 이름 / 설명] [도착 시각] */
@Composable
internal fun SpotCard(spot: PickupSpots.Spot, t: SpotTime?, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(u(44))
    Row(
        modifier.size(u(698), u(127)).clip(shape)
            .background(if (selected) Color.Transparent else V01.chip)
            .border(u(1.5f), if (selected) V01.yellow.copy(alpha = 0.6f) else Color.Transparent, shape)
            .clickable(enabled = !selected, role = Role.RadioButton, onClick = onClick)
            .padding(start = u(33), end = u(35)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            t?.driveSeconds?.let { "${(it + 59) / 60}분" } ?: "--분",
            color = if (selected) V01.yellow else Color.White, fontSize = us(36), fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.width(u(102)),
        )
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (spot.isDefault) {
                    Text(
                        "기본", color = V01.yellow.copy(alpha = 0.85f), fontSize = us(15), fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(u(6))).background(Color(0xFF303309)).padding(horizontal = u(8), vertical = u(3)),
                    )
                    Spacer(Modifier.width(u(10)))
                }
                Text(spot.name ?: "-", color = Color.White, fontSize = us(24), fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                when (val w = t?.walkMinutes) {
                    null -> "걷는 시간 정보 없음"
                    in 0..1 -> "건물 바로 앞"
                    else -> "걸어서 ${w}분"
                },
                color = Color(0xFFA6A69E), fontSize = us(18), modifier = Modifier.padding(top = u(6)),
            )
        }
        t?.driveSeconds?.let { Text("${clockIn(it)} 도착", color = V01.sub, fontSize = us(18)) }
    }
}

/** 698 x 73 노란 버튼 + 자동 출발까지 남은 초를 보여 주는 고리 */
@Composable
internal fun StartButton(left: Int, enabled: Boolean, modifier: Modifier, label: String = "여기로 출발하기", width: Number = 698, ring: Boolean = true, onClick: () -> Unit) {
    Row(
        modifier.size(u(width), u(73)).clip(RoundedCornerShape(u(18)))
            .background(if (enabled) V01.yellow else V01.yellow.copy(alpha = 0.5f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = V01.bg, fontSize = us(22), fontWeight = FontWeight.ExtraBold)
        if (enabled && ring) {
            Spacer(Modifier.width(u(14)))
            Box(Modifier.size(u(38)), contentAlignment = Alignment.Center) {
                val frac = left / AUTO_START_S.toFloat()
                Canvas(Modifier.size(u(38))) {
                    val w = size.minDimension * 0.07f
                    drawArc(
                        V01.bg, -90f, 360f * frac, false, Offset(w / 2, w / 2), Size(size.width - w, size.height - w),
                        style = Stroke(w, cap = StrokeCap.Round),
                    )
                }
                Text("$left", color = V01.bg, fontSize = us(15), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
internal fun MenuIcon(size: Dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val st = w * 0.09f
        listOf(0.22f, 0.5f, 0.78f).forEach { y -> drawLine(color, Offset(0f, w * y), Offset(w, w * y), st, StrokeCap.Round) }
    }
}

/** 등록 장소에 맞는 추천 후보(50m 안)가 있으면 그 운전 · 걷는 시간, 없으면 장소 목록 값 */
internal fun timeOf(spot: PickupSpots.Spot, rec: SpotRecommendations?): SpotTime {
    val p = spot.stopPoint
    val match = if (p == null) null else rec?.items?.filter { it.location != null }?.minByOrNull { meters(p.lat, p.lng, it.location!!.lat, it.location.lng) }
        ?.takeIf { meters(p.lat, p.lng, it.location!!.lat, it.location.lng) <= 50 }
    return SpotTime(
        // 서버 · 추천 값이 없으면 임시 값 (화면에 '--분'이 남지 않게)
        driveSeconds = match?.driveSeconds ?: spot.etaSeconds ?: TEMP_ETA_SECONDS,
        walkMinutes = match?.walkSeconds?.let { (it + 59) / 60 } ?: spot.walkMinutes,
    )
}

/** 짧은 거리용 근사 (m) */
private fun meters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val dy = (lat2 - lat1) * 111_000
    val dx = (lng2 - lng1) * 111_000 * cos(Math.toRadians(lat1))
    return sqrt(dx * dx + dy * dy)
}
