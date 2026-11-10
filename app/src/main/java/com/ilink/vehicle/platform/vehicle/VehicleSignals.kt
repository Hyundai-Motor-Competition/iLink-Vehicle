package com.ilink.vehicle.platform.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import android.content.Context
import android.util.Log
import com.ilink.vehicle.domain.DoorCycleDetector.Door
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

data class DoorSignal(val door: Door, val open: Boolean, val at: Long)

interface VehicleSignalSource {
    val doors: SharedFlow<DoorSignal>
    val gear: StateFlow<String?> // P | R | N | D
    /** 도어 개폐 신호를 받을 수 없으면 false → V05 수동 완료 폴백 (ERR-14) */
    val doorSignalAvailable: StateFlow<Boolean>
    fun start()
    fun stop()
}

/**
 * Pleos Vehicle SDK 연동.
 * 도어 position 값은 0 = 닫힘, 0 초과 = 열림으로 해석 (Pleos Connect 에뮬레이터에서 확인 필요).
 */
class PleosVehicleSignals(context: Context) : VehicleSignalSource {
    private val vehicle = Vehicle(context.applicationContext)
    private val _doors = MutableSharedFlow<DoorSignal>(extraBufferCapacity = 16)
    private val _gear = MutableStateFlow<String?>(null)
    private val _doorAvailable = MutableStateFlow(true)
    override val doors: SharedFlow<DoorSignal> = _doors
    override val gear: StateFlow<String?> = _gear
    override val doorSignalAvailable: StateFlow<Boolean> = _doorAvailable

    private val doorMap = mapOf(
        DoorValue.ROW_1_LEFT to Door.DRIVER,
        DoorValue.ROW_1_RIGHT to Door.FRONT_RIGHT,
        DoorValue.ROW_2_LEFT to Door.REAR_LEFT,
        DoorValue.ROW_2_RIGHT to Door.REAR_RIGHT,
    )
    private val doorListeners = mutableMapOf<DoorValue, DoorPositionListener>()

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) {
            _gear.value = gear?.toServerGear()
        }

        override fun onFailed(e: Exception) {
            Log.w(TAG, "gear listener failed", e)
        }
    }

    override fun start() {
        runCatching { vehicle.initialize() }.onFailure {
            Log.w(TAG, "Vehicle SDK init failed", it)
            _doorAvailable.value = false
            return
        }
        doorMap.forEach { (value, door) ->
            val l = object : DoorPositionListener {
                override fun onPositionUpdated(pos: Int?) {
                    if (pos != null) _doors.tryEmit(DoorSignal(door, pos > 0, System.currentTimeMillis()))
                }

                override fun onFailed(e: Exception) {
                    Log.w(TAG, "door $door failed", e)
                    _doorAvailable.value = false
                }
            }
            runCatching { vehicle.getDoor().registerDoorPosition(DoorArea(value), l) }
                .onSuccess { doorListeners[value] = l }
                .onFailure { _doorAvailable.value = false }
        }
        runCatching { vehicle.getGear().registerGearSelection(gearListener) }
    }

    override fun stop() {
        doorListeners.forEach { (value, l) -> runCatching { vehicle.getDoor().unregisterDoorPosition(DoorArea(value), l) } }
        doorListeners.clear()
        runCatching { vehicle.getGear().unregisterGearSelection(gearListener) }
        runCatching { vehicle.release() }
    }

    private fun VehicleGear.toServerGear(): String? = when (this) {
        VehicleGear.GEAR_PARK -> "P"
        VehicleGear.GEAR_REVERSE -> "R"
        VehicleGear.GEAR_NEUTRAL -> "N"
        VehicleGear.GEAR_UNKNOWN -> null
        else -> "D"
    }

    private companion object {
        const val TAG = "VehicleSignals"
    }
}
