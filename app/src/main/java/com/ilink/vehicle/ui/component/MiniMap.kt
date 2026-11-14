package com.ilink.vehicle.ui.component

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import com.ilink.vehicle.data.model.LatLng
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/** 지도에 찍을 표식. selected면 큰 파란 핀, 아니면 번호가 적힌 작은 원 */
data class MapMark(val at: LatLng, val selected: Boolean, val label: String = "")

/**
 * 앱 창 안의 작은 지도 (V02 개선안 오른쪽). Pleos SDK에는 앱에 넣는 지도 뷰가 없어서
 * OpenStreetMap 타일을 osmdroid로 그린다 (API 키 필요 없음). 길안내는 그대로 Pleos 내비가 한다.
 * 미리보기 · Robolectric에서는 지도 대신 빈 판을 그린다.
 */
@Composable
fun MiniMap(center: LatLng, marks: List<MapMark>, modifier: Modifier = Modifier, zoom: Double = 17.3) {
    val context = LocalContext.current
    if (LocalInspectionMode.current || Build.FINGERPRINT == "robolectric") {
        // 미리보기 · 화면 캡처: 디버그 빌드에만 있는 지도 그림(map_preview, 에뮬레이터에서 찍은 실제 지도)을 깐다. 없으면 빈 판
        val preview = context.resources.getIdentifier("map_preview", "drawable", context.packageName)
        if (preview != 0) {
            androidx.compose.foundation.Image(
                androidx.compose.ui.res.painterResource(preview), contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = modifier,
            )
        } else {
            Box(modifier.background(Color(0xFFEDEEF2)))
        }
        return
    }
    val density = context.resources.displayMetrics.density
    val map = remember {
        Configuration.getInstance().apply {
            userAgentValue = context.packageName // OSM 타일 정책: 앱을 밝히는 User-Agent
            osmdroidBasePath = context.cacheDir.resolve("osmdroid")
            osmdroidTileCache = context.cacheDir.resolve("osmdroid/tiles")
        }
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            // 휴대전화 앱(TileMap)과 같은 지도: 같은 OSM 타일을 밝은 회색 바탕(#F4F4F2) 위에 36%로 옅게
            overlayManager.tilesOverlay.setColorFilter(PALE_TILES)
            overlayManager.tilesOverlay.setLoadingBackgroundColor(MAP_BG)
            setBackgroundColor(MAP_BG)
            controller.setZoom(zoom)
        }
    }
    DisposableEffect(map) {
        map.onResume()
        onDispose {
            map.onPause()
            map.onDetach()
        }
    }
    AndroidView({ map }, modifier) { mv ->
        mv.overlays.clear()
        // 고른 장소가 맨 위에 오도록 마지막에 추가
        marks.sortedBy { it.selected }.forEach { m ->
            mv.overlays.add(
                Marker(mv).apply {
                    position = GeoPoint(m.at.lat, m.at.lng)
                    icon = BitmapDrawable(mv.resources, if (m.selected) pinBitmap(density) else badgeBitmap(density, m.label))
                    setAnchor(Marker.ANCHOR_CENTER, if (m.selected) Marker.ANCHOR_BOTTOM else Marker.ANCHOR_CENTER)
                    setInfoWindow(null)
                },
            )
        }
        mv.controller.setCenter(GeoPoint(center.lat, center.lng))
        mv.invalidate()
    }
}

private const val BLUE = 0xFF1F6BFF.toInt()
private const val MAP_BG = 0xFFF4F4F2.toInt()

/** 타일 색 = 원래 색 × 0.36 + 바탕색 × 0.64 (휴대전화 앱의 opacity 0.36과 같은 결과) */
private val PALE_TILES = ColorMatrixColorFilter(
    ColorMatrix(
        floatArrayOf(
            0.36f, 0f, 0f, 0f, 0.64f * 0xF4,
            0f, 0.36f, 0f, 0f, 0.64f * 0xF4,
            0f, 0f, 0.36f, 0f, 0.64f * 0xF2,
            0f, 0f, 0f, 1f, 0f,
        ),
    ),
)

/** 디자인의 파란 물방울 핀 (가운데 흰 점) */
private fun pinBitmap(d: Float): Bitmap {
    val w = (44 * d).toInt()
    val h = (56 * d).toInt()
    return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { b ->
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BLUE }
        val r = w / 2f
        val path = Path().apply {
            addCircle(r, r, r * 0.92f, Path.Direction.CW)
            moveTo(r * 0.28f, r * 1.5f)
            lineTo(r, h.toFloat())
            lineTo(r * 1.72f, r * 1.5f)
            close()
        }
        c.drawPath(path, p)
        p.color = android.graphics.Color.WHITE
        c.drawCircle(r, r, r * 0.32f, p)
    }
}

/** 다른 후보: 흰 테두리 파란 원 안 번호 */
private fun badgeBitmap(d: Float, label: String): Bitmap {
    val s = (30 * d).toInt()
    return Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888).also { b ->
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
        c.drawCircle(s / 2f, s / 2f, s / 2f, p)
        p.color = BLUE
        c.drawCircle(s / 2f, s / 2f, s / 2f - 2.5f * d, p)
        p.color = android.graphics.Color.WHITE
        p.textSize = 15 * d
        p.typeface = Typeface.DEFAULT_BOLD
        p.textAlign = Paint.Align.CENTER
        c.drawText(label, s / 2f, s / 2f - (p.descent() + p.ascent()) / 2, p)
    }
}
