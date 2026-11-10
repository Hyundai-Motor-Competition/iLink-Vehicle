package com.ilink.vehicle.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ilink.vehicle.R
import com.ilink.vehicle.ui.theme.Pleos
import kotlin.math.min

/*
 * Pleos Connect 앱 창 공통 부품 (assets/V00~V03 디자인 기준).
 * 디자인의 앱 창은 1305 x 908 단위다. 실제 창 크기에 맞춰 비율(k)로 모든 크기를 함께 바꾼다.
 */

/** 디자인 단위 → dp/sp 변환 */
@Immutable
class DesignUnits(val k: Float) {
    fun dp(v: Number): Dp = (v.toFloat() * k).dp
    fun sp(v: Number): TextUnit = (v.toFloat() * k).sp
}

val LocalUnits = staticCompositionLocalOf { DesignUnits(1f) }

@Composable
fun u(v: Number): Dp = LocalUnits.current.dp(v)

@Composable
fun us(v: Number): TextUnit = LocalUnits.current.sp(v)

/** 디자인 앱 창 크기 (단위) */
const val DESIGN_W = 1305f
const val DESIGN_H = 908f

/**
 * 앱 창 틀: 왼쪽 위 서비스 아이콘 + 제목, 그 아래 내용.
 * @param titleBold V00은 서비스 이름(보통 굵기), V01~는 화면 제목(굵게)
 */
@Composable
fun PleosWindow(
    title: String,
    titleBold: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Pleos.colors
    BoxWithConstraints(modifier.fillMaxSize().background(c.window)) {
        val k = min(maxWidth.value / DESIGN_W, maxHeight.value / DESIGN_H).coerceIn(0.5f, 1.6f)
        CompositionLocalProvider(LocalUnits provides DesignUnits(k)) {
            Column(Modifier.fillMaxSize().padding(start = u(24), end = u(24), top = u(30), bottom = u(24))) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon()
                    Spacer(Modifier.width(u(22)))
                    Text(
                        title,
                        color = if (titleBold) c.text else c.serviceName,
                        fontSize = us(30),
                        fontWeight = if (titleBold) FontWeight.Bold else FontWeight.Normal,
                    )
                }
                Spacer(Modifier.height(u(16)))
                content()
            }
        }
    }
}

@Composable
fun AppIcon(size: Number = 60) {
    val c = Pleos.colors
    Box(
        Modifier.size(u(size)).clip(RoundedCornerShape(u(size.toFloat() * 0.23f)))
            .background(Brush.linearGradient(listOf(c.accent, c.accentDeep))),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painterResource(R.drawable.ic_service_route), contentDescription = null,
            colorFilter = ColorFilter.tint(Color.White), modifier = Modifier.size(u(size.toFloat() * 0.6f)),
        )
    }
}

/** 둥근 정보 카드 */
@Composable
fun PleosCard(modifier: Modifier = Modifier, padding: Number = 24, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(u(22))).background(Pleos.colors.card).padding(u(padding)),
        content = content,
    )
}

enum class ButtonTone { Primary, Secondary }

/** 디자인의 큰 버튼 (높이 76, 모서리 18) */
@Composable
fun PleosButton(
    label: String,
    tone: ButtonTone = ButtonTone.Primary,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c = Pleos.colors
    val (bg, fg) = when (tone) {
        ButtonTone.Primary -> c.primaryButton to c.onPrimaryButton
        ButtonTone.Secondary -> c.secondaryButton to c.onSecondaryButton
    }
    Box(
        modifier.fillMaxWidth().height(u(76)).clip(RoundedCornerShape(u(20)))
            .background(if (enabled) bg else bg.copy(alpha = 0.4f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (enabled) fg else fg.copy(alpha = 0.5f), fontSize = us(22), fontWeight = FontWeight.SemiBold)
    }
}

/** 작은 알약. accent면 파란 바탕(예: 예측 안정 · 승인됨) */
@Composable
fun Pill(text: String, accent: Boolean = false) {
    val c = Pleos.colors
    Text(
        text,
        color = if (accent) Color.White else c.onPill,
        fontSize = us(18),
        fontWeight = if (accent) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (accent) c.stepDone else c.pill)
            .padding(horizontal = u(16), vertical = u(6)),
    )
}

@Composable
fun Label(text: String, modifier: Modifier = Modifier) {
    Text(text, color = Pleos.colors.subText, fontSize = us(18), modifier = modifier)
}

@Composable
fun Divider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Pleos.colors.divider))
}

enum class StepState { Done, Current, Pending }

/** 진행 단계 한 줄 (V03 아이 준비 상태) */
@Composable
fun StepRow(title: String, sub: String?, state: StepState) {
    val c = Pleos.colors
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = u(6))) {
        Box(Modifier.size(u(28)), contentAlignment = Alignment.Center) {
            when (state) {
                StepState.Done -> Canvas(Modifier.size(u(28))) {
                    drawCircle(c.stepDone)
                    val s = size.minDimension
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(s * 0.28f, s * 0.52f); lineTo(s * 0.44f, s * 0.68f); lineTo(s * 0.73f, s * 0.36f)
                    }
                    drawPath(path, Color.White, style = Stroke(width = s * 0.1f, cap = StrokeCap.Round))
                }
                StepState.Current -> Box(
                    Modifier.size(u(28)).clip(CircleShape).background(c.stepCurrentFill).border(u(3), c.stepDone, CircleShape),
                )
                StepState.Pending -> Box(Modifier.size(u(28)).clip(CircleShape).background(c.stepPending))
            }
        }
        Spacer(Modifier.width(u(14)))
        Column {
            Text(
                title,
                color = if (state == StepState.Pending) c.subText else c.text,
                fontSize = us(19),
                fontWeight = if (state == StepState.Pending) FontWeight.Normal else FontWeight.SemiBold,
            )
            sub?.let { Text(it, color = c.subText, fontSize = us(17), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

// ── 직접 그린 작은 아이콘 (Material 기본 세트에 없는 것) ──

@Composable
fun ClockIcon(size: Dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        drawCircle(color, radius = s * 0.42f, style = Stroke(width = s * 0.08f))
        drawLine(color, center, Offset(center.x, s * 0.26f), strokeWidth = s * 0.08f, cap = StrokeCap.Round)
        drawLine(color, center, Offset(s * 0.68f, s * 0.6f), strokeWidth = s * 0.08f, cap = StrokeCap.Round)
    }
}

@Composable
fun CarIcon(size: Dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val w = s * 0.08f
        drawRoundRect(color, Offset(s * 0.14f, s * 0.42f), Size(s * 0.72f, s * 0.3f), CornerRadius(s * 0.08f), style = Stroke(w))
        drawLine(color, Offset(s * 0.24f, s * 0.42f), Offset(s * 0.32f, s * 0.24f), w, StrokeCap.Round)
        drawLine(color, Offset(s * 0.32f, s * 0.24f), Offset(s * 0.68f, s * 0.24f), w, StrokeCap.Round)
        drawLine(color, Offset(s * 0.68f, s * 0.24f), Offset(s * 0.76f, s * 0.42f), w, StrokeCap.Round)
        drawCircle(color, s * 0.05f, Offset(s * 0.3f, s * 0.57f))
        drawCircle(color, s * 0.05f, Offset(s * 0.7f, s * 0.57f))
        drawLine(color, Offset(s * 0.24f, s * 0.72f), Offset(s * 0.24f, s * 0.8f), w, StrokeCap.Round)
        drawLine(color, Offset(s * 0.76f, s * 0.72f), Offset(s * 0.76f, s * 0.8f), w, StrokeCap.Round)
    }
}

@Composable
fun BuildingIcon(size: Dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val w = s * 0.07f
        drawRect(color, Offset(s * 0.22f, s * 0.2f), Size(s * 0.56f, s * 0.64f), style = Stroke(w))
        for (row in 0..2) for (col in 0..1) {
            drawRect(color, Offset(s * (0.33f + col * 0.22f), s * (0.3f + row * 0.15f)), Size(s * 0.12f, s * 0.07f))
        }
        drawLine(color, Offset(s * 0.12f, s * 0.84f), Offset(s * 0.88f, s * 0.84f), w, StrokeCap.Round)
    }
}

@Composable
fun PinIcon(size: Dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val w = s * 0.08f
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(s * 0.5f, s * 0.9f)
            cubicTo(s * 0.2f, s * 0.6f, s * 0.18f, s * 0.48f, s * 0.18f, s * 0.4f)
            cubicTo(s * 0.18f, s * 0.2f, s * 0.33f, s * 0.08f, s * 0.5f, s * 0.08f)
            cubicTo(s * 0.67f, s * 0.08f, s * 0.82f, s * 0.2f, s * 0.82f, s * 0.4f)
            cubicTo(s * 0.82f, s * 0.48f, s * 0.8f, s * 0.6f, s * 0.5f, s * 0.9f)
            close()
        }
        drawPath(path, color, style = Stroke(w))
        drawCircle(color, s * 0.1f, Offset(s * 0.5f, s * 0.4f), style = Stroke(w))
    }
}

@Composable
fun InfoIcon(size: Dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        drawCircle(color, s * 0.42f, style = Stroke(s * 0.08f))
        drawLine(color, Offset(s * 0.5f, s * 0.45f), Offset(s * 0.5f, s * 0.72f), s * 0.09f, StrokeCap.Round)
        drawCircle(color, s * 0.05f, Offset(s * 0.5f, s * 0.31f))
    }
}

@Composable
fun ChevronIcon(size: Dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        drawLine(color, Offset(s * 0.38f, s * 0.2f), Offset(s * 0.66f, s * 0.5f), s * 0.08f, StrokeCap.Round)
        drawLine(color, Offset(s * 0.66f, s * 0.5f), Offset(s * 0.38f, s * 0.8f), s * 0.08f, StrokeCap.Round)
    }
}
