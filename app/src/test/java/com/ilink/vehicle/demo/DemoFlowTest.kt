package com.ilink.vehicle.demo

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.ilink.vehicle.core.auth.TokenStore
import com.ilink.vehicle.core.network.ApiClient
import com.ilink.vehicle.data.model.VehicleEventRequest
import com.ilink.vehicle.data.model.VehicleTelemetryRequest
import com.ilink.vehicle.data.remote.VehicleApi
import com.ilink.vehicle.data.repository.LinkRepository
import com.ilink.vehicle.data.repository.LinkState
import com.ilink.vehicle.data.repository.SessionRepository
import com.ilink.vehicle.domain.VehicleScreen
import com.ilink.vehicle.domain.VehicleScreenResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.UUID

/**
 * 데모 서버가 앱의 실제 API 클라이언트 · 저장소 · 화면 결정 로직과 맞물려 V00 → V05까지 가는지 확인한다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DemoFlowTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val world = DemoWorld(scope, approveAfterPolls = 2)
    private val tokens = TokenStore(ApplicationProvider.getApplicationContext())
    private val api = VehicleApi(ApiClient("https://demo.invalid", tokens, OkHttpClient.Builder().addInterceptor(world.server).build()))
    private val links = LinkRepository(api, tokens)
    private val sessions = SessionRepository(api, world.events, scope)

    @After fun tearDown() = scope.cancel()

    private fun screen() = VehicleScreenResolver.resolve(sessions.session.value!!)
    private fun event(type: String) =
        VehicleEventRequest(UUID.randomUUID().toString(), type, Instant.now().toString(), door = "REAR_RIGHT".takeIf { type == "DOOR_CYCLE" })
    private fun telemetry(eta: Int) = VehicleTelemetryRequest(Instant.now().toString(), eta, eta * 8, gear = "D")

    @Test fun `연결부터 탑승 완료까지`() = runBlocking {
        withTimeout(30_000) {
            // V00: 코드 발급 → 두 번째 조회에서 연결
            val states = links.startLink().toList()
            assertTrue(states.first() is LinkState.Waiting)
            assertEquals(LinkState.Linked, states.last())
            assertTrue(links.isLinked.first())

            // V01: 오늘 일정
            val today = api.weeklyPickups().days.first { it.date == java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).toString() }
            assertTrue(today.pickups.isNotEmpty())

            // V02 → V03: 시작 후 경로 확정
            sessions.start(listOf(today.pickups.first().scheduleId))
            assertEquals(VehicleScreen.V02, screen())
            sessions.confirmRoute(DemoData.spots.academies.first().spots.first().spotId, null)
            assertEquals(VehicleScreen.V03, screen())

            // 가드 G6: 3분 이내로 와도 달리는 동안은 합류 안내를 보내지 않는다 (V03 유지)
            sessions.sendTelemetry(telemetry(170))
            sessions.refresh()
            assertEquals(null, sessions.session.value!!.call?.callId)
            assertEquals(listOf("G6"), sessions.session.value!!.call?.guard?.unmet)
            assertEquals(VehicleScreen.V03, screen())

            // 정차하면 합류 안내 → V04
            sessions.sendVehicleEvent(event("STOPPED"))
            sessions.refresh()
            assertNotNull(sessions.session.value!!.call?.callId)
            assertEquals("CALLED", sessions.session.value!!.children.first().phase)
            assertEquals(VehicleScreen.V04, screen())

            // 도어 개폐 → 탑승 감지(V05) → 수동 완료
            sessions.sendVehicleEvent(event("DOOR_CYCLE"))
            sessions.refresh()
            assertEquals(VehicleScreen.V05, screen())
            assertNotNull(sessions.session.value!!.boarding?.cancelableUntil)
            sessions.completeManually()
            assertTrue(sessions.session.value!!.isTerminal)
        }
    }

    @Test fun `합류 안내 보류 중에는 안내를 만들지 않고, 탑승 거절이면 V04로 복귀`() = runBlocking {
        withTimeout(30_000) {
            links.startLink().filterIsInstance<LinkState.Linked>().first()
            sessions.start(listOf(101))
            sessions.confirmRoute(201, null)
            sessions.holdCall()
            sessions.sendTelemetry(telemetry(100))
            sessions.sendVehicleEvent(event("STOPPED"))
            sessions.refresh()
            assertEquals(null, sessions.session.value!!.call?.callId) // 정차해도 보류 중이면 안 보냄

            sessions.releaseCallHold() // 정차 중에 보류를 풀면 바로 보낸다
            assertNotNull(sessions.session.value!!.call?.callId)
            sessions.sendVehicleEvent(event("DOOR_CYCLE"))
            sessions.refresh()
            // 기획 QA-37: 취소 창 안에서 취소하면 완료만 취소되고 합류 진행(V04)으로 돌아간다
            sessions.rejectBoarding(null)
            assertEquals(VehicleScreen.V04, screen())
            assertEquals("ACTIVE", sessions.session.value!!.status?.session)
            assertEquals("MEETING", sessions.session.value!!.children.first().phase)
        }
    }
}
