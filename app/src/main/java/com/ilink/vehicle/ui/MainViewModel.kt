package com.ilink.vehicle.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ilink.vehicle.ILinkApplication
import com.ilink.vehicle.core.network.ApiException
import com.ilink.vehicle.data.model.Contacts
import com.ilink.vehicle.data.model.PickupSpots
import com.ilink.vehicle.data.model.SessionView
import com.ilink.vehicle.data.model.VehicleSettingsSummary
import com.ilink.vehicle.data.model.WeeklyPickups
import com.ilink.vehicle.data.repository.LinkState
import com.ilink.vehicle.domain.VehicleScreen
import com.ilink.vehicle.domain.VehicleScreenResolver
import com.ilink.vehicle.domain.VoiceCues
import com.ilink.vehicle.platform.voice.Banner
import java.time.LocalDate
import java.time.ZoneId
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
    /** V01 날짜 띠: 지난주 ~ 다음 주 (3주) */
    val calendar: List<WeeklyPickups.Day> = emptyList(),
    val spots: PickupSpots? = null,
    /** SPOT-04 추천 결과 (V02 소요 시간 · 도착 시각). 없으면 장소 목록 값만 쓴다 */
    val recommendations: com.ilink.vehicle.data.model.SpotRecommendations? = null,
    val settings: VehicleSettingsSummary? = null,
    val drivingRestricted: Boolean = false,
    val doorSignalAvailable: Boolean = true,
    /** 음성 안내 중이면 앱 창에 U01~U08 안내를 띄운다 */
    val banner: Banner? = null,
    /** V06 '직접 연락'으로 받은 연락처. null이면 닫힘 */
    val contacts: List<Contacts.Contact>? = null,
    val busy: Boolean = false,
    val error: String? = null,
    /** 출발 알림을 보낸 때 (길안내 시작 = 확정). 1분 뒤 V03 단계가 '목적지로 가는 중'으로 바뀐다 */
    val departedAt: Long? = null,
    /** V05 '여기로 부르기'로 지금 차 위치에 아이를 부른 때 */
    val calledHereAt: Long? = null,
    /** V05 '다른장소로 가기'를 눌러 V06(대안 장소 고르기)을 보는 중 */
    val choosingPlace: Boolean = false,
) {
    /** 주행 중(주행 규제): 화면을 주행용으로 줄이고 정차 전용 조작을 숨긴다 */
    val driving: Boolean get() = drivingRestricted
}

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
        c.voice.banner,
    ) { s, session, restricted, doorOk, banner ->
        val screen = when {
            s.screen == VehicleScreen.V00 || s.screen == VehicleScreen.V07 -> s.screen
            session != null && !session.isTerminal -> VehicleScreenResolver.resolve(session)
            // 완료 직후에는 '확인'(finish)을 누를 때까지 V05 유지
            session?.status?.session == "COMPLETED" -> VehicleScreen.V05
            else -> s.screen
        }
        s.copy(screen = screen, session = session, drivingRestricted = restricted, doorSignalAvailable = doorOk, banner = banner)
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
        // 세션 변화 → 음성 안내 U02~U07
        viewModelScope.launch {
            var prev: com.ilink.vehicle.data.model.SessionView? = null
            c.sessions.session.collect { next ->
                if (next != null) VoiceCues.onSession(prev, next).forEach { c.voice.announce(it.banner, it.key) }
                // 앱을 다시 열어 진행 중 세션을 이어 받으면 지금을 출발 시각으로 본다
                if (next != null && !next.isTerminal && next.status?.spot == "CONFIRMED" && local.value.departedAt == null) {
                    local.value = local.value.copy(departedAt = System.currentTimeMillis())
                }
                prev = next
            }
        }
        viewModelScope.launch {
            c.navi.routeResults.collect { r ->
                val spotId = pendingSpotId ?: return@collect
                when (r) {
                    RouteResult.Started -> launchCatching {
                        c.sessions.confirmRoute(spotId, null)
                        pendingSpotId = null
                        // 처음 출발할 때만 출발 알림 시각을 잡는다 (다른 장소로 바꿔 다시 출발해도 유지)
                        if (local.value.departedAt == null) local.value = local.value.copy(departedAt = System.currentTimeMillis())
                        local.value = local.value.copy(choosingPlace = false, calledHereAt = null)
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
        // V01 인사말(보호자 이름) · 날짜 띠(앞뒤 주) · 'N분 후 도착'(기본 장소)에 쓰는 값. 실패해도 오늘 일정은 보여 준다
        runCatching { c.api.settingsSummary() }.onSuccess { local.value = local.value.copy(settings = it) }
        val monday = weekly.weekStart?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val around = if (monday == null) emptyList() else listOf(monday.minusWeeks(1), monday.plusWeeks(1)).flatMap { w ->
            runCatching { c.api.weeklyPickups(w.toString()).days }.getOrDefault(emptyList())
        }
        local.value = local.value.copy(calendar = (around + weekly.days).distinctBy { it.date }.sortedBy { it.date })
        if (local.value.spots == null) runCatching { c.api.pickupSpots() }.onSuccess { local.value = local.value.copy(spots = it) }
        // U01 오늘 픽업 일정 안내 (진행 중 세션이 없을 때 하루 한 번)
        if (c.sessions.session.value == null) {
            val today = LocalDate.now(ZoneId.of("Asia/Seoul")).toString()
            val pickups = weekly.days.firstOrNull { it.date == today }?.pickups.orEmpty()
            VoiceCues.today(today, pickups)?.let { c.voice.announce(it.banner, it.key) }
        }
    }

    fun openSettings() = launchCatching {
        go(VehicleScreen.V07)
        local.value = local.value.copy(settings = c.api.settingsSummary())
    }

    fun startPickup(scheduleIds: List<Long>) = launchCatching {
        c.sessions.start(scheduleIds)
        local.value = local.value.copy(spots = c.api.pickupSpots())
        loadRecommendations()
        PickupSessionService.start(getApplication())
    }

    /** SPOT-04: 지금 위치에서 학원까지의 추천 후보. 실패해도 V02는 장소 목록만으로 그린다 */
    private suspend fun loadRecommendations() {
        val academyId = local.value.spots?.academies?.firstOrNull()?.academyId ?: return
        val here = c.location.location.value ?: return
        runCatching { c.api.recommendSpots(academyId, com.ilink.vehicle.data.model.LatLng(here.lat, here.lng)) }
            .onSuccess { local.value = local.value.copy(recommendations = it) }
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
    /** V06 보류·취소: 픽업을 끝내고 오늘의 픽업으로 */
    fun cancelPickup() = launchCatching {
        c.sessions.cancel(null)
        finish()
    }

    /** V06 다시 진행 (아이가 안 탔을 때 다시 기다리기) */
    fun resume() = launchCatching { c.sessions.resume() }

    /** V05 여기로 부르기: 정한 장소가 아닌 근처에 섰을 때 지금 차 위치로 아이에게 합류 안내를 바로 보낸다 (CALL-02) */
    fun callHere() = launchCatching {
        c.sessions.sendCallManually()
        local.value = local.value.copy(calledHereAt = System.currentTimeMillis())
    }

    /** V05 다른장소로 가기 → V06 대안 장소 고르기 */
    fun chooseOtherPlace(on: Boolean) {
        local.value = local.value.copy(choosingPlace = on, error = null)
    }

    /** V06 대안 확인: 장소를 바꾸고(SES-05) 그 장소로 다시 길안내 → 길안내가 시작되면 확정 */
    fun useAlternative(spot: PickupSpots.Spot) = launchCatching {
        // 고른 순간 V06 목록을 닫는다 (다시 자동 출발하지 않게)
        local.value = local.value.copy(choosingPlace = false, calledHereAt = null)
        c.sessions.changeSpot(spot.spotId)
        routeTo(spot)
    }

    /** V04·V06 직접 연락: 세션 연락처(SES-10)를 받아 창 안에 보여 준다 */
    fun openContacts() = launchCatching {
        local.value = local.value.copy(contacts = c.sessions.contacts().items)
    }

    fun closeContacts() {
        local.value = local.value.copy(contacts = null)
    }

    fun clearBanner() = c.voice.clearBanner()

    /**
     * U08 제한적 음성 명령. 지금은 '상태'와 '보류'만 받는다.
     * 음성 인식(Pleos STT) 결과 문장이 들어온다고 보고, 데모 패널에서도 같은 문장으로 부른다.
     */
    fun voiceCommand(utterance: String) {
        val text = utterance.replace(" ", "")
        when {
            "보류" in text && "해제" !in text -> {
                if (c.sessions.session.value?.call?.held == false) holdCall()
                c.voice.announce(Banner(com.ilink.vehicle.platform.voice.VoiceCue.U08, "합류 안내를 보류했어요", "다시 보내려면 '보류 해제'라고 말해 주세요", "\"$utterance\""), null)
            }
            "해제" in text -> {
                if (c.sessions.session.value?.call?.held == true) releaseCallHold()
                c.voice.announce(Banner(com.ilink.vehicle.platform.voice.VoiceCue.U08, "합류 안내 보류를 풀었어요", "도착 3분 전에 자동으로 보내요", "\"$utterance\""), null)
            }
            else -> c.voice.announce(VoiceCues.status(c.sessions.session.value, "\"$utterance\""), null)
        }
    }

    /** 탑승 완료 → 집으로 가기: 아직 완료 전이면 완료(SES-11) 후 내비 즐겨찾기 '집'으로 길안내 */
    fun goHome() = launchCatching {
        if (c.sessions.session.value?.isTerminal == false) c.sessions.completeManually()
        c.navi.routeHome()
        finish()
    }

    /** 탑승 완료 → 픽업 끝내기: 아직 완료 전이면 완료 후 오늘의 픽업으로 */
    fun endPickup() = launchCatching {
        if (c.sessions.session.value?.isTerminal == false) c.sessions.completeManually()
        finish()
    }

    fun finish() {
        c.sessions.clear()
        local.value = local.value.copy(departedAt = null, calledHereAt = null, choosingPlace = false)
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
