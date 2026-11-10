package com.ilink.vehicle.core.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.authStore by preferencesDataStore("auth")

/**
 * 차량 토큰 2단계
 * - deviceToken: 연결 전 기기 토큰 (AUTH-02). LINK-01 · LINK-02만 호출 가능
 * - access/refresh: 보호자 승인 후 받은 차량 토큰 (LINK-02 LINKED)
 */
class TokenStore(private val context: Context) {
    private val deviceKey = stringPreferencesKey("device_token")
    private val accessKey = stringPreferencesKey("access_token")
    private val refreshKey = stringPreferencesKey("refresh_token")

    val isLinked: Flow<Boolean> = context.authStore.data.map { it[accessKey] != null }

    suspend fun accessToken(): String? = context.authStore.data.first().let { it[accessKey] ?: it[deviceKey] }
    suspend fun refreshToken(): String? = context.authStore.data.first()[refreshKey]
    suspend fun deviceToken(): String? = context.authStore.data.first()[deviceKey]

    suspend fun saveDeviceToken(token: String) {
        context.authStore.edit { it[deviceKey] = token }
    }

    suspend fun saveUserTokens(access: String, refresh: String) {
        context.authStore.edit {
            it[accessKey] = access
            it[refreshKey] = refresh
        }
    }

    suspend fun clearUserTokens() {
        context.authStore.edit {
            it.remove(accessKey)
            it.remove(refreshKey)
        }
    }

    suspend fun clearAll() {
        context.authStore.edit { it.clear() }
    }
}
