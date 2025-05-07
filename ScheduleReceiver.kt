package hathan.daljit.esp32_iot_studentversion_2

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*
import hathan.daljit.esp32_iot_studentversion_2.ScheduleDatabase
import java.lang.Compiler.command

class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val scheduleId = intent.getStringExtra("scheduleId") ?: return
        val actuatorId = intent.getStringExtra("actuatorId") ?: return
        val action = intent.getStringExtra("action") ?: return
        val deviceId = intent.getStringExtra("deviceId") ?: return

        val scheduleDao = AppModule.provideScheduleDao(AppModule.provideScheduleDatabase(context))
        val firebaseHost = ConfigManager.getFirebaseHost(context)
        val firebaseAuth = ConfigManager.getFirebaseAuth(context)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Fetch schedule from Room
                val schedule = scheduleDao.getScheduleById(scheduleId) ?: return@launch

                // Execute action if not already executed
                if (!schedule.executed) {
                    val command = mapOf(
                        "actuatorId" to actuatorId,
                        "state" to action
                    )
                    val success = MainActivity.FirebaseRestHelper.writeData(
                        firebaseHost,
                        firebaseAuth,
                        "devices/$deviceId/commands",
                        command
                    )
                    if (success) {
                        scheduleDao.updateExecuted(scheduleId, true)
                    }
                    android.util.Log.d("ScheduleReceiver", "Sending command: $command for actuator $actuatorId")
                }

                // Calculate time for next day
                val calendar = Calendar.getInstance().apply {
                    val timeParts = schedule.time.split(":")
                    set(Calendar.HOUR_OF_DAY, timeParts[0].toInt())
                    set(Calendar.MINUTE, timeParts[1].toInt())
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    add(Calendar.DAY_OF_MONTH, 1)
                }
                val newTime = "${calendar.get(Calendar.HOUR_OF_DAY)}:${calendar.get(Calendar.MINUTE).toString().padStart(2, '0')}"

                // Check if identical schedule already exists
                val existingSchedule = scheduleDao.getScheduleByDetails(
                    actuatorId = schedule.actuatorId,
                    time = newTime,
                    action = schedule.action,
                    deviceId = schedule.deviceId
                )
                
                if (existingSchedule == null) {
                    // Create new schedule only if none exists
                    val newScheduleId = "schedule_${System.currentTimeMillis()}"
                    val newSchedule = ScheduleEntity(
                        scheduleId = newScheduleId,
                        actuatorId = schedule.actuatorId,
                        time = newTime,
                        action = schedule.action,
                        executed = false,
                        deviceId = schedule.deviceId
                    )
                    scheduleDao.insertSchedule(newSchedule)
                    AlarmScheduler.setAlarm(context, newSchedule)
                    android.util.Log.d("ScheduleReceiver", "Created new schedule for $newTime")
                } else {
                    // Just reset the executed flag if schedule exists
                    scheduleDao.updateExecuted(existingSchedule.scheduleId, false)
                    android.util.Log.d("ScheduleReceiver", "Reset existing schedule for $newTime")
                }

                // Cancel the current alarm since we've either created a new one or reset an existing one
                AlarmScheduler.cancelAlarm(context, scheduleId)

            } catch (e: Exception) {
                android.util.Log.e("ScheduleReceiver", "Error processing schedule: ${e.message}")
            }
        }
    }
}
//class ScheduleReceiver : BroadcastReceiver() {
//    override fun onReceive(context: Context, intent: Intent) {
//        val scheduleId = intent.getStringExtra("scheduleId") ?: return
//        val actuatorId = intent.getStringExtra("actuatorId") ?: return
//        val action = intent.getStringExtra("action") ?: return
//        val deviceId = intent.getStringExtra("deviceId") ?: return
//
//        val scheduleDao = AppModule.provideScheduleDao(AppModule.provideScheduleDatabase(context))
//        val firebaseHost = ConfigManager.getFirebaseHost(context)
//        val firebaseAuth = ConfigManager.getFirebaseAuth(context)
//
//        CoroutineScope(Dispatchers.IO).launch {
//            try {
//                // Fetch schedule from Room
//                val schedule = scheduleDao.getScheduleById(scheduleId) ?: return@launch
//
//                // Execute action if not already executed
//                if (!schedule.executed) {
//                    val command = mapOf(
//                        "actuatorId" to actuatorId,
//                        "state" to action  // Send the action directly ("ON" or "OFF")
//                    )
//                    val success = MainActivity.FirebaseRestHelper.writeData(
//                        firebaseHost,
//                        firebaseAuth,
//                        "devices/$deviceId/commands",
//                        command
//                    )
//                    if (success) {
//                        scheduleDao.updateExecuted(scheduleId, true)
//                    }
//
//                    android.util.Log.d("ScheduleReceiver", "Sending command: $command for actuator $actuatorId")
//                }
//
//                // Check for existing schedule for the next day
//                val calendar = Calendar.getInstance().apply {
//                    val timeParts = schedule.time.split(":")
//                    set(Calendar.HOUR_OF_DAY, timeParts[0].toInt())
//                    set(Calendar.MINUTE, timeParts[1].toInt())
//                    set(Calendar.SECOND, 0)
//                    set(Calendar.MILLISECOND, 0)
//                    add(Calendar.DAY_OF_MONTH, 1)
//                }
//                val newTime = "${calendar.get(Calendar.HOUR_OF_DAY)}:${calendar.get(Calendar.MINUTE).toString().padStart(2, '0')}"
//                val existingSchedule = scheduleDao.getScheduleByDetails(
//                    actuatorId = schedule.actuatorId,
//                    time = newTime,
//                    action = schedule.action,
//                    deviceId = schedule.deviceId
//                )
//
//                if (existingSchedule == null) {
//                    // Create new schedule for the next day
//                    val newScheduleId = "schedule_${System.currentTimeMillis()}"
//                    val updatedSchedule = ScheduleEntity(
//                        scheduleId = newScheduleId,
//                        actuatorId = schedule.actuatorId,
//                        time = newTime,
//                        action = schedule.action,
//                        executed = false,
//                        deviceId = schedule.deviceId
//                    )
//                    scheduleDao.insertSchedule(updatedSchedule)
//                    AlarmScheduler.setAlarm(context, updatedSchedule)
//                } else {
//                    // Reset executed flag and reschedule existing
//                    scheduleDao.updateExecuted(existingSchedule.scheduleId, false)
//                    AlarmScheduler.cancelAlarm(context, existingSchedule.scheduleId)
//                    AlarmScheduler.setAlarm(context, existingSchedule)
//                }
//            } catch (e: Exception) {
//                android.util.Log.e("ScheduleReceiver", "Error processing schedule: ${e.message}")
//            }
//        }
//    }
//}