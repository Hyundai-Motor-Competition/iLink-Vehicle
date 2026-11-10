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
        val res = api.registerDevice(model = Build.MODEL ?: "unknown", vin = null, key = ApiClient.newIdempotencyKey())
        tokens.saveDeviceToken(res.deviceToken)
    }

    fun startLink(): Flow<LinkState> = flow {
        ensureDevice()
        val code = api.issueLinkCode()
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
}
