package com.clinicaserena.app

import android.app.Application

class ClinicaSerenaApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.sessionManager.restoreIfNeeded()
    }
}
