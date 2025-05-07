#include <WiFi.h>
#include <WebServer.h>
#include <FirebaseESP32.h>
#include <ArduinoJson.h>
#include <time.h>
#include <EEPROM.h>

#define ONBOARD_LED 2
#define RESET_PIN 13
#define EEPROM_SIZE 512
#define WIFI_SSID_ADDR 0
#define WIFI_PASS_ADDR 64
#define FIREBASE_HOST_ADDR 128
#define FIREBASE_AUTH_ADDR 256
#define DEVICE_ID_ADDR 384
#define OWNER_UID_ADDR 416
#define NTP_SERVER "pool.ntp.org"
#define GMT_OFFSET_SEC 19800
#define DAYLIGHT_OFFSET_SEC 0
#define PRESENCE_INTERVAL 10000
#define SENSOR_READ_INTERVAL 2000
#define ACTUATOR_CHECK_INTERVAL 5000
#define AP_PASSWORD "66398612"
const int VALID_PINS[] =  {4, 5, 12, 14, 15, 23, 27, 32, 33, 34, 35, 36, 39};
const int VALID_PINS_COUNT = sizeof(VALID_PINS) / sizeof(VALID_PINS[0]);
FirebaseData fbdo;
FirebaseConfig config;
FirebaseAuth auth;
WebServer server(80);
String deviceId = "ESP32_355c7bcc";
String AP_SSID = "ESP32-Setup-ESP32_355c7bcc";
struct LedActuator {
  const char* id;
  int pin;
  bool state;
};
LedActuator ledActuators[] = {  {"led1746259709171", 14, false}};
const int LED_ACTUATOR_COUNT = 1;
struct PIRSensor {
  const char* id;
  int pin;
  bool state;
};
PIRSensor pirSensors[] = {  {"pir1746259719596", 12, false}};
const int PIR_SENSOR_COUNT = 1;unsigned long lastPresenceUpdate = 0;
unsigned long lastSensorUpdate = 0;
unsigned long lastActuatorCheck = 0;
bool actuatorsInitialized = false;

bool isValidPin(int pin) {
  for (int i = 0; i < VALID_PINS_COUNT; i++) {
    if (VALID_PINS[i] == pin) return true;
  }
  return false;
}
void logMessage(String level, String message) {
  String timestamp;
  time_t now = time(nullptr);
  if (now > 100000) {
    char buf[20];
    strftime(buf, sizeof(buf), "%Y-%m-%d %H:%M:%S", localtime(&now));
    timestamp = String(buf);
  } else {
    timestamp = "No NTP";
  }
  Serial.print("[" + timestamp + "] " + level + ": " + message + "\n");
}
String readEEPROM(int address, int maxLength) {
  String data;
  for (int i = 0; i < maxLength; i++) {
    char c = EEPROM.read(address + i);
    if (c == 0) break;
    data += c;
  }
  return data;
}
void writeEEPROM(int address, String data) {
  for (int i = 0; i < data.length(); i++) {
    EEPROM.write(address + i, data[i]);
  }
  EEPROM.write(address + data.length(), 0);
  EEPROM.commit();
}
bool connectToWiFi() {
  String ssid = readEEPROM(WIFI_SSID_ADDR, 64);
  String password = readEEPROM(WIFI_PASS_ADDR, 64);
  if (ssid.length() == 0) {
    logMessage("ERROR", "No WiFi credentials. Starting AP mode.");
    WiFi.mode(WIFI_AP);
    WiFi.softAP(AP_SSID.c_str(), AP_PASSWORD);
    logMessage("INFO", "AP SSID: " + AP_SSID + ", Password: " + String(AP_PASSWORD));
    digitalWrite(ONBOARD_LED, LOW);
    return false;
  }
  logMessage("INFO", "Connecting to WiFi: " + ssid);
  WiFi.mode(WIFI_STA);
  WiFi.begin(ssid.c_str(), password.c_str());
  unsigned long startTime = millis();
  bool ledState = false;
  while (WiFi.status() != WL_CONNECTED && millis() - startTime < 20000) {
    ledState = !ledState;
    digitalWrite(ONBOARD_LED, ledState ? HIGH : LOW);
    delay(200);
    Serial.print(".");
  }
  Serial.println();
  if (WiFi.status() == WL_CONNECTED) {
    logMessage("INFO", "WiFi connected. IP: " + WiFi.localIP().toString());
    digitalWrite(ONBOARD_LED, HIGH);
    return true;
  }
  logMessage("ERROR", "Failed to connect to WiFi");
  digitalWrite(ONBOARD_LED, LOW);
  return false;
}
bool initFirebase() {
  String host = readEEPROM(FIREBASE_HOST_ADDR, 128);
  String authKey = readEEPROM(FIREBASE_AUTH_ADDR, 128);
  if (host.length() == 0 || authKey.length() == 0) {
    logMessage("ERROR", "Firebase credentials missing");
    return false;
  }
  config.host = host.c_str();
  config.signer.tokens.legacy_token = authKey.c_str();
  config.timeout.serverResponse = 10000;
  Firebase.begin(&config, &auth);
  Firebase.reconnectWiFi(true);
  unsigned long startTime = millis();
  while (!Firebase.ready() && millis() - startTime < 10000) {
    delay(500);
    Serial.print(".");
  }
  if (Firebase.ready()) {
    logMessage("INFO", "Firebase initialized");
    return true;
  }
  logMessage("ERROR", "Firebase initialization failed");
  return false;
}
void syncNetworkTime() {
  configTime(GMT_OFFSET_SEC, DAYLIGHT_OFFSET_SEC, NTP_SERVER);
  logMessage("INFO", "Waiting for NTP time sync...");
  time_t now;
  for (int i = 0; i < 10; i++) {
    now = time(nullptr);
    if (now > 100000) {
      logMessage("INFO", "Time synchronized");
      return;
    }
    delay(500);
  }
  logMessage("WARNING", "NTP sync failed");
}
void handleRoot() {
  String html = "<!DOCTYPE html><html><head>";
  html += "<meta name='viewport' content='width=device-width, initial-scale=1'>";
  html += "<style>";
  html += "body { font-family: Arial, sans-serif; margin: 20px; text-align: center; }";
  html += "input, button { padding: 10px; margin: 5px; width: 80%; max-width: 300px; }";
  html += "button { background-color: #4CAF50; color: white; border: none; cursor: pointer; }";
  html += "button:hover { background-color: #45a049; }";
  html += "</style></head><body>";
  html += "<h2>ESP32 Configuration</h2>";
  html += "<form action='/configure' method='post'>";
  html += "<input type='text' name='ssid' placeholder='WiFi SSID' required><br>";
  html += "<input type='password' name='pass' placeholder='WiFi Password' required><br>";
  html += "<input type='text' name='host' placeholder='Firebase Host' required><br>";
  html += "<input type='text' name='auth' placeholder='Firebase Auth' required><br>";
  html += "<input type='text' name='uid' placeholder='Owner UID' required><br>";
  html += "<button type='submit'>Configure</button>";
  html += "</form></body></html>";
  server.send(200, "text/html", html);
}
void handleConfigure() {
  if (!server.hasArg("ssid") || !server.hasArg("pass") || !server.hasArg("host") ||
      !server.hasArg("auth") || !server.hasArg("uid")) {
    server.send(400, "text/plain", "Missing parameters");
    return;
  }
  String ssid = server.arg("ssid");
  String password = server.arg("pass");
  String host = server.arg("host");
  String authKey = server.arg("auth");
  String ownerUid = server.arg("uid");
  writeEEPROM(WIFI_SSID_ADDR, ssid);
  writeEEPROM(WIFI_PASS_ADDR, password);
  writeEEPROM(FIREBASE_HOST_ADDR, host);
  writeEEPROM(FIREBASE_AUTH_ADDR, authKey);
  writeEEPROM(DEVICE_ID_ADDR, deviceId);
  writeEEPROM(OWNER_UID_ADDR, ownerUid);
  server.send(200, "text/plain", "Configuration saved. Rebooting...");
  delay(1000);
  ESP.restart();
}
float readHCSR04Distance(int trigPin, int echoPin) {
  digitalWrite(trigPin, LOW);
  delayMicroseconds(2);
  digitalWrite(trigPin, HIGH);
  delayMicroseconds(10);
  digitalWrite(trigPin, LOW);
  long duration = pulseIn(echoPin, HIGH);
  float distance = duration * 0.034 / 2;
  if (distance > 400 || distance < 2) return -1;
  return distance;
}
float readYL69Moisture(int pin) {
  int rawValue = analogRead(pin);
  float moisture = 100.0 - ((float)rawValue / 4095.0 * 100.0);
  if (moisture < 0 || moisture > 100) return -1;
  return moisture;
}
bool readTTP223State(int pin) {
  return digitalRead(pin) == HIGH;
}
bool readPIRState(int pin) {
  bool state = digitalRead(pin) == HIGH;
  logMessage("DEBUG", "PIR raw state on pin " + String(pin) + ": " + (state ? "HIGH" : "LOW"));
  return state;
}

void setup() {
  Serial.begin(115200);
  delay(1000);
  logMessage("INFO", "Booting ESP32...");
  pinMode(ONBOARD_LED, OUTPUT);
  digitalWrite(ONBOARD_LED, LOW);
  pinMode(RESET_PIN, INPUT_PULLUP);
  EEPROM.begin(EEPROM_SIZE);
  writeEEPROM(DEVICE_ID_ADDR, deviceId);  for (int i = 0; i < LED_ACTUATOR_COUNT; i++) {
    if (isValidPin(ledActuators[i].pin)) {
      pinMode(ledActuators[i].pin, OUTPUT);
      digitalWrite(ledActuators[i].pin, LOW);
      ledActuators[i].state = false;
    } else {
      logMessage("ERROR", "Invalid pin for LED " + String(ledActuators[i].id));
    }
  }  for (int i = 0; i < PIR_SENSOR_COUNT; i++) {
    if (isValidPin(pirSensors[i].pin)) {
      pinMode(pirSensors[i].pin, INPUT_PULLDOWN);
      pirSensors[i].state = false;
      logMessage("INFO", "PIR " + String(pirSensors[i].id) + " initialized on pin " + String(pirSensors[i].pin));
    } else {
      logMessage("ERROR", "Invalid pin for PIR " + String(pirSensors[i].id) + ": " + String(pirSensors[i].pin));
    }
  }
  logMessage("INFO", "Waiting 30 seconds for PIR sensor warm-up...");
  for (int i = 0; i < 30; i++) {
    digitalWrite(ONBOARD_LED, HIGH);
    delay(500);
    digitalWrite(ONBOARD_LED, LOW);
    delay(500);
  }
  logMessage("INFO", "PIR warm-up complete");  if (!connectToWiFi()) {
    server.on("/", handleRoot);
    server.on("/configure", handleConfigure);
    server.begin();
    logMessage("INFO", "Web server started");
  } else {
    syncNetworkTime();
    if (initFirebase()) {
      String path = "/devices/" + deviceId + "/status";
      Firebase.setString(fbdo, path.c_str(), "Connected");
      path = "/devices/" + deviceId + "/ownerUid";
      String ownerUid = readEEPROM(OWNER_UID_ADDR, 64);
      Firebase.setString(fbdo, path.c_str(), ownerUid);
    }
  }
}

void loop() {
  if (WiFi.status() != WL_CONNECTED) {
    server.handleClient();
    if (digitalRead(RESET_PIN) == LOW) {
      logMessage("INFO", "Reset requested");
      writeEEPROM(WIFI_SSID_ADDR, "");
      writeEEPROM(WIFI_PASS_ADDR, "");
      writeEEPROM(FIREBASE_HOST_ADDR, "");
      writeEEPROM(FIREBASE_AUTH_ADDR, "");
      writeEEPROM(OWNER_UID_ADDR, "");
      delay(1000);
      ESP.restart();
    }
    return;
  }
  unsigned long currentMillis = millis();
  if (currentMillis - lastPresenceUpdate >= PRESENCE_INTERVAL) {
    String path = "/devices/" + deviceId + "/lastSeen";
    time_t now = time(nullptr);
    if (Firebase.setInt(fbdo, path.c_str(), now)) {
      path = "/devices/" + deviceId + "/status";
      Firebase.setString(fbdo, path.c_str(), "Connected");
    } else {
      logMessage("ERROR", "Failed to update presence: " + fbdo.errorReason());
    }
    lastPresenceUpdate = currentMillis;
  }
  if (currentMillis - lastSensorUpdate >= SENSOR_READ_INTERVAL) {
    if (Firebase.ready()) {  for (int i = 0; i < PIR_SENSOR_COUNT; i++) {
    bool state = readPIRState(pirSensors[i].pin);
    String stateStr = state ? "MOTION" : "NO_MOTION";
    logMessage("DEBUG", "PIR " + String(pirSensors[i].id) + " checked, state: " + stateStr);
    if (pirSensors[i].state != state) {
      pirSensors[i].state = state;
      String path = "/devices/" + deviceId + "/actuators/" + pirSensors[i].id + "/state";
      if (Firebase.setString(fbdo, path.c_str(), stateStr.c_str())) {
        logMessage("INFO", "PIR " + String(pirSensors[i].id) + " state uploaded: " + stateStr);
      } else {
        logMessage("ERROR", "Failed to upload PIR " + String(pirSensors[i].id) + " state: " + fbdo.errorReason());
      }
      path = "/devices/" + deviceId + "/sensors/pir/state";
      if (Firebase.setString(fbdo, path.c_str(), stateStr.c_str())) {
        logMessage("INFO", "PIR aggregated state uploaded: " + stateStr);
      } else {
        logMessage("ERROR", "Failed to upload PIR aggregated state: " + fbdo.errorReason());
      }
    }
  }    }
    lastSensorUpdate = currentMillis;
  }
  if (!actuatorsInitialized) {
    actuatorsInitialized = true;  for (int i = 0; i < LED_ACTUATOR_COUNT; i++) {
    String path = "/devices/" + deviceId + "/actuators/" + ledActuators[i].id + "/state";
    if (Firebase.getString(fbdo, path.c_str())) {
      String state = fbdo.stringData();
      bool newState = state == "ON";
      if (ledActuators[i].state != newState) {
        ledActuators[i].state = newState;
        digitalWrite(ledActuators[i].pin, newState ? HIGH : LOW);
        logMessage("INFO", "LED " + String(ledActuators[i].id) + " set to " + state);
      }
    } else {
      logMessage("ERROR", "Failed to read LED " + String(ledActuators[i].id) + " state: " + fbdo.errorReason());
    }
  }  } else if (currentMillis - lastActuatorCheck >= ACTUATOR_CHECK_INTERVAL) {  for (int i = 0; i < LED_ACTUATOR_COUNT; i++) {
    String path = "/devices/" + deviceId + "/actuators/" + ledActuators[i].id + "/state";
    if (Firebase.getString(fbdo, path.c_str())) {
      String state = fbdo.stringData();
      bool newState = state == "ON";
      if (ledActuators[i].state != newState) {
        ledActuators[i].state = newState;
        digitalWrite(ledActuators[i].pin, newState ? HIGH : LOW);
        logMessage("INFO", "LED " + String(ledActuators[i].id) + " set to " + state);
      }
    } else {
      logMessage("ERROR", "Failed to read LED " + String(ledActuators[i].id) + " state: " + fbdo.errorReason());
    }
  }    lastActuatorCheck = currentMillis;
  }
  if (digitalRead(RESET_PIN) == LOW) {
    logMessage("INFO", "Reset requested");
    writeEEPROM(WIFI_SSID_ADDR, "");
    writeEEPROM(WIFI_PASS_ADDR, "");
    writeEEPROM(FIREBASE_HOST_ADDR, "");
    writeEEPROM(FIREBASE_AUTH_ADDR, "");
    writeEEPROM(OWNER_UID_ADDR, "");
    delay(1000);
    ESP.restart();
  }
} 