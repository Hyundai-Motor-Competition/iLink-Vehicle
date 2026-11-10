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
import com.ilink.vehicle.ui.screen.V03Progress
import com.ilink.vehicle.ui.screen.V04Call
import com.ilink.vehicle.ui.screen.V05Complete
import com.ilink.vehicle.ui.screen.V06Exception
import com.ilink.vehicle.ui.screen.V07Settings
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

    fun entries(now: Instant = Instant.now()): List<Entry> = listOf(
        // 남은 시간은 실제 시계로 줄어들므로, 지금 기준 8초 전에 발급한 코드로 그린다 (디자인처럼 04:52)
        Entry("V00_연결_QR", UiState(VehicleScreen.V00, link = LinkState.Waiting(DemoData.linkCode(Instant.now().minusSeconds(8))))),
        Entry("V00_연결_주행중", UiState(VehicleScreen.V00, link = LinkState.Waiting(DemoData.linkCode(now)), drivingRestricted = true)),
        Entry("V01_오늘의픽업", UiState(VehicleScreen.V01, weekly = DemoData.weekly())),
        Entry("V02_장소선택", UiState(VehicleScreen.V02, session = DemoData.sessionSelecting(now), spots = DemoData.spots)),
        Entry("V02_장소선택_주행중", UiState(VehicleScreen.V02, session = DemoData.sessionSelecting(now), spots = DemoData.spots, drivingRestricted = true)),
        Entry("V03_진행", UiState(VehicleScreen.V03, session = DemoData.sessionRouting(now))),
        Entry("V04_합류안내", UiState(VehicleScreen.V04, session = DemoData.sessionCalled(now))),
        Entry("V05_탑승감지", UiState(VehicleScreen.V05, session = DemoData.sessionBoarding(now))),
        Entry("V05_수동완료", UiState(VehicleScreen.V05, session = DemoData.sessionCalled(now), doorSignalAvailable = false)),
        Entry("V05_완료", UiState(VehicleScreen.V05, session = DemoData.sessionCompleted(now))),
        Entry("V06_미탑승", UiState(VehicleScreen.V06, session = DemoData.sessionNotBoarded(now))),
        Entry("V06_장소사용불가", UiState(VehicleScreen.V06, session = DemoData.sessionSpotInvalid(now))),
        Entry("V07_설정", UiState(VehicleScreen.V07, settings = DemoData.settings)),
    )

    /** 버튼 동작 없이 화면만 그린다 */
    @Composable
    fun Render(s: UiState) {
        ILinkTheme {
            Surface {
                when (s.screen) {
                    VehicleScreen.V00 -> V00Connect(s, onReissue = {})
                    VehicleScreen.V01 -> V01Today(s, onStart = {}, onSettings = {})
                    VehicleScreen.V02 -> V02Recommend(s, onRoute = {})
                    VehicleScreen.V03 -> V03Progress(s, onHold = {}, onRelease = {})
                    VehicleScreen.V04 -> V04Call(s, onHold = {}, onRelease = {})
                    VehicleScreen.V05 -> V05Complete(s, onReject = {}, onManual = {}, onDone = {})
                    VehicleScreen.V06 -> V06Exception(s, onCancel = {})
                    VehicleScreen.V07 -> V07Settings(s, onBack = {}, onLogout = {})
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
