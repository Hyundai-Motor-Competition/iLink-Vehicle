package com.ilink.vehicle.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.ilink.vehicle.R
import com.ilink.vehicle.ui.theme.Pleos
import kotlinx.coroutines.delay
import java.time.Instant
import com.ilink.vehicle.data.repository.LinkState
import com.ilink.vehicle.ui.UiState
import com.ilink.vehicle.ui.component.AdaptiveScreen
import com.ilink.vehicle.ui.component.PrimaryAction
import com.ilink.vehicle.ui.component.ButtonTone
import com.ilink.vehicle.ui.component.Pill
import com.ilink.vehicle.ui.component.PleosButton
import com.ilink.vehicle.ui.component.PleosWindow
import com.ilink.vehicle.ui.component.QrCode
import com.ilink.vehicle.ui.component.u
import com.ilink.vehicle.ui.component.us
import com.ilink.vehicle.ui.component.SecondaryAction

/**
 * V00 서비스 시작·연결 (assets/V00 … Landscape · Light / Dark).
 * 주행 중에는 QR을 숨긴다 (QA-27).
 */
@Composable
fun V00Connect(s: UiState, onReissue: () -> Unit, onLater: () -> Unit = {}) {
    val c = Pleos.colors
    PleosWindow(stringResource(R.string.app_name), titleBold = false) {
        Column(Modifier.weight(1f).padding(horizontal = u(24))) {
            Text(
                "스마트 폰을 연결하면 픽업을 시작할 수 있어요.",
                color = c.text, fontSize = us(32), fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = u(24), bottom = u(26)),
            )
            // QR 영역
            Box(
                Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(u(24))).background(c.panel),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    when {
                        s.drivingRestricted -> PanelMessage("정차 후 연결할 수 있어요", "주행 중에는 QR 코드를 보여 주지 않아요")
                        s.error != null && s.link !is LinkState.Waiting -> PanelMessage(s.error, "새 QR 코드를 받아 다시 시도해 주세요")
                        s.link is LinkState.Waiting -> {
                            // 여백 1칸 포함 283 → 보이는 QR이 디자인의 262와 같다
                            QrCode(s.link.code.qrPayload, u(283), dot = c.qrDot, background = c.panel)
                            Spacer(Modifier.height(u(18)))
                            Text("스마트폰으로 QR 코드를 스캔해 주세요", color = c.text, fontSize = us(22), fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(u(14)))
                            Countdown(s.link.code.expiresAt)
                        }
                        s.link is LinkState.Expired -> {
                            PanelMessage("QR 코드 시간이 지났어요", "새 QR 코드를 받아 주세요")
                            Spacer(Modifier.height(u(14)))
                            Pill("남은 시간 00:00")
                        }
                        else -> {
                            CircularProgressIndicator(color = c.accent, modifier = Modifier.size(u(48)))
                            Spacer(Modifier.height(u(24)))
                            Text("연결 코드를 준비하고 있어요", color = c.subText, fontSize = us(22))
                        }
                    }
                }
            }
            Spacer(Modifier.height(u(18)))
            PleosButton("새 QR 코드 받기", enabled = !s.drivingRestricted && !s.busy, onClick = onReissue)
            Spacer(Modifier.height(u(20)))
            PleosButton("나중에 하기", ButtonTone.Secondary, onClick = onLater)
            Spacer(Modifier.height(u(24)))
        }
    }
}

@Composable
private fun PanelMessage(title: String, sub: String) {
    val c = Pleos.colors
    Text(title, color = c.text, fontSize = us(26), fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    Text(sub, color = c.subText, fontSize = us(20), textAlign = TextAlign.Center, modifier = Modifier.padding(top = u(10)))
}

/** 코드 만료까지 남은 시간. 1초마다 줄어든다 */
@Composable
private fun Countdown(expiresAt: String) {
    val end = remember(expiresAt) { runCatching { Instant.parse(expiresAt).toEpochMilli() }.getOrNull() }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(end) {
        while (end != null && now < end) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }
    val left = ((end ?: now) - now).coerceAtLeast(0) / 1000
    Pill("남은 시간 %02d:%02d".format(left / 60, left % 60))
}

/** V07 설정 요약. 수정은 휴대전화에서 */
@Composable
fun V07Settings(s: UiState, onBack: () -> Unit, onLogout: () -> Unit) {
    AdaptiveScreen("V07", "설정 요약") {
        val st = s.settings
        Text("보호자 ${st?.guardianNameMasked ?: "-"}", style = MaterialTheme.typography.bodyLarge)
        Text("자녀 ${st?.children?.joinToString { it.name ?: "" } ?: "-"}", style = MaterialTheme.typography.bodyLarge)
        Text("합류 안내 ${if (st?.joinCallMode == "GUARDIAN_CONFIRM") "보호자 확인" else "자동"}", style = MaterialTheme.typography.bodyLarge)
        Text("설정 변경은 휴대전화 앱에서 할 수 있어요", style = MaterialTheme.typography.bodyMedium)
        PrimaryAction("돌아가기", onClick = onBack)
        SecondaryAction("로그아웃", enabled = !s.drivingRestricted, onClick = onLogout)
    }
}
