package com.clinicaserena.app

import android.app.Application
import com.clinicaserena.app.core.push.PushNotifications

class ClinicaSerenaApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        PushNotifications.createChannel(this)
        // Antes de restaurar: el gestor push escucha la sesión desde su primer estado.
        container.pushRegistration.start()
        container.sessionManager.restoreIfNeeded()
    }
}
