package com.opushkarev.workbridge

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationManagerCompat

class WorkBridgeActionProxyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FORWARD_NOTIFICATION_ACTION) {
            return
        }

        val originalPendingIntent = intent.readOriginalPendingIntent()
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)

        if (notificationId != 0) {
            NotificationManagerCompat.from(context).cancel(notificationId)
        }

        if (originalPendingIntent == null) {
            Log.w(LOG_TAG, "Missing original pending intent for forwarded action")
            return
        }

        val fillInIntent = Intent(intent).apply {
            component = null
            `package` = null
            removeExtra(EXTRA_ORIGINAL_PENDING_INTENT)
            removeExtra(EXTRA_NOTIFICATION_KEY)
            removeExtra(EXTRA_NOTIFICATION_ID)
        }

        try {
            originalPendingIntent.send(context, 0, fillInIntent)
        } catch (exception: PendingIntent.CanceledException) {
            Log.e(LOG_TAG, "Original pending intent was cancelled", exception)
        }
    }

    private fun Intent.readOriginalPendingIntent(): PendingIntent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(EXTRA_ORIGINAL_PENDING_INTENT, PendingIntent::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(EXTRA_ORIGINAL_PENDING_INTENT)
        }
    }

    companion object {
        const val ACTION_FORWARD_NOTIFICATION_ACTION =
            "com.opushkarev.workbridge.action.FORWARD_NOTIFICATION_ACTION"
        const val EXTRA_ORIGINAL_PENDING_INTENT = "original_pending_intent"
        const val EXTRA_NOTIFICATION_KEY = "notification_key"
        const val EXTRA_NOTIFICATION_ID = "notification_id"

        private const val LOG_TAG = "WorkBridgeAction"
    }
}
