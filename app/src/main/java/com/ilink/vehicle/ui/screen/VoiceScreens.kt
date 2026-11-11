package com.ilink.vehicle.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.background
import com.ilink.vehicle.R
import com.ilink.vehicle.data.model.Contacts
import com.ilink.vehicle.platform.voice.Banner
import com.ilink.vehicle.platform.voice.VoiceCue
import com.ilink.vehicle.ui.component.ButtonTone
import com.ilink.vehicle.ui.component.CalendarIcon
import com.ilink.vehicle.ui.component.CheckIcon
import com.ilink.vehicle.ui.component.CheckRingIcon
import com.ilink.vehicle.ui.component.IconCircle
import com.ilink.vehicle.ui.component.MicIcon
import com.ilink.vehicle.ui.component.PinIcon
import com.ilink.vehicle.ui.component.PleosButton
import com.ilink.vehicle.ui.component.PleosWindow
import com.ilink.vehicle.ui.component.RouteIcon
import com.ilink.vehicle.ui.component.SendIcon
import com.ilink.vehicle.ui.component.SpinnerIcon
import com.ilink.vehicle.ui.component.StatusCard
import com.ilink.vehicle.ui.component.WarningIcon
import com.ilink.vehicle.ui.component.u
import com.ilink.vehicle.ui.component.us
import com.ilink.vehicle.ui.theme.Pleos
import kotlinx.coroutines.delay

/** 음성 안내를 창에 띄워 두는 시간 */
private const val BANNER_MS = 5_000L

/**
 * U01~U08 음성 안내 (assets/U0x). 말하는 동안 앱 창에 '발화 중'과 안내 카드를 띄운다.
 * 몇 초 뒤 저절로 닫히고, 누르면 바로 닫힌다.
 */
@Composable
fun VoiceOverlay(banner: Banner, onDismiss: () -> Unit) {
    val c = Pleos.colors
    LaunchedEffect(banner) {
        delay(BANNER_MS)
        onDismiss()
    }
    PleosWindow(
        stringResource(R.string.app_name), titleBold = false,
        modifier = Modifier.clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        trailing = { SpeakingPill() },
    ) {
        banner.query?.let { q ->
            StatusCard(q, null) { IconCircle(c.iconCircle) { MicIcon(it, c.text) } }
            Spacer(Modifier.height(u(19)))
        }
        StatusCard(banner.title, banner.sub) { CueIcon(banner.cue) }
    }
}

@Composable
private fun SpeakingPill() {
    Text(
        "발화 중", color = Color(0xFF0B2E1A), fontSize = us(17), fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(Pleos.colors.meet).padding(horizontal = u(16), vertical = u(6)),
    )
}

/** 안내마다 디자인의 아이콘 · 바탕색 */
@Composable
private fun CueIcon(cue: VoiceCue) {
    val c = Pleos.colors
    when (cue) {
        VoiceCue.U01 -> IconCircle(c.iconCircle) { CalendarIcon(it, c.text) }
        VoiceCue.U02 -> IconCircle(c.stepDone) { PinIcon(it, Color.White) }
        VoiceCue.U03 -> IconCircle(c.stepDone) { SendIcon(it, Color.White) }
        VoiceCue.U04 -> IconCircle(c.iconCircle) { SpinnerIcon(it, c.text) }
        VoiceCue.U05 -> IconCircle(c.stepDone) { CheckIcon(it, Color.White) }
        VoiceCue.U06 -> IconCircle(c.danger) { WarningIcon(it, Color.White) }
        VoiceCue.U07 -> IconCircle(c.stepDone) { CheckRingIcon(it, Color.White) }
        VoiceCue.U08, VoiceCue.U09 -> IconCircle(c.iconCircle) { RouteIcon(it, c.text) }
    }
}

/** V04·V06 '직접 연락': 세션 연락처. 누르면 전화 앱으로 넘긴다 */
@Composable
fun ContactSheet(contacts: List<Contacts.Contact>, driving: Boolean, onCall: (String) -> Unit, onClose: () -> Unit) {
    val c = Pleos.colors
    PleosWindow("직접 연락") {
        if (contacts.isEmpty()) Text("연락할 수 있는 사람이 없어요", color = c.subText, fontSize = us(20))
        contacts.forEach { ct ->
            val phone = ct.phone
            StatusCard(
                listOfNotNull(ct.name, roleLabel(ct.role)).joinToString(" · "),
                phone ?: "전화번호 없음",
                Modifier.padding(bottom = u(12)).then(if (phone != null) Modifier.clickable { onCall(phone) } else Modifier),
            ) { IconCircle(c.meet) { PhoneGlyph(it) } }
        }
        Spacer(Modifier.weight(1f))
        if (driving) Text("주행 중에는 음성 통화로 연결돼요", color = c.subText, fontSize = us(18), modifier = Modifier.padding(bottom = u(10)))
        PleosButton("닫기", ButtonTone.Secondary, onClick = onClose)
    }
}

@Composable
private fun PhoneGlyph(size: androidx.compose.ui.unit.Dp) {
    Text("☎", color = Color.White, fontSize = us(24))
}

private fun roleLabel(role: String?) = when (role) {
    "CHILD" -> "자녀"
    "GUARDIAN" -> "보호자"
    "ACADEMY" -> "학원"
    else -> null
}
