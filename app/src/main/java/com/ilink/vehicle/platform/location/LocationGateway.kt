package com.ilink.vehicle.platform.location

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 텔레메트리(SIG-01)에 싣는 위치 */
data class GeoFix(val lat: Double, val lng: Double, val heading: Double? = null)

interface LocationSource {
    val location: StateFlow<GeoFix?>
    fun start()
    fun stop()
}

/** Pleos FusedLocation 연동. 텔레메트리(SIG-01)의 location · headingDeg에 사용 */
class LocationGateway(context: Context) : LocationSource {
    private val fused = FusedLocation(context.applicationContext)
    private val _location = MutableStateFlow<GeoFix?>(null)
    override val location: StateFlow<GeoFix?> = _location

    private val listener = object : FusedLocationListener {
        override fun onChanged(data: LocationData?) {
            if (data == null) return
            _location.value = GeoFix(data.latitude, data.longitude, data.heading?.toDouble())
        }

        override fun onError(e: Exception) {
            Log.w("LocationGateway", "fused location error", e)
        }
    }

    override fun start() {
        runCatching {
            fused.initialize()
            if (fused.isSupported()) fused.registerFusedLocationCallback(listener)
        }.onFailure { Log.w("LocationGateway", "init failed", it) }
    }

    override fun stop() {
        runCatching { fused.unregisterFusedLocationCallback(listener) }
        runCatching { fused.release() }
    }
}
