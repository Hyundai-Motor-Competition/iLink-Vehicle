package com.ilink.vehicle.platform.navi

import android.util.Log
import com.ilink.vehicle.platform.location.LocationSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 내비가 남은 시간 · 거리(onDrivingInfo)를 주지 않을 때(에뮬레이터 · 길안내 미시작) 직선 거리로 대신 계산한다.
 * 내비 값이 20초 안에 들어왔으면 그대로 쓰고, 아니면 현재 위치 → 목적지 거리 × 1.3(도로 보정) ÷ 시속 25km.
 */
class EtaFallbackNavigation(
    private val real: Navigation,
    private val location: LocationSource,
    private val scope: CoroutineScope,
    /** 시연(debug) 빌드: 에뮬레이터 내비 대신 1.5초 뒤 길안내를 시작한 것으로 보고, ETA는 위치 · 거리로 계산한다 */
    private val simulateRoute: Boolean = false,
) : Navigation {
    private val _eta = MutableStateFlow<NaviEta?>(null)
    override val eta: StateFlow<NaviEta?> = _eta
    private val _routeResults = kotlinx.coroutines.flow.MutableSharedFlow<RouteResult>(extraBufferCapacity = 4)
    override val routeResults: SharedFlow<RouteResult> = _routeResults
    @Volatile private var started = false
    private var timeout: kotlinx.coroutines.Job? = null

    @Volatile private var dest: Pair<Double, Double>? = null

    init {
        scope.launch {
            real.routeResults.collect { r ->
                when {
                    r is RouteResult.Started -> { started = true; timeout?.cancel(); _routeResults.emit(r) }
                    simulateRoute -> Log.w("iLinkRoute", "navi failed ($r), simulating start")
                    else -> _routeResults.emit(r)
                }
            }
        }
        scope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                val r = real.eta.value
                val here = location.location.value
                val d = dest
                _eta.value = when {
                    r != null && now - r.at < 20_000 -> r
                    d != null && here != null -> {
                        val meters = (haversine(here.lat, here.lng, d.first, d.second) * 1.3).roundToInt()
                        NaviEta(seconds = (meters / SPEED_MPS).roundToInt(), meters = meters, at = now)
                    }
                    else -> r
                }
                delay(2_000)
            }
        }
    }

    override fun start() = real.start()

    override fun routeTo(lat: Double, lng: Double, name: String) {
        dest = lat to lng
        // 시연 스크립트(demo-drive.sh)가 목적지를 읽어 에뮬레이터 위치를 움직인다
        Log.i("iLinkRoute", "dest=$lat,$lng name=$name")
        started = false
        // 시연(debug) 빌드: 에뮬레이터 내비는 위치가 순간 이동하면 경로 오류 창을 띄우고 앞으로 나와서 부르지 않는다
        if (!simulateRoute) real.routeTo(lat, lng, name)
        if (simulateRoute) {
            timeout?.cancel()
            timeout = scope.launch {
                delay(1_500)
                if (!started) {
                    Log.i("iLinkRoute", "simulated route start")
                    started = true
                    _routeResults.emit(RouteResult.Started)
                }
            }
        }
    }

    override fun routeHome() {
        dest = null
        if (!simulateRoute) real.routeHome()
    }

    override fun stop() = real.stop()

    private companion object {
        const val SPEED_MPS = 25_000.0 / 3600

        fun haversine(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
            val r = 6_371_000.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLng = Math.toRadians(lng2 - lng1)
            val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
            return 2 * r * asin(sqrt(a))
        }
    }
}
