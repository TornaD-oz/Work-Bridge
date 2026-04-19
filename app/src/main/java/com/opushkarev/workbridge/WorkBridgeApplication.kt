package com.opushkarev.workbridge

import android.app.Application

class WorkBridgeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensureChannels(this)
    }
}
