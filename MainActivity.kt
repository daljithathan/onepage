package hathan.daljit.esp32_iot_studentversion_2

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {
    private val auth = FirebaseAuth.getInstance()
    private val scanScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        Log.d("MainActivity", "Permissions result: $permissions")
        if (!permissions.values.all { it }) {
            Log.w("MainActivity", "Some permissions denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideNavigationBarOnly()
        FirebaseApp.initializeApp(this)
        ConfigManager.loadConfigFromAssets(this)
        try {
            FirebaseDatabase.getInstance().setPersistenceEnabled(true)
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to enable persistence: ${e.message}")
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        // Initialize Room database and reschedule alarms
        val scheduleDao = AppModule.provideScheduleDatabase(this).scheduleDao()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Clean up duplicate schedules
                val allSchedules = scheduleDao.getAllSchedules()
                val seen = mutableSetOf<String>()
                val duplicates = mutableListOf<ScheduleEntity>()
                allSchedules.forEach { schedule ->
                    val key = "${schedule.actuatorId}:${schedule.time}:${schedule.action}:${schedule.deviceId}"
                    if (seen.contains(key)) {
                        duplicates.add(schedule)
                    } else {
                        seen.add(key)
                    }
                }
                duplicates.forEach { duplicate ->
                    scheduleDao.deleteSchedule(duplicate.scheduleId)
                    AlarmScheduler.cancelAlarm(this@MainActivity, duplicate.scheduleId)
                    Log.d("MainActivity", "Deleted duplicate schedule ${duplicate.scheduleId}")
                }

                // Reschedule remaining alarms
                val schedules = scheduleDao.getAllSchedules()
                Log.d("MainActivity", "Rescheduling ${schedules.size} alarms on app start")
                schedules.forEach { schedule ->
                    AlarmScheduler.cancelAlarm(this@MainActivity, schedule.scheduleId)
                    AlarmScheduler.setAlarm(this@MainActivity, schedule)
                    Log.d("MainActivity", "Rescheduled alarm for schedule ${schedule.scheduleId}")
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to clean up or reschedule: ${e.message}")
            }
        }

        setContent {
            AppContent()
        }
    }

    private fun hideNavigationBarOnly() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.navigationBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun setNavigationBarVisibility(activity: Activity, visible: Boolean) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, !visible)
        if (visible) {
            activity.window.navigationBarColor = Color.Transparent.toArgb()
        } else {
            activity.window.navigationBarColor = Color.Black.toArgb()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scanScope.cancel("MainActivity destroyed")
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onLost(network: Network) {
            Log.w("MainActivity", "Network connection lost")
        }

        override fun onAvailable(network: Network) {
            Log.d("MainActivity", "Network connection restored")
        }
    }

    object NetworkUtils {
        fun isInternetAvailable(context: Context): Boolean {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork ?: return false
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            } else {
                @Suppress("DEPRECATION")
                val networkInfo = connectivityManager.activeNetworkInfo
                networkInfo != null && networkInfo.isConnected
            }
        }
    }

    @Composable
    fun AppTheme(
        darkTheme: Boolean,
        content: @Composable () -> Unit
    ) {
        val view = LocalView.current
        if (!view.isInEditMode) {
            SideEffect {
                val window = (view.context as Activity).window
                WindowCompat.getInsetsController(window, view)?.let { controller ->
                    controller.hide(WindowInsetsCompat.Type.navigationBars())
                    controller.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }
        }
        val colors = if (darkTheme) {
            darkColorScheme(
                primary = Color(0xFF00BFA6),
                secondary = Color(0xFF1DE9B6),
                tertiary = Color(0xFFFFC400),
                background = Color(0xFF26A69A),
                surface = Color(0xFF1A1A1A),
                surfaceVariant = Color(0xFF2C2C2C),
                onPrimary = Color.White,
                onSecondary = Color.White,
                onBackground = Color.White,
                onSurface = Color.White
            )
        } else {
            lightColorScheme(
                primary = Color(0xFF00796B),
                secondary = Color(0xFF26A69A),
                tertiary = Color(0xFFFFA000),
                background = Color(0xFF26A69A),
                surface = Color(0xFFFFFFFF),
                surfaceVariant = Color(0xFFE0F2F1),
                onPrimary = Color.White,
                onSecondary = Color.White,
                onBackground = Color.Black,
                onSurface = Color.Black
            )
        }
        MaterialTheme(
            colorScheme = colors,
            typography = Typography(),
            content = content
        )
    }

    @Composable
    fun AppContent() {
        val context = LocalContext.current
        val prefs = remember { context.getSharedPreferences("ESP32Config", Context.MODE_PRIVATE) }
        val wifiManager = remember { context.getSystemService(Context.WIFI_SERVICE) as WifiManager }
        var isAuthenticated by remember { mutableStateOf(auth.currentUser != null) }
        var permissionsGranted by remember { mutableStateOf(false) }
        var currentScreen by remember {
            mutableStateOf(
                if (isAuthenticated && prefs.contains("device_id") && NetworkUtils.isInternetAvailable(context)) {
                    "ledControl"
                } else if (isAuthenticated) {
                    "welcome"
                } else {
                    "login"
                }
            )
        }
        var isDarkTheme by remember {
            mutableStateOf(prefs.getBoolean("isDarkTheme", false))
        }

        LaunchedEffect(Unit) {
            val window = (context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowInsetsControllerCompat(window, window.decorView).let { controller ->
                controller.hide(WindowInsetsCompat.Type.navigationBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }

        LaunchedEffect(isDarkTheme) {
            val window = (context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, true)
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            window.statusBarColor = if (isDarkTheme) {
                "#1A1A1A".toColorInt()
            } else {
                "#26A69A".toColorInt()
            }
            insetsController.isAppearanceLightStatusBars = !isDarkTheme
            setNavigationBarVisibility(context as Activity, false)
        }

        LaunchedEffect(Unit) {
            val window = (context as Activity).window
            val view = window.decorView

            ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
                val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
                setNavigationBarVisibility(context as Activity, imeVisible)
                WindowInsetsCompat.CONSUMED
            }
        }

        var isSetupModeDetected by remember { mutableStateOf(false) }
        val coroutineScope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }
        var showNoInternetDialog by remember { mutableStateOf(!NetworkUtils.isInternetAvailable(context)) }
        var isInternetAvailable by remember { mutableStateOf(NetworkUtils.isInternetAvailable(context)) }

        LaunchedEffect(isDarkTheme) {
            with(prefs.edit()) {
                putBoolean("isDarkTheme", isDarkTheme)
                apply()
            }
        }

        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.INTERNET
        )

        fun getPreviousScreen(current: String): String? {
            return when (current) {
                "login" -> null
                "signup" -> "login"
                "welcome" -> "login"
                "config" -> "welcome"
                "ledControl" -> null
                "settings" -> "ledControl"
                "help" -> "ledControl"
                "studentGuide" -> "settings"
                "codeGenerator" -> "settings"
                else -> null
            }
        }

        BackHandler(enabled = true) {
            val previousScreen = getPreviousScreen(currentScreen)
            if (previousScreen != null) {
                currentScreen = previousScreen
            } else {
                (context as? Activity)?.finish()
            }
        }

        LaunchedEffect(Unit) {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            connectivityManager.registerDefaultNetworkCallback(networkCallback)

            if (auth.currentUser == null) {
                auth.signOut()
                isAuthenticated = false
                currentScreen = "login"
            }
            val allGranted = permissions.all {
                ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
            if (!allGranted) {
                requestPermissionLauncher.launch(permissions)
            }
            permissionsGranted = true

            scanScope.launch {
                while (isActive) {
                    try {
                        scanForDevices(context, wifiManager) { results ->
                            isSetupModeDetected = results.any { it.SSID.startsWith("ESP32-Setup-") }
                        }
                        val newInternetStatus = NetworkUtils.isInternetAvailable(context)
                        if (newInternetStatus != isInternetAvailable) {
                            isInternetAvailable = newInternetStatus
                            withContext(Dispatchers.Main) {
                                if (!isInternetAvailable) {
                                    showNoInternetDialog = true
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("No internet connection. Some features may be unavailable.")
                                    }
                                } else {
                                    showNoInternetDialog = false
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Internet connection restored.")
                                    }
                                }
                            }
                        }
                        delay(10000)
                    } catch (e: CancellationException) {
                        Log.d("MainActivity", "WiFi scan coroutine cancelled: ${e.message}")
                        throw e
                    } catch (e: Exception) {
                        Log.e("MainActivity", "WiFi scan failed: ${e.message}")
                        delay(10000)
                    }
                }
            }
        }

        LaunchedEffect(Unit) {
            auth.addAuthStateListener { firebaseAuth ->
                try {
                    val newAuthState = firebaseAuth.currentUser != null
                    if (newAuthState != isAuthenticated) {
                        isAuthenticated = newAuthState
                        if (!newAuthState) {
                            FirebaseDatabase.getInstance().purgeOutstandingWrites()
                            FirebaseDatabase.getInstance().goOffline()
                            with(prefs.edit()) { clear().apply() }
                        }
                        currentScreen = when {
                            !newAuthState -> "login"
                            prefs.contains("device_id") && NetworkUtils.isInternetAvailable(context) -> "ledControl"
                            else -> "welcome"
                        }
                    }
                } catch (e: com.google.firebase.FirebaseNetworkException) {
                    Log.e("MainActivity", "Auth state listener error: No internet connection")
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("No internet connection. Please check your network.")
                    }
                    if (isAuthenticated) {
                        currentScreen = "welcome"
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Auth state listener error: ${e.message}")
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Authentication error: ${e.message}")
                    }
                }
            }
        }

        AppTheme(darkTheme = isDarkTheme) {
            Scaffold(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
                    .imePadding(),
                snackbarHost = { SnackbarHost(snackbarHostState) }
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                        .padding(0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(0.dp),
                        verticalArrangement = Arrangement.Top,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (!permissionsGranted) {
                            PermissionRequestScreen()
                        } else {
                            when (currentScreen) {
                                "login" -> LoginScreen(
                                    onLoginSuccess = {
                                        isAuthenticated = true
                                        currentScreen =
                                            if (prefs.contains("device_id") && NetworkUtils.isInternetAvailable(
                                                    context
                                                )
                                            ) "ledControl" else "welcome"
                                    },
                                    onSignupNeeded = { currentScreen = "signup" }
                                )

                                "signup" -> SignupScreen(
                                    onSignupSuccess = { currentScreen = "welcome" },
                                    onLoginNeeded = { currentScreen = "login" }
                                )

                                "welcome" -> WelcomeScreen(
                                    onProceed = { currentScreen = "config" },
                                    onLogout = {
                                        auth.signOut()
                                        isAuthenticated = false
                                        currentScreen = "login"
                                        with(prefs.edit()) { clear().apply() }
                                    },
                                    onConfigRetrieved = { deviceId ->
                                        currentScreen =
                                            if (NetworkUtils.isInternetAvailable(context)) "ledControl" else "welcome"
                                        with(prefs.edit()) {
                                            putString("device_id", deviceId)
                                            apply()
                                        }
                                    },
                                    prefs = prefs
                                )

                                "config" -> ConfigScreen(
                                    onConfigSaved = { currentScreen = "ledControl" },
                                    onBack = { currentScreen = "ledControl" },
                                    prefs = prefs,
                                    wifiManager = wifiManager
                                )

                                "ledControl" -> LEDControlScreen(
                                    onReconfigure = { currentScreen = "config" },
                                    onLogout = {
                                        coroutineScope.launch {
                                            auth.signOut()
                                            isAuthenticated = false
                                            currentScreen = "login"
                                            with(prefs.edit()) {
                                                clear().apply()
                                            }
                                            FirebaseDatabase.getInstance().purgeOutstandingWrites()
                                            FirebaseDatabase.getInstance().goOffline()
                                        }
                                    },
                                    prefs = prefs,
                                    toggleTheme = { isDarkTheme = !isDarkTheme },
                                    wifiManager = wifiManager,
                                    setCurrentScreen = { screen -> currentScreen = screen },
                                    dialogToShow = if (currentScreen in listOf(
                                            "esp32",
                                            "firebase"
                                        )
                                    ) currentScreen else null
                                )

                                "settings" -> SettingsScreen(
                                    onReconfigure = { currentScreen = "config" },
                                    onLogout = {
                                        auth.signOut()
                                        isAuthenticated = false
                                        currentScreen = "login"
                                        with(prefs.edit()) { clear().apply() }
                                    },
                                    toggleTheme = { isDarkTheme = !isDarkTheme },
                                    isSetupModeDetected = isSetupModeDetected,
                                    onBack = { currentScreen = "ledControl" },
                                    setCurrentScreen = { screen -> currentScreen = screen },
                                    prefs = prefs
                                )

                                "codeGenerator" -> {
                                    var actuators by remember { mutableStateOf(emptyList<Actuator>()) }
                                    var errorMessage by remember { mutableStateOf<String?>(null) }
                                    val context = LocalContext.current
                                    val prefs = context.getSharedPreferences(
                                        "ESP32Config",
                                        Context.MODE_PRIVATE
                                    )
                                    LaunchedEffect(Unit) {
                                        val deviceId = prefs.getString("device_id", "") ?: ""
                                        if (deviceId.isNotEmpty() && NetworkUtils.isInternetAvailable(
                                                context
                                            )
                                        ) {
                                            try {
                                                val firebaseHost =
                                                    ConfigManager.getFirebaseHost(context)
                                                val firebaseAuth =
                                                    ConfigManager.getFirebaseAuth(context)
                                                val path = "devices/$deviceId/actuators"
                                                val json = FirebaseRestHelper.readData(
                                                    context,
                                                    firebaseHost,
                                                    firebaseAuth,
                                                    path
                                                )
                                                if (json != null && json.length() > 0) {
                                                    val actuatorList = mutableListOf<Actuator>()
                                                    val validPins = listOf(
                                                        4, 5, 12, 14, 15, 23, 27, 32, 33, 34, 35, 36, 39
                                                    )
                                                    val analogPins = listOf(34, 35, 36, 39)
                                                    json.keys().forEach { key ->
                                                        try {
                                                            val obj = json.getJSONObject(key)
                                                            val name = obj.optString("name", key)
                                                            val type = obj.optString("type", "")
                                                                .takeIf { it.isNotEmpty() }
                                                                ?: throw IllegalArgumentException("Missing type for actuator $key")
                                                            val pin = obj.optInt("pin", -1)
                                                                .takeIf { it != -1 }
                                                                ?: throw IllegalArgumentException("Missing or invalid pin for actuator $key")
                                                            if (pin !in validPins) {
                                                                throw IllegalArgumentException("Invalid pin $pin for actuator $key (valid pins: $validPins)")
                                                            }
                                                            if (type in listOf("YL69", "LM393") && pin !in analogPins) {
                                                                throw IllegalArgumentException("$type actuator $key must use ADC pin (34, 35, 36, 39)")
                                                            }
                                                            val state = when (type) {
                                                                "LED" -> obj.optString(
                                                                    "state",
                                                                    "OFF"
                                                                )
                                                                "TTP223" -> obj.optString(
                                                                    "state",
                                                                    "NOT_TOUCHED"
                                                                ).takeIf {
                                                                    it in listOf(
                                                                        "TOUCHED",
                                                                        "NOT_TOUCHED"
                                                                    )
                                                                }
                                                                    ?: throw IllegalArgumentException(
                                                                        "Invalid state for TTP223 actuator $key: must be TOUCHED or NOT_TOUCHED"
                                                                    )
                                                                "PIR" -> obj.optString(
                                                                    "state",
                                                                    "NO_MOTION"
                                                                ).takeIf {
                                                                    it in listOf(
                                                                        "MOTION",
                                                                        "NO_MOTION"
                                                                    )
                                                                }
                                                                    ?: throw IllegalArgumentException(
                                                                        "Invalid state for PIR actuator $key: must be MOTION or NO_MOTION"
                                                                    )
                                                                else -> null
                                                            }
                                                            actuatorList.add(
                                                                Actuator(
                                                                    id = key,
                                                                    name = name,
                                                                    type = type,
                                                                    pin = pin,
                                                                    state = state,
                                                                    trigPin = obj.optInt(
                                                                        "trigPin",
                                                                        -1
                                                                    ).takeIf { it != -1 },
                                                                    echoPin = obj.optInt(
                                                                        "echoPin",
                                                                        -1
                                                                    ).takeIf { it != -1 },
                                                                    angle = obj.optInt("angle", -1)
                                                                        .takeIf { it != -1 && it in 0..180 }
                                                                )
                                                            )
                                                            Log.d(
                                                                "MainActivity",
                                                                "Parsed actuator $key: type=$type, pin=$pin, state=$state"
                                                            )
                                                        } catch (e: IllegalArgumentException) {
                                                            Log.e(
                                                                "MainActivity",
                                                                "Invalid actuator $key: ${e.message}"
                                                            )
                                                        } catch (e: Exception) {
                                                            Log.e(
                                                                "MainActivity",
                                                                "Error parsing actuator $key: ${e.message}"
                                                            )
                                                        }
                                                    }
                                                    actuators = actuatorList
                                                    if (actuatorList.any { it.type == "TTP223" }) {
                                                        Log.d(
                                                            "MainActivity",
                                                            "TTP223 actuator(s) successfully parsed"
                                                        )
                                                    }
                                                    if (actuatorList.any { it.type == "YL69" }) {
                                                        Log.d(
                                                            "MainActivity",
                                                            "YL-69 actuator(s) successfully parsed"
                                                        )
                                                    }
                                                    if (actuatorList.any { it.type == "PIR" }) {
                                                        Log.d(
                                                            "MainActivity",
                                                            "PIR actuator(s) successfully parsed"
                                                        )
                                                    }
                                                    if (actuatorList.any { it.type == "LM393" }) {
                                                        Log.d(
                                                            "MainActivity",
                                                            "LM393 actuator(s) successfully parsed"
                                                        )
                                                    }
                                                } else {
                                                    Log.w(
                                                        "MainActivity",
                                                        "No actuators found at $path"
                                                    )
                                                }
                                            } catch (e: com.google.firebase.FirebaseNetworkException) {
                                                Log.e(
                                                    "MainActivity",
                                                    "Network error fetching actuators: ${e.message}"
                                                )
                                                errorMessage =
                                                    "No internet connection. Please check your network."
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(
                                                        context,
                                                        errorMessage,
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                }
                                            } catch (e: Exception) {
                                                Log.e(
                                                    "MainActivity",
                                                    "Error fetching actuators: ${e.message}"
                                                )
                                                errorMessage =
                                                    "Failed to load actuators: ${e.message}"
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(
                                                        context,
                                                        errorMessage,
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                }
                                            }
                                        } else {
                                            errorMessage =
                                                if (deviceId.isEmpty()) "Device ID not found" else "No internet connection. Please check your network."
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(
                                                    context,
                                                    errorMessage,
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        }
                                    }
                                    if (errorMessage != null) {
                                        Text(
                                            text = errorMessage!!,
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.padding(16.dp)
                                        )
                                    }
                                    ESP32CodeGeneratorScreen(
                                        onBack = { currentScreen = "settings" },
                                        initialActuators = actuators,
                                        deviceId = prefs.getString("device_id", "") ?: ""
                                    )
                                }

                                "help" -> HelpScreen(
                                    onBack = { currentScreen = "ledControl" }
                                )

                                "studentGuide" -> StudentGuideScreen(
                                    onClose = { currentScreen = "settings" }
                                )
                            }
                        }
                    }

                    if (showNoInternetDialog && isAuthenticated) {
                        AlertDialog(
                            onDismissRequest = { /* Prevent dismissal */ },
                            title = { Text("No Internet Connection") },
                            text = { Text("Please check your network connection. Some features may be unavailable until the internet is restored.") },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            if (NetworkUtils.isInternetAvailable(context)) {
                                                isInternetAvailable = true
                                                showNoInternetDialog = false
                                                snackbarHostState.showSnackbar("Internet connection restored.")
                                            } else {
                                                snackbarHostState.showSnackbar("Still no internet connection.")
                                            }
                                        }
                                    }
                                ) {
                                    Text("Retry")
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = {
                                        showNoInternetDialog = false
                                    }
                                ) {
                                    Text("Continue Offline")
                                }
                            }
                        )
                    }
                }
            }
        }
    }

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
    fun BulletPoint(text: String) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "•",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }

    private suspend fun testFirebaseHost(firebaseHost: String, firebaseAuth: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://$firebaseHost/.json?auth=$firebaseAuth")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            val responseCode = conn.responseCode
            conn.disconnect()

            when (responseCode) {
                HttpURLConnection.HTTP_OK -> true
                HttpURLConnection.HTTP_UNAUTHORIZED -> {
                    Log.e("ConfigScreen", "Firebase authentication failed - check auth token")
                    false
                }
                else -> {
                    Log.e("ConfigScreen", "Firebase host test failed with code $responseCode")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e("ConfigScreen", "Firebase host test failed: ${e.message}")
            false
        }
    }

    object FirebaseRestHelper {
        private const val TAG = "FirebaseRestHelper"

        suspend fun writeData(
            firebaseHost: String,
            firebaseAuth: String,
            path: String,
            data: Map<String, Any?>
        ): Boolean = withContext(Dispatchers.IO) {
            try {
                val url = URL("https://$firebaseHost/$path.json?auth=$firebaseAuth")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "PUT"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 10000
                conn.readTimeout = 10000

                val jsonData = JSONObject(data).toString()
                conn.outputStream.use { os ->
                    os.write(jsonData.toByteArray())
                    os.flush()
                }

                val responseCode = conn.responseCode
                conn.disconnect()

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    Log.d(TAG, "Write successful to $path")
                    true
                } else {
                    Log.e(TAG, "Write failed to $path: HTTP $responseCode")
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Write failed to $path: ${e.message}")
                false
            }
        }

        suspend fun updateData(
            context: Context,
            firebaseHost: String,
            firebaseAuth: String,
            path: String,
            data: Map<String, Any>
        ): Boolean = withContext(Dispatchers.IO) {
            try {
                val url = ConfigManager.getFirebaseUrlWithAuth(context, path)
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.requestMethod = "PATCH"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 10000
                conn.readTimeout = 10000

                conn.setRequestProperty("Authorization", "Bearer $firebaseAuth")

                val jsonData = JSONObject(data).toString()
                conn.outputStream.use { os ->
                    os.write(jsonData.toByteArray())
                    os.flush()
                }

                val responseCode = conn.responseCode
                conn.disconnect()

                when (responseCode) {
                    HttpURLConnection.HTTP_OK -> true
                    HttpURLConnection.HTTP_UNAUTHORIZED -> {
                        Log.e(TAG, "Auth failed - token may have expired")
                        false
                    }
                    else -> false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Update failed to $path: ${e.message}")
                false
            }
        }

        suspend fun readData(
            context: Context,
            firebaseHost: String,
            firebaseAuth: String,
            path: String
        ): JSONObject? = withContext(Dispatchers.IO) {
            try {
                val url = ConfigManager.getFirebaseUrlWithAuth(context, path)
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 10000
                conn.readTimeout = 10000

                val responseCode = conn.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    conn.disconnect()
                    if (response.isNotEmpty() && response != "null") {
                        Log.d(TAG, "Read successful from $path")
                        JSONObject(response)
                    } else {
                        Log.w(TAG, "No data at $path")
                        null
                    }
                } else {
                    Log.e(TAG, "Read failed from $path: HTTP $responseCode")
                    conn.disconnect()
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Read failed from $path: ${e.message}")
                null
            }
        }

        suspend fun deleteData(
            firebaseHost: String,
            firebaseAuth: String,
            path: String
        ): Boolean = withContext(Dispatchers.IO) {
            var attempt = 1
            val maxAttempts = 3
            var lastError: String? = null

            while (attempt <= maxAttempts) {
                try {
                    val url = URL("https://$firebaseHost/$path.json?auth=$firebaseAuth")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "DELETE"
                    conn.connectTimeout = 10000
                    conn.readTimeout = 10000

                    val responseCode = conn.responseCode
                    val responseMessage = conn.inputStream?.bufferedReader()?.use { it.readText() } ?: conn.errorStream?.bufferedReader()?.use { it.readText() }
                    conn.disconnect()

                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        Log.d(TAG, "Delete successful for $path")
                        return@withContext true
                    } else {
                        lastError = "HTTP $responseCode: ${responseMessage ?: "Unknown error"}"
                        Log.w(TAG, "Delete attempt $attempt/$maxAttempts failed for $path: $lastError")
                    }
                } catch (e: Exception) {
                    lastError = e.message
                    Log.w(TAG, "Delete attempt $attempt/$maxAttempts failed for $path: ${e.message}")
                }
                attempt++
                if (attempt <= maxAttempts) delay(1000)
            }
            Log.e(TAG, "Delete failed for $path after $maxAttempts attempts: $lastError")
            false
        }

        suspend fun readAngle(
            context: Context,
            firebaseHost: String,
            firebaseAuth: String,
            path: String
        ): Int? = withContext(Dispatchers.IO) {
            try {
                val json = readData(context, firebaseHost, firebaseAuth, path)
                json?.optInt("angle")?.takeIf { it in 0..180 }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read angle from $path: ${e.message}")
                null
            }
        }

        suspend fun getData(firebaseHost: String, firebaseAuth: String, path: String): Map<String, Any>? {
            val url = "https://$firebaseHost/$path.json?auth=$firebaseAuth"
            println("FirebaseRestHelper: Attempting GET to $url")
            return withContext(Dispatchers.IO) {
                try {
                    val connection = URL(url).openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.setRequestProperty("Accept", "application/json")
                    val responseCode = connection.responseCode
                    println("FirebaseRestHelper: Response code $responseCode")
                    if (responseCode == 200) {
                        val reader = BufferedReader(InputStreamReader(connection.inputStream))
                        val response = reader.readLines().joinToString("")
                        reader.close()
                        println("FirebaseRestHelper: Response $response")
                        val mapper = jacksonObjectMapper()
                        mapper.readValue(response, Map::class.java) as Map<String, Any>?
                    } else {
                        val errorReader = BufferedReader(InputStreamReader(connection.errorStream))
                        val error = errorReader.readLines().joinToString("")
                        errorReader.close()
                        println("FirebaseRestHelper: Read failed from $path: HTTP $responseCode, Error: $error")
                        null
                    }
                } catch (e: Exception) {
                    println("FirebaseRestHelper: Error reading from $path: ${e.message}")
                    null
                }
            }
        }
    }
}