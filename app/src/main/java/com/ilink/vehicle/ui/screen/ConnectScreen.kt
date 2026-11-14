package com.ilink.vehicle.ui.screen

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.rememberLottieDynamicProperties
import com.airbnb.lottie.compose.rememberLottieDynamicProperty
import com.ilink.vehicle.R
import com.ilink.vehicle.data.repository.LinkState
import com.ilink.vehicle.ui.UiState
import com.ilink.vehicle.ui.component.DesignCanvas
import com.ilink.vehicle.ui.component.QrCode
import com.ilink.vehicle.ui.component.u
import com.ilink.vehicle.ui.component.us
import kotlinx.coroutines.delay
import java.time.Instant

/*
 * V00 서비스 시작 · 연결 (정차 중) — 2차 시안 좌표(앱 창 1305 x 908) 그대로.
 * 왼쪽: QR 카드(새 코드 받기 · QR · 남은 시간 · 안내), 오른쪽: 노란 카드(문구 + 차가 달려와 깃발 앞에 서는 애니메이션).
 * 오른쪽 위 메뉴는 '나중에 하기'(앱을 뒤로 보냄). 주행 중 화면은 LinkScreens의 V00Driving 그대로.
 */

private object C {
    val bg = V01.bg
    val card = Color(0xFF21211F)
    val yellow = V01.yellow
    val sub = Color(0xFF9C9C96)
    val ink = Color(0xFF111200)
    val inkSub = Color(0xFF4A4C1E)
}

@Composable
internal fun V00Parking(s: UiState, onReissue: () -> Unit, onLater: () -> Unit) {
    DesignCanvas(C.bg) {
        Box(Modifier.fillMaxSize()) {
            // 오른쪽 위 메뉴 (지름 74): 연결 전이라 갈 곳이 없어 '나중에 하기'
            Box(
                Modifier.offset(u(1196), u(49)).size(u(74)).clip(CircleShape).background(C.card)
                    .clickable(role = Role.Button, onClickLabel = "나중에 하기", onClick = onLater),
                contentAlignment = Alignment.Center,
            ) { MenuIcon(u(26), Color(0xFFE6E6E0)) }

            Text(
                "스마트폰을 연결하면\n픽업을 시작할 수 있어요.",
                color = Color.White, fontSize = us(36), lineHeight = us(56),
                modifier = Modifier.offset(u(38), u(183)),
            )

            QrCard(s, onReissue, Modifier.offset(u(38), u(351)).size(u(697), u(507)))
            YellowCard(Modifier.offset(u(750), u(351)).size(u(518), u(507)))
        }
    }
}

/** QR 카드 697 x 507: '새 코드 받기'(위) · QR 208 · 남은 시간 · 안내 */
@Composable
private fun QrCard(s: UiState, onReissue: () -> Unit, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(u(44))).background(C.card)) {
        // 새 코드 받기 (터치 영역 넉넉히)
        Row(
            Modifier.offset(y = u(48)).fillMaxWidth().height(u(48)),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.clip(RoundedCornerShape(u(24))).clickable(enabled = !s.busy, role = Role.Button, onClick = onReissue)
                    .height(u(48)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.width(u(14)))
                RefreshIcon(u(20), C.sub)
                Spacer(Modifier.width(u(8)))
                Text("새 코드 받기", color = C.sub, fontSize = us(17))
                Spacer(Modifier.width(u(14)))
            }
        }
        Column(
            Modifier.offset(y = u(112)).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                s.link is LinkState.Waiting -> {
                    // 여백 1칸 포함 225 → 보이는 QR이 시안의 208과 같다
                    QrCode(s.link.code.qrPayload, u(225), dot = Color.White, background = C.card)
                    Spacer(Modifier.height(u(26)))
                    Remaining(s.link.code.expiresAt)
                    Spacer(Modifier.height(u(30)))
                    Text("스마트폰으로 QR 코드를 스캔해 주세요.", color = Color.White, fontSize = us(20))
                }
                s.link is LinkState.Expired -> Message("QR 코드 시간이 지났어요", "위의 '새 코드 받기'를 눌러 주세요")
                s.error != null -> Message(s.error, "위의 '새 코드 받기'를 눌러 다시 시도해 주세요")
                else -> {
                    Spacer(Modifier.height(u(80)))
                    CircularProgressIndicator(color = C.yellow, modifier = Modifier.size(u(48)))
                    Spacer(Modifier.height(u(24)))
                    Text("연결 코드를 준비하고 있어요", color = C.sub, fontSize = us(20))
                }
            }
        }
    }
}

@Composable
private fun Message(title: String, sub: String) {
    Spacer(Modifier.height(u(90)))
    Text(title, color = Color.White, fontSize = us(26), fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    Spacer(Modifier.height(u(12)))
    Text(sub, color = C.sub, fontSize = us(19), textAlign = TextAlign.Center)
}

/** '남은 시간 02:45' (시각만 노랑). 1초마다 줄어든다 */
@Composable
private fun Remaining(expiresAt: String) {
    val end = remember(expiresAt) { runCatching { Instant.parse(expiresAt).toEpochMilli() }.getOrNull() }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(end) {
        while (end != null && now < end) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }
    val left = ((end ?: now) - now).coerceAtLeast(0) / 1000
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("남은 시간 ", color = C.sub, fontSize = us(20))
        Text("%02d:%02d".format(left / 60, left % 60), color = C.yellow, fontSize = us(24), fontWeight = FontWeight.Bold)
    }
}

/**
 * 노란 카드 518 x 507: '안심 픽업의 시작' · '우리 아이와 만나는 가장 안전한 연결',
 * 아래에 차가 달려와 깃발 앞에 서는 애니메이션(모바일 스플래시와 같은 res/raw/ilink_splash.json).
 * 애니메이션의 어두운 길은 노란 바탕에 맞게 숨기고 밝은 길을 따로 그린다.
 */
@Composable
private fun YellowCard(modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(u(44))).background(C.yellow)) {
        Text("안심 픽업의 시작", color = C.inkSub, fontSize = us(19), modifier = Modifier.offset(u(44), u(44)))
        Text(
            "우리 아이와 만나는\n가장 안전한 연결", color = C.ink, fontSize = us(34), fontWeight = FontWeight.ExtraBold,
            lineHeight = us(48), modifier = Modifier.offset(u(44), u(88)),
        )
        // 길: 노란 바탕보다 살짝 어두운 띠 (시안: 왼쪽에서 서서히 나타나 오른쪽 끝까지)
        Box(
            Modifier.offset(y = u(451)).fillMaxWidth().height(u(8))
                .background(Brush.horizontalGradient(0f to Color.Transparent, 0.2f to Color.Transparent, 0.55f to Color(0x1A111200), 1f to Color(0x26111200))),
        )
        // 애니메이션 456 x 140 (원본 390 x 120을 1.17배): 차 왼쪽이 카드 x 200, 차 위쪽이 y 359에 오게
        CarAndFlag(Modifier.offset(u(111), u(329)).size(u(456), u(140)))
    }
}

@Composable
private fun CarAndFlag(modifier: Modifier) {
    val still = LocalInspectionMode.current || Build.FINGERPRINT == "robolectric"
    val context = androidx.compose.ui.platform.LocalContext.current
    // 미리보기 · 캡처는 비동기 로딩을 기다리지 않으므로 바로 읽는다
    val sync = remember(still) {
        if (still) com.airbnb.lottie.LottieCompositionFactory.fromRawResSync(context, R.raw.ilink_splash).value else null
    }
    val loaded by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.ilink_splash))
    val composition = sync ?: loaded
    val progress by animateLottieCompositionAsState(composition, iterations = 1, isPlaying = !still)
    val hideRoad = rememberLottieDynamicProperties(
        rememberLottieDynamicProperty(LottieProperty.TRANSFORM_OPACITY, 0, "Road base", "**"),
        rememberLottieDynamicProperty(LottieProperty.TRANSFORM_OPACITY, 0, "Road streak", "**"),
        rememberLottieDynamicProperty(LottieProperty.TRANSFORM_OPACITY, 0, "Road matte", "**"),
        // 시안: 노란 바탕 위 깃발은 어두운 색 (원본은 노랑이라 묻힌다). 원본 그러데이션 색 4개와 개수를 맞춘다
        rememberLottieDynamicProperty(LottieProperty.GRADIENT_COLOR, arrayOf(0xFF2A2A26.toInt(), 0xFF4A4A44.toInt(), 0xFF2E2E2A.toInt(), 0xFF111200.toInt()), "Flag", "flag_2", "**"),
    )
    // 미리보기 · 캡처에서는 마지막 장면(깃발 앞에 선 차)
    LottieAnimation(composition, { if (still) 1f else progress }, modifier, dynamicProperties = hideRoad)
}

/** 새로고침 화살표 (원호 + 화살촉) */
@Composable
private fun RefreshIcon(size: Dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val st = w * 0.11f
        drawArc(color, 300f, 300f, false, Offset(w * 0.14f, w * 0.14f), androidx.compose.ui.geometry.Size(w * 0.72f, w * 0.72f), style = Stroke(st, cap = StrokeCap.Round))
        drawLine(color, Offset(w * 0.82f, w * 0.08f), Offset(w * 0.82f, w * 0.32f), st, StrokeCap.Round)
        drawLine(color, Offset(w * 0.82f, w * 0.32f), Offset(w * 0.58f, w * 0.32f), st, StrokeCap.Round)
    }
}
