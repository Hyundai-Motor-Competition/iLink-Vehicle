package com.ilink.vehicle.ui.component

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/** 연결 QR. 다크 모드에서는 흰 점 · 검은 바탕으로 그린다 (디자인 V00 Dark) */
@Composable
fun QrCode(
    payload: String,
    size: Dp,
    dot: Color = Color.Black,
    background: Color = Color.White,
    modifier: Modifier = Modifier,
) {
    val bitmap = remember(payload, dot, background) {
        val px = 512
        val m = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, px, px, mapOf(EncodeHintType.MARGIN to 1))
        val on = dot.toArgb()
        val off = background.toArgb()
        val pixels = IntArray(px * px) { i -> if (m[i % px, i / px]) on else off }
        Bitmap.createBitmap(pixels, px, px, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
    Image(bitmap = bitmap, contentDescription = "연결 QR", modifier = modifier.size(size))
}
