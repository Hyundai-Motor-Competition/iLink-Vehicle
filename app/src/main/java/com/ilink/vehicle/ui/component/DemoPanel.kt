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
 * 주행 규제·도어 신호 끊김 같은 예외 상황을 에뮬레이터 콘솔 없이 바꿔 본다.
 */
@Composable
fun DemoPanel(demo: DemoWorld, onReset: () -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val restricted by demo.restrictedControl.collectAsStateWithLifecycle()
    val doorOk by demo.doorAvailableControl.collectAsStateWithLifecycle()

    Column(modifier.padding(12.dp), horizontalAlignment = Alignment.End) {
        AssistChip(onClick = { open = !open }, label = { Text(if (open) "데모 닫기" else "DEMO") })
        if (open) {
            Surface(tonalElevation = 6.dp, shadowElevation = 6.dp, shape = MaterialTheme.shapes.large) {
                Column(Modifier.width(280.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Toggle("주행 중 (주행 규제)", restricted) { demo.setRestricted(it) }
                    Toggle("도어 신호 받기", doorOk) { demo.setDoorAvailable(it) }
                    FilledTonalButton(onClick = demo::arriveNow) { Text("바로 도착") }
                    FilledTonalButton(onClick = onReset) { Text("처음부터 (연결 해제)") }
                }
            }
        }
    }
}

@Composable
private fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = value, onCheckedChange = onChange)
    }
}
