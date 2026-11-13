package com.ilink.vehicle.demo

import com.ilink.vehicle.data.remote.SessionEventSource
import com.ilink.vehicle.data.remote.StreamEvent
import com.ilink.vehicle.domain.DoorCycleDetector.Door
import com.ilink.vehicle.platform.location.GeoFix
import com.ilink.vehicle.platform.location.LocationSource
import com.ilink.vehicle.platform.navi.NaviEta
import com.ilink.vehicle.platform.navi.Navigation
import com.ilink.vehicle.platform.navi.RouteResult
import com.ilink.vehicle.platform.ux.RestrictionSource
import com.ilink.vehicle.platform.vehicle.DoorSignal
import com.ilink.vehicle.platform.vehicle.VehicleSignalSource
import com.ilink.vehicle.platform.voice.Announcer
import com.ilink.vehicle.platform.voice.Banner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 데모 빌드의 가짜 차량 세계. Pleos SDK 대신 이 객체들이 내비·기어·도어·위치 신호를 낸다.
 *
 * 길안내를 시작하면(이 장소로 길안내) 약 1분 동안 차가 다가오고(ETA 300초 → 0초, 실제보다 5배 빠름),
 * 그동안 기어가 D라 화면이 주행용으로 바뀐다.
 * 도착하면 기어 P(이때 합류 안내 발송) → 9초 뒤 뒷좌석 오른쪽 문 열림 → 2초 뒤 닫힘을 낸다.
 * 앱의 서비스·도어 판정·서버 동기화 로직은 실제와 똑같이 이 신호를 처리한다.
 */
class DemoWorld(private val scope: CoroutineScope, approveAfterPolls: Int = 6) {
    val server = DemoServer(scope, approveAfterPolls)

    private val _eta = MutableStateFlow<NaviEta?>(null)
    private val _routeResults = MutableSharedFlow<RouteResult>(extraBufferCapacity = 4)
    private val _gear = MutableStateFlow<String?>("P")
    private val _doors = MutableSharedFlow<DoorSignal>(extraBufferCapacity = 16)
    private val _doorAvailable = MutableStateFlow(true)
    private val _restricted = MutableStateFlow(false)
    private val _location = MutableStateFlow<GeoFix?>(GeoFix(37.5050, 127.0550, 120.0))
    private val _banner = MutableStateFlow<Banner?>(null)
    private var drive: Job? = null

    // ── 데모 조작 패널에서 바꾸는 값 ──

    /** 주행 규제 강제로 켜기 (정차 중에도 주행 화면 보기). 기어가 D면 이 값과 상관없이 주행 중 */
    val restrictedControl: StateFlow<Boolean> = _restricted
    fun setRestricted(on: Boolean) {
        _restricted.value = on
    }

    /** 실제 차처럼 기어가 P가 아니면 주행 규제. 데모 패널 스위치로 강제로 켤 수도 있다 */
    private val drivingNow: StateFlow<Boolean> = combine(_restricted, _gear) { forced, gear -> forced || (gear != null && gear != "P") }
        .stateIn(scope, SharingStarted.Eagerly, false)

    /** 정차 실패(STOP_FAILED) 흉내 → V06 '안전하게 정차할 수 없어요' */
    fun stopFailed() {
        drive?.cancel() // 정차 지점에 서지 못하고 지나감: 주행은 계속(D)
        server.injectStopFailed()
    }

    /** 지금 자리에서 정차 (기어 P) */
    fun park() {
        drive?.cancel()
        _gear.value = "P"
    }

    /** 도어 신호 끊김 흉내 → V05 수동 완료 폴백 */
    val doorAvailableControl: StateFlow<Boolean> = _doorAvailable
    fun setDoorAvailable(on: Boolean) {
        _doorAvailable.value = on
    }

    /** 남은 주행을 건너뛰고 바로 도착 */
    fun arriveNow() {
        val e = _eta.value ?: return
        _eta.value = e.copy(seconds = 0, meters = 0, at = System.currentTimeMillis())
    }

    /** 서버 상태와 주행을 처음으로 */
    fun reset() {
        drive?.cancel()
        _eta.value = null
        _gear.value = "P"
        server.reset()
    }

    // ── 앱이 쓰는 가짜 플랫폼 ──

    val navigation = object : Navigation {
        override val eta: StateFlow<NaviEta?> = _eta
        override val routeResults: SharedFlow<RouteResult> = _routeResults
        override fun start() = Unit
        override fun stop() = Unit
        override fun routeTo(lat: Double, lng: Double, name: String) {
            drive?.cancel()
            drive = scope.launch { simulateDrive() }
        }
    }

    private suspend fun simulateDrive() {
        delay(800)
        _routeResults.emit(RouteResult.Started)
        _gear.value = "D"
        var seconds = 300
        while (true) {
            val cur = _eta.value?.seconds ?: seconds
            seconds = minOf(seconds, cur) // '바로 도착'을 누르면 0으로 내려온다
            _eta.value = NaviEta(seconds, seconds * 8, System.currentTimeMillis())
            if (seconds <= 0) break
            delay(1_000)
            seconds = (seconds - 5).coerceAtLeast(0)
        }
        // 도착 → 정차(P) → 아이가 뒷문으로 탑승
        delay(1_500)
        _gear.value = "P"
        // 정차 → 합류 안내 발송 → 자녀가 걸어오는 시간
        delay(9_000)
        _doors.emit(DoorSignal(Door.REAR_RIGHT, open = true, at = System.currentTimeMillis()))
        delay(2_000)
        _doors.emit(DoorSignal(Door.REAR_RIGHT, open = false, at = System.currentTimeMillis()))
    }

    val signals = object : VehicleSignalSource {
        override val doors: SharedFlow<DoorSignal> = _doors
        override val gear: StateFlow<String?> = _gear
        override val doorSignalAvailable: StateFlow<Boolean> = _doorAvailable
        override fun start() = Unit
        override fun stop() = Unit
    }

    val location = object : LocationSource {
        override val location: StateFlow<GeoFix?> = _location
        override fun start() = Unit
        override fun stop() = Unit
    }

    val restriction = object : RestrictionSource {
        override val restricted: StateFlow<Boolean> = drivingNow
        override fun start() = Unit
        override fun stop() = Unit
    }

    /** 음성 대신 배너만 */
    val announcer = object : Announcer {
        private val spoken = HashSet<String>()
        override val banner: StateFlow<Banner?> = _banner
        override fun start() = Unit
        override fun stop() = Unit
        override fun announce(banner: Banner, eventKey: String?) {
            if (eventKey == null || spoken.add(eventKey)) _banner.value = banner
        }
        override fun clearBanner() {
            _banner.value = null
        }
    }

    /** 가짜 SSE: 서버 세션이 바뀔 때마다 이벤트 하나 */
    val events = object : SessionEventSource {
        override fun open(sessionId: Long, lastEventId: String?): Flow<StreamEvent> =
            server.changes.drop(1).map { StreamEvent(id = it.toString(), type = "SESSION_UPDATED", data = "{}") }
    }
}
