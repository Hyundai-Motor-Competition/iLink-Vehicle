package com.ilink.vehicle

import android.app.Application

class ILinkApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.restriction.start()
        container.voice.start()
        container.navi.start()
    }
}
