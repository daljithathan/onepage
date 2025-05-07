package hathan.daljit.esp32_iot_studentversion_2

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ScheduleActuatorDialog(
    actuator: Actuator,
    onDismiss: () -> Unit,
    onSchedule: (String, String, String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var time by remember { mutableStateOf("") }
    var action by remember { mutableStateOf("ON") }
    var error by remember { mutableStateOf<String?>(null) }
    val scheduleDao = AppModule.provideScheduleDao(AppModule.provideScheduleDatabase(context))
    val firebaseHost = ConfigManager.getFirebaseHost(context)
    val firebaseAuth = ConfigManager.getFirebaseAuth(context)
    val deviceId = context.getSharedPreferences("ESP32Config", Context.MODE_PRIVATE)
        .getString("device_id", "") ?: ""

//    fun isValidTimeFormat(time: String): Boolean {
//        return try {
//            val sdf = SimpleDateFormat("HH:mm", Locale.US)
//            sdf.isLenient = false
//            sdf.parse(time)
//            true
//        } catch (e: Exception) {
//            false
//        }
//    }
fun isValidTimeFormat(time: String): Boolean {
    return try {
        val (hours, minutes) = time.split(":")
        hours.toInt() in 0..23 && minutes.toInt() in 0..59
    } catch (e: Exception) {
        false
    }
}

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Schedule ${actuator.name}") },
        text = {
            Column {
                OutlinedTextField(
                    value = time,
                    onValueChange = {
                        time = it
                        error = null
                    },
                    label = { Text("Time (HH:mm)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (actuator.type == "LED") {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Action: $action")
                    Row {
                        Button(
                            onClick = { action = "ON" },
                            modifier = Modifier.weight(1f)
                        ) { Text("ON") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { action = "OFF" },
                            modifier = Modifier.weight(1f)
                        ) { Text("OFF") }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (time.isEmpty()) {
                        error = "Time cannot be empty"
                        return@Button
                    }
                    if (!isValidTimeFormat(time)) {
                        error = "Invalid time format. Use HH:mm (e.g., 14:30)"
                        return@Button
                    }
                    coroutineScope.launch {
                        // Normalize time format
                        val timeParts = time.split(":")
                        val normalizedTime = "${timeParts[0].toInt()}:${timeParts[1].padStart(2, '0')}"
                        // Check for existing schedule with exact match
                        val existingSchedule = scheduleDao.getScheduleByDetails(
                            actuatorId = actuator.id,
                            time = normalizedTime,
                            action = action,
                            deviceId = deviceId
                        )
                        if (existingSchedule != null) {
                            error = "A schedule with this time and action already exists"
                            Toast.makeText(
                                context,
                                "Schedule already exists for this time and action",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@launch
                        }

                        val scheduleId = "schedule_${System.currentTimeMillis()}"
                        val scheduleEntity = ScheduleEntity(
                            scheduleId = scheduleId,
                            actuatorId = actuator.id,
                            time = normalizedTime,
                            action = action,
                            executed = false,
                            deviceId = deviceId
                        )
                        try {
                            scheduleDao.insertSchedule(scheduleEntity)
                            AlarmScheduler.setAlarm(context, scheduleEntity)
                            if (MainActivity.NetworkUtils.isInternetAvailable(context)) {
                                val scheduleData = mapOf(
                                    "actuatorId" to actuator.id,
                                    "time" to normalizedTime,
                                    "action" to action,
                                    "executed" to false
                                )
                                val success = MainActivity.FirebaseRestHelper.writeData(
                                    firebaseHost,
                                    firebaseAuth,
                                    "devices/$deviceId/schedules/$scheduleId",
                                    scheduleData
                                )
                                if (!success) {
                                    Toast.makeText(
                                        context,
                                        "Saved locally, but failed to sync to Firebase",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } else {
                                Toast.makeText(
                                    context,
                                    "Saved locally, will sync to Firebase when online",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            onSchedule(actuator.id, normalizedTime, action)
                            onDismiss()
                        } catch (e: Exception) {
                            error = "Failed to save schedule: ${e.message}"
                            Toast.makeText(
                                context,
                                "Failed to save schedule: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
                enabled = time.isNotEmpty()
            ) {
                Text("Schedule")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}