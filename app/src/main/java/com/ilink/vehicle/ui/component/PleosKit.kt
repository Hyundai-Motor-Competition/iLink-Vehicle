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
    trailing: (@Composable () -> Unit)? = null,
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
                        modifier = Modifier.weight(1f),
                    )
                    trailing?.invoke()
                }
                Spacer(Modifier.height(u(16)))
                content()
            }
        }
    }
}

/**
 * 머리(아이콘 + 제목) 없이 디자인 단위만 맞춘 빈 앱 창. 디자인 좌표(1305 x 908)를 그대로 옮길 때 쓴다 (V01 개선안).
 */
@Composable
fun DesignCanvas(background: Color, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier.fillMaxSize().background(background)) {
        val k = min(maxWidth.value / DESIGN_W, maxHeight.value / DESIGN_H).coerceIn(0.5f, 1.6f)
        CompositionLocalProvider(LocalUnits provides DesignUnits(k)) { content() }
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

/**
 * 진행 단계 한 줄.
 * @param green 합류·탑승 단계(V04·V05)는 완료를 초록 체크로, 현재를 파란 점 고리로 그린다. V03은 파랑 체크.
 */
@Composable
fun StepRow(title: String, sub: String?, state: StepState, green: Boolean = false, markSize: Number = 28) {
    val c = Pleos.colors
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = u(6))) {
        Box(Modifier.size(u(markSize)), contentAlignment = Alignment.Center) {
            when (state) {
                StepState.Done -> CheckCircle(u(markSize), if (green) c.meet else c.stepDone)
                StepState.Current -> if (green) {
                    Box(
                        Modifier.size(u(markSize)).clip(CircleShape).background(c.card).border(u(markSize.toFloat() * 0.2f), c.stepDone, CircleShape),
                    )
                } else {
                    Box(Modifier.size(u(markSize)).clip(CircleShape).background(c.stepCurrentFill).border(u(3), c.stepDone, CircleShape))
                }
                StepState.Pending -> if (green) {
                    Box(Modifier.size(u(markSize)).clip(CircleShape).border(u(2), c.stepPending, CircleShape))
                } else {
                    Box(Modifier.size(u(markSize)).clip(CircleShape).background(c.stepPending))
                }
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

/** 원 안에 체크 */
@Composable
fun CheckCircle(size: Dp, color: Color) {
    Canvas(Modifier.size(size)) {
        drawCircle(color)
        val s = this.size.minDimension
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(s * 0.28f, s * 0.52f); lineTo(s * 0.44f, s * 0.68f); lineTo(s * 0.73f, s * 0.36f)
        }
        drawPath(path, Color.White, style = Stroke(width = s * 0.1f, cap = StrokeCap.Round))
    }
}

/** 동그라미 안 아이콘 (상태 카드 · 대안 장소 행) */
@Composable
fun IconCircle(bg: Color, size: Number = 55, icon: @Composable (Dp) -> Unit) {
    Box(Modifier.size(u(size)).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        icon(u(size.toFloat() * 0.5f))
    }
}

/** 아이콘 + 제목 + 한 줄 설명 카드 (V04·V05·V06 주행 중, U01~U08 음성 안내) */
@Composable
fun StatusCard(
    title: String,
    sub: String?,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    icon: @Composable () -> Unit,
) {
    val c = Pleos.colors
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(u(22))).background(c.card).padding(horizontal = u(24), vertical = u(23)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(u(19)))
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text, fontSize = us(22), fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            sub?.let { Text(it, color = c.subText, fontSize = us(18), modifier = Modifier.padding(top = u(3)), maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
        trailing?.invoke()
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

@Composable
fun CalendarIcon(size: Dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val w = s * 0.08f
        drawRoundRect(color, Offset(s * 0.16f, s * 0.22f), Size(s * 0.68f, s * 0.62f), CornerRadius(s * 0.1f), style = Stroke(w))
        drawLine(color, Offset(s * 0.16f, s * 0.42f), Offset(s * 0.84f, s * 0.42f), w)
        drawLine(color, Offset(s * 0.34f, s * 0.12f), Offset(s * 0.34f, s * 0.3f), w, StrokeCap.Round)
        drawLine(color, Offset(s * 0.66f, s * 0.12f), Offset(s * 0.66f, s * 0.3f), w, StrokeCap.Round)
    }
}

/** 종이비행기 (보냄) */
@Composable
fun SendIcon(size: Dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(s * 0.86f, s * 0.14f); lineTo(s * 0.12f, s * 0.44f); lineTo(s * 0.46f, s * 0.54f)
            lineTo(s * 0.56f, s * 0.88f); close()
        }
        drawPath(path, color, style = Stroke(s * 0.08f, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        drawLine(color, Offset(s * 0.46f, s * 0.54f), Offset(s * 0.86f, s * 0.14f), s * 0.08f, StrokeCap.Round)
    }
}

/** 기다리는 중 (열린 원) */
@Composable
fun SpinnerIcon(size: Dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        drawArc(color, -60f, 300f, false, Offset(s * 0.18f, s * 0.18f), Size(s * 0.64f, s * 0.64f), style = Stroke(s * 0.1f, cap = StrokeCap.Round))
    }
}

@Composable
fun CheckIcon(size: Dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(s * 0.2f, s * 0.52f); lineTo(s * 0.42f, s * 0.72f); lineTo(s * 0.8f, s * 0.3f)
        }
        drawPath(path, color, style = Stroke(s * 0.1f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

/** 원 테두리 + 체크 (U07 완료) */
@Composable
fun CheckRingIcon(size: Dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        drawCircle(color, s * 0.4f, style = Stroke(s * 0.08f))
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(s * 0.32f, s * 0.52f); lineTo(s * 0.45f, s * 0.64f); lineTo(s * 0.68f, s * 0.38f)
        }
        drawPath(path, color, style = Stroke(s * 0.08f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

/** 경고 삼각형 */
@Composable
fun WarningIcon(size: Dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(s * 0.5f, s * 0.14f); lineTo(s * 0.9f, s * 0.84f); lineTo(s * 0.1f, s * 0.84f); close()
        }
        drawPath(path, color, style = Stroke(s * 0.08f, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        drawLine(color, Offset(s * 0.5f, s * 0.4f), Offset(s * 0.5f, s * 0.6f), s * 0.08f, StrokeCap.Round)
        drawCircle(color, s * 0.045f, Offset(s * 0.5f, s * 0.72f))
    }
}

@Composable
fun MicIcon(size: Dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val w = s * 0.08f
        drawRoundRect(color, Offset(s * 0.37f, s * 0.12f), Size(s * 0.26f, s * 0.46f), CornerRadius(s * 0.13f), style = Stroke(w))
        drawArc(color, 0f, 180f, false, Offset(s * 0.24f, s * 0.3f), Size(s * 0.52f, s * 0.4f), style = Stroke(w, cap = StrokeCap.Round))
        drawLine(color, Offset(s * 0.5f, s * 0.7f), Offset(s * 0.5f, s * 0.86f), w, StrokeCap.Round)
    }
}

/** 서비스 경로 아이콘 (앱 아이콘 안 모양) */
@Composable
fun RouteIcon(size: Dp, color: Color) {
    Image(
        painterResource(R.drawable.ic_service_route), contentDescription = null,
        colorFilter = ColorFilter.tint(color), modifier = Modifier.size(size),
    )
}

/** 톱니바퀴 (설정) */
@Composable
fun GearIcon(size: Dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val c = Offset(s / 2, s / 2)
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0)
            val x1 = c.x + (s * 0.30f) * kotlin.math.cos(a).toFloat()
            val y1 = c.y + (s * 0.30f) * kotlin.math.sin(a).toFloat()
            val x2 = c.x + (s * 0.44f) * kotlin.math.cos(a).toFloat()
            val y2 = c.y + (s * 0.44f) * kotlin.math.sin(a).toFloat()
            drawLine(color, Offset(x1, y1), Offset(x2, y2), s * 0.12f, StrokeCap.Round)
        }
        drawCircle(color, s * 0.30f, c, style = Stroke(s * 0.09f))
        drawCircle(color, s * 0.11f, c, style = Stroke(s * 0.08f))
    }
}
