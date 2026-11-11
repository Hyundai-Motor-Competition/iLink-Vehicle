package com.ilink.vehicle.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ilink.vehicle.demo.DemoWorld

/**
 * demo 빌드에서만 오른쪽 위에 뜨는 조작 패널.
 * 주행 규제·도어 신호 끊김·정차 실패·음성 명령을 에뮬레이터 콘솔 없이 바꿔 본다.
 * 기어가 D면 자동으로 주행 화면이 된다. '주행 화면 강제'는 정차 중에도 주행 화면을 볼 때 쓴다.
 */
@Composable
fun DemoPanel(demo: DemoWorld, onReset: () -> Unit, onVoice: (String) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val restricted by demo.restrictedControl.collectAsStateWithLifecycle()
    val doorOk by demo.doorAvailableControl.collectAsStateWithLifecycle()

    Column(modifier.padding(12.dp), horizontalAlignment = Alignment.End) {
        if (open) {
            Surface(tonalElevation = 6.dp, shadowElevation = 6.dp, shape = MaterialTheme.shapes.large) {
                Column(Modifier.width(280.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Toggle("주행 화면 강제", restricted) { demo.setRestricted(it) }
                    Toggle("도어 신호 받기", doorOk) { demo.setDoorAvailable(it) }
                    FilledTonalButton(onClick = demo::arriveNow) { Text("바로 도착") }
                    FilledTonalButton(onClick = demo::park) { Text("지금 정차 (P)") }
                    FilledTonalButton(onClick = demo::stopFailed) { Text("정차 실패") }
                    FilledTonalButton(onClick = { onVoice("지금 픽업 상태 어때?") }) { Text("음성: 지금 픽업 상태 어때?") }
                    FilledTonalButton(onClick = { onVoice("보류") }) { Text("음성: 보류") }
                    FilledTonalButton(onClick = onReset) { Text("처음부터 (연결 해제)") }
                }
            }
        }
        // 화면 디자인(오른쪽 위 설정 버튼)을 가리지 않게 오른쪽 아래에 둔다
        AssistChip(onClick = { open = !open }, label = { Text(if (open) "데모 닫기" else "DEMO") })
    }
}

@Composable
private fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = value, onCheckedChange = onChange)
    }
}
