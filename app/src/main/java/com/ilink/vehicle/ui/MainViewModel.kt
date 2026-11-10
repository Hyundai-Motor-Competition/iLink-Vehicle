package com.ilink.vehicle.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ilink.vehicle.ILinkApplication
import com.ilink.vehicle.core.network.ApiException
import com.ilink.vehicle.data.model.PickupSpots
import com.ilink.vehicle.data.model.SessionView
import com.ilink.vehicle.data.model.VehicleSettingsSummary
import com.ilink.vehicle.data.model.WeeklyPickups
import com.ilink.vehicle.data.repository.LinkState
import com.ilink.vehicle.domain.VehicleScreen
import com.ilink.vehicle.domain.VehicleScreenResolver
import com.ilink.vehicle.platform.navi.RouteResult
import com.ilink.vehicle.service.PickupSessionService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UiState(
    val screen: VehicleScreen = VehicleScreen.V00,
    val session: SessionView? = null,
    val link: LinkState? = null,
    val weekly: WeeklyPickups? = null,
    val spots: PickupSpots? = null,
    val settings: VehicleSettingsSummary? = null,
    val drivingRestricted: Boolean = false,
    val doorSignalAvailable: Boolean = true,
    val busy: Boolean = false,
    val error: String? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as ILinkApplication).container
    private val local = MutableStateFlow(UiState())
    private var linkJob: Job? = null
    private var pendingSpotId: Long? = null

    val state: StateFlow<UiState> = combine(
        local,
        c.sessions.session,
        c.restriction.restricted,
        c.signals.doorSignalAvailable,
    ) { s, session, restricted, doorOk ->
        val screen = when {
            s.screen == VehicleScreen.V00 || s.screen == VehicleScreen.V07 -> s.screen
            session != null && !session.isTerminal -> VehicleScreenResolver.resolve(session)
            // 완료 직후에는 '확인'(finish)을 누를 때까지 V05 유지
            session?.status?.session == "COMPLETED" -> VehicleScreen.V05
            else -> s.screen
        }
        s.copy(screen = screen, session = session, drivingRestricted = restricted, doorSignalAvailable = doorOk)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, UiState())

    init {
        viewModelScope.launch {
            if (c.links.isLinked.first()) {
                go(VehicleScreen.V01)
                launchCatching { if (c.sessions.restoreActive() != null) PickupSessionService.start(app) }
                loadToday()
            } else {
                startLink()
            }
        }
        viewModelScope.launch {
            c.navi.routeResults.collect { r ->
                val spotId = pendingSpotId ?: return@collect
                when (r) {
                    RouteResult.Started -> launchCatching {
                        c.sessions.confirmRoute(spotId, null)
                        pendingSpotId = null
                    }
                    is RouteResult.Failed -> {
                        pendingSpotId = null // ERR-03: 장소 확정 금지
                        local.value = local.value.copy(error = "길안내를 시작하지 못했어요. 다시 시도해 주세요")
                    }
                }
            }
        }
    }

    fun go(screen: VehicleScreen) {
        local.value = local.value.copy(screen = screen, error = null)
    }

    fun startLink() {
        go(VehicleScreen.V00)
        linkJob?.cancel()
        linkJob = viewModelScope.launch {
            runCatching {
                c.links.startLink().collect { ls ->
                    local.value = local.value.copy(link = ls)
                    if (ls is LinkState.Linked) {
                        go(VehicleScreen.V01)
                        loadToday()
                    }
                }
            }.onFailure { onError(it) }
        }
    }

    fun loadToday() = launchCatching {
        val weekly = c.api.weeklyPickups()
        local.value = local.value.copy(weekly = weekly)
    }

    fun openSettings() = launchCatching {
        go(VehicleScreen.V07)
        local.value = local.value.copy(settings = c.api.settingsSummary())
    }

    fun startPickup(scheduleIds: List<Long>) = launchCatching {
        c.sessions.start(scheduleIds)
        local.value = local.value.copy(spots = c.api.pickupSpots())
        PickupSessionService.start(getApplication())
    }

    /** V02 '이 장소로 길안내': 내비 길안내 성공(onRouteStarted) 뒤에만 SES-04로 확정 */
    fun routeTo(spot: PickupSpots.Spot) {
        val p = spot.stopPoint ?: return
        pendingSpotId = spot.spotId
        c.navi.routeTo(p.lat, p.lng, spot.name ?: "")
    }

    fun holdCall() = launchCatching { c.sessions.holdCall() }
    fun releaseCallHold() = launchCatching { c.sessions.releaseCallHold() }
    fun rejectBoarding(childUserId: Long?) = launchCatching { c.sessions.rejectBoarding(childUserId) }
    fun completeManually() = launchCatching { c.sessions.completeManually() }
    fun cancelPickup() = launchCatching { c.sessions.cancel(null) }

    fun finish() {
        c.sessions.clear()
        go(VehicleScreen.V01)
        loadToday()
    }

    fun logout() = launchCatching {
        c.sessions.clear()
        c.links.logout()
        startLink()
    }

    private fun launchCatching(block: suspend () -> Unit) = viewModelScope.launch {
        local.value = local.value.copy(busy = true, error = null)
        runCatching { block() }.onFailure { onError(it) }
        local.value = local.value.copy(busy = false)
    }

    private suspend fun onError(t: Throwable) {
        // 409: 최신 세션을 다시 받고 사용자에게 바뀐 상태를 보여준다
        if (t is ApiException && t.isConflict) runCatching { c.sessions.refresh() }
        local.value = local.value.copy(error = (t as? ApiException)?.message ?: "잠시 후 다시 시도해 주세요")
    }
}
