package hathan.daljit.esp32_iot_studentversion_2

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Device rebooted, rescheduling alarms")
            val scheduleDao = AppModule.provideScheduleDao(AppModule.provideScheduleDatabase(context))
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val schedules = scheduleDao.getAllSchedules()
                    Log.d("BootReceiver", "Found ${schedules.size} schedules to reschedule")
                    schedules.forEach { schedule ->
                        AlarmScheduler.setAlarm(context, schedule)
                        Log.d("BootReceiver", "Rescheduled alarm for schedule ${schedule.scheduleId}")
                    }
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Failed to reschedule alarms: ${e.message}")
                }
            }
        }
    }
}