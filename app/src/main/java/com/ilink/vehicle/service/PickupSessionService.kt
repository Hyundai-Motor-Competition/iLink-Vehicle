package com.ilink.vehicle.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.ilink.vehicle.ILinkApplication
import com.ilink.vehicle.R
import com.ilink.vehicle.data.model.LatLng
import com.ilink.vehicle.data.model.VehicleEventRequest
import com.ilink.vehicle.data.model.VehicleTelemetryRequest
import com.ilink.vehicle.domain.DoorCycleDetector
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

/**
 * 세션 진행 중 백그라운드 신호 전송.
 * - SIG-01 텔레메트리: 15초마다 (내비 ETA · 남은 거리 · 위치 · 기어)
 * - SIG-02 차량 이벤트: 정차/출발(기어 P 전환) · 도어 개폐(DOOR_CYCLE) 즉시
 * 합류 호출 판정(3분·700m, 가드 G1~G5)은 서버가 한다. 차량은 신호만 보낸다.
 */
class PickupSessionService : LifecycleService() {

    private val c by lazy { (application as ILinkApplication).container }
    private val doorDetector = DoorCycleDetector()

    override fun onCreate() {
        super.onCreate()
        // 위치 권한이 없으면 위치 포그라운드 서비스를 시작할 수 없다 (Android 14+ SecurityException → 앱 종료).
        // 그때는 일반 서비스로 돌린다: 화면이 떠 있는 동안 신호는 계속 보내고, 권한은 MainActivity가 요청한다.
        if (hasLocationPermission(this)) {
            runCatching {
                ServiceCompat.startForeground(this, NOTI_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            }.onFailure { Log.w(TAG, "foreground start failed, running as normal service", it) }
        } else {
            Log.w(TAG, "no location permission: running without foreground")
        }
        c.signals.start()
        c.location.start()

        lifecycleScope.launch { telemetryLoop() }
        lifecycleScope.launch { watchGear() }
        lifecycleScope.launch { watchDoors() }
        lifecycleScope.launch {
            c.sessions.session.collect { if (it == null || it.isTerminal) stopSelf() }
        }
    }

    private suspend fun telemetryLoop() {
        while (lifecycleScope.isActive) {
            val eta = c.navi.eta.value
            val loc = c.location.location.value
            if (eta != null) {
                runCatching {
                    c.sessions.sendTelemetry(
                        VehicleTelemetryRequest(
                            observedAt = Instant.ofEpochMilli(eta.at).toString(),
                            navEtaSeconds = eta.seconds.coerceAtLeast(0),
                            remainingMeters = eta.meters.coerceAtLeast(0),
                            location = loc?.let { LatLng(it.lat, it.lng) },
                            headingDeg = loc?.heading?.toInt()?.mod(360),
                            gear = c.signals.gear.value,
                        ),
                    )
                }
            }
            delay(c.telemetryIntervalMs)
        }
    }

    private suspend fun watchGear() {
        var previous: String? = null
        c.signals.gear.collect { gear ->
            when {
                gear == "P" && previous != null && previous != "P" -> sendEvent("STOPPED")
                previous == "P" && gear != null && gear != "P" -> {
                    doorDetector.reset()
                    sendEvent("DEPARTED")
                }
            }
            previous = gear
        }
    }

    private suspend fun watchDoors() {
        lifecycleScope.launch {
            c.signals.doors.collect { s ->
                doorDetector.onDoor(s.door, s.open, s.at, stopped = c.signals.gear.value == "P")
            }
        }
        while (lifecycleScope.isActive) {
            doorDetector.poll(System.currentTimeMillis())?.let { cycle ->
                sendEvent("DOOR_CYCLE", door = cycle.door.serverCode, at = cycle.closedAt)
            }
            delay(500)
        }
    }

    private suspend fun sendEvent(type: String, door: String? = null, at: Long = System.currentTimeMillis()) {
        val loc = c.location.location.value
        runCatching {
            c.sessions.sendVehicleEvent(
                VehicleEventRequest(
                    eventId = UUID.randomUUID().toString(),
                    type = type,
                    occurredAt = Instant.ofEpochMilli(at).toString(),
                    location = loc?.let { LatLng(it.lat, it.lng) },
                    door = door,
                ),
            )
        }
    }

    override fun onDestroy() {
        c.signals.stop()
        c.location.stop()
        super.onDestroy()
    }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.session_channel), NotificationManager.IMPORTANCE_LOW))
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.session_channel))
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL = "pickup_session"
        private const val NOTI_ID = 1001

        private const val TAG = "PickupSessionService"

        fun hasLocationPermission(context: Context): Boolean =
            listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).any {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }

        fun start(context: Context) {
            val intent = Intent(context, PickupSessionService::class.java)
            // startForegroundService로 띄우면 5초 안에 startForeground를 꼭 불러야 하므로, 권한이 있을 때만 쓴다
            if (hasLocationPermission(context)) context.startForegroundService(intent) else context.startService(intent)
        }
    }
}
