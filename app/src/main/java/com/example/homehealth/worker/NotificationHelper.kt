package com.example.homehealth.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.homehealth.MainActivity
import com.example.homehealth.R
import com.example.homehealth.data.local.entity.MedicationReminder
import com.example.homehealth.util.NotifConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** 通知工具：渠道创建、用药提醒与健康预警通知 */
@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                NotifConstants.CHANNEL_MEDICATION,
                context.getString(R.string.channel_medication),
                NotificationManager.IMPORTANCE_HIGH
            )
        )
        manager.createNotificationChannel(
            NotificationChannel(
                NotifConstants.CHANNEL_HEALTH,
                context.getString(R.string.channel_health),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }

    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun canNotify(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    /** 用药提醒通知 */
    fun showMedicationReminder(reminder: MedicationReminder, memberName: String) {
        if (!canNotify()) return
        val times = reminder.dailyTimes().joinToString("、").ifBlank { "每日" }
        val notification = NotificationCompat.Builder(context, NotifConstants.CHANNEL_MEDICATION)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("用药提醒：${reminder.medicationName}")
            .setContentText("$memberName · ${reminder.dosage} · $times")
            .setContentIntent(contentIntent())
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(reminder.id.hashCode(), notification)
        } catch (_: SecurityException) {
        }
    }

    /** 异常检测结果汇总通知 */
    fun showAnomalySummary(count: Int) {
        if (count <= 0 || !canNotify()) return
        val notification = NotificationCompat.Builder(context, NotifConstants.CHANNEL_HEALTH)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("健康预警")
            .setContentText("检测到 $count 条新的异常预警，请打开应用查看详情")
            .setContentIntent(contentIntent())
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID_ANOMALY_SUMMARY, notification)
        } catch (_: SecurityException) {
        }
    }

    companion object {
        const val NOTIF_ID_ANOMALY_SUMMARY = 2001
    }
}
