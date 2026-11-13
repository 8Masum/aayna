package com.themasum.aayna

import android.app.Application

class AaynaApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.logger.logInfo(TAG, "Project Aayna application started")
    }

    private companion object {
        const val TAG = "AaynaApp"
    }
}
