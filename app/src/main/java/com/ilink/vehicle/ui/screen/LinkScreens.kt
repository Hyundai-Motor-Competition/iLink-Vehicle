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
import com.ilink.vehicle.ui.component.IconCircle
import com.ilink.vehicle.ui.component.CarIcon
import com.ilink.vehicle.ui.component.PleosWindow
import com.ilink.vehicle.ui.component.QrCode
import com.ilink.vehicle.ui.component.u
import com.ilink.vehicle.ui.component.us
import com.ilink.vehicle.ui.component.SecondaryAction

/**
 * V00 서비스 시작·연결. 정차 중은 2차 시안(V00Parking), 주행 중에는 QR을 숨기고 안내만 (QA-27).
 */
@Composable
fun V00Connect(s: UiState, onReissue: () -> Unit, onLater: () -> Unit = {}) {
    if (s.driving) {
        V00Driving()
        return
    }
    // 정차 중: 2차 시안 (QR 카드 + 노란 카드, ConnectScreen.kt)
    V00Parking(s, onReissue, onLater)
}

/** V00 주행 중 (assets/V00 Driving): QR과 버튼을 모두 숨기고, 정차하면 저절로 QR 화면으로 돌아간다 */
@Composable
private fun V00Driving() {
    val c = Pleos.colors
    PleosWindow(stringResource(R.string.app_name), titleBold = false) {
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = u(40))) {
                IconCircle(c.iconCircle, 90) { CarIcon(it, c.text) }
                Spacer(Modifier.height(u(34)))
                Text("안전을 위해 주행 중에는 로그인을 할 수 없어요", color = c.text, fontSize = us(32), fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(u(20)))
                Text("차량을 안전한 곳에 정차하면 화면이 자동으로 다시 나타나요.", color = c.subText, fontSize = us(20), textAlign = TextAlign.Center)
            }
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
    // 기획 QA-27: 주행 중 V07은 '정차 후 이용' 안내만
    if (s.driving) {
        AdaptiveScreen("V07", "설정 요약") {
            Text("정차 후 이용할 수 있어요", style = MaterialTheme.typography.titleMedium)
            Text("안전을 위해 주행 중에는 설정을 볼 수 없어요", style = MaterialTheme.typography.bodyLarge)
            PrimaryAction("돌아가기", onClick = onBack)
        }
        return
    }
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
