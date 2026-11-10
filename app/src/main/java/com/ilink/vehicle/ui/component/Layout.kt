package com.ilink.vehicle.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Pleos 화면 모델: Portrait(창 1개)는 핵심 정보만, Landscape(창 2개)는 보조 정보를 옆에 더한다.
 * 방향이 아니라 실제 창 비율로 판단한다 (사용자가 창 크기를 바꿀 수 있음).
 */
@Composable
fun AdaptiveScreen(
    screenId: String,
    title: String,
    secondary: (@Composable ColumnScope.() -> Unit)? = null,
    primary: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(24.dp)) {
        val wide = maxWidth > maxHeight && secondary != null
        val header: @Composable ColumnScope.() -> Unit = {
            Text(screenId, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Text(title, style = MaterialTheme.typography.headlineSmall)
        }
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    header()
                    primary()
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) { secondary?.invoke(this) }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                header()
                primary()
            }
        }
    }
}

/** 하단 주요 버튼 (화면당 최대 2개, 큰 터치 영역) */
@Composable
fun PrimaryAction(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SecondaryAction(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun Headline(value: String, caption: String? = null) {
    Column {
        Text(value, style = MaterialTheme.typography.displayMedium)
        caption?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
    }
}
