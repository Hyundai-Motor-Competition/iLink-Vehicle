package com.ilink.vehicle

import android.content.Context
import com.ilink.vehicle.core.auth.TokenStore
import com.ilink.vehicle.core.network.ApiClient
import com.ilink.vehicle.data.remote.SessionEventSource
import com.ilink.vehicle.data.remote.SessionStream
import com.ilink.vehicle.data.remote.VehicleApi
import com.ilink.vehicle.data.repository.LinkRepository
import com.ilink.vehicle.data.repository.SessionRepository
import com.ilink.vehicle.demo.DemoWorld
import com.ilink.vehicle.platform.location.LocationGateway
import com.ilink.vehicle.platform.location.LocationSource
import com.ilink.vehicle.platform.navi.NaviGateway
import com.ilink.vehicle.platform.navi.Navigation
import com.ilink.vehicle.platform.ux.DrivingRestriction
import com.ilink.vehicle.platform.ux.RestrictionSource
import com.ilink.vehicle.platform.vehicle.PleosVehicleSignals
import com.ilink.vehicle.platform.vehicle.VehicleSignalSource
import com.ilink.vehicle.platform.voice.Announcer
import com.ilink.vehicle.platform.voice.VoiceAnnouncer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * 수동 DI. 화면·서비스가 같은 인스턴스를 공유한다.
 * demo 빌드(`BuildConfig.DEMO`)에서는 서버와 Pleos SDK 대신 [DemoWorld]를 끼운다.
 */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** demo 빌드에서만 있음. 데모 조작 패널이 쓴다 */
    val demo: DemoWorld? = if (BuildConfig.DEMO) DemoWorld(appScope) else null

    val tokens = TokenStore(context)
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .apply { demo?.let { addInterceptor(it.server) } }
        .build()
    private val client = ApiClient(BuildConfig.API_BASE_URL, tokens, http)
    val api = VehicleApi(client)

    val links = LinkRepository(api, tokens)
    private val events: SessionEventSource = demo?.events ?: SessionStream(client, tokens)
    val sessions = SessionRepository(api, events, appScope)

    val signals: VehicleSignalSource = demo?.signals ?: PleosVehicleSignals(context)
    val navi: Navigation = demo?.navigation ?: NaviGateway(context)
    val location: LocationSource = demo?.location ?: LocationGateway(context)
    val voice: Announcer = demo?.announcer ?: VoiceAnnouncer(context)
    val restriction: RestrictionSource = demo?.restriction ?: DrivingRestriction(context)

    /** SIG-01 텔레메트리 주기. 데모는 주행을 10배 빨리 돌리므로 2초 */
    val telemetryIntervalMs: Long = if (demo != null) 2_000L else 15_000L
}
