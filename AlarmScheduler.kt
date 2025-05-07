package hathan.daljit.esp32_iot_studentversion_2

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.text.SimpleDateFormat
import java.util.*

object AlarmScheduler {
    private const val REQUEST_CODE_BASE = 1000

    fun setAlarm(context: Context, schedule: ScheduleEntity) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ScheduleReceiver::class.java).apply {
            putExtra("scheduleId", schedule.scheduleId)
            putExtra("actuatorId", schedule.actuatorId)
            putExtra("action", schedule.action)
            putExtra("deviceId", schedule.deviceId)
        }
        // Use a unique request code based on scheduleId
        val requestCode = REQUEST_CODE_BASE + schedule.scheduleId.hashCode()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Parse time (HH:mm) to Calendar
        try {
            val timeParts = schedule.time.split(":")
            if (timeParts.size != 2) {
                android.util.Log.e("AlarmScheduler", "Invalid time format for schedule ${schedule.scheduleId}: ${schedule.time}")
                return
            }
            val hour = timeParts[0].toIntOrNull() ?: return
            val minute = timeParts[1].toIntOrNull() ?: return
            if (hour !in 0..23 || minute !in 0..59) {
                android.util.Log.e("AlarmScheduler", "Invalid time values for schedule ${schedule.scheduleId}: ${schedule.time}")
                return
            }

            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                // If time is in the past today, schedule for tomorrow
                if (timeInMillis < System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_MONTH, 1)
                }
            }

            // Set exact alarm
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
            android.util.Log.d("AlarmScheduler", "Alarm set for schedule ${schedule.scheduleId} at ${schedule.time}")
        } catch (e: Exception) {
            android.util.Log.e("AlarmScheduler", "Failed to set alarm for schedule ${schedule.scheduleId}: ${e.message}")
        }
    }

    fun cancelAlarm(context: Context, scheduleId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ScheduleReceiver::class.java)
        val requestCode = REQUEST_CODE_BASE + scheduleId.hashCode()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
        android.util.Log.d("AlarmScheduler", "Alarm cancelled for schedule $scheduleId")
    }
}