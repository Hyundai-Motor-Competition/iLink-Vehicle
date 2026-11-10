package com.ilink.vehicle.platform.ux

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 주행 규제 상태 (QA-20, QA-27, QA-28).
 * 규제 중에는 QR(V00·V07), 정차 전용 조작(V05 수동 완료), 텍스트 입력을 막는다.
 */
interface RestrictionSource {
    val restricted: StateFlow<Boolean>
    fun start()
    fun stop()
}

class DrivingRestriction(private val context: Context) : RestrictionSource {
    private var car: Car? = null
    private var manager: CarUxRestrictionsManager? = null
    private val _restricted = MutableStateFlow(false)
    override val restricted: StateFlow<Boolean> = _restricted

    private val listener = CarUxRestrictionsManager.OnUxRestrictionsChangedListener { apply(it) }

    override fun start() {
        runCatching {
            val c = Car.createCar(context) ?: return
            car = c
            manager = (c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager)?.also {
                it.registerListener(listener)
                apply(it.currentCarUxRestrictions)
            }
        }.onFailure { Log.w("DrivingRestriction", "car service unavailable", it) }
    }

    private fun apply(r: CarUxRestrictions?) {
        _restricted.value = r?.isRequiresDistractionOptimization == true
    }

    override fun stop() {
        runCatching { manager?.unregisterListener() }
        runCatching { car?.disconnect() }
        manager = null
        car = null
    }
}
