package com.ilink.vehicle

import android.app.Application
import android.util.Log

class ILinkApplication : Application() {
    private companion object {
        const val TAG = "ILinkApplication"
    }

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // 시연 스크립트 신호 (debug 실제 빌드만, scripts/demo/drive.sh)
        if (BuildConfig.DEBUG && !BuildConfig.DEMO) com.ilink.vehicle.platform.sim.SimInput.register(this)
        // 플랫폼 서비스 하나가 실패해도(권한 · 미지원) 앱은 뜨게 한다
        runCatching { container.restriction.start() }.onFailure { Log.w(TAG, "restriction", it) }
        runCatching { container.voice.start() }.onFailure { Log.w(TAG, "voice", it) }
        runCatching { container.navi.start() }.onFailure { Log.w(TAG, "navi", it) }
    }
}
