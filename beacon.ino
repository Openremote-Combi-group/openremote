// Not neccesary in Arduino IDE
// #include <Arduino.h>
#include "BLEDevice.h"
#include <WiFi.h>
#include <PubSubClient.h>

// --- WiFi Credentials ---
const char* ssid = "WiFii";
const char* password = "12345678";

// --- MQTT Broker Details (OpenRemote) ---
const char* mqtt_server = "10.80.63.70"; 
const int mqtt_port = 1883;

// --- OpenRemote MQTT Configuration ---
const char* mqtt_username = "master:mqtt";  // Format: {realm}:{service-username}
const char* mqtt_password = "cB2zFq3e53XR1cSKsbt8lR4ZxozCD30q";  // Your service user secret
const char* mqtt_realm = "master";
const char* mqtt_client_id = "esp32-beacon-001";  // Unique client ID for this device
const char* asset_id = "6UGj4OxCfhtOxqHaeh22Up";  // Your PalletAsset ID in OpenRemote

// --- Testing Mode ---
const bool USE_DUMMY_DATA = true;  // Set to true to use dummy data instead of BLE
const int DUMMY_DATA_INTERVAL_MS = 1000;  // Delay between publishes in dummy mode (1 second)
const int REAL_DATA_INTERVAL_MS = 1000;   // Delay between publishes in real mode (1 second)

// --- Client Setup ---
WiFiClient espClient;
PubSubClient client(espClient);

// The service we are looking for:
static BLEUUID serviceUUID("586c9fb7-d77e-4a84-841b-f226ed7bc789");

// The characteristics we are looking for:
static BLEUUID tempUUID("dfa08a30-9fdc-4bb5-a125-f11ef653c003");
static BLEUUID humidUUID("107810dc-202e-4674-92e5-b40364fa74d7");
static BLEUUID gyroX_UUID("ba5ede19-7172-42d3-b451-1d8ffcab9c16");
static BLEUUID gyroY_UUID("1d4b2fea-bd52-4603-adc5-938df5f1c938");
static BLEUUID gyroZ_UUID("c8ad5644-2eed-4e14-b2b6-86cbb52d283d");

static boolean doConnect = false;
static boolean connected = false;
static boolean doScan = false;

static BLERemoteCharacteristic *pRemoteTempCharacteristic;
static BLERemoteCharacteristic *pRemoteHumidCharacteristic;
static BLERemoteCharacteristic *pRemoteGyroX_Characteristic;
static BLERemoteCharacteristic *pRemoteGyroY_Characteristic;
static BLERemoteCharacteristic *pRemoteGyroZ_Characteristic;

static BLEAdvertisedDevice *myDevice;

// Sensor values storage
uint16_t tempValue = 0;
uint16_t humidValue = 0;
uint16_t gyroX_Value = 0;
uint16_t gyroY_Value = 0;
uint16_t gyroZ_Value = 0;

// MAC address storage
String deviceMacAddress = "";


// Callback function to handle notifications
static void notifyCallback(BLERemoteCharacteristic *pBLERemoteCharacteristic, uint8_t *pData, size_t length, bool isNotify) {
  Serial.print("Notify callback for characteristic ");
  Serial.print(pBLERemoteCharacteristic->getUUID().toString().c_str());
  Serial.print(" of data length ");
  Serial.println(length);
  Serial.print("data: ");
  Serial.write(pData, length);
  Serial.println();
  
  // Parse and store the value based on which characteristic it is
  if (length >= 2) {
    uint16_t value = (pData[1] << 8) | pData[0]; // Little-endian format
    
    if (pBLERemoteCharacteristic->getUUID().equals(tempUUID)) {
      tempValue = value;
      Serial.println("Updated temp value from notification");
    } else if (pBLERemoteCharacteristic->getUUID().equals(humidUUID)) {
      humidValue = value;
      Serial.println("Updated humid value from notification");
    } else if (pBLERemoteCharacteristic->getUUID().equals(gyroX_UUID)) {
      gyroX_Value = value;
      Serial.println("Updated gyro X value from notification");
    } else if (pBLERemoteCharacteristic->getUUID().equals(gyroY_UUID)) {
      gyroY_Value = value;
      Serial.println("Updated gyro Y value from notification");
    } else if (pBLERemoteCharacteristic->getUUID().equals(gyroZ_UUID)) {
      gyroZ_Value = value;
      Serial.println("Updated gyro Z value from notification");
    }
  }
}

class MyClientCallback : public BLEClientCallbacks {
  void onConnect(BLEClient *pclient) {}

  void onDisconnect(BLEClient *pclient) {
    connected = false;
    Serial.println("onDisconnect");
  }
};

bool connectToServer() {
  Serial.print("Forming a connection to ");
  Serial.println(myDevice->getAddress().toString().c_str());

  BLEClient *pClient = BLEDevice::createClient();
  Serial.println(" - Created client");

  pClient->setClientCallbacks(new MyClientCallback());


  pClient->connect(myDevice);  
  Serial.println(" - Connected to server");
  pClient->setMTU(517);  

  // Obtain a reference to the service we are after in the remote BLE server.
  BLERemoteService *pRemoteService = pClient->getService(serviceUUID);
  if (pRemoteService == nullptr) {
    Serial.print("Failed to find our service UUID: ");
    Serial.println(serviceUUID.toString().c_str());
    pClient->disconnect();
    return false;
  }
  Serial.println(" - Found our service");

  // Obtain a reference to the characteristic in the service of the remote BLE server.
  pRemoteTempCharacteristic = pRemoteService->getCharacteristic(tempUUID);
  if (pRemoteTempCharacteristic == nullptr) {
    Serial.print("Failed to find our characteristic UUID: ");
    Serial.println(tempUUID.toString().c_str());
    pClient->disconnect();
    return false;
  }
  Serial.println(" - Found temp characteristic");

  // Obtain a reference to the characteristic in the service of the remote BLE server.
  pRemoteHumidCharacteristic = pRemoteService->getCharacteristic(humidUUID);
  if (pRemoteHumidCharacteristic == nullptr) {
    Serial.print("Failed to find our characteristic UUID: ");
    Serial.println(humidUUID.toString().c_str());
    pClient->disconnect();
    return false;
  }
  Serial.println(" - Found humid characteristic");


  // Obtain a reference to the characteristic in the service of the remote BLE server.
  pRemoteGyroX_Characteristic = pRemoteService->getCharacteristic(gyroX_UUID);
  if (pRemoteGyroX_Characteristic == nullptr) {
    Serial.print("Failed to find our characteristic UUID: ");
    Serial.println(gyroX_UUID.toString().c_str());
    pClient->disconnect();
    return false;
  }
  Serial.println(" - Found gyro x characteristic");

  // Obtain a reference to the characteristic in the service of the remote BLE server.
  pRemoteGyroY_Characteristic = pRemoteService->getCharacteristic(gyroY_UUID);
  if (pRemoteGyroY_Characteristic == nullptr) {
    Serial.print("Failed to find our characteristic UUID: ");
    Serial.println(gyroY_UUID.toString().c_str());
    pClient->disconnect();
    return false;
  }
  Serial.println(" - Found gyro y characteristic");

  // Obtain a reference to the characteristic in the service of the remote BLE server.
  pRemoteGyroZ_Characteristic = pRemoteService->getCharacteristic(gyroZ_UUID);
  if (pRemoteGyroZ_Characteristic == nullptr) {
    Serial.print("Failed to find our characteristic UUID: ");
    Serial.println(gyroZ_UUID.toString().c_str());
    pClient->disconnect();
    return false;
  }
  Serial.println(" - Found gyro Z characteristic");
  


  // Read the temp value of the characteristic.
  if (pRemoteTempCharacteristic->canRead()) {
    tempValue = pRemoteTempCharacteristic->readUInt16();
    Serial.print("The characteristic temp value was: ");
    Serial.println(tempValue);
  }


 // Read the humidity value of the characteristic.
  if (pRemoteHumidCharacteristic->canRead()) {
    humidValue = pRemoteHumidCharacteristic->readUInt16();
    Serial.print("The characteristic humid value was: ");
    Serial.println(humidValue);
  }

   // Read the gyro value of the characteristic.
  if (pRemoteGyroX_Characteristic->canRead()) {
    gyroX_Value = pRemoteGyroX_Characteristic->readUInt16();
    Serial.print("The characteristic gyro value was: ");
    Serial.println(gyroX_Value);
  }

  if (pRemoteTempCharacteristic->canNotify()) {
    // Register/Subscribe for notifications
    pRemoteTempCharacteristic->registerForNotify(notifyCallback);
  }

 if (pRemoteHumidCharacteristic->canNotify()) {
    // Register/Subscribe for notifications
    pRemoteHumidCharacteristic->registerForNotify(notifyCallback);
  }

   if (pRemoteGyroX_Characteristic->canNotify()) {
    // Register/Subscribe for notifications
    pRemoteGyroX_Characteristic->registerForNotify(notifyCallback);
  }
 if (pRemoteGyroY_Characteristic->canNotify()) {
    // Register/Subscribe for notifications
    pRemoteGyroY_Characteristic->registerForNotify(notifyCallback);
  }
   if (pRemoteGyroZ_Characteristic->canNotify()) {
    // Register/Subscribe for notifications
    pRemoteGyroZ_Characteristic->registerForNotify(notifyCallback);
  }
  connected = true;
  return true;
}


class MyAdvertisedDeviceCallbacks : public BLEAdvertisedDeviceCallbacks {

  void onResult(BLEAdvertisedDevice advertisedDevice) {
    Serial.print("BLE Advertised Device found: ");
    Serial.println(advertisedDevice.toString().c_str());

    // We have found a device, let us now see if it contains the service we are looking for.
    if (advertisedDevice.haveServiceUUID() && advertisedDevice.isAdvertisingService(serviceUUID)) {

      BLEDevice::getScan()->stop();
      myDevice = new BLEAdvertisedDevice(advertisedDevice);
      doConnect = true;
      doScan = true;

    }  
  }  
};  

// ===============================================
// HELPER Functions
// ===============================================

void setup_wifi() {
  delay(10);
  // We start by connecting to a WiFi network
  Serial.println();
  Serial.print("Connecting to ");
  Serial.println(ssid);

  WiFi.begin(ssid, password);

  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }

  Serial.println("");
  Serial.println("WiFi connected");
  Serial.print("ESP32 IP address: ");
  Serial.println(WiFi.localIP());
  
  // Get and store MAC address
  deviceMacAddress = WiFi.macAddress();
  Serial.print("ESP32 MAC address: ");
  Serial.println(deviceMacAddress);
}

void reconnect() {
  // Loop until we're reconnected
  while (!client.connected()) {
    Serial.print("Attempting MQTT connection to OpenRemote...");
    
    // Attempt to connect with OpenRemote credentials
    if (client.connect(mqtt_client_id, mqtt_username, mqtt_password)) {
      Serial.println("connected to OpenRemote broker.");
      Serial.print("Client ID: ");
      Serial.println(mqtt_client_id);
    } else {
      Serial.print("failed, rc=");
      Serial.print(client.state());
      Serial.println(" Retrying in 5 seconds...");
      // Wait 5 seconds before retrying
      delay(5000);
    }
  }
}

// Helper function to publish to OpenRemote MQTT topic
bool publishToOpenRemote(const char* attributeName, String value) {
  // Build topic: {realm}/{clientId}/writeattributevalue/{attributeName}/{assetId}
  String topic = String(mqtt_realm) + "/" + String(mqtt_client_id) + "/writeattributevalue/" + String(attributeName) + "/" + String(asset_id);
  
  Serial.print("Publishing to topic: ");
  Serial.println(topic);
  Serial.print("Value: ");
  Serial.println(value);
  
  bool result = client.publish(topic.c_str(), value.c_str());
  
  if (result) {
    Serial.print("✓ Published ");
    Serial.print(attributeName);
    Serial.println(" successfully");
  } else {
    Serial.print("✗ Failed to publish ");
    Serial.println(attributeName);
  }
  
  return result;
}

// Generate realistic dummy sensor data
void generateDummyData() {
  // Temperature: Simulate 18-28°C (in hundredths)
  // Creates a smooth sine wave + small random variations
  static float tempBase = 23.0;
  tempBase += (random(-50, 50) / 100.0); // Small random walk
  if (tempBase < 18.0) tempBase = 18.0;
  if (tempBase > 28.0) tempBase = 28.0;
  tempValue = (uint16_t)(tempBase * 100);
  
  // Humidity: Simulate 40-80% (in hundredths)
  static float humidBase = 60.0;
  humidBase += (random(-100, 100) / 100.0);
  if (humidBase < 40.0) humidBase = 40.0;
  if (humidBase > 80.0) humidBase = 80.0;
  humidValue = (uint16_t)(humidBase * 100);
  
  // Gyroscope: Simulate movement with occasional spikes
  // Normal values around 0, with occasional movements
  static int gyroXBase = 0;
  static int gyroYBase = 0;
  static int gyroZBase = 0;
  
  // Simulate occasional movement (20% chance of bigger change)
  if (random(0, 100) < 20) {
    gyroXBase = random(-500, 500);
    gyroYBase = random(-500, 500);
    gyroZBase = random(-500, 500);
  } else {
    // Small drift
    gyroXBase += random(-50, 50);
    gyroYBase += random(-50, 50);
    gyroZBase += random(-50, 50);
  }
  
  // Clamp values
  gyroXBase = constrain(gyroXBase, -1000, 1000);
  gyroYBase = constrain(gyroYBase, -1000, 1000);
  gyroZBase = constrain(gyroZBase, -1000, 1000);
  
  gyroX_Value = (uint16_t)abs(gyroXBase);
  gyroY_Value = (uint16_t)abs(gyroYBase);
  gyroZ_Value = (uint16_t)abs(gyroZBase);
}

void setup() {
  Serial.begin(115200);
  Serial.println("\n\n========================================");
  Serial.println("  ESP32 OpenRemote MQTT Client");
  Serial.println("========================================");
  
  if (USE_DUMMY_DATA) {
    Serial.println("MODE: Dummy Data (Testing)");
    Serial.println("To use real BLE data, set USE_DUMMY_DATA = false");
  } else {
    Serial.println("MODE: Real BLE Data");
  }
  Serial.println("========================================\n");

  // Initialize random seed for dummy values
  randomSeed(analogRead(0));

  setup_wifi();
  client.setServer(mqtt_server, mqtt_port);

  // Only initialize BLE if not using dummy data
  if (!USE_DUMMY_DATA) {
    BLEDevice::init("");

    // Retrieve a Scanner and set the callback we want to use to be informed when we
    // have detected a new device.  Specify that we want active scanning and start the
    // scan to run for 5 seconds.
    BLEScan *pBLEScan = BLEDevice::getScan();
    pBLEScan->setAdvertisedDeviceCallbacks(new MyAdvertisedDeviceCallbacks());
    pBLEScan->setInterval(1349);
    pBLEScan->setWindow(449);
    pBLEScan->setActiveScan(true);
    pBLEScan->start(5, false);
  } else {
    Serial.println("BLE scanning disabled (using dummy data)");
  }
}  


void loop() {

  // Handle BLE connection (only if not using dummy data)
  if (!USE_DUMMY_DATA) {
    if (doConnect == true) {
      if (connectToServer()) {
        Serial.println("We are now connected to the BLE Server.");
      } else {
        Serial.println("We have failed to connect to the server; there is nothing more we will do.");
      }
      doConnect = false;
    }
  }

  // Publish JSON to MQTT
  if (!client.connected()) {
    reconnect();
  }
  client.loop(); // Required to maintain the connection and process incoming/outgoing messages

  // Decide whether to use dummy data or real BLE data
  bool shouldPublish = false;
  
  if (USE_DUMMY_DATA) {
    // Using dummy data mode
    Serial.println("\n[DUMMY DATA MODE]");
    generateDummyData();
    shouldPublish = true;
    
  } else if (connected) {
    // Using real BLE data
    Serial.println("\n[REAL BLE DATA MODE]");
    // Read current values from BLE characteristics
    if (pRemoteTempCharacteristic->canRead()) {
      tempValue = pRemoteTempCharacteristic->readUInt16();
    }
    if (pRemoteHumidCharacteristic->canRead()) {
      humidValue = pRemoteHumidCharacteristic->readUInt16();
    }
    if (pRemoteGyroX_Characteristic->canRead()) {
      gyroX_Value = pRemoteGyroX_Characteristic->readUInt16();
    }
    if (pRemoteGyroY_Characteristic->canRead()) {
      gyroY_Value = pRemoteGyroY_Characteristic->readUInt16();
    }
    if (pRemoteGyroZ_Characteristic->canRead()) {
      gyroZ_Value = pRemoteGyroZ_Characteristic->readUInt16();
    }
    shouldPublish = true;
    
  } else if (doScan && !USE_DUMMY_DATA) {
    BLEDevice::getScan()->start(0);
  }
  
  // Publish data to OpenRemote
  if (shouldPublish) {
    Serial.println("\n========================================");
    Serial.println("Publishing sensor data to OpenRemote...");
    Serial.println("========================================");
    Serial.println("Sensor values:");
    Serial.println("  Temperature: " + String(tempValue / 100.0) + " °C");
    Serial.println("  Humidity: " + String(humidValue / 100.0) + " %");
    Serial.println("  Gyro X: " + String(gyroX_Value));
    Serial.println("  Gyro Y: " + String(gyroY_Value));
    Serial.println("  Gyro Z: " + String(gyroZ_Value));
    Serial.println("----------------------------------------");
    
    // Publish each attribute separately to OpenRemote
    // Note: OpenRemote MQTT requires one attribute per message
    
    // 1. Publish Temperature
    publishToOpenRemote("temperature", String(tempValue));
    delay(100); // Small delay between publishes
    
    // 2. Publish Humidity
    publishToOpenRemote("humidity", String(humidValue));
    delay(100);
    
    // 3. Publish Gyro as JSON object (GyroData type in OpenRemote)
    String gyroJson = "{";
    gyroJson += "\"x\":" + String(gyroX_Value) + ",";
    gyroJson += "\"y\":" + String(gyroY_Value) + ",";
    gyroJson += "\"z\":" + String(gyroZ_Value);
    gyroJson += "}";
    publishToOpenRemote("gyro", gyroJson);
    delay(100);
    
    // 4. Optional: Publish signal strength (WiFi RSSI)
    int rssi = WiFi.RSSI();
    publishToOpenRemote("signalStrength", String(rssi));
    
    Serial.println("========================================");
    Serial.println("All attributes published!\n");
  }

  // Wait before next publish cycle
  if (USE_DUMMY_DATA) {
    delay(DUMMY_DATA_INTERVAL_MS);  // Use dummy data interval
  } else {
    delay(REAL_DATA_INTERVAL_MS);   // Use real data interval
  }
}  
