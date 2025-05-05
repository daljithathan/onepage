package hathan.daljit.esp32_iot_studentversion_2

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException

object ConfigManager {

    private const val PREFS_NAME = "SecureESP32Config"
    private const val KEY_FIREBASE_HOST = "firebase_host"
    private const val KEY_FIREBASE_AUTH = "firebase_auth"
    private const val DEFAULT_FIREBASE_HOST = "esp32studentversion2-a80cf-default-rtdb.firebaseio.com"
    private const val DEFAULT_FIREBASE_AUTH = "VAHWtX1AH6MM5OIOcIMlZ3L5ufIYIbyaG54cDG9l"

    // Initialize EncryptedSharedPreferences
    private fun getSecurePrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // Load configuration from assets (e.g., firebase-config.json)
    fun loadConfigFromAssets(context: Context) {
        try {
            Log.d("ConfigManager", "Loading config from assets")
            val jsonString = context.assets.open("firebase-config.json").bufferedReader().use { it.readText() }
            Log.d("ConfigManager", "Loaded config: $jsonString")
            val json = JSONObject(jsonString)
            val host = json.optString("firebase_host", DEFAULT_FIREBASE_HOST)
            val auth = json.optString("firebase_auth", DEFAULT_FIREBASE_AUTH)

            // Store in EncryptedSharedPreferences
            val prefs = getSecurePrefs(context)
            with(prefs.edit()) {
                putString(KEY_FIREBASE_HOST, host)
                putString(KEY_FIREBASE_AUTH, auth)
                apply()
            }
        } catch (e: IOException) {
            Log.e("ConfigManager", "Error loading config from assets", e)
            // Fallback to default values if asset file is missing
            val prefs = getSecurePrefs(context)
            with(prefs.edit()) {
                putString(KEY_FIREBASE_HOST, DEFAULT_FIREBASE_HOST)
                putString(KEY_FIREBASE_AUTH, DEFAULT_FIREBASE_AUTH)
                apply()
            }
        }
    }

    // Get Firebase host
    fun getFirebaseHost(context: Context): String {
        val prefs = getSecurePrefs(context)
        return prefs.getString(KEY_FIREBASE_HOST, DEFAULT_FIREBASE_HOST) ?: DEFAULT_FIREBASE_HOST
    }

    // Get Firebase auth
    fun getFirebaseAuth(context: Context): String {
        val prefs = getSecurePrefs(context)
        return prefs.getString(KEY_FIREBASE_AUTH, DEFAULT_FIREBASE_AUTH) ?: DEFAULT_FIREBASE_AUTH
    }
    fun getFirebaseUrlWithAuth(context: Context, path: String): String {
        val host = getFirebaseHost(context)
        val auth = getFirebaseAuth(context)
        return "https://$host/$path.json?auth=$auth"
    }
}
