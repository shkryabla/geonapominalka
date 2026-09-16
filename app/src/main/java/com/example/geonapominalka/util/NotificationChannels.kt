package com.example.geonapominalka.util
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import com.example.geonapominalka.R

/**
 * Звук и вибрация уведомления о приходе в зону задаются ТОЛЬКО через канал
 * REMINDER_CHANNEL_ID — на Android 8+ Builder.setSound()/setVibrate() игнорируются,
 * если канал уже создан. Канал foreground-сервиса отдельный и всегда без звука,
 * чтобы кастомный звук не влиял на служебное уведомление "напомниТут активно".
 */
object NotificationChannels {
    /** Создаёт оба канала, если их ещё нет. Повторный вызов ничего не меняет. */
    fun ensureCreated(context: Context, soundUri: Uri?, vibrationEnabled: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(Constants.NOTIFICATION_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    Constants.NOTIFICATION_CHANNEL_ID,
                    context.getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = context.getString(R.string.notification_channel_description)
                    setSound(null, null)
                    enableVibration(false)
                }
            )
        }
        if (manager.getNotificationChannel(Constants.REMINDER_CHANNEL_ID) == null) {
            manager.createNotificationChannel(buildReminderChannel(context, soundUri, vibrationEnabled))
        }
    }

    /** Единственный способ поменять звук/вибрацию на Android 8+ — канал неизменяем после создания. */
    fun rebuildReminderChannel(context: Context, soundUri: Uri?, vibrationEnabled: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.deleteNotificationChannel(Constants.REMINDER_CHANNEL_ID)
        manager.createNotificationChannel(buildReminderChannel(context, soundUri, vibrationEnabled))
    }

    private fun buildReminderChannel(context: Context, soundUri: Uri?, vibrationEnabled: Boolean): NotificationChannel {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        return NotificationChannel(
            Constants.REMINDER_CHANNEL_ID,
            context.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.reminder_channel_description)
            setSound(soundUri ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), audioAttributes)
            enableVibration(vibrationEnabled)
            if (vibrationEnabled) vibrationPattern = longArrayOf(0, 300, 200, 300)
        }
    }
}
