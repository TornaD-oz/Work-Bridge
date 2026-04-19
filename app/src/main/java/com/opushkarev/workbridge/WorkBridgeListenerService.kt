package com.opushkarev.workbridge

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.Person as CompatPerson
import androidx.core.app.RemoteInput as CompatRemoteInput
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.IconCompat
import kotlinx.coroutines.runBlocking
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class WorkBridgeListenerService : NotificationListenerService() {
    private val repository by lazy { NotifierRepository(applicationContext) }
    private val logTag = "WorkBridgeListener"
    private val preservedCloneKeys = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    private data class SenderIdentity(
        val name: String? = null,
        val iconBitmap: Bitmap? = null,
    )

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensureChannels(this)
        Log.d(logTag, "onCreate")
        runBlocking { repository.recordLifecycle("Created") }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_REQUEST_UNBIND) {
            Log.d(logTag, "manual listener restart requested")
            runCatching {
                requestUnbind()
            }.onFailure { error ->
                Log.w(logTag, "requestUnbind failed during manual restart", error)
            }
            stopSelf(startId)
            return START_NOT_STICKY
        }

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(logTag, "onListenerConnected")
        runBlocking { repository.recordLifecycle("Connected") }
    }

    override fun onListenerDisconnected() {
        Log.d(logTag, "onListenerDisconnected")
        runBlocking { repository.recordLifecycle("Disconnected") }
        requestRebind(ComponentName(this, WorkBridgeListenerService::class.java))
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        Log.d(logTag, "onDestroy")
        runBlocking { repository.recordLifecycle("Destroyed") }
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val sourcePackage = sbn.packageName
        val isSelfNotification = sourcePackage == applicationContext.packageName
        Log.d(logTag, "onNotificationPosted package=$sourcePackage key=${sbn.key}")
        runBlocking {
            repository.recordCallback(
                packageName = sourcePackage,
                includeInRecents = !isSelfNotification,
            )
        }

        if (isSelfNotification) {
            Log.d(logTag, "skip self notification")
            runBlocking { repository.recordSkip(sourcePackage, "App notification") }
            return
        }

        val state = runBlocking { repository.snapshot() }
        if (!state.monitoringEnabled) {
            Log.d(logTag, "skip monitoring disabled")
            runBlocking { repository.recordSkip(sourcePackage, "Monitoring disabled") }
            return
        }

        if (isSummaryNotification(sbn.notification)) {
            Log.d(logTag, "skip summary notification")
            runBlocking { repository.recordSkip(sourcePackage, "Summary notification") }
            return
        }

        if (!sbn.isClearable) {
            Log.d(logTag, "skip not clearable")
            runBlocking { repository.recordSkip(sourcePackage, "Notification not clearable") }
            return
        }

        if (!state.monitoredPackages.contains(sourcePackage)) {
            Log.d(logTag, "skip package not monitored")
            runBlocking { repository.recordSkip(sourcePackage, "Package not monitored") }
            return
        }

        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            Log.d(logTag, "skip app notifications disabled")
            runBlocking { repository.recordSkip(sourcePackage, "App notifications disabled") }
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(logTag, "skip post notifications denied")
            runBlocking { repository.recordSkip(sourcePackage, "POST_NOTIFICATIONS denied") }
            return
        }

        try {
            NotificationChannels.ensureChannels(this)
            val forwarded = buildForwardedNotification(sbn)
            NotificationManagerCompat.from(this).notify(sbn.key.hashCode(), forwarded)
            Log.d(logTag, "forwarded package=$sourcePackage key=${sbn.key}")
            runBlocking { repository.recordForwarded(sourcePackage) }
            if (state.cancelOriginal) {
                preservedCloneKeys.add(sbn.key)
                cancelNotification(sbn.key)
            }
        } catch (exception: Exception) {
            Log.e(logTag, "forward failed for package=$sourcePackage", exception)
            val message = "${exception::class.java.simpleName}: ${exception.message ?: "Unknown error"}"
            runBlocking { repository.recordError(sourcePackage, message) }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        Log.d(logTag, "onNotificationRemoved package=${sbn.packageName} key=${sbn.key}")
        if (sbn.packageName != applicationContext.packageName) {
            if (preservedCloneKeys.remove(sbn.key)) {
                Log.d(logTag, "keeping clone for intentionally cancelled original key=${sbn.key}")
            } else {
                NotificationManagerCompat.from(this).cancel(sbn.key.hashCode())
            }
        }
        super.onNotificationRemoved(sbn)
    }

    private fun buildForwardedNotification(sbn: StatusBarNotification): Notification {
        val original = sbn.notification
        val extras = original.extras ?: Bundle.EMPTY
        val messagingStyle = resolveMessagingStyle(original)
        val senderIdentity = resolveSenderIdentity(original, messagingStyle)
        val title = preferredTitle(extras, sbn.packageName, messagingStyle, senderIdentity)
        val body = preferredBody(extras, messagingStyle)
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()
        val sourceLabel = resolveSourceLabel(sbn.packageName)

        val builder = NotificationCompat.Builder(this, NotificationChannels.FORWARDED_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_stat)
            .setContentTitle(title)
            .setContentText(body.ifBlank { "Forwarded notification from ${sbn.packageName}" })
            .setSubText(sourceLabel)
            .setCategory(original.category)
            .setWhen(sbn.postTime)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)

        if (messagingStyle != null) {
            builder.setStyle(messagingStyle)
        } else if (bigText.isNotBlank()) {
            builder.setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(title)
                    .bigText(bigText)
            )
        } else if (body.isNotBlank()) {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(body))
        }

        original.contentIntent?.let(builder::setContentIntent)
        original.deleteIntent?.let(builder::setDeleteIntent)
        resolveLargeIcon(sbn, senderIdentity)?.let(builder::setLargeIcon)

        original.actions.orEmpty().forEachIndexed { index, action ->
            convertAction(sbn, index, action)?.let(builder::addAction)
        }

        return builder.build()
    }

    private fun preferredTitle(
        extras: Bundle,
        fallbackPackage: String,
        messagingStyle: NotificationCompat.MessagingStyle?,
        senderIdentity: SenderIdentity?,
    ): String {
        senderIdentity?.name
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.let { return it }

        messagingStyle?.messages?.lastOrNull()?.person?.name
            ?.toString()
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.let { return it }

        messagingStyle?.conversationTitle
            ?.toString()
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.let { return it }

        return listOf(
            Notification.EXTRA_CONVERSATION_TITLE,
            Notification.EXTRA_TITLE_BIG,
            Notification.EXTRA_TITLE,
        ).firstNotNullOfOrNull { key ->
            extras.getCharSequence(key)?.toString()?.trim()?.takeIf(String::isNotBlank)
        } ?: fallbackPackage
    }

    private fun preferredBody(
        extras: Bundle,
        messagingStyle: NotificationCompat.MessagingStyle?,
    ): String {
        messagingStyle?.messages?.lastOrNull()?.text
            ?.toString()
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.let { return it }

        return listOf(
            Notification.EXTRA_BIG_TEXT,
            Notification.EXTRA_TEXT,
            Notification.EXTRA_SUB_TEXT,
            Notification.EXTRA_SUMMARY_TEXT,
        ).firstNotNullOfOrNull { key ->
            extras.getCharSequence(key)?.toString()?.trim()?.takeIf(String::isNotBlank)
        }.orEmpty()
    }

    private fun isSummaryNotification(notification: Notification): Boolean {
        return notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
    }

    private fun convertAction(
        sbn: StatusBarNotification,
        actionIndex: Int,
        action: Notification.Action,
    ): NotificationCompat.Action? {
        val pendingIntent = action.actionIntent ?: return null
        val iconCompat = runCatching {
            action.getIcon()?.let(IconCompat::createFromIcon)
        }.getOrNull() ?: IconCompat.createWithResource(this, R.drawable.ic_notification_stat)
        val wrappedPendingIntent = createActionProxyPendingIntent(
            sbn = sbn,
            actionIndex = actionIndex,
            originalPendingIntent = pendingIntent,
            requiresMutableIntent = !action.remoteInputs.isNullOrEmpty() || !action.getDataOnlyRemoteInputs().isNullOrEmpty(),
        )

        val builder = NotificationCompat.Action.Builder(
            iconCompat,
            action.title,
            wrappedPendingIntent,
        )
            .addExtras(action.extras)
            .setAllowGeneratedReplies(action.allowGeneratedReplies)
            .setSemanticAction(action.semanticAction)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setContextual(action.isContextual)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAuthenticationRequired(action.isAuthenticationRequired)
        }

        action.remoteInputs.orEmpty().forEach { remoteInput ->
            builder.addRemoteInput(remoteInput.toCompatRemoteInput())
        }

        action.getDataOnlyRemoteInputs().orEmpty().forEach { remoteInput ->
            builder.addRemoteInput(remoteInput.toCompatRemoteInput())
        }

        return builder.build()
    }

    private fun createActionProxyPendingIntent(
        sbn: StatusBarNotification,
        actionIndex: Int,
        originalPendingIntent: PendingIntent,
        requiresMutableIntent: Boolean,
    ): PendingIntent {
        val intent = Intent(this, WorkBridgeActionProxyReceiver::class.java).apply {
            action = WorkBridgeActionProxyReceiver.ACTION_FORWARD_NOTIFICATION_ACTION
            putExtra(WorkBridgeActionProxyReceiver.EXTRA_ORIGINAL_PENDING_INTENT, originalPendingIntent)
            putExtra(WorkBridgeActionProxyReceiver.EXTRA_NOTIFICATION_KEY, sbn.key)
            putExtra(WorkBridgeActionProxyReceiver.EXTRA_NOTIFICATION_ID, sbn.key.hashCode())
        }
        val requestCode = 31 * sbn.key.hashCode() + actionIndex
        val mutabilityFlag = if (requiresMutableIntent) {
            PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_IMMUTABLE
        }

        return PendingIntent.getBroadcast(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or mutabilityFlag,
        )
    }

    private fun android.app.RemoteInput.toCompatRemoteInput(): CompatRemoteInput {
        val builder = CompatRemoteInput.Builder(resultKey)
            .setLabel(label)
            .setChoices(choices)
            .setAllowFreeFormInput(allowFreeFormInput)
            .addExtras(extras)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            allowedDataTypes.forEach { mimeType ->
                builder.setAllowDataType(mimeType, true)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setEditChoicesBeforeSending(editChoicesBeforeSending)
        }

        return builder.build()
    }

    private fun resolveMessagingStyle(original: Notification): NotificationCompat.MessagingStyle? {
        return runCatching {
            NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(original)
        }.getOrNull()
    }

    private fun resolveSenderIdentity(
        original: Notification,
        messagingStyle: NotificationCompat.MessagingStyle?,
    ): SenderIdentity? {
        val currentUser = messagingStyle?.user

        messagingStyle?.messages
            .orEmpty()
            .asReversed()
            .firstNotNullOfOrNull { message ->
                val person = message.person
                if (person != null && !isSamePerson(person, currentUser)) {
                    val name = person.name?.toString()?.trim()?.takeIf(String::isNotBlank)
                    if (name != null) {
                        return@firstNotNullOfOrNull SenderIdentity(
                            name = name,
                            iconBitmap = person.icon?.loadDrawable(this)?.toBitmap(),
                        )
                    }
                }

                message.sender?.toString()?.trim()?.takeIf(String::isNotBlank)?.let { sender ->
                    SenderIdentity(name = sender)
                }
            }?.let { return it }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val platformMessages = Notification.MessagingStyle.Message.getMessagesFromBundleArray(
                original.extras?.getParcelableArray(Notification.EXTRA_MESSAGES)
            )
            platformMessages
                .asReversed()
                .firstNotNullOfOrNull { message ->
                    val sender = message.senderPerson
                    val name = sender?.name?.toString()?.trim()?.takeIf(String::isNotBlank)
                    if (name != null) {
                        SenderIdentity(
                            name = name,
                            iconBitmap = sender.icon?.let(::loadBitmapFromIcon),
                        )
                    } else {
                        message.sender?.toString()?.trim()?.takeIf(String::isNotBlank)?.let { fallback ->
                            SenderIdentity(name = fallback)
                        }
                    }
                }?.let { return it }
        }

        return null
    }

    private fun isSamePerson(person: CompatPerson, other: CompatPerson?): Boolean {
        if (other == null) {
            return false
        }

        val personKey = person.key?.trim().orEmpty()
        val otherKey = other.key?.trim().orEmpty()
        if (personKey.isNotEmpty() && otherKey.isNotEmpty()) {
            return personKey == otherKey
        }

        val personName = person.name?.toString()?.trim().orEmpty()
        val otherName = other.name?.toString()?.trim().orEmpty()
        return personName.isNotEmpty() && personName == otherName
    }

    private fun resolveSourceLabel(packageName: String): String {
        return runCatching {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        }.getOrDefault(packageName)
    }

    private fun resolveLargeIcon(sbn: StatusBarNotification, senderIdentity: SenderIdentity?): Bitmap? {
        senderIdentity?.iconBitmap?.let { return it }

        val original = sbn.notification

        runCatching {
            original.getLargeIcon()?.let(::loadBitmapFromIcon)
        }.getOrNull()?.let { return it }

        runCatching {
            original.smallIcon?.let(::loadBitmapFromIcon)
        }.getOrNull()?.let { return it }

        return runCatching {
            packageManager.getApplicationIcon(sbn.packageName).toBitmap()
        }.getOrNull()
    }

    private fun loadBitmapFromIcon(icon: Icon): Bitmap? {
        return icon.loadDrawable(this)?.toBitmap()
    }

    private fun Drawable.toBitmap(): Bitmap {
        if (this is BitmapDrawable && bitmap != null) {
            return bitmap
        }

        val width = intrinsicWidth.takeIf { it > 0 } ?: 96
        val height = intrinsicHeight.takeIf { it > 0 } ?: 96
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        return bitmap
    }

    companion object {
        const val ACTION_REQUEST_UNBIND =
            "com.opushkarev.workbridge.action.REQUEST_LISTENER_UNBIND"
    }
}
