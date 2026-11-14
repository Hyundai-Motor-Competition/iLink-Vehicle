package com.ilink.vehicle.data.repository

import android.os.Build
import com.ilink.vehicle.core.auth.TokenStore
import com.ilink.vehicle.core.network.ApiClient
import com.ilink.vehicle.data.model.LinkCodeResponse
import com.ilink.vehicle.data.remote.VehicleApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

sealed interface LinkState {
    data class Waiting(val code: LinkCodeResponse) : LinkState
    data object Linked : LinkState
    data object Expired : LinkState
}

/** V00: 기기 등록(AUTH-02) → 연결 코드 발급(LINK-01) → 보호자 승인까지 상태 폴링(LINK-02) */
class LinkRepository(private val api: VehicleApi, private val tokens: TokenStore) {

    val isLinked: Flow<Boolean> = tokens.isLinked

    suspend fun ensureDevice() {
        if (tokens.deviceToken() != null) return
        val res = api.registerDevice(model = model(), vin = vin(), key = ApiClient.newIdempotencyKey())
        tokens.saveDeviceToken(res.deviceToken)
    }

    fun startLink(): Flow<LinkState> = flow {
        ensureDevice()
        val code = api.issueLinkCode()
        // 시연 스크립트(scripts/demo/link.sh)가 QR 내용을 읽어 카메라 없는 시뮬레이터에서 연다
        if (com.ilink.vehicle.BuildConfig.DEBUG) android.util.Log.i("iLinkQr", code.qrPayload)
        emit(LinkState.Waiting(code))
        while (true) {
            val status = api.linkStatus(code.codeId)
            when (status.status) {
                "LINKED", "RELINKED" -> {
                    tokens.saveUserTokens(status.accessToken!!, status.refreshToken!!)
                    emit(LinkState.Linked)
                    return@flow
                }
                "EXPIRED" -> {
                    emit(LinkState.Expired)
                    return@flow
                }
            }
            // 서버 권장 간격(기본 2초). 1초보다 자주 부르면 429
            delay((status.pollAfterSeconds ?: 2).coerceAtLeast(1) * 1_000L)
        }
    }

    suspend fun logout() {
        runCatching { api.logout() }
        tokens.clearAll()
    }

    /**
     * AUTH-02는 17자 VIN(영대문자 · 숫자, I · O · Q 제외)을 받는다. Pleos 에뮬레이터는 VIN을 주지 않아서
     * 기기 등록 때 'PLE'로 시작하는 시험용 VIN을 만든다. 앱 데이터를 지우면 새 차량으로 등록된다.
     */
    /** 부모 앱 '차량 정보 확인'에 보이는 이름. 에뮬레이터는 'Car on arm64 emulator' 대신 시연 차종으로 */
    private fun model(): String {
        val m = Build.MODEL ?: return "IONIQ 5"
        return if (m.contains("emulator", ignoreCase = true) || m.contains("sdk", ignoreCase = true)) "IONIQ 5" else m
    }

    private fun vin(): String {
        val chars = "ABCDEFGHJKLMNPRSTUVWXYZ0123456789"
        return "PLE" + (1..14).map { chars.random() }.joinToString("")
    }
}
