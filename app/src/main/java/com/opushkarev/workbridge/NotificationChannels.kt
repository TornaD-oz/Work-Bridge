package com.opushkarev.workbridge

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationChannels {
    const val FORWARDED_CHANNEL_ID = "work_bridge_forwarded_notifications"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            FORWARDED_CHANNEL_ID,
            "Work Bridge notifications",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications mirrored by Work Bridge from monitored work-profile apps."
            enableLights(true)
            enableVibration(true)
            setShowBadge(true)
        }

        manager.createNotificationChannel(channel)
    }

    fun ensureForwardingChannel(context: Context) {
        ensureChannels(context)
    }
}
