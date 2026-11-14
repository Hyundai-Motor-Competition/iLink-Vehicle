package com.ilink.vehicle.platform.navi

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.constants.BookmarkType
import ai.pleos.playground.navi.data.BookmarkInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** 내비 남은 시간·거리. 단위(초·m)는 에뮬레이터에서 확인 필요 */
data class NaviEta(val seconds: Int, val meters: Int, val at: Long)

sealed interface RouteResult {
    data object Started : RouteResult
    data class Failed(val reason: String) : RouteResult // ERR-03 NAVIGATION_UNAVAILABLE
}

/** 길안내 요청 · 남은 시간/거리. 실제는 Pleos 내비, 데모는 가짜 주행 */
interface Navigation {
    val eta: StateFlow<NaviEta?>
    val routeResults: SharedFlow<RouteResult>
    fun start()
    fun routeTo(lat: Double, lng: Double, name: String)
    /** 내비 즐겨찾기의 '집'으로 길안내 (탑승 완료 → 집으로 가기). 집이 없으면 아무것도 하지 않는다 */
    fun routeHome() {}
    fun stop()
}

/** Pleos NaviHelper 연동: 확정 장소로 길안내(F1-06) · ETA 수집(F3-01) */
class NaviGateway(context: Context) : Navigation {
    private val navi = NaviHelper(context.applicationContext)
    private val _eta = MutableStateFlow<NaviEta?>(null)
    private val _routeResults = MutableSharedFlow<RouteResult>(extraBufferCapacity = 4)
    override val eta: StateFlow<NaviEta?> = _eta
    override val routeResults: SharedFlow<RouteResult> = _routeResults

    private val listener = object : NaviHelperEventListener {
        override fun onRouteStarted(info: RouteStartInfo) {
            _routeResults.tryEmit(RouteResult.Started)
        }

        override fun onDrivingInfo(info: DrivingInfo) {
            val dest = info.destination ?: return
            _eta.value = NaviEta(dest.duration, dest.distance, System.currentTimeMillis())
        }

        override fun onError(code: NaviErrorCode) {
            _routeResults.tryEmit(RouteResult.Failed(code.name))
        }

        override fun onRouteCancelled() {
            _eta.value = null
        }

        // routeHome()이 즐겨찾기를 물어본 경우에만 '집'으로 길안내
        override fun onBookmarkInfo(list: List<BookmarkInfo>) {
            if (!homeRequested) return
            homeRequested = false
            val home = list.firstOrNull { it.category == BookmarkType.HOME } ?: return
            runCatching {
                navi.requestRoute(
                    RouteInfo(
                        latitude = home.latitude, longitude = home.longitude, poiName = home.name ?: "집",
                        poiId = home.poiId ?: "", address = home.address ?: "", poiSubId = home.poiSubId ?: "",
                    ),
                )
            }
        }
    }

    @Volatile private var homeRequested = false

    override fun routeHome() {
        homeRequested = true
        runCatching { navi.getBookmarkInfo() }.onFailure { homeRequested = false }
    }

    override fun start() {
        runCatching { navi.initialize({ navi.addListener(listener) }, { _ -> }) }
    }

    override fun routeTo(lat: Double, lng: Double, name: String) {
        runCatching { navi.requestRoute(RouteInfo(latitude = lat, longitude = lng, poiName = name, poiId = "", address = "", poiSubId = "")) }
            .onFailure { _routeResults.tryEmit(RouteResult.Failed(it.message ?: "requestRoute failed")) }
    }

    override fun stop() {
        runCatching { navi.removeListener(listener) }
        runCatching { navi.release() }
    }
}
