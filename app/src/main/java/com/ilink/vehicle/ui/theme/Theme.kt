package com.ilink.vehicle.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.ilink.vehicle.R
import androidx.compose.ui.unit.sp

/**
 * Pleos Connect 앱 창 색 (assets/V00 … Light · Dark 디자인에서 뽑은 값).
 * 차량 앱은 디자이너 결정에 따라 다크 컨셉 하나만 쓴다 (시스템 라이트/다크와 상관없이).
 * Pleos 가이드는 라이트/다크 둘 다를 요구하지 않고 대비 4.5:1 이상만 요구한다. LightPleos는 미리보기 비교용으로 남겨 둔다.
 */
@Immutable
data class PleosColors(
    /** 앱 창 바탕 */
    val window: Color,
    /** 창 안의 큰 패널 (QR 영역 등) */
    val panel: Color,
    val text: Color,
    val subText: Color,
    /** 앱 창 왼쪽 위 서비스 이름 */
    val serviceName: Color,
    /** 주요 버튼: 라이트는 진회색, 다크는 흰색 */
    val primaryButton: Color,
    val onPrimaryButton: Color,
    val secondaryButton: Color,
    val onSecondaryButton: Color,
    /** 남은 시간 같은 작은 알약 (두 모드 모두 밝은 회색) */
    val pill: Color,
    val onPill: Color,
    /** QR 점 색과 바탕 */
    val qrDot: Color,
    val qrBackground: Color,
    val accent: Color,
    val accentDeep: Color,
    val danger: Color,
    /** 창 안의 정보 카드 (V02·V03) */
    val card: Color,
    val divider: Color,
    /** 진행 단계: 완료/현재(파랑), 예정(회색) */
    val stepDone: Color,
    val stepCurrentFill: Color,
    val stepPending: Color,
    /** 그림 지도 */
    val mapBg: Color,
    val mapBlock: Color,
    val mapRoad: Color,
    val route: Color,
    val meet: Color,
    val iconCircle: Color,
    /**
     * V01 개선안의 강조색. 다크는 디자인의 노랑, 라이트는 흰 바탕에서 노랑 글자·버튼이
     * 대비 기준(글자 4.5:1, 버튼 경계 3:1)을 못 넘어서 파랑으로 바꾼다.
     */
    val highlight: Color,
    val onHighlight: Color,
)

val LightPleos = PleosColors(
    window = Color(0xFFF7F8FA),
    panel = Color(0xFFFFFFFF),
    text = Color(0xFF131417),
    subText = Color(0xFF5B5D63),
    serviceName = Color(0xFF131417),
    primaryButton = Color(0xFF44464E),
    onPrimaryButton = Color(0xFFFFFFFF),
    secondaryButton = Color(0xFFECEDEF),
    onSecondaryButton = Color(0xFF131417),
    pill = Color(0xFFEDEEF2),
    onPill = Color(0xFF404143),
    qrDot = Color(0xFF131417),
    qrBackground = Color(0xFFFFFFFF),
    accent = Color(0xFF0984FB),
    accentDeep = Color(0xFF015FE7),
    danger = Color(0xFFE51B1B),
    card = Color(0xFFFFFFFF),
    divider = Color(0xFFDFE2E8),
    stepDone = Color(0xFF0064FF),
    stepCurrentFill = Color(0xFFE5F0FF),
    stepPending = Color(0xFFE0E1E6),
    mapBg = Color(0xFFEDEEF2),
    mapBlock = Color(0xFFE0E1E6),
    mapRoad = Color(0xFFFFFFFF),
    route = Color(0xFF0064FF),
    meet = Color(0xFF02C265),
    iconCircle = Color(0xFFEDEEF2),
    highlight = Color(0xFF0064FF),
    onHighlight = Color(0xFFFFFFFF),
)

val DarkPleos = PleosColors(
    window = Color(0xFF1D1E21),
    panel = Color(0xFF131417),
    text = Color(0xFFFFFFFF),
    subText = Color(0xFFB1B3B8),
    serviceName = Color(0xFFB1B3B8),
    primaryButton = Color(0xFFFFFFFF),
    onPrimaryButton = Color(0xFF131417),
    secondaryButton = Color(0xFF44464E),
    onSecondaryButton = Color(0xFFFFFFFF),
    pill = Color(0xFFEDEEF2),
    onPill = Color(0xFF404143),
    qrDot = Color(0xFFFFFFFF),
    qrBackground = Color(0xFF131417),
    accent = Color(0xFF0984FB),
    accentDeep = Color(0xFF015FE7),
    danger = Color(0xFFFF5A4F),
    card = Color(0xFF28292C),
    divider = Color(0xFF33363B),
    stepDone = Color(0xFF0082FF),
    stepCurrentFill = Color(0xFF1D1E21),
    stepPending = Color(0xFF44464E),
    mapBg = Color(0xFF313236),
    mapBlock = Color(0xFF44464E),
    mapRoad = Color(0xFF1D1E21),
    route = Color(0xFF0082FF),
    meet = Color(0xFF00C46A),
    iconCircle = Color(0xFF44464E),
    highlight = Color(0xFFF6FF43),
    onHighlight = Color(0xFF131417),
)

val LocalPleosColors = staticCompositionLocalOf { LightPleos }

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF4D8BFF),
    background = DarkPleos.window,
    surface = DarkPleos.window,
    onBackground = DarkPleos.text,
    onSurface = DarkPleos.text,
    error = DarkPleos.danger,
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF1040CA),
    background = LightPleos.window,
    surface = LightPleos.window,
    onBackground = LightPleos.text,
    onSurface = LightPleos.text,
    error = LightPleos.danger,
)

/**
 * 현대 전용 글꼴 NeoHyundai (L 300 · R 400 · B 700 · EB 800 · EBK 900).
 * 500(Medium)은 R, 600(SemiBold)은 B로 그려진다 (가까운 굵기).
 */
val NeoHyundai = FontFamily(
    Font(R.font.neohyundai_l, FontWeight.Light),
    Font(R.font.neohyundai_r, FontWeight.Normal),
    Font(R.font.neohyundai_r, FontWeight.Medium),
    Font(R.font.neohyundai_b, FontWeight.SemiBold),
    Font(R.font.neohyundai_b, FontWeight.Bold),
    Font(R.font.neohyundai_eb, FontWeight.ExtraBold),
    Font(R.font.neohyundai_ebk, FontWeight.Black),
)

// Pleos 가이드: 대비 4.5:1 이상, 최소 12sp, 화면당 핵심 수치 1개를 Headline으로.
private val Type = Typography(
    displayMedium = TextStyle(fontFamily = NeoHyundai, fontSize = 44.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontFamily = NeoHyundai, fontSize = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontFamily = NeoHyundai, fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontFamily = NeoHyundai, fontSize = 18.sp),
    bodyMedium = TextStyle(fontFamily = NeoHyundai, fontSize = 16.sp),
    labelLarge = TextStyle(fontFamily = NeoHyundai, fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun ILinkTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPleosColors provides if (darkTheme) DarkPleos else LightPleos) {
        MaterialTheme(colorScheme = if (darkTheme) DarkScheme else LightScheme, typography = Type) {
            // 모든 Text가 NeoHyundai를 쓰도록 기본 글자 스타일에 넣는다
            CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = NeoHyundai), content = content)
        }
    }
}

object Pleos {
    val colors: PleosColors
        @Composable get() = LocalPleosColors.current
}
