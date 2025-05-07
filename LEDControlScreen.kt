package hathan.daljit.esp32_iot_studentversion_2

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeviceUnknown
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PermDeviceInformation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.abs
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private suspend fun scanForDevices(
    context: Context,
    wifiManager: WifiManager,
    onResult: (List<ScanResult>) -> Unit
) = withContext(Dispatchers.IO) {
    try {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w("MainActivity", "Location permission missing for WiFi scan")
            onResult(emptyList())
            return@withContext
        }
        wifiManager.startScan()
        delay(2000)
        val results = wifiManager.scanResults ?: emptyList()
        onResult(results)
        Log.d("MainActivity", "WiFi scan completed, found ${results.size} networks")
    } catch (e: Exception) {
        Log.e("MainActivity", "WiFi scan failed: ${e.message}")
        onResult(emptyList())
    }
}

@Composable
fun LEDControlScreen(
    onReconfigure: () -> Unit,
    onLogout: () -> Unit,
    prefs: SharedPreferences,
    toggleTheme: () -> Unit,
    wifiManager: WifiManager,
    setCurrentScreen: (String) -> Unit,
    dialogToShow: String?
) {
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val deviceId = prefs.getString("device_id", "") ?: ""
    val firebaseHost = ConfigManager.getFirebaseHost(context)
    val firebaseAuth = ConfigManager.getFirebaseAuth(context)
    val wifiSsid = prefs.getString("wifi_ssid", "Unknown") ?: "Unknown"
    var actuators by remember { mutableStateOf<Map<String, Actuator>>(emptyMap()) }
    var wifiStatus by remember { mutableStateOf("Checking...") }
    var authStatus by remember { mutableStateOf("Checking...") }
    var databaseError by remember { mutableStateOf<String?>(null) }
    var isDeviceOnline by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var showReconfigureDialog by remember { mutableStateOf(false) }
    var isSetupModeDetected by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteActuatorId by remember { mutableStateOf<String?>(null) }
    var temperature by remember { mutableStateOf<Float?>(null) }
    var humidity by remember { mutableStateOf<Float?>(null) }
    var distance by remember { mutableStateOf<Float?>(null) }
    var moisture by remember { mutableStateOf<Float?>(null) }
    var touchState by remember { mutableStateOf<String?>(null) }
    var pirState by remember { mutableStateOf<String?>(null) }
    var lightIntensity by remember { mutableStateOf<Float?>(null) }
    var showESP32Dialog by remember { mutableStateOf(dialogToShow == "esp32") }
    var showFirebaseRulesDialog by remember { mutableStateOf(dialogToShow == "firebase") }
    val userName by remember { mutableStateOf(auth.currentUser?.displayName ?: "Guest") }
    val coroutineScope = rememberCoroutineScope()
    var showActuatorListDialog by remember { mutableStateOf(false) }
    var isInternetAvailable by remember {
        mutableStateOf(MainActivity.NetworkUtils.isInternetAvailable(context))
    }
    var showSensorGraph by remember { mutableStateOf(false) }
    var selectedSensor by remember { mutableStateOf("Temperature") }
    var temperatureHistory by remember { mutableStateOf(mutableListOf<Float?>()) }
    var humidityHistory by remember { mutableStateOf(mutableListOf<Float?>()) }
    var distanceHistory by remember { mutableStateOf(mutableListOf<Float?>()) }
    var moistureHistory by remember { mutableStateOf(mutableListOf<Float?>()) }
    var lightIntensityHistory by remember { mutableStateOf(mutableListOf<Float?>()) }
    var showScheduleDialog by remember { mutableStateOf(false) }
    var selectedActuatorId by remember { mutableStateOf<String?>(null) }
    var showSchedulesDialog by remember { mutableStateOf(false) }
    val scheduleDao = AppModule.provideScheduleDao(AppModule.provideScheduleDatabase(context))

    // Timeout for Manual Device Status Override
    LaunchedEffect(isDeviceOnline) {
        if (isDeviceOnline) {
            delay(30000)
            if (!isDeviceOnline) { // Check if still online after delay
                isDeviceOnline = false
                databaseError = "Device override timed out. No recent data."
            }
        }
    }

    // Sync Room schedules with Firebase
    LaunchedEffect(deviceId, isInternetAvailable) {
        if (deviceId.isEmpty() || auth.currentUser == null || !isInternetAvailable) return@LaunchedEffect
        val mutex = Mutex()
        mutex.withLock {
            try {
                val localSchedules = scheduleDao.getSchedulesForDevice(deviceId)
                val schedulesJson = MainActivity.FirebaseRestHelper.readData(
                    context,
                    firebaseHost,
                    firebaseAuth,
                    "devices/$deviceId/schedules"
                ) ?: JSONObject()

                // Clean up Firebase duplicates
                val seen = mutableSetOf<String>()
                val toDelete = mutableListOf<String>()
                schedulesJson.keys().forEach { scheduleId ->
                    val firebaseSchedule = schedulesJson.getJSONObject(scheduleId)
                    val actuatorId = firebaseSchedule.optString("actuatorId")
                    val time = firebaseSchedule.optString("time")
                    val action = firebaseSchedule.optString("action")
                    val key = "$actuatorId:$time:$action:$deviceId"
                    if (seen.contains(key)) {
                        toDelete.add(scheduleId)
                    } else {
                        seen.add(key)
                    }
                }
                toDelete.forEach { scheduleId ->
                    MainActivity.FirebaseRestHelper.deleteData(
                        firebaseHost,
                        firebaseAuth,
                        "devices/$deviceId/schedules/$scheduleId"
                    )
                    Log.d("LEDControlScreen", "Deleted duplicate Firebase schedule $scheduleId")
                }

                // Sync local to Firebase
                localSchedules.forEach { localSchedule ->
                    if (!schedulesJson.has(localSchedule.scheduleId)) {
                        val scheduleData = mapOf(
                            "actuatorId" to localSchedule.actuatorId,
                            "time" to localSchedule.time,
                            "action" to localSchedule.action,
                            "executed" to localSchedule.executed
                        )
                        MainActivity.FirebaseRestHelper.writeData(
                            firebaseHost,
                            firebaseAuth,
                            "devices/$deviceId/schedules/${localSchedule.scheduleId}",
                            scheduleData
                        )
                    }
                }

                // Sync Firebase to local
                schedulesJson.keys().forEach { scheduleId ->
                    if (toDelete.contains(scheduleId)) return@forEach
                    val firebaseSchedule = schedulesJson.getJSONObject(scheduleId)
                    val actuatorId = firebaseSchedule.optString("actuatorId")
                    val time = firebaseSchedule.optString("time")
                    val action = firebaseSchedule.optString("action")
                    val executed = firebaseSchedule.optBoolean("executed", false)

                    val existingSchedule = scheduleDao.getScheduleByDetails(
                        actuatorId = actuatorId,
                        time = time,
                        action = action,
                        deviceId = deviceId
                    )

                    if (existingSchedule == null) {
                        val scheduleEntity = ScheduleEntity(
                            scheduleId = scheduleId,
                            actuatorId = actuatorId,
                            time = time,
                            action = action,
                            executed = executed,
                            deviceId = deviceId
                        )
                        scheduleDao.insertSchedule(scheduleEntity)
                        AlarmScheduler.setAlarm(context, scheduleEntity)
                    } else if (existingSchedule.scheduleId != scheduleId) {
                        scheduleDao.deleteSchedule(existingSchedule.scheduleId)
                        AlarmScheduler.cancelAlarm(context, existingSchedule.scheduleId)
                        val updatedSchedule = existingSchedule.copy(scheduleId = scheduleId, executed = executed)
                        scheduleDao.insertSchedule(updatedSchedule)
                        AlarmScheduler.setAlarm(context, updatedSchedule)
                    }
                }
            } catch (e: Exception) {
                databaseError = "Failed to sync schedules: ${e.message}"
            }
        }
    }

    LaunchedEffect(dialogToShow) {
        showESP32Dialog = dialogToShow == "esp32"
        showFirebaseRulesDialog = dialogToShow == "firebase"
        if (dialogToShow in listOf("esp32", "firebase")) {
            setCurrentScreen("ledControl")
        }
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            scanForDevices(context, wifiManager) { results ->
                isSetupModeDetected = results.any { it.SSID.startsWith("ESP32-Setup-") }
            }
            isInternetAvailable = MainActivity.NetworkUtils.isInternetAvailable(context)
            delay(10000)
        }
    }

    LaunchedEffect(Unit) {
        if (!isInternetAvailable) {
            authStatus = "Offline"
            databaseError = "No internet connection. Using cached data."
            return@LaunchedEffect
        }
        if (auth.currentUser == null) {
            databaseError = "User not authenticated"
            onLogout()
            return@LaunchedEffect
        }
        try {
            auth.currentUser?.getIdToken(true)?.await()
            authStatus = "Authenticated"
        } catch (e: com.google.firebase.FirebaseNetworkException) {
            authStatus = "Offline"
            databaseError = "No internet connection. Using cached data."
        } catch (e: Exception) {
            authStatus = "Auth error: ${e.message}"
            databaseError = "Authentication failed: ${e.message}"
            if (e is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException) {
                onLogout()
            }
        }

        while (isActive) {
            if (!isInternetAvailable) {
                authStatus = "Offline"
                databaseError = "No internet connection. Using cached data."
                delay(30000)
                isInternetAvailable = MainActivity.NetworkUtils.isInternetAvailable(context)
                continue
            }
            try {
                auth.currentUser?.reload()?.await()
                authStatus = "Authenticated"
            } catch (e: com.google.firebase.FirebaseNetworkException) {
                authStatus = "Offline"
                databaseError = "No internet connection. Using cached data."
            } catch (e: Exception) {
                authStatus = "Auth error: ${e.message}"
                databaseError = "Authentication failed: ${e.message}"
                if (e is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException) {
                    onLogout()
                    break
                }
            }
            delay(30000)
        }
    }

    LaunchedEffect(firebaseHost, deviceId) {
        if (firebaseHost.isEmpty() || deviceId.isEmpty() || auth.currentUser == null) {
            databaseError = "Firebase host, device ID, or user not configured"
            return@LaunchedEffect
        }

        while (isActive) {
            if (!isInternetAvailable) {
                databaseError = "No internet connection. Using cached data."
                delay(2000)
                isInternetAvailable = MainActivity.NetworkUtils.isInternetAvailable(context)
                continue
            }
            try {
                val deviceJson = MainActivity.FirebaseRestHelper.readData(
                    context,
                    firebaseHost,
                    firebaseAuth,
                    "devices/$deviceId"
                )
                if (deviceJson != null) {
                    val lastSeen = deviceJson.optLong("lastSeen", 0)
                    val currentTime = System.currentTimeMillis() / 1000
                    val timeDifference = abs(currentTime - lastSeen)
                    isDeviceOnline = timeDifference < 120
                    wifiStatus = deviceJson.optString("status", "Unknown")
                    val actuatorsJson = deviceJson.optJSONObject("actuators") ?: JSONObject()
                    val newActuators = mutableMapOf<String, Actuator>()
                    actuatorsJson.keys().forEach { id ->
                        val actuatorJson = actuatorsJson.getJSONObject(id)
                        val name = actuatorJson.optString("name", id)
                        val type = actuatorJson.optString("type", "LED")
                        val pin = actuatorJson.optInt("pin", 0)
                        val trigPin =
                            actuatorJson.optInt("trigPin", -1).let { if (it == -1) null else it }
                        val echoPin =
                            actuatorJson.optInt("echoPin", -1).let { if (it == -1) null else it }
                        val state = when (type) {
                            "LED" -> actuatorJson.optString("state", "OFF")
                            "TTP223" -> actuatorJson.optString("state", "NOT_TOUCHED")
                            "PIR" -> actuatorJson.optString("state", "NO_MOTION")
                            else -> "N/A"
                        }
                        val angle = if (type == "Servo") actuatorJson.optInt("angle", 0)
                            .let { if (it in 0..180) it else 0 } else null
                        newActuators[id] =
                            Actuator(id, name, type, pin, state, trigPin, echoPin, angle)
                    }
                    actuators = newActuators
                    val sensorsJson = deviceJson.optJSONObject("sensors")
                    val dht11Json = sensorsJson?.optJSONObject("dht11")
                    temperature = dht11Json?.optDouble("temperature")?.toFloat()
                    humidity = dht11Json?.optDouble("humidity")?.toFloat()
                    val hcsr04Json = sensorsJson?.optJSONObject("hcsr04")
                    distance = hcsr04Json?.optDouble("distance")?.toFloat()
                    val yl69Json = sensorsJson?.optJSONObject("yl69")
                    moisture = yl69Json?.optDouble("moisture")?.toFloat()
                    val ttp223Json = sensorsJson?.optJSONObject("ttp223")
                    touchState = ttp223Json?.optString("state")
                    val pirJson = sensorsJson?.optJSONObject("pir")
                    pirState = pirJson?.optString("state")
                    val lm393Json = sensorsJson?.optJSONObject("lm393")
                    lightIntensity = lm393Json?.optDouble("lightIntensity")?.toFloat()
                    // Update history
                    temperature?.let { temperatureHistory.add(it) }
                    humidity?.let { humidityHistory.add(it) }
                    distance?.let { distanceHistory.add(it) }
                    moisture?.let { moistureHistory.add(it) }
                    lightIntensity?.let { lightIntensityHistory.add(it) }
                    databaseError = null
                } else {
                    wifiStatus = "Unknown"
                    isDeviceOnline = false
                    actuators = emptyMap()
                    temperature = null
                    humidity = null
                    distance = null
                    moisture = null
                    touchState = null
                    pirState = null
                    lightIntensity = null
                    databaseError = "Failed to fetch device data"
                }
            } catch (e: com.google.firebase.FirebaseNetworkException) {
                databaseError = "No internet connection. Using cached data."
            } catch (e: Exception) {
                databaseError = "Error fetching data: ${e.message}"
                wifiStatus = "Error"
                isDeviceOnline = false
            }
            delay(2000)
        }
    }

    LaunchedEffect(deviceId) {
        if (deviceId.isEmpty() || auth.currentUser == null) {
            return@LaunchedEffect
        }
        while (isActive) {
            if (!isInternetAvailable) {
                delay(5000)
                isInternetAvailable = MainActivity.NetworkUtils.isInternetAvailable(context)
                continue
            }
            actuators.values.filter { it.type == "Servo" }.forEach { actuator ->
                try {
                    val anglePath = "devices/$deviceId/actuators/${actuator.id}"
                    val angle = MainActivity.FirebaseRestHelper.readAngle(
                        context,
                        firebaseHost,
                        firebaseAuth,
                        anglePath
                    )
                    angle?.let {
                        actuators = actuators.toMutableMap().apply {
                            this[actuator.id] = actuator.copy(angle = it)
                        }
                    }
                } catch (e: com.google.firebase.FirebaseNetworkException) {
                    databaseError = "No internet connection. Using cached data."
                } catch (e: Exception) {
                    databaseError = "Failed to read angle for ${actuator.name}: ${e.message}"
                }
            }
            delay(5000)
        }
    }

    if (showAddDialog) {
        AddActuatorDialog(
            actuators = actuators.values.toList(),
            onDismiss = { showAddDialog = false },
            onAdd = { name, pin, type, trigPin, echoPin, angle ->
                val newId = "${type.lowercase()}${actuators.size + 1}"
                val actuatorData = when (type) {
                    "HCSR04" -> mapOf(
                        "name" to name,
                        "type" to type,
                        "trigPin" to trigPin,
                        "echoPin" to echoPin
                    )
                    "Servo" -> mapOf(
                        "name" to name,
                        "type" to type,
                        "pin" to pin,
                        "angle" to (angle ?: 0)
                    )
                    "YL69" -> mapOf(
                        "name" to name,
                        "type" to type,
                        "pin" to pin
                    )
                    "TTP223" -> mapOf(
                        "name" to name,
                        "type" to type,
                        "pin" to pin,
                        "state" to "NOT_TOUCHED"
                    )
                    "PIR" -> mapOf(
                        "name" to name,
                        "type" to type,
                        "pin" to pin,
                        "state" to "NO_MOTION"
                    )
                    "LM393" -> mapOf(
                        "name" to name,
                        "type" to type,
                        "pin" to pin
                    )
                    else -> mapOf(
                        "name" to name,
                        "type" to type,
                        "pin" to pin,
                        "state" to "OFF"
                    )
                }
                coroutineScope.launch {
                    if (!isInternetAvailable) {
                        databaseError = "Cannot add actuator: No internet connection."
                        showAddDialog = false
                        return@launch
                    }
                    try {
                        val success = MainActivity.FirebaseRestHelper.writeData(
                            firebaseHost,
                            firebaseAuth,
                            "devices/$deviceId/actuators/$newId",
                            actuatorData
                        )
                        if (!success) {
                            databaseError = "Failed to add actuator: $name"
                        }
                    } catch (e: com.google.firebase.FirebaseNetworkException) {
                        databaseError = "Cannot add actuator: No internet connection."
                    } catch (e: Exception) {
                        databaseError = "Failed to add actuator: ${e.message}"
                    }
                    showAddDialog = false
                }
            }
        )
    }

    if (showDeleteDialog && deleteActuatorId != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
                deleteActuatorId = null
            },
            title = { Text("Delete Actuator") },
            text = { Text("Are you sure you want to delete this actuator?") },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            if (!isInternetAvailable) {
                                databaseError = "Cannot delete actuator: No internet connection."
                                showDeleteDialog = false
                                deleteActuatorId = null
                                return@launch
                            }
                            try {
                                val success = MainActivity.FirebaseRestHelper.deleteData(
                                    firebaseHost,
                                    firebaseAuth,
                                    "devices/$deviceId/actuators/$deleteActuatorId"
                                )
                                if (!success) {
                                    databaseError = "Failed to delete actuator"
                                }
                            } catch (e: com.google.firebase.FirebaseNetworkException) {
                                databaseError = "Cannot delete actuator: No internet connection."
                            } catch (e: Exception) {
                                databaseError = "Failed to delete actuator: ${e.message}"
                            }
                            showDeleteDialog = false
                            deleteActuatorId = null
                        }
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        deleteActuatorId = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showReconfigureDialog) {
        AlertDialog(
            onDismissRequest = { showReconfigureDialog = false },
            title = { Text("Reconfigure Device") },
            text = { Text("Do you want to reconfigure the ESP32 device? This will clear the current configuration.") },
            confirmButton = {
                Button(
                    onClick = {
                        with(prefs.edit()) {
                            remove("device_id")
                            remove("wifi_ssid")
                            remove("wifi_password")
                            apply()
                        }
                        onReconfigure()
                        showReconfigureDialog = false
                    }
                ) {
                    Text("Reconfigure")
                }
            },
            dismissButton = {
                Button(onClick = { showReconfigureDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text("Help") },
            text = {
                Column {
                    Text("Control your ESP32 actuators and monitor sensors.")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("• Use the switch to toggle LEDs ON/OFF.")
                    Text("• Add new actuators using the 'Add Actuator' button.")
                    Text("• Monitor temperature, humidity, distance, moisture, touch, motion, and light intensity if sensors are configured.")
                    Text("• Reconfigure the device in Settings if needed.")
                }
            },
            confirmButton = {
                Button(onClick = { showHelp = false }) {
                    Text("OK")
                }
            }
        )
    }

    if (showESP32Dialog) {
        AlertDialog(
            onDismissRequest = { showESP32Dialog = false },
            title = { Text("ESP32 Firmware") },
            text = {
                Column {
                    Text("Ensure your ESP32 is running the latest firmware compatible with this app.")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("• Download the firmware from the official repository.")
                    Text("• Use the Code Generator in Settings to create custom firmware.")
                    Text("• Flash the firmware using ESP32 Flash Tools.")
                }
            },
            confirmButton = {
                Button(onClick = { showESP32Dialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    if (showFirebaseRulesDialog) {
        AlertDialog(
            onDismissRequest = { showFirebaseRulesDialog = false },
            title = { Text("Firebase Rules") },
            text = {
                Column {
                    Text("Ensure your Firebase Realtime Database rules are correctly configured:")
                    Spacer(modifier = Modifier.height(8.dp))
                    val rules = """
                    {
                      "rules": {
                        "users": {
                          "uid": {
                            ".read": "auth.uid === uid",
                            ".write": "auth.uid === uid"
                          }
                        },
                        "devices": {
                          "$deviceId": {
                            ".read": "auth != null && data.child('ownerUid').val() === auth.uid",
                            ".write": "auth != null && data.child('ownerUid').val() === auth.uid"
                          }
                        }
                      }
                    }
                """.trimIndent()
                    Text(rules)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val clipboard =
                                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Firebase Rules", rules)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Rules copied to clipboard", Toast.LENGTH_SHORT)
                                .show()
                        }
                    ) {
                        Text("Copy Rules")
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showFirebaseRulesDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    if (showActuatorListDialog) {
        AlertDialog(
            onDismissRequest = { showActuatorListDialog = false },
            title = { Text("Actuator List") },
            text = {
                if (actuators.isEmpty()) {
                    Text("No actuators configured.")
                } else {
                    LazyColumn {
                        items(actuators.values.toList()) { actuator ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            actuator.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            "Node ID: ${actuator.id}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                        )
                                        Text(
                                            "Type: ${actuator.type}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                        )
                                        Text(
                                            when (actuator.type) {
                                                "HCSR04" -> "Trig Pin: ${actuator.trigPin ?: "N/A"}, Echo Pin: ${actuator.echoPin ?: "N/A"}"
                                                else -> "Pin: ${actuator.pin}"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                        )
                                    }
                                    Row {
                                        IconButton(
                                            onClick = {
                                                if (!isDeviceOnline || actuator.type != "LED" || actuator.state == "OFF") {
                                                    deleteActuatorId = actuator.id
                                                    showDeleteDialog = true
                                                } else {
                                                    databaseError =
                                                        "Cannot delete ${actuator.name}: Device is online and actuator is ON"
                                                }
                                                showActuatorListDialog = false
                                            },
                                            enabled = !isDeviceOnline || actuator.type != "LED" || actuator.state == "OFF"
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Delete actuator",
                                                tint = if (!isDeviceOnline || actuator.type != "LED" || actuator.state == "OFF")
                                                    MaterialTheme.colorScheme.error
                                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                selectedActuatorId = actuator.id
                                                showScheduleDialog = true
                                            },
                                            enabled = isDeviceOnline && isInternetAvailable && actuator.type == "LED"
                                        ) {
                                            Icon(
                                                Icons.Default.Schedule,
                                                contentDescription = "Schedule actuator",
                                                tint = if (isDeviceOnline && isInternetAvailable && actuator.type == "LED")
                                                    MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showActuatorListDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showSchedulesDialog) {
        AlertDialog(
            onDismissRequest = { showSchedulesDialog = false },
            title = { Text("Schedules") },
            text = {
                var schedules by remember { mutableStateOf<List<ScheduleEntity>>(emptyList()) }
                var isLoading by remember { mutableStateOf(true) }
                LaunchedEffect(Unit) {
                    try {
                        schedules = scheduleDao.getSchedulesForDevice(deviceId)
                        Log.d("LEDControlScreen", "Schedules fetched from Room: ${schedules.size}")
                    } catch (e: Exception) {
                        databaseError = "Failed to fetch schedules: ${e.message}"
                        Log.e("LEDControlScreen", "Error fetching schedules: ${e.message}")
                    } finally {
                        isLoading = false
                    }
                }
                if (isLoading) {
                    Text("Loading schedules...")
                } else if (schedules.isEmpty()) {
                    Text("No schedules configured.")
                } else {
                    LazyColumn {
                        items(schedules) { schedule ->
                            val actuator = actuators[schedule.actuatorId]
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            "Actuator: ${actuator?.name ?: schedule.actuatorId}",
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Text(
                                            "Time: ${schedule.time}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            "Action: ${schedule.action}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            "Status: ${if (schedule.executed) "Executed" else "Pending"}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (schedule.executed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                try {
                                                    // Delete from Room
                                                    scheduleDao.deleteSchedule(schedule.scheduleId)
                                                    // Cancel alarm
                                                    AlarmScheduler.cancelAlarm(context, schedule.scheduleId)
                                                    // Delete from Firebase if online
                                                    if (isInternetAvailable) {
                                                        val success = MainActivity.FirebaseRestHelper.deleteData(
                                                            firebaseHost,
                                                            firebaseAuth,
                                                            "devices/$deviceId/schedules/${schedule.scheduleId}"
                                                        )
                                                        if (success) {
                                                            Toast.makeText(context, "Schedule deleted", Toast.LENGTH_SHORT).show()
                                                            Log.d("LEDControlScreen", "Schedule deleted: ${schedule.scheduleId}")
                                                        } else {
                                                            databaseError = "Failed to delete schedule from Firebase"
                                                            Toast.makeText(context, "Failed to delete from Firebase", Toast.LENGTH_SHORT).show()
                                                        }
                                                    } else {
                                                        Toast.makeText(context, "Schedule deleted locally, will sync when online", Toast.LENGTH_SHORT).show()
                                                    }
                                                    // Refresh schedules
                                                    schedules = scheduleDao.getSchedulesForDevice(deviceId)
                                                } catch (e: Exception) {
                                                    databaseError = "Failed to delete schedule: ${e.message}"
                                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                                    Log.e("LEDControlScreen", "Delete schedule failed: ${e.message}")
                                                }
                                            }
                                        },
                                        enabled = true
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete schedule",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showSchedulesDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showScheduleDialog && selectedActuatorId != null) {
        val actuator = actuators[selectedActuatorId]
        if (actuator != null) {
            ScheduleActuatorDialog(
                actuator = actuator,
                onDismiss = {
                    showScheduleDialog = false
                    selectedActuatorId = null
                },
                onSchedule = { actuatorId, time, action ->
                    coroutineScope.launch {
                        try {
                            val scheduleId = "schedule_${System.currentTimeMillis()}"
                            val scheduleEntity = ScheduleEntity(
                                scheduleId = scheduleId,
                                actuatorId = actuatorId,
                                time = time,
                                action = action,
                                executed = false,
                                deviceId = deviceId
                            )
                            // Save to Room and set alarm
                            scheduleDao.insertSchedule(scheduleEntity)
                            AlarmScheduler.setAlarm(context, scheduleEntity)
                            // Sync to Firebase if online
                            if (isInternetAvailable) {
                                val scheduleData = mapOf(
                                    "actuatorId" to actuatorId,
                                    "time" to time,
                                    "action" to action,
                                    "executed" to false
                                )
                                val success = MainActivity.FirebaseRestHelper.writeData(
                                    firebaseHost,
                                    firebaseAuth,
                                    "devices/$deviceId/schedules/$scheduleId",
                                    scheduleData
                                )
                                if (success) {
                                    Toast.makeText(context, "Schedule added for ${actuator.name}", Toast.LENGTH_SHORT).show()
                                } else {
                                    databaseError = "Failed to sync schedule to Firebase"
                                    Toast.makeText(context, "Saved locally, failed to sync to Firebase", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "Schedule saved locally, will sync when online", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: com.google.firebase.FirebaseNetworkException) {
                            databaseError = "Cannot schedule actuator: No internet connection."
                        } catch (e: Exception) {
                            databaseError = "Failed to schedule ${actuator.name}: ${e.message}"
                        }
                        showScheduleDialog = false
                        selectedActuatorId = null
                    }
                }
            )
        } else {
            showScheduleDialog = false
            selectedActuatorId = null
        }
    }

    Scaffold(
        floatingActionButtonPosition = FabPosition.End,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showHelp = true },
                containerColor = MaterialTheme.colorScheme.error,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Info, contentDescription = "Help")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(1.0f)
                    .shadow(8.dp, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .animateContentSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Dashboard,
                            contentDescription = "Dashboard Icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Dashboard",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Text(
                        "Welcome: $userName",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "Device Info",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.PermDeviceInformation,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("ID: ${deviceId.take(8)}...", color = MaterialTheme.colorScheme.primary)
                                }

                                Divider(modifier = Modifier.padding(vertical = 8.dp))

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Wifi,
                                        contentDescription = null,
                                        tint = if (isDeviceOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Status: ${if (isDeviceOnline) "Online" else "Offline"}",
                                        color = if (isDeviceOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                    )
                                }

                                Divider(modifier = Modifier.padding(vertical = 8.dp))

                                Text(
                                    "WiFi: $wifiSsid ($wifiStatus)",
                                    color = when {
                                        wifiStatus == "Connected" -> MaterialTheme.colorScheme.primary
                                        wifiStatus.contains("Error", ignoreCase = true) -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                )

                                if (!isDeviceOnline && (temperature != null || humidity != null || distance != null || moisture != null || touchState != null || pirState != null || lightIntensity != null)) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = {
                                            isDeviceOnline = true
                                            databaseError = null
                                            Toast.makeText(context, "Device set to online based on sensor data", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Force Online Status")
                                    }
                                }
                            }
                        }
                    }

                    databaseError?.let {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        "Actuators",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (actuators.isEmpty()) {
                        Text(
                            "No actuators configured. Add one below.",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    } else {
                        actuators.forEach { (id, actuator) ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                when (actuator.type) {
                                                    "LED" -> Icons.Default.Lightbulb
                                                    "DHT11" -> Icons.Default.Sensors
                                                    "HCSR04" -> Icons.Default.Sensors
                                                    "Servo" -> Icons.Default.Build
                                                    "YL69" -> Icons.Default.WaterDrop
                                                    "TTP223" -> Icons.Default.TouchApp
                                                    "PIR" -> Icons.Default.Visibility
                                                    "LM393" -> Icons.Default.WbSunny
                                                    else -> Icons.Default.DeviceUnknown
                                                },
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    actuator.name,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    "Node ID: $id",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(
                                                        alpha = 0.7f
                                                    )
                                                )
                                                Text(
                                                    when (actuator.type) {
                                                        "HCSR04" -> "Trig Pin: ${actuator.trigPin ?: "N/A"}, Echo Pin: ${actuator.echoPin ?: "N/A"}"
                                                        else -> "Pin: ${actuator.pin}"
                                                    },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(
                                                        alpha = 0.7f
                                                    )
                                                )
                                                Text(
                                                    "Type: ${actuator.type}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(
                                                        alpha = 0.7f
                                                    )
                                                )
                                            }
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (actuator.type == "LED") {
                                                Switch(
                                                    checked = actuator.state == "ON",
                                                    onCheckedChange = { newState ->
                                                        if (!isInternetAvailable) {
                                                            databaseError =
                                                                "Cannot toggle ${actuator.name}: No internet connection."
                                                            return@Switch
                                                        }
                                                        coroutineScope.launch {
                                                            try {
                                                                val success =
                                                                    MainActivity.FirebaseRestHelper.updateData(
                                                                        context,
                                                                        firebaseHost,
                                                                        firebaseAuth,
                                                                        "devices/$deviceId/actuators/$id",
                                                                        mapOf("state" to if (newState) "ON" else "OFF")
                                                                    )
                                                                if (!success) {
                                                                    databaseError =
                                                                        "Failed to set ${actuator.name}"
                                                                }
                                                            } catch (e: com.google.firebase.FirebaseNetworkException) {
                                                                databaseError =
                                                                    "Cannot toggle ${actuator.name}: No internet connection."
                                                            } catch (e: Exception) {
                                                                databaseError =
                                                                    "Failed to set ${actuator.name}: ${e.message}"
                                                            }
                                                        }
                                                    },
                                                    enabled = isDeviceOnline && isInternetAvailable,
                                                    colors = SwitchDefaults.colors(
                                                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                                                        checkedTrackColor = MaterialTheme.colorScheme.primary.copy(
                                                            alpha = 0.5f
                                                        )
                                                    )
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                            }
                                            IconButton(
                                                onClick = {
                                                    if (!isDeviceOnline || actuator.type != "LED" || actuator.state == "OFF") {
                                                        deleteActuatorId = id
                                                        showDeleteDialog = true
                                                    } else {
                                                        databaseError =
                                                            "Cannot delete ${actuator.name}: Device is online and actuator is ON"
                                                    }
                                                },
                                                enabled = !isDeviceOnline || actuator.type != "LED" || actuator.state == "OFF"
                                            ) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "Delete actuator",
                                                    tint = if (!isDeviceOnline || actuator.type != "LED" || actuator.state == "OFF")
                                                        MaterialTheme.colorScheme.error
                                                    else MaterialTheme.colorScheme.onSurface.copy(
                                                        alpha = 0.3f
                                                    )
                                                )
                                            }
                                            if (actuator.type == "LED") {
                                                IconButton(
                                                    onClick = {
                                                        selectedActuatorId = id
                                                        showScheduleDialog = true
                                                    },
                                                    enabled = isDeviceOnline && isInternetAvailable
                                                ) {
                                                    Icon(
                                                        Icons.Default.Schedule,
                                                        contentDescription = "Schedule actuator",
                                                        tint = if (isDeviceOnline && isInternetAvailable)
                                                            MaterialTheme.colorScheme.primary
                                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    if (actuator.type.equals("DHT11", ignoreCase = true)) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(
                                                Icons.Default.Thermostat,
                                                contentDescription = null,
                                                tint = if (temperature != null) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                "Temperature: ${
                                                    temperature?.let {
                                                        String.format(
                                                            "%.1f°C",
                                                            it
                                                        )
                                                    } ?: "N/A"
                                                }",
                                                color = if (temperature != null) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(
                                                Icons.Default.WaterDrop,
                                                contentDescription = null,
                                                tint = if (humidity != null) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                "Humidity: ${
                                                    humidity?.let {
                                                        String.format(
                                                            "%.1f%%",
                                                            it
                                                        )
                                                    } ?: "N/A"
                                                }",
                                                color = if (humidity != null) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                            )
                                        }
                                    }
                                    if (actuator.type.equals("HCSR04", ignoreCase = true)) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(
                                                Icons.Default.Sensors,
                                                contentDescription = null,
                                                tint = if (distance != null) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                "Distance: ${
                                                    distance?.let {
                                                        String.format(
                                                            "%.1f cm",
                                                            it
                                                        )
                                                    } ?: "N/A"
                                                }",
                                                color = if (distance != null) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                            )
                                        }
                                    }
                                    if (actuator.type.equals("YL69", ignoreCase = true)) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(
                                                Icons.Default.WaterDrop,
                                                contentDescription = null,
                                                tint = if (moisture != null) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                "Moisture: ${
                                                    moisture?.let {
                                                        String.format(
                                                            "%.1f%%",
                                                            it
                                                        )
                                                    } ?: "N/A"
                                                }",
                                                color = if (moisture != null) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                            )
                                        }
                                    }
                                    if (actuator.type.equals("TTP223", ignoreCase = true)) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(
                                                Icons.Default.TouchApp,
                                                contentDescription = null,
                                                tint = if (touchState != null) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                "Touch: ${touchState ?: "N/A"}",
                                                color = if (touchState != null) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                            )
                                        }
                                    }
                                    if (actuator.type.equals("PIR", ignoreCase = true)) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(
                                                Icons.Default.Visibility,
                                                contentDescription = null,
                                                tint = if (pirState != null) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                "Motion: ${pirState ?: "N/A"}",
                                                color = if (pirState != null) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                            )
                                        }
                                    }
                                    if (actuator.type.equals("LM393", ignoreCase = true)) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(
                                                Icons.Default.WbSunny,
                                                contentDescription = null,
                                                tint = if (lightIntensity != null) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                "Light Intensity: ${
                                                    lightIntensity?.let {
                                                        String.format(
                                                            "%.1f%%",
                                                            it
                                                        )
                                                    } ?: "N/A"
                                                }",
                                                color = if (lightIntensity != null) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                            )
                                        }
                                    }
                                    if (actuator.type == "Servo") {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        var angleInput by remember {
                                            mutableStateOf(
                                                actuator.angle?.toString() ?: "0"
                                            )
                                        }
                                        var sliderValue by remember {
                                            mutableStateOf(
                                                actuator.angle?.toFloat() ?: 0f
                                            )
                                        }
                                        var currentAngle by remember {
                                            mutableStateOf(
                                                actuator.angle?.toString() ?: "0"
                                            )
                                        }

                                        Column(
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = "Current Angle: $currentAngle°",
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    color = if (isDeviceOnline) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.onSurface.copy(
                                                        alpha = 0.5f
                                                    )
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                OutlinedTextField(
                                                    value = angleInput,
                                                    onValueChange = { newValue ->
                                                        angleInput = newValue
                                                        val angle = newValue.toIntOrNull()
                                                        if (angle != null && angle in 0..180) {
                                                            sliderValue = angle.toFloat()
                                                            if (!isInternetAvailable) {
                                                                databaseError =
                                                                    "Cannot set angle: No internet connection."
                                                                return@OutlinedTextField
                                                            }
                                                            coroutineScope.launch {
                                                                try {
                                                                    val success =
                                                                        MainActivity.FirebaseRestHelper.updateData(
                                                                            context,
                                                                            firebaseHost,
                                                                            firebaseAuth,
                                                                            "devices/$deviceId/actuators/${actuator.id}",
                                                                            mapOf("angle" to angle)
                                                                        )
                                                                    if (success) {
                                                                        currentAngle =
                                                                            angle.toString()
                                                                    } else {
                                                                        databaseError =
                                                                            "Failed to set angle for ${actuator.name}"
                                                                    }
                                                                } catch (e: com.google.firebase.FirebaseNetworkException) {
                                                                    databaseError =
                                                                        "Cannot set angle: No internet connection."
                                                                } catch (e: Exception) {
                                                                    databaseError =
                                                                        "Failed to set angle for ${actuator.name}: ${e.message}"
                                                                }
                                                            }
                                                        }
                                                    },
                                                    label = { Text("Set Angle (0-180)") },
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                    modifier = Modifier.weight(1f),
                                                    enabled = isDeviceOnline && isInternetAvailable
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    "°",
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    color = if (isDeviceOnline) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.onSurface.copy(
                                                        alpha = 0.5f
                                                    )
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Slider(
                                                value = sliderValue,
                                                onValueChange = { newValue ->
                                                    val angle = newValue.toInt()
                                                    angleInput = angle.toString()
                                                    sliderValue = newValue
                                                    if (!isInternetAvailable) {
                                                        databaseError =
                                                            "Cannot set angle: No internet connection."
                                                        return@Slider
                                                    }
                                                    coroutineScope.launch {
                                                        try {
                                                            val success =
                                                                MainActivity.FirebaseRestHelper.updateData(
                                                                    context,
                                                                    firebaseHost,
                                                                    firebaseAuth,
                                                                    "devices/$deviceId/actuators/${actuator.id}",
                                                                    mapOf("angle" to angle)
                                                                )
                                                            if (success) {
                                                                currentAngle = angle.toString()
                                                            } else {
                                                                databaseError =
                                                                    "Failed to set angle for ${actuator.name}"
                                                            }
                                                        } catch (e: com.google.firebase.FirebaseNetworkException) {
                                                            databaseError =
                                                                "Cannot set angle: No internet connection."
                                                        } catch (e: Exception) {
                                                            databaseError =
                                                                "Failed to set angle: ${e.message}"
                                                        }
                                                    }
                                                },
                                                valueRange = 0f..180f,
                                                steps = 179,
                                                enabled = isDeviceOnline && isInternetAvailable,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Row(
                                                horizontalArrangement = Arrangement.SpaceEvenly,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Button(
                                                    onClick = {
                                                        if (!isInternetAvailable) {
                                                            databaseError =
                                                                "Cannot set angle: No internet connection."
                                                            return@Button
                                                        }
                                                        coroutineScope.launch {
                                                            try {
                                                                val success =
                                                                    MainActivity.FirebaseRestHelper.updateData(
                                                                        context,
                                                                        firebaseHost,
                                                                        firebaseAuth,
                                                                        "devices/$deviceId/actuators/${actuator.id}",
                                                                        mapOf("angle" to 0)
                                                                    )
                                                                if (success) {
                                                                    angleInput = "0"
                                                                    sliderValue = 0f
                                                                    currentAngle = "0"
                                                                } else {
                                                                    databaseError =
                                                                        "Failed to set angle to 0° for ${actuator.name}"
                                                                }
                                                            } catch (e: com.google.firebase.FirebaseNetworkException) {
                                                                databaseError =
                                                                    "Cannot set angle: No internet connection."
                                                            } catch (e: Exception) {
                                                                databaseError =
                                                                    "Failed to set angle: ${e.message}"
                                                            }
                                                        }
                                                    },
                                                    enabled = isDeviceOnline && isInternetAvailable
                                                ) {
                                                    Text("0°")
                                                }
                                                Button(
                                                    onClick = {
                                                        if (!isInternetAvailable) {
                                                            databaseError =
                                                                "Cannot set angle: No internet connection."
                                                            return@Button
                                                        }
                                                        coroutineScope.launch {
                                                            try {
                                                                val success =
                                                                    MainActivity.FirebaseRestHelper.updateData(
                                                                        context,
                                                                        firebaseHost,
                                                                        firebaseAuth,
                                                                        "devices/$deviceId/actuators/${actuator.id}",
                                                                        mapOf("angle" to 45)
                                                                    )
                                                                if (success) {
                                                                    angleInput = "45"
                                                                    sliderValue = 45f
                                                                    currentAngle = "45"
                                                                } else {
                                                                    databaseError =
                                                                        "Failed to set angle to 45° for ${actuator.name}"
                                                                }
                                                            } catch (e: com.google.firebase.FirebaseNetworkException) {
                                                                databaseError =
                                                                    "Cannot set angle: No internet connection."
                                                            } catch (e: Exception) {
                                                                databaseError =
                                                                    "Failed to set angle: ${e.message}"
                                                            }
                                                        }
                                                    },
                                                    enabled = isDeviceOnline && isInternetAvailable
                                                ) {
                                                    Text("45°")
                                                }
                                                Button(
                                                    onClick = {
                                                        if (!isInternetAvailable) {
                                                            databaseError =
                                                                "Cannot set angle: No internet connection."
                                                            return@Button
                                                        }
                                                        coroutineScope.launch {
                                                            try {
                                                                val success =
                                                                    MainActivity.FirebaseRestHelper.updateData(
                                                                        context,
                                                                        firebaseHost,
                                                                        firebaseAuth,
                                                                        "devices/$deviceId/actuators/${actuator.id}",
                                                                        mapOf("angle" to 90)
                                                                    )
                                                                if (success) {
                                                                    angleInput = "90"
                                                                    sliderValue = 90f
                                                                    currentAngle = "90"
                                                                } else {
                                                                    databaseError =
                                                                        "Failed to set angle to 90° for ${actuator.name}"
                                                                }
                                                            } catch (e: com.google.firebase.FirebaseNetworkException) {
                                                                databaseError =
                                                                    "Cannot set angle: No internet connection."
                                                            } catch (e: Exception) {
                                                                databaseError =
                                                                    "Failed to set angle: ${e.message}"
                                                            }
                                                        }
                                                    },
                                                    enabled = isDeviceOnline && isInternetAvailable
                                                ) {
                                                    Text("90°")
                                                }
                                                Button(
                                                    onClick = {
                                                        if (!isInternetAvailable) {
                                                            databaseError =
                                                                "Cannot set angle: No internet connection."
                                                            return@Button
                                                        }
                                                        coroutineScope.launch {
                                                            try {
                                                                val success =
                                                                    MainActivity.FirebaseRestHelper.updateData(
                                                                        context,
                                                                        firebaseHost,
                                                                        firebaseAuth,
                                                                        "devices/$deviceId/actuators/${actuator.id}",
                                                                        mapOf("angle" to 180)
                                                                    )
                                                                if (success) {
                                                                    angleInput = "180"
                                                                    sliderValue = 180f
                                                                    currentAngle = "180"
                                                                } else {
                                                                    databaseError =
                                                                        "Failed to set angle to 180° for ${actuator.name}"
                                                                }
                                                            } catch (e: com.google.firebase.FirebaseNetworkException) {
                                                                databaseError =
                                                                    "Cannot set angle: No internet connection."
                                                            } catch (e: Exception) {
                                                                databaseError =
                                                                    "Failed to set angle: ${e.message}"
                                                            }
                                                        }
                                                    },
                                                    enabled = isDeviceOnline && isInternetAvailable
                                                ) {
                                                    Text("180°")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { showAddDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    enabled = isDeviceOnline && isInternetAvailable
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Actuator", style = MaterialTheme.typography.labelLarge)
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { showActuatorListDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Default.List,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("View Actuator List", style = MaterialTheme.typography.labelLarge)
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { showSensorGraph = !showSensorGraph },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Default.BarChart,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (showSensorGraph) "Hide Sensor Graph" else "Show Sensor Graph",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { showSchedulesDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("View Schedules", style = MaterialTheme.typography.labelLarge)
                }

                if (showSensorGraph) {
                    Spacer(modifier = Modifier.height(16.dp))
                    var expanded by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Sensor: $selectedSensor",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Box {
                            Button(
                                onClick = { expanded = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary
                                )
                            ) {
                                Text("Select")
                            }
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                listOf("Temperature", "Humidity", "Distance", "Moisture", "Light Intensity").forEach { sensor ->
                                    DropdownMenuItem(
                                        text = { Text(sensor) },
                                        onClick = {
                                            selectedSensor = sensor
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    when (selectedSensor) {
                        "Temperature" -> SensorHistoryGraph(temperatureHistory, "Temperature (°C)")
                        "Humidity" -> SensorHistoryGraph(humidityHistory, "Humidity (%)")
                        "Distance" -> SensorHistoryGraph(distanceHistory, "Distance (cm)")
                        "Moisture" -> SensorHistoryGraph(moistureHistory, "Moisture (%)")
                        "Light Intensity" -> SensorHistoryGraph(lightIntensityHistory, "Light Intensity (%)")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { setCurrentScreen("settings") },
            modifier = Modifier
                .fillMaxWidth(0.9f).padding(bottom = 50.dp)
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                Icons.Default.Settings,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Settings", style = MaterialTheme.typography.labelLarge)
        }

        Text(
            "Developed with ❤\uFE0F by: Er. Daljit Singh",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}


@Composable
fun SensorHistoryGraph(sensorData: List<Float?>, label: String) {
    val validData = sensorData.filterNotNull().takeLast(60)
    if (validData.isNotEmpty()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("$label History", style = MaterialTheme.typography.titleMedium)
                Canvas(modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)) {
                    val maxValue = validData.maxOrNull() ?: 100f
                    val minValue = validData.minOrNull() ?: 0f
                    val range = maxOf(maxValue - minValue, 1f)
                    val stepX = size.width / (validData.size - 1)
                    validData.forEachIndexed { index, value ->
                        val x = index * stepX
                        val y = size.height * (1 - (value - minValue) / range)
                        if (index > 0) {
                            drawLine(
                                color = Color(0xFF00796B),
                                start = Offset((index - 1) * stepX, validData[index - 1].let { size.height * (1 - (it - minValue) / range) }),
                                end = Offset(x, y),
                                strokeWidth = 2f
                            )
                        }
                    }
                }
            }
        }
    }
}