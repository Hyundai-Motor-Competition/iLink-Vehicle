package com.ilink.vehicle.platform.sim

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import com.ilink.vehicle.domain.DoorCycleDetector.Door
import com.ilink.vehicle.platform.location.GeoFix
import com.ilink.vehicle.platform.location.LocationSource
import com.ilink.vehicle.platform.ux.RestrictionSource
import com.ilink.vehicle.platform.vehicle.DoorSignal
import com.ilink.vehicle.platform.vehicle.VehicleSignalSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn

/**
 * 시연용 차량 신호 입력 (debug 빌드에서만 켠다).
 * Pleos Connect 에뮬레이터는 user 빌드라 VHAL 주입(`cmd car_service inject-vhal-event`)이 막혀 있어서,
 * 시연 스크립트가 adb broadcast로 기어 · 주행 · 문 · 위치를 넣는다. 들어온 값이 있으면 SDK 값 대신 쓴다.
 *
 *   adb shell am broadcast -p com.ilink.vehicle -a com.ilink.vehicle.SIM --es gear D --ez driving true
 *   adb shell am broadcast -p com.ilink.vehicle -a com.ilink.vehicle.SIM --es lat 37.49 --es lng 127.02
 *   adb shell am broadcast -p com.ilink.vehicle -a com.ilink.vehicle.SIM --es door REAR_RIGHT --ez open true
 */
object SimInput {
    const val ACTION = "com.ilink.vehicle.SIM"
    val gear = MutableStateFlow<String?>(null)
    val driving = MutableStateFlow<Boolean?>(null)
    val location = MutableStateFlow<GeoFix?>(null)
    val doors = MutableSharedFlow<DoorSignal>(extraBufferCapacity = 8)

    fun register(context: Context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                i.getStringExtra("gear")?.let { gear.value = it }
                if (i.hasExtra("driving")) driving.value = i.getBooleanExtra("driving", false)
                val lat = i.getStringExtra("lat")?.toDoubleOrNull()
                val lng = i.getStringExtra("lng")?.toDoubleOrNull()
                if (lat != null && lng != null) location.value = GeoFix(lat, lng, i.getStringExtra("heading")?.toDoubleOrNull())
                i.getStringExtra("door")?.let { name ->
                    runCatching { Door.valueOf(name) }.getOrNull()?.let { d ->
                        doors.tryEmit(DoorSignal(d, i.getBooleanExtra("open", false), System.currentTimeMillis()))
                    }
                }
                Log.i("iLinkSim", "gear=${gear.value} driving=${driving.value} loc=${location.value} door=${i.getStringExtra("door")}")
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(ACTION), ContextCompat.RECEIVER_EXPORTED)
    }
}

class SimVehicleSignals(private val real: VehicleSignalSource, scope: CoroutineScope) : VehicleSignalSource {
    override val doors = merge(real.doors, SimInput.doors).shareIn(scope, SharingStarted.Eagerly)
    override val gear: StateFlow<String?> =
        combine(real.gear, SimInput.gear) { r, s -> s ?: r }.stateIn(scope, SharingStarted.Eagerly, null)
    override val doorSignalAvailable: StateFlow<Boolean> = MutableStateFlow(true)
    override fun start() = real.start()
    override fun stop() = real.stop()
}

class SimRestriction(private val real: RestrictionSource, scope: CoroutineScope) : RestrictionSource {
    override val restricted: StateFlow<Boolean> =
        combine(real.restricted, SimInput.driving) { r, s -> s ?: r }.stateIn(scope, SharingStarted.Eagerly, false)
    override fun start() = real.start()
    override fun stop() = real.stop()
}

class SimLocation(private val real: LocationSource, scope: CoroutineScope) : LocationSource {
    override val location: StateFlow<GeoFix?> =
        combine(real.location, SimInput.location) { r, s -> s ?: r }.stateIn(scope, SharingStarted.Eagerly, null)
    override fun start() = real.start()
    override fun stop() = real.stop()
}

