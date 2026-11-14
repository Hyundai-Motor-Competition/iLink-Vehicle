package com.ilink.vehicle.ui.screen

import com.ilink.vehicle.domain.shortName
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import com.ilink.vehicle.ui.component.DESIGN_W
import com.ilink.vehicle.ui.component.DesignCanvas
import com.ilink.vehicle.ui.component.GearIcon
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.ilink.vehicle.data.model.PickupSpots
import com.ilink.vehicle.data.model.SessionView
import com.ilink.vehicle.domain.VoiceCues
import com.ilink.vehicle.data.model.WeeklyPickups
import com.ilink.vehicle.R
import androidx.compose.ui.res.stringResource
import com.ilink.vehicle.ui.component.IconCircle
import com.ilink.vehicle.ui.component.StatusCard
import com.ilink.vehicle.ui.component.WarningIcon
import com.ilink.vehicle.ui.UiState
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

internal val CLOCK = DateTimeFormatter.ofPattern("HH:mm")
internal fun clock(iso: String?): String = runCatching {
    // 기기 시간대와 상관없이 한국 시간으로 보여 준다 (V02 정차 예상과 같은 기준)
    OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.of("Asia/Seoul")).format(CLOCK)
}.getOrDefault("--:--")

/**
 * V01 오늘의 픽업.
 * 주행 중: assets/V01 Driving — 다음 픽업 1건과 시작 버튼만.
 * 정차 중: assets/V01 디자인 개선안 — 인사말, 이번 주 날짜 띠, 아이별 픽업 카드.
 *   Pleos 가이드에 맞춰 바꾼 점: 앱 창 머리(아이콘 + 서비스 이름) 유지, 시스템 라이트/다크를 따름,
 *   라이트에서는 노랑 대신 파랑 강조(대비), 요일은 한국어, 아이 사진 대신 이름 첫 글자.
 * 필수 설정이 빠졌으면 시작을 막는다.
 */
@Composable
fun V01Today(s: UiState, onStart: (List<Long>) -> Unit, onSettings: () -> Unit) {
    val c = Pleos.colors
    val today = LocalDate.now(SEOUL)
    val days = s.weekly?.days.orEmpty()
    val pickups = days.firstOrNull { it.date == today.toString() }?.pickups.orEmpty()
    val readiness = s.weekly?.readiness
    val next = pickups.firstOrNull()
    val canStart = next != null && readiness?.startable != false && !s.busy

    if (!s.driving) {
        V01Parking(s, today, days, onStart, onSettings)
        return
    }
    PleosWindow(stringResource(R.string.app_name), titleBold = false) {
        Column(Modifier.weight(1f).padding(horizontal = u(24))) {
            Text(todayHeadline(pickups), color = c.text, fontSize = us(30), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = u(14), bottom = u(20)))
            // 주행 중: 고르지 않고 다음 픽업 1건만
            if (readiness?.startable == false) ReadinessCard(readiness.missing)
            next?.let { PickupCard(it, selected = false, onClick = null) }
            Spacer(Modifier.weight(1f))
            s.error?.let { Text(it, color = c.danger, fontSize = us(18), modifier = Modifier.padding(bottom = u(8))) }
            if (next != null) {
                PleosButton("${next.targetPickupTime ?: next.classEndTime ?: ""} 픽업 시작".trim(), enabled = canStart) { onStart(listOf(next.scheduleId)) }
            }
            Spacer(Modifier.height(u(23)))
        }
    }
}

/**
 * V01 정차 중 — assets/V01 디자인 개선안을 디자인 좌표(1305 x 908) 그대로 옮겼다.
 * 머리(서비스 이름) 없이 인사말 · 날짜 띠(좌우로 넘김, 양 끝 흐림) · 아이별 카드.
 * 사진 대신 이름 첫 글자, 'N분 후 도착'은 기본 픽업 장소의 정차 예상으로 채운다.
 */
@Composable
private fun V01Parking(
    s: UiState,
    today: LocalDate,
    days: List<WeeklyPickups.Day>,
    onStart: (List<Long>) -> Unit,
    onSettings: () -> Unit,
) {
    val calendar = s.calendar.ifEmpty { days }
    var day by remember(today) { mutableStateOf(today.toString()) }
    val isToday = day == today.toString()
    val pickups = calendar.firstOrNull { it.date == day }?.pickups.orEmpty()
    val todayCount = calendar.firstOrNull { it.date == today.toString() }?.pickups?.size ?: 0
    val readiness = s.weekly?.readiness
    val eta = s.spots?.academies?.flatMap { it.spots }?.let { all -> all.firstOrNull { it.isDefault } ?: all.firstOrNull() }?.etaSeconds

    DesignCanvas(V01.bg) {
        Box(Modifier.fillMaxSize()) {
            // 오른쪽 위 동그라미: 설정 (지름 74 → 터치 영역 74, 기준 60 이상)
            Box(
                Modifier.offset(u(1196), u(47)).size(u(74)).clip(CircleShape).background(V01.circle)
                    .clickable(role = Role.Button, onClickLabel = "설정", onClick = onSettings),
                contentAlignment = Alignment.Center,
            ) { GearIcon(u(34), V01.sub) }

            // 인사말 (32, 줄 높이 46). 'N건'만 노랑
            val name = s.settings?.guardianNameMasked?.takeIf { '*' !in it }
            Text(
                buildAnnotatedString {
                    if (name != null) {
                        withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append(name) }
                        append("님, ")
                    }
                    if (todayCount == 0) append("오늘은 예정된 픽업이 없어요.")
                    else {
                        append("오늘 ")
                        withStyle(SpanStyle(color = V01.yellow, fontWeight = FontWeight.ExtraBold)) { append("${todayCount}건") }
                        append("의 픽업 일정이 있네요.")
                    }
                    append("\n오늘도 안전한 픽업 되세요.")
                },
                color = Color.White, fontSize = us(32), lineHeight = us(46),
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                modifier = Modifier.offset(u(38), u(171)),
            )

            // 날짜 띠: y 320, 정사각 160 · 모서리 48 · 간격 18, 양 끝 200을 흐리게
            DateStrip(calendar, day, today.toString(), Modifier.offset(y = u(320)).fillMaxWidth().height(u(162))) { day = it }

            // 아이별 카드: y 560, 518 x 290, 간격 16. 3건이면 옆으로 넘긴다
            Row(
                Modifier.offset(y = u(560)).fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = u(38)),
                horizontalArrangement = Arrangement.spacedBy(u(16)),
            ) {
                if (pickups.isEmpty()) {
                    Text(
                        if (isToday) "일정은 휴대전화 앱에서 등록할 수 있어요" else "이날은 픽업 일정이 없어요",
                        color = V01.sub, fontSize = us(20), modifier = Modifier.padding(top = u(10)),
                    )
                }
                pickups.take(3).forEachIndexed { i, p ->
                    // 한 번에 한 픽업만: 오늘 첫 일정만 시작, 나머지는 '픽업 예정'
                    val next = isToday && i == 0
                    V01Card(
                        p, eta = if (next) eta else null,
                        startable = next && readiness?.startable != false && !s.busy,
                        showStart = next,
                    ) { onStart(listOf(p.scheduleId)) }
                }
            }
            if (isToday && readiness?.startable == false) {
                Text(
                    "시작하기 전에 휴대전화 앱에서 ${readiness.missing.joinToString(" · ")} 설정이 필요해요",
                    color = Pleos.colors.danger, fontSize = us(18), modifier = Modifier.offset(u(38), u(868)),
                )
            }
            s.error?.let { Text(it, color = Pleos.colors.danger, fontSize = us(18), modifier = Modifier.offset(u(38), u(868))) }
        }
    }
}

/** V01 개선안 색 (다크 전용, 디자인에서 뽑은 값) */
internal object V01 {
    val bg = Color(0xFF111200)
    val chip = Color(0xFF21211E)
    val card = Color(0xFF2E2E2C)
    val circle = Color(0xFF262624)
    val yellow = Color(0xFFF6FF43)
    val dot = Color(0xFFEFFE89)
    val sub = Color(0xFFCECECE)
    val disabled = Color(0xFF707069)
    val onDisabled = Color(0xFF2E2E2C)
    val avatar = Color(0xFFE4E6EA)
}

private val WEEK_EN = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")

/** 좌우로 넘기는 날짜 띠. 처음에는 고른 날이 가운데(네 번째)에 오고, 양 끝은 바탕색으로 흐려진다 */
@Composable
private fun DateStrip(days: List<WeeklyPickups.Day>, selected: String, today: String, modifier: Modifier, onPick: (String) -> Unit) {
    val start = days.indexOfFirst { it.date == today }.let { if (it < 0) 0 else (it - 3).coerceAtLeast(0) }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = start)
    // 앞뒤 주 일정이 나중에 들어오면 다시 오늘을 가운데(네 번째)로
    LaunchedEffect(days.firstOrNull()?.date, days.size) { state.scrollToItem(start) }
    LazyRow(
        state = state,
        flingBehavior = rememberSnapFlingBehavior(state),
        contentPadding = PaddingValues(horizontal = u(41)),
        horizontalArrangement = Arrangement.spacedBy(u(18)),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val f = 200f / DESIGN_W
                drawRect(
                    Brush.horizontalGradient(0f to Color.Transparent, f to Color.Black, 1f - f to Color.Black, 1f to Color.Transparent),
                    blendMode = BlendMode.DstIn,
                )
            },
    ) {
        items(days, key = { it.date }) { d ->
            val date = runCatching { LocalDate.parse(d.date) }.getOrNull()
            val on = d.date == selected
            Column(
                Modifier.size(u(160)).clip(RoundedCornerShape(u(48))).background(V01.chip)
                    .border(u(1.5f), if (on) V01.yellow.copy(alpha = 0.6f) else Color.Transparent, RoundedCornerShape(u(48)))
                    .clickable(role = Role.Tab, onClickLabel = "${date?.monthValue}월 ${date?.dayOfMonth}일 일정 보기") { onPick(d.date) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(date?.let { WEEK_EN[it.dayOfWeek.value - 1] } ?: "", color = Color.White, fontSize = us(15), fontWeight = FontWeight.Medium)
                Text(
                    date?.dayOfMonth?.toString() ?: d.date, color = Color.White, fontSize = us(40), fontWeight = FontWeight.Light,
                    style = TextStyle(lineHeight = us(46), platformStyle = PlatformTextStyle(includeFontPadding = false)),
                )
                // 점 = 그날 픽업 수 (고른 날에만, 개선안처럼)
                if (on && d.pickups.isNotEmpty()) {
                    Row(Modifier.padding(top = u(10)), horizontalArrangement = Arrangement.spacedBy(u(8))) {
                        repeat(d.pickups.size.coerceAtMost(3)) { Box(Modifier.size(u(8)).clip(CircleShape).background(V01.dot)) }
                    }
                }
            }
        }
    }
}

/**
 * 아이 픽업 카드 518 x 290. 글자 · 사진 줄은 양옆 46으로 같게 두고(사진은 오른쪽 46 안쪽),
 * 버튼은 양옆 28로 가운데 정렬한다.
 */
@Composable
private fun V01Card(p: WeeklyPickups.Pickup, eta: Int?, startable: Boolean, showStart: Boolean, onStart: () -> Unit) {
    Box(Modifier.size(u(518), u(290)).clip(RoundedCornerShape(u(42))).background(V01.card)) {
        Row(Modifier.padding(start = u(46), end = u(46), top = u(34)).fillMaxWidth()) {
            Column(Modifier.weight(1f).padding(top = u(2))) {
                Text(p.childName ?: "아이", color = V01.sub, fontSize = us(18))
                Text(
                    p.academyName ?: "-", color = Color.White, fontSize = us(28), fontWeight = FontWeight.ExtraBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = u(6)),
                )
                Row(Modifier.padding(top = u(22)), verticalAlignment = Alignment.CenterVertically) {
                    Text("${p.targetPickupTime ?: p.classEndTime ?: "--:--"} 픽업", color = V01.sub, fontSize = us(18))
                    eta?.let { Text("  ${(it + 59) / 60}분 후 도착", color = V01.yellow, fontSize = us(18), fontWeight = FontWeight.ExtraBold) }
                }
            }
            Spacer(Modifier.width(u(16)))
            // 아이 사진 자리 (74): 사진이 없어 이름
            Box(Modifier.size(u(74)).clip(CircleShape).background(V01.avatar), contentAlignment = Alignment.Center) {
                Text(p.childName?.let(::shortName) ?: "아이", color = Color(0xFF2E2E2C), fontSize = us(22), fontWeight = FontWeight.ExtraBold)
            }
        }
        // 버튼 463 x 72 (y 191), 모서리 18
        Box(
            Modifier.offset(u(28), u(191)).size(u(463), u(72)).clip(RoundedCornerShape(u(18)))
                .background(if (showStart) (if (startable) V01.yellow else V01.yellow.copy(alpha = 0.4f)) else V01.disabled)
                .clickable(enabled = showStart && startable, role = Role.Button, onClick = onStart),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (showStart) "픽업 시작하기" else "픽업 예정",
                color = if (showStart) Color(0xFF111200) else V01.onDisabled, fontSize = us(22),
                fontWeight = if (showStart) FontWeight.ExtraBold else FontWeight.Normal,
            )
        }
    }
}

/** '오늘은 오후 6시 외 1건 픽업이 예정되어 있어요.' (수업 종료 시각 기준) */
internal fun todayHeadline(pickups: List<WeeklyPickups.Pickup>): String {
    val first = pickups.firstOrNull() ?: return "오늘은 예정된 픽업이 없어요."
    val more = if (pickups.size > 1) " 외 ${pickups.size - 1}건" else ""
    return "오늘은 ${koreanTime(first.classEndTime ?: first.targetPickupTime)}$more 픽업이 예정되어 있어요."
}

/** '18:00' → '오후 6시', '18:30' → '오후 6시 30분' */
internal fun koreanTime(hhmm: String?): String = runCatching {
    val t = LocalTime.parse(hhmm)
    val h = t.hour % 12
    "${if (t.hour < 12) "오전" else "오후"} ${if (h == 0) 12 else h}시${if (t.minute > 0) " ${t.minute}분" else ""}"
}.getOrDefault(hhmm ?: "")

/** 오늘 픽업 1건 (V01 Driving 카드) */
@Composable
private fun PickupCard(p: WeeklyPickups.Pickup, selected: Boolean, onClick: (() -> Unit)?) {
    val c = Pleos.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(u(22))).background(c.card)
            .border(u(2), if (selected) c.stepDone else Color.Transparent, RoundedCornerShape(u(22)))
            .then(if (onClick != null) Modifier.clickable(role = Role.RadioButton, onClick = onClick) else Modifier)
            .padding(horizontal = u(30), vertical = u(28)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                listOfNotNull(p.childName, p.academyName).joinToString(" · "),
                color = c.text, fontSize = us(28), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(p.classEndTime?.let { "수업 종료 $it" }, p.targetPickupTime?.let { "목표 픽업 $it" }).joinToString(" · "),
                color = c.subText, fontSize = us(20), modifier = Modifier.padding(top = u(8)),
            )
        }
        if (onClick != null) StepRowMarker(selected)
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

@Composable
private fun ReadinessCard(missing: List<String>) {
    val c = Pleos.colors
    StatusCard("시작하기 전에 설정이 필요해요", (missing + "휴대전화 앱에서 설정해 주세요").joinToString(" · "), Modifier.padding(bottom = u(16))) {
        IconCircle(c.danger) { WarningIcon(it, Color.White) }
    }
}

/**
 * V02 추천 픽업 장소 (assets/V02). 대안을 누르면 그 장소가 위로 올라와 길안내 대상이 된다.
 * 내비 적용 성공 후에만 장소 확정 (F1-06, ERR-03).
 */
@Composable
fun V02Recommend(s: UiState, onRoute: (PickupSpots.Spot) -> Unit, onMenu: () -> Unit = {}) {
    val spots = s.spots?.academies?.flatMap { it.spots }.orEmpty().sortedByDescending { it.isDefault }
    var selectedId by remember(spots) { mutableStateOf(spots.firstOrNull()?.spotId) }
    val best = spots.firstOrNull { it.spotId == selectedId } ?: spots.firstOrNull()

    if (s.driving) {
        V02Driving(s, best, onRoute)
        return
    }
    // 정차 중: assets/V02 개선안 (오른쪽 작은 지도)
    V02Parking(s, spots, selectedId, onSelect = { selectedId = it }, onRoute = onRoute, onMenu = onMenu)
}

/** V02 주행 중 (assets/V02 픽업 장소 선택 Driving): 추천 1곳과 길안내만, 대안은 정차 후 */
@Composable
private fun V02Driving(s: UiState, best: PickupSpots.Spot?, onRoute: (PickupSpots.Spot) -> Unit) {
    val c = Pleos.colors
    PleosWindow("픽업 장소 선택") {
        PleosCard(Modifier.fillMaxWidth(), padding = 30) {
            Text(best?.name ?: "안전하게 만날 장소가 없어요", color = c.text, fontSize = us(30), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                best?.etaSeconds?.let { "약 ${(it + 59) / 60}분 후 도착 · ${clockIn(it)}" }
                    ?: best?.walkMinutes?.let { "아이 보행 ${it}분" } ?: "휴대전화 앱에서 픽업 장소를 등록해 주세요",
                color = c.subText, fontSize = us(20), modifier = Modifier.padding(top = u(8)),
            )
        }
        Spacer(Modifier.height(u(19)))
        StatusCard("다른 장소 선택은 정차 후에 할 수 있어요", null) { IconCircle(c.iconCircle) { InfoIcon(it, c.text) } }
        Spacer(Modifier.weight(1f))
        s.error?.let { Text(it, color = c.danger, fontSize = us(18), modifier = Modifier.padding(bottom = u(10))) }
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

    if (s.driving) {
        V03Driving(v, onHold, onRelease)
        return
    }
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
                    // 서버 규칙(2026.11.13~): 도착 3분 전 · 700m에 실내 준비 알림, 합류 안내는 정차 뒤
                    val near = VoiceCues.isNear(v)
                    StepRow(
                        "준비 알림 발송", if (near) null else "도착 3분 전 또는 700m에 보내요",
                        if (near) StepState.Done else StepState.Pending,
                    )
                    val prep = child?.extraPrepMinutes?.takeIf { it > 0 }?.let { "준비 시간 +${it}분 요청" }
                    StepRow("실내에서 준비 중", prep, if (near) StepState.Current else StepState.Pending)
                    StepRow(
                        "합류 안내 자동 발송",
                        if (call?.held == true) "보류 중" else "정차하면 보내요",
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

/** V03 주행 중 (assets/V03 Driving): 정차 예상 시각을 크게, 장소와 아이 상태, 보류 1탭 */
@Composable
private fun V03Driving(v: SessionView, onHold: () -> Unit, onRelease: () -> Unit) {
    val c = Pleos.colors
    val call = v.call
    PleosWindow("픽업 진행 중") {
        PleosCard(Modifier.fillMaxWidth(), padding = 24) {
            Label("정차 예상")
            Text(
                if (v.eta?.stale == true) "--:--" else clock(v.eta?.stopEtaAt),
                color = c.text, fontSize = us(96), fontWeight = FontWeight.Bold,
                style = TextStyle(lineHeight = us(100), platformStyle = PlatformTextStyle(includeFontPadding = false)),
            )
        }
        Spacer(Modifier.height(u(19)))
        PleosCard(Modifier.fillMaxWidth(), padding = 24) {
            Text(v.spot?.name ?: "-", color = c.text, fontSize = us(24), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Divider(Modifier.padding(vertical = u(18)))
            Text(childLine(v), color = c.text, fontSize = us(22), fontWeight = FontWeight.SemiBold)
            Text(
                when {
                    call?.held == true -> "합류 안내 보류 중 · 보류를 풀면 다시 보내요"
                    call?.mode == "GUARDIAN_CONFIRM" -> "합류 안내는 보호자 확인 후 발송"
                    else -> "합류 안내는 정차하면 자동 발송"
                },
                color = c.subText, fontSize = us(18), modifier = Modifier.padding(top = u(6)),
            )
        }
        Spacer(Modifier.weight(1f))
        if (call?.held == true) PleosButton("자동 호출 보류 해제", ButtonTone.Secondary, onClick = onRelease)
        else PleosButton("자동 호출 보류", ButtonTone.Secondary, onClick = onHold)
        Spacer(Modifier.height(u(23)))
    }
}

/** 아이 상태 한 줄 (여러 명이면 첫 아이 기준 + 외 N명) */
internal fun childLine(v: SessionView): String {
    val child = v.children.firstOrNull()
    val phase = when (child?.phase) {
        "CALLED" -> if (child.acknowledgedAt != null) "만남 장소로 이동 중" else "합류 안내 받음"
        "MEETING" -> "만남 장소에서 기다리는 중"
        "NOT_BOARDED" -> "탑승하지 않음"
        else -> "실내에서 대기 중"
    }
    return if (v.children.size > 1) "${child?.name ?: "아이"} 외 ${v.children.size - 1}명 · $phase" else phase
}

private fun callTexts(v: SessionView): Pair<String, String> {
    val c = v.call
    return when {
        c?.held == true -> "합류 안내 보류 중" to "보류를 풀면 다시 자동으로 보내요"
        c?.mode == "GUARDIAN_CONFIRM" -> "합류 안내 보호자 확인" to "정차하면 보호자 확인 후 보내요"
        VoiceCues.isNear(v) -> "합류 안내 자동 발송" to "실내 준비 알림을 보냈어요 · 정차하면 합류 안내"
        else -> "합류 안내 자동 발송" to "도착 3분 전 준비 알림 · 정차하면 합류 안내"
    }
}

internal fun distance(m: Int) = if (m >= 1000) "%.1fkm".format(m / 1000.0) else "${m}m"

internal val SEOUL = ZoneId.of("Asia/Seoul")
private val WEEK_KO = listOf("월", "화", "수", "목", "금", "토", "일")
internal fun clockIn(seconds: Int): String = LocalTime.now(SEOUL).plusSeconds(seconds.toLong()).format(CLOCK)

