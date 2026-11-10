package com.ilink.vehicle.domain

import com.ilink.vehicle.data.model.SessionView

/** 차량 IVI 화면 ID (기획서 ID Rule) */
enum class VehicleScreen { V00, V01, V02, V03, V04, V05, V06, V07 }

/** 진행 중 세션 상태로 V02~V06 중 보여줄 화면을 정한다. */
object VehicleScreenResolver {
    fun resolve(view: SessionView): VehicleScreen {
        val s = view.status
        return when {
            s?.session == "COMPLETED" || view.boarding?.detectedAt != null -> VehicleScreen.V05
            s?.session == "CANCELLED" -> VehicleScreen.V01
            s?.session == "ON_HOLD" || view.incident?.type != null || s?.spot == "INVALIDATED" -> VehicleScreen.V06
            view.call?.callId != null || s?.vehicle == "STOPPED" -> VehicleScreen.V04
            s?.spot == "CONFIRMED" -> VehicleScreen.V03
            else -> VehicleScreen.V02
        }
    }
}
