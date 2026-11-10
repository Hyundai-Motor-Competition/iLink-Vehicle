package com.ilink.vehicle.core.network

import kotlinx.serialization.Serializable

/** 서버 오류 본문: { "error": { code, message, retryAfterSeconds } } */
@Serializable
internal data class ErrorEnvelope(val error: ErrorBody? = null) {
    @Serializable
    data class ErrorBody(val code: String? = null, val message: String? = null, val retryAfterSeconds: Int? = null)
}

class ApiException(
    val status: Int,
    val code: String,
    override val message: String,
    val retryAfterSeconds: Int? = null,
) : Exception(message) {
    /** 409: 최신 세션을 다시 조회한 뒤 판단 (ERR-07 등) */
    val isConflict get() = status == 409
    val isNetwork get() = status == 0
}
