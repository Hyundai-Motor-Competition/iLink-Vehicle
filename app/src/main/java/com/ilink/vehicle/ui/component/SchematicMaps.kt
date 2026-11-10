package com.ilink.vehicle.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import com.ilink.vehicle.ui.theme.Pleos

/*
 * 그림 지도 (디자인의 단순화된 지도). 지도 SDK를 붙이기 전까지 위치 관계만 보여 준다.
 * 좌표는 지도 영역 대비 비율(0~1)이다.
 */

/** 비율 좌표에 가운데를 맞춰 올리는 표식 */
@Composable
private fun Marker(fx: Float, fy: Float, w: Dp, h: Dp, maxW: Dp, maxH: Dp, content: @Composable () -> Unit) {
    Box(Modifier.offset(maxW * fx - w / 2, maxH * fy - h / 2).size(w, h), contentAlignment = Alignment.Center) { content() }
}

/**
 * V02 추천 픽업 장소 지도: 차 → 추천 승차 지점(초록 핀) 경로, 학원 건물, 다른 후보 핀.
 * @param altCount 지도에 같이 찍을 대안 장소 수 (최대 2)
 */
@Composable
fun SpotMap(spotLabel: String, altCount: Int, modifier: Modifier = Modifier) {
    val c = Pleos.colors
    BoxWithConstraints(modifier.clip(RoundedCornerShape(u(22))).background(c.mapBg)) {
        val w = maxWidth
        val h = maxHeight
        Canvas(Modifier.matchParentSize()) {
            val W = size.width
            val H = size.height
            val r = CornerRadius(W * 0.012f)
            // 건물 블록
            val cols = listOf(0.039f to 0.333f, 0.413f to 0.656f, 0.746f to 0.961f)
            val rows = listOf(0.058f to 0.417f, 0.580f to 0.942f)
            for ((x0, x1) in cols) for ((y0, y1) in rows) {
                drawRoundRect(c.mapBlock, Offset(W * x0, H * y0), Size(W * (x1 - x0), H * (y1 - y0)), r)
            }
            // 도로
            drawRect(c.mapRoad, Offset(W * 0.358f, 0f), Size(W * 0.029f, H))
            drawRect(c.mapRoad, Offset(W * 0.687f, 0f), Size(W * 0.029f, H))
            drawRect(c.mapRoad, Offset(W * 0.013f, H * 0.466f), Size(W * 0.957f, H * 0.068f))
            // 차 → 승차 지점 경로
            val route = Path().apply {
                moveTo(W * 0.03f, H * 0.495f); lineTo(W * 0.373f, H * 0.495f)
                lineTo(W * 0.373f, H * 0.457f); lineTo(W * 0.539f, H * 0.457f)
            }
            drawPath(route, c.route, style = Stroke(W * 0.009f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            // 학원 → 승차 지점 걸어가는 길
            drawLine(
                c.text, Offset(W * 0.539f, H * 0.27f), Offset(W * 0.539f, H * 0.37f), W * 0.004f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(W * 0.008f, W * 0.008f)),
            )
        }
        val markerBg = c.iconCircle
        // 학원 건물
        Marker(0.539f, 0.192f, u(64), u(64), w, h) {
            Box(Modifier.size(u(64)).clip(CircleShape).background(markerBg), contentAlignment = Alignment.Center) {
                BuildingIcon(u(34), c.text)
            }
        }
        // 다른 후보
        listOf(0.747f to 0.152f, 0.794f to 0.844f).take(altCount).forEach { (fx, fy) ->
            Marker(fx, fy, u(64), u(64), w, h) {
                Box(Modifier.size(u(64)).clip(CircleShape).background(markerBg), contentAlignment = Alignment.Center) {
                    PinIcon(u(30), c.text)
                }
            }
        }
        // 차
        Marker(0.059f, 0.495f, u(64), u(64), w, h) {
            Box(Modifier.size(u(64)).clip(CircleShape).background(c.route), contentAlignment = Alignment.Center) {
                CarIcon(u(34), Color.White)
            }
        }
        // 추천 승차 지점
        Marker(0.539f, 0.457f, u(72), u(72), w, h) {
            Box(Modifier.size(u(72)).clip(CircleShape).background(c.meet), contentAlignment = Alignment.Center) {
                PinIcon(u(34), Color.White)
            }
        }
        Box(Modifier.offset(w * 0.565f, h * 0.505f)) {
            Text(
                spotLabel, color = c.text, fontSize = us(20),
                modifier = Modifier.clip(RoundedCornerShape(u(10))).background(c.card).padding(horizontal = u(14), vertical = u(6)),
            )
        }
    }
}

/**
 * V03 길안내 지도: 격자 도로, 차 → 정차 지점(P) 경로, 아이와 만나는 곳.
 * @param chip 왼쪽 위 안내 (예: 길안내 중 · 남은 거리 4.2km)
 */
@Composable
fun RouteMap(chip: String, modifier: Modifier = Modifier) {
    val c = Pleos.colors
    BoxWithConstraints(modifier.clip(RoundedCornerShape(u(22))).background(c.mapBg)) {
        val w = maxWidth
        val h = maxHeight
        Canvas(Modifier.matchParentSize()) {
            val W = size.width
            val H = size.height
            drawRect(c.mapBlock, Offset(W * 0.300f, 0f), Size(W * 0.036f, H))
            drawRect(c.mapBlock, Offset(W * 0.718f, 0f), Size(W * 0.029f, H))
            drawRect(c.mapBlock, Offset(0f, H * 0.239f), Size(W, H * 0.021f))
            drawRect(c.mapBlock, Offset(0f, H * 0.623f), Size(W, H * 0.029f))
            val route = Path().apply {
                moveTo(W * 0.12f, H * 0.85f); lineTo(W * 0.12f, H * 0.35f); lineTo(W * 0.60f, H * 0.35f)
            }
            drawPath(route, c.route, style = Stroke(W * 0.01f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        Marker(0.60f, 0.35f, u(54), u(54), w, h) { RoundBadge("P") }
        Marker(0.12f, 0.85f, u(54), u(54), w, h) { RoundBadge("차") }
        Marker(0.70f, 0.465f, u(54), u(36), w, h) {
            Text(
                "만남", color = Color.White, fontSize = us(17), fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(u(8))).background(c.meet).padding(horizontal = u(10), vertical = u(4)),
            )
        }
        Text(
            chip, color = c.text, fontSize = us(19), fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(u(18)).clip(RoundedCornerShape(u(12))).background(c.window)
                .padding(horizontal = u(16), vertical = u(9)),
        )
    }
}

@Composable
private fun RoundBadge(text: String) {
    val c = Pleos.colors
    Box(
        Modifier.size(u(54)).clip(CircleShape).background(c.route).border(u(4), Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontSize = us(21), fontWeight = FontWeight.Bold)
    }
}
