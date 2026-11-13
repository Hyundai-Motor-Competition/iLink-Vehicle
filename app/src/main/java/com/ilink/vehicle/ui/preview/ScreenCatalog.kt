package com.ilink.vehicle.ui.preview

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.ilink.vehicle.data.repository.LinkState
import com.ilink.vehicle.demo.DemoData
import com.ilink.vehicle.domain.VehicleScreen
import com.ilink.vehicle.ui.UiState
import com.ilink.vehicle.ui.screen.V00Connect
import com.ilink.vehicle.ui.screen.V01Today
import com.ilink.vehicle.ui.screen.V02Recommend
import com.ilink.vehicle.ui.screen.JourneyArrived
import com.ilink.vehicle.ui.screen.JourneyEnRoute
import com.ilink.vehicle.ui.screen.JourneyOtherPlace
import com.ilink.vehicle.ui.screen.V04Call
import com.ilink.vehicle.ui.screen.V05Complete
import com.ilink.vehicle.ui.screen.V06Exception
import com.ilink.vehicle.ui.screen.V07Settings
import com.ilink.vehicle.ui.screen.VoiceOverlay
import com.ilink.vehicle.domain.VoiceCues
import com.ilink.vehicle.data.model.SessionIncident
import com.ilink.vehicle.data.model.SessionStatus
import com.ilink.vehicle.ui.theme.ILinkTheme
import java.time.Instant

/**
 * V00~V07 화면을 상태별로 모은 목록.
 * Android Studio 미리보기와 화면 캡처 테스트(`scripts/capture-screens.sh`)가 같이 쓴다.
 */
object ScreenCatalog {
    data class Entry(val name: String, val state: UiState) {
        override fun toString() = name
    }

    fun entries(now: Instant = Instant.now()): List<Entry> {
        val call = DemoData.sessionCalled(now)
        val acked = call.copy(
            call = call.call?.copy(progress = call.call.progress?.copy(acknowledgedAt = now.toString())),
            children = call.children.map { it.copy(acknowledgedAt = now.toString()) },
        )
        // 정차 직후 · 합류 안내 보내는 중 (가드 G6)
        val stopped = DemoData.sessionRouting(now).let { r ->
            r.copy(status = r.status?.copy(vehicle = "STOPPED"), eta = r.eta?.copy(remainingSeconds = 0), route = r.route?.copy(remainingMeters = 0))
        }
        // 도착 3분 전 · 실내 준비 알림을 보낸 뒤
        val near = DemoData.sessionRouting(now).let { r -> r.copy(eta = r.eta?.copy(remainingSeconds = 170), route = r.route?.copy(remainingMeters = 650)) }
        val stopFailed = DemoData.sessionRouting(now).copy(
            status = SessionStatus("ON_HOLD", "APPROACHING", "NONE", "CONFIRMED"),
            incident = SessionIncident("STOP_FAILED", 2, now.toString()),
        )
        // 2차 디자인 V03~V06: 출발 시각은 실제 시계 기준 (1분이 지나면 단계가 바뀐다)
        val wall = System.currentTimeMillis()
        val routing = DemoData.sessionRouting(now).let { r -> r.copy(eta = r.eta?.copy(remainingSeconds = 12 * 60)) }
        val prep = routing.copy(children = routing.children.map { it.copy(extraPrepMinutes = 5) })
        return listOf(
            Entry("V03_가는길_출발알림", UiState(VehicleScreen.V03, session = routing, spots = DemoData.spots, departedAt = wall)),
            Entry("V03_가는길_출발알림_주행중", UiState(VehicleScreen.V03, session = routing, spots = DemoData.spots, departedAt = wall, drivingRestricted = true)),
            Entry("V04_가는길_목적지로", UiState(VehicleScreen.V03, session = routing, spots = DemoData.spots, departedAt = wall - 120_000)),
            Entry("V04_가는길_아이준비지연", UiState(VehicleScreen.V03, session = prep, spots = DemoData.spots, departedAt = wall - 120_000)),
            Entry("V05_도착정차", UiState(VehicleScreen.V04, session = stopped, spots = DemoData.spots, departedAt = wall - 300_000)),
            Entry("V05_도착_여기로부름", UiState(VehicleScreen.V04, session = call, spots = DemoData.spots, departedAt = wall - 300_000, calledHereAt = wall)),
            Entry("V06_다른장소", UiState(VehicleScreen.V04, session = stopped, spots = DemoData.spots, departedAt = wall - 300_000, choosingPlace = true)),
            // 남은 시간은 실제 시계로 줄어들므로, 지금 기준 8초 전에 발급한 코드로 그린다 (디자인처럼 04:52)
            Entry("V00_연결_QR", UiState(VehicleScreen.V00, link = LinkState.Waiting(DemoData.linkCode(Instant.now().minusSeconds(8))))),
            Entry("V00_연결_주행중", UiState(VehicleScreen.V00, link = LinkState.Waiting(DemoData.linkCode(now)), drivingRestricted = true)),
            Entry("V01_오늘의픽업", UiState(VehicleScreen.V01, weekly = DemoData.weekly(), settings = DemoData.settings)),
            Entry("V01_오늘의픽업_주행중", UiState(VehicleScreen.V01, weekly = DemoData.weekly(), drivingRestricted = true)),
            Entry("V02_장소선택", UiState(VehicleScreen.V02, session = DemoData.sessionSelecting(now), spots = DemoData.spots)),
            Entry("V02_장소선택_주행중", UiState(VehicleScreen.V02, session = DemoData.sessionSelecting(now), spots = DemoData.spots, drivingRestricted = true)),
            Entry("V03_진행", UiState(VehicleScreen.V03, session = DemoData.sessionRouting(now))),
            Entry("V03_진행_주행중", UiState(VehicleScreen.V03, session = DemoData.sessionRouting(now), drivingRestricted = true)),
            Entry("V03_곧도착_준비알림", UiState(VehicleScreen.V03, session = near)),
            Entry("V04_정차", UiState(VehicleScreen.V04, session = stopped)),
            Entry("V04_합류안내", UiState(VehicleScreen.V04, session = acked)),
            Entry("V04_합류안내_주행중", UiState(VehicleScreen.V04, session = acked, drivingRestricted = true)),
            Entry("V04_수동완료", UiState(VehicleScreen.V04, session = acked, doorSignalAvailable = false)),
            Entry("V05_탑승감지", UiState(VehicleScreen.V05, session = DemoData.sessionBoarding(now))),
            Entry("V05_완료", UiState(VehicleScreen.V05, session = DemoData.sessionCompleted(now))),
            Entry("V05_완료_주행중", UiState(VehicleScreen.V05, session = DemoData.sessionCompleted(now), drivingRestricted = true)),
            Entry("V06_정차실패", UiState(VehicleScreen.V06, session = stopFailed, spots = DemoData.spots)),
            Entry("V06_정차실패_주행중", UiState(VehicleScreen.V06, session = stopFailed, spots = DemoData.spots, drivingRestricted = true)),
            Entry("V06_미탑승", UiState(VehicleScreen.V06, session = DemoData.sessionNotBoarded(now))),
            Entry("V07_설정", UiState(VehicleScreen.V07, settings = DemoData.settings)),
            Entry("U01_오늘일정", UiState(VehicleScreen.V01, weekly = DemoData.weekly(), banner = VoiceCues.today("", DemoData.weekly().days.first().pickups)?.banner)),
            Entry("U08_음성명령", UiState(VehicleScreen.V03, session = DemoData.sessionRouting(now), banner = VoiceCues.status(DemoData.sessionRouting(now), "\"지금 픽업 상태 어때?\""))),
        )
    }

    /** 버튼 동작 없이 화면만 그린다 */
    @Composable
    fun Render(s: UiState) {
        ILinkTheme {
            Surface {
              androidx.compose.foundation.layout.Box {
                when (s.screen) {
                    VehicleScreen.V00 -> V00Connect(s, onReissue = {})
                    VehicleScreen.V01 -> V01Today(s, onStart = {}, onSettings = {})
                    VehicleScreen.V02 -> V02Recommend(s, onRoute = {})
                    VehicleScreen.V03 -> JourneyEnRoute(s, onHold = {}, onRelease = {}, onContact = {}, onMenu = {})
                    VehicleScreen.V04 -> when {
                        s.driving -> V04Call(s, onHold = {}, onRelease = {})
                        s.choosingPlace -> JourneyOtherPlace(s, onGo = {}, onBack = {}, onContact = {}, onMenu = {})
                        else -> JourneyArrived(s, onCallHere = {}, onOtherPlace = {}, onContact = {}, onMenu = {})
                    }
                    VehicleScreen.V05 -> V05Complete(s, onReject = {}, onComplete = {}, onDone = {})
                    VehicleScreen.V06 -> V06Exception(s)
                    VehicleScreen.V07 -> V07Settings(s, onBack = {}, onLogout = {})
                }
                s.banner?.let { VoiceOverlay(it, onDismiss = {}) }
              }
            }
        }
    }
}

class ScreenCatalogProvider : PreviewParameterProvider<ScreenCatalog.Entry> {
    override val values = ScreenCatalog.entries().asSequence()
}

// Android Studio 미리보기: 가로(창 2개) / 세로(창 1개). 크기는 Pleos 폼팩터에 맞게 바꿔 쓴다.
@Preview(name = "Landscape", widthDp = 1280, heightDp = 720, locale = "ko")
@Composable
private fun LandscapePreview(@PreviewParameter(ScreenCatalogProvider::class) e: ScreenCatalog.Entry) = ScreenCatalog.Render(e.state)

@Preview(name = "Portrait", widthDp = 720, heightDp = 1100, locale = "ko")
@Composable
private fun PortraitPreview(@PreviewParameter(ScreenCatalogProvider::class) e: ScreenCatalog.Entry) = ScreenCatalog.Render(e.state)
