
// Not neccesary in Arduino IDE
// #include <Arduino.h>
#include "BLEDevice.h"
#include <WiFi.h>
#include <PubSubClient.h>
#include <ArduinoJson.h>

// --- WiFi Credentials ---
const char *ssid = "WiFii";
const char *password = "12345678";

// --- MQTT Broker Details (Your Raspberry Pi 4) ---
const char *mqtt_server = "10.56.150.70";
const int mqtt_port = 1883;

// --- OpenRemote MQTT Configuration ---
const char* mqtt_username = "master:mqtt";  // Format: {realm}:{service-username}
const char* mqtt_password = "Dfoxm0dCGLQKgTQMpHckbVrFAsXc5Q9P";  // Your service user secret
const char* mqtt_realm = "master";
const char* mqtt_client_id = "esp32-beacon-001";  // Unique client ID for this device

// --- BEACON Asset ID (THIS beacon's own asset in OpenRemote) ---
// const char* beacon_asset_id = "4FI96rotb2ZDdoIzbTgFy5";  // Replace with your beacon's asset ID
// const char* beacon_asset_id = "5agtMa7iX8HfFnP5lkI30n";  // Replace with your beacon's asset ID
const char* beacon_asset_id = "3w98uTo5uUPD1574Z0dEem";  // Replace with your beacon's asset ID

// --- Asset Pool Configuration ---
// List of available TAG asset IDs in OpenRemote
const char* available_assets[] = {
  "76n5e5FQbJVWRPixedTcTp",
  "4sMvO1TCOlTQ7971byMtHX"
};
const int MAX_ASSETS = 2;  // Number of available assets

// MAC Address to Asset ID mapping
struct TagMapping {
  String macAddress;
  String assetId;
  bool isUsed;
};

TagMapping tagMappings[10];  // Support up to 10 tags
int mappingCount = 0;

String currentAssetId = "";  // Current asset ID being used

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
static boolean messageSent = false;
static boolean isScanning = false;  // Prevent scan callback race conditions

static BLERemoteCharacteristic *pRemoteTempCharacteristic;
static BLERemoteCharacteristic *pRemoteHumidCharacteristic;
static BLERemoteCharacteristic *pRemoteGyroX_Characteristic;
static BLERemoteCharacteristic *pRemoteGyroY_Characteristic;
static BLERemoteCharacteristic *pRemoteGyroZ_Characteristic;

static BLEAdvertisedDevice *myDevice;
BLEClient *pClient = nullptr;  //NEW

// Sensor value storage
uint16_t tempValue = 0;
uint16_t humidValue = 0;
uint16_t gyroX_Value = 0;
uint16_t gyroY_Value = 0;
uint16_t gyroZ_Value = 0;
int rssiValue = 0;

// MAC address storage
String deviceMacAddress = "";  // This will store the BLE tag's MAC address
String esp32MacAddress = "";   // ESP32's own WiFi MAC (for reference)

// ===============================================
// BLE Client Functions
// ===============================================

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
    uint16_t value = (pData[1] << 8) | pData[0];  // Little-endian format

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
  void onConnect(BLEClient *pclient) {
    // rssiValue = pclient->getRssi();
  }

  void onDisconnect(BLEClient *pclient) {
    connected = false;
    Serial.println("onDisconnect");
  }
};

bool connectToServer() {
  if (!myDevice) {
    Serial.println("connectToServer(): no device set");
    return false;
  }

  // Get the BLE tag's MAC address
  deviceMacAddress = myDevice->getAddress().toString().c_str();
  deviceMacAddress.toUpperCase();  // Normalize to uppercase
  
  Serial.print("Connecting to BLE Tag: ");
  Serial.println(deviceMacAddress);

  // Clean up any existing client
  if (pClient) {
    delete pClient;
    pClient = nullptr;
  }

  // Small delay before creating new client
  delay(100);
  
  pClient = BLEDevice::createClient();
  Serial.println(" - Created client");

  pClient->setClientCallbacks(new MyClientCallback());


  pClient->connect(myDevice);
  Serial.println(" - Connected to server");
  pClient->setMTU(517);

  // Obtain a reference to the service we are after in the remote BLE server.
  BLERemoteService *pRemoteService = pClient->getService(serviceUUID);
  if (pRemoteService == nullptr) {
    Serial.println("Wrong device - doesn't have our service");
    if (pClient) {
      pClient->disconnect();
      delay(100);
      delete pClient;
      pClient = nullptr;
    }
    return false;
  }
  Serial.println(" - Found our service");

  // Obtain a reference to the characteristic in the service of the remote BLE server.
  pRemoteTempCharacteristic = pRemoteService->getCharacteristic(tempUUID);
  if (pRemoteTempCharacteristic == nullptr) {
    Serial.println("Missing temperature characteristic");
    if (pClient) {
      pClient->disconnect();
      delay(100);
      delete pClient;
      pClient = nullptr;
    }
    return false;
  }
  Serial.println(" - Found temp characteristic");

  // Obtain a reference to the characteristic in the service of the remote BLE server.
  pRemoteHumidCharacteristic = pRemoteService->getCharacteristic(humidUUID);
  if (pRemoteHumidCharacteristic == nullptr) {
    Serial.println("Missing humidity characteristic");
    if (pClient) {
      pClient->disconnect();
      delay(100);
      delete pClient;
      pClient = nullptr;
    }
    return false;
  }
  Serial.println(" - Found humid characteristic");


  // Obtain a reference to the characteristic in the service of the remote BLE server.
  pRemoteGyroX_Characteristic = pRemoteService->getCharacteristic(gyroX_UUID);
  if (pRemoteGyroX_Characteristic == nullptr) {
    Serial.println("Missing gyro X characteristic");
    if (pClient) {
      pClient->disconnect();
      delay(100);
      delete pClient;
      pClient = nullptr;
    }
    return false;
  }
  Serial.println(" - Found gyro x characteristic");

  // Obtain a reference to the characteristic in the service of the remote BLE server.
  pRemoteGyroY_Characteristic = pRemoteService->getCharacteristic(gyroY_UUID);
  if (pRemoteGyroY_Characteristic == nullptr) {
    Serial.println("Missing gyro Y characteristic");
    if (pClient) {
      pClient->disconnect();
      delay(100);
      delete pClient;
      pClient = nullptr;
    }
    return false;
  }
  Serial.println(" - Found gyro y characteristic");

  // Obtain a reference to the characteristic in the service of the remote BLE server.
  pRemoteGyroZ_Characteristic = pRemoteService->getCharacteristic(gyroZ_UUID);
  if (pRemoteGyroZ_Characteristic == nullptr) {
    Serial.println("Missing gyro Z characteristic");
    if (pClient) {
      pClient->disconnect();
      delay(100);
      delete pClient;
      pClient = nullptr;
    }
    return false;
  }
  Serial.println(" - Found gyro Z characteristic");

  // Read the temp value of the characteristic.
  // Replaced
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
    Serial.print("The characteristic gyro x value was: ");
    Serial.println(gyroX_Value);
  }

  // Read the gyro value of the characteristic.
  if (pRemoteGyroY_Characteristic->canRead()) {
    gyroY_Value = pRemoteGyroY_Characteristic->readUInt16();
    Serial.print("The characteristic gyro y value was: ");
    Serial.println(gyroY_Value);
  }

  // Read the gyro value of the characteristic.
  if (pRemoteGyroZ_Characteristic->canRead()) {
    gyroZ_Value = pRemoteGyroZ_Characteristic->readUInt16();
    Serial.print("The characteristic gyro z value was: ");
    Serial.println(gyroZ_Value);
  }

  if (pRemoteTempCharacteristic->canNotify()) {
    Serial.println(" - register temp notify");
    pRemoteTempCharacteristic->registerForNotify(notifyCallback);
  } else {
    Serial.println(" - temp cannot notify");
  }

  if (pRemoteHumidCharacteristic->canNotify()) {
    Serial.println(" - register humid notify");
    pRemoteHumidCharacteristic->registerForNotify(notifyCallback);
  } else {
    Serial.println(" - humid cannot notify");
  }

  if (pRemoteGyroX_Characteristic->canNotify()) {
    Serial.println(" - register gyroX notify");
    pRemoteGyroX_Characteristic->registerForNotify(notifyCallback);
  } else {
    Serial.println(" - gyroX cannot notify");
  }

  if (pRemoteGyroY_Characteristic->canNotify()) {
    Serial.println(" - register gyroY notify");
    pRemoteGyroY_Characteristic->registerForNotify(notifyCallback);
  } else {
    Serial.println(" - gyroY cannot notify");
  }

  if (pRemoteGyroZ_Characteristic->canNotify()) {
    Serial.println(" - register gyroZ notify");
    pRemoteGyroZ_Characteristic->registerForNotify(notifyCallback);
  } else {
    Serial.println(" - gyroZ cannot notify");
  }
  connected = true;
  return true;
}

void disconnectFromServer() {
  if (pClient != nullptr) {
    Serial.println("Forcing BLE disconnect...");
    
    // Ensure scan is stopped
    BLEDevice::getScan()->stop();
    isScanning = false;
    
    if (connected) {
      pClient->disconnect();
    }
    // Small delay to ensure disconnect completes
    delay(200);
    
    // Clean up client
    delete pClient;
    pClient = nullptr;
    
    connected = false;
    doConnect = false;
    messageSent = false;
    
    // Clear scan results to prevent memory buildup
    BLEScan* pBLEScan = BLEDevice::getScan();
    if (pBLEScan != nullptr) {
      pBLEScan->clearResults();
    }
    
    doScan = true;  // scan for reconnection
  }
}

class MyAdvertisedDeviceCallbacks : public BLEAdvertisedDeviceCallbacks {

  void onResult(BLEAdvertisedDevice advertisedDevice) {
    // Prevent processing if already connecting or connected
    if (doConnect || connected) {
      return;
    }
    
    // Check if it contains the service we are looking for FIRST (before any printing)
    if (advertisedDevice.haveServiceUUID() && advertisedDevice.isAdvertisingService(serviceUUID)) {
      
      // Only print minimal info to avoid memory issues
      Serial.print("Target device found: ");
      Serial.println(advertisedDevice.getAddress().toString().c_str());
      
      // Stop scan immediately
      BLEDevice::getScan()->stop();
      isScanning = false;
      
      // Clean up previous device
      if (myDevice) {
        delete myDevice;
        myDevice = nullptr;
      }
      
      // Store new device
      myDevice = new BLEAdvertisedDevice(advertisedDevice);
      doConnect = true;
      doScan = false;
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

  // Get and store ESP32's WiFi MAC address (for reference only)
  esp32MacAddress = WiFi.macAddress();
  Serial.print("ESP32 WiFi MAC address: ");
  Serial.println(esp32MacAddress);
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

// Get or assign asset ID for a MAC address
String getAssetIdForMac(String macAddress) {
  // Check if this MAC already has an assigned asset
  for (int i = 0; i < mappingCount; i++) {
    if (tagMappings[i].macAddress == macAddress) {
      Serial.println("Found existing mapping for MAC: " + macAddress + " -> Asset: " + tagMappings[i].assetId);
      return tagMappings[i].assetId;
    }
  }
  
  // This is a new MAC address, assign a free asset
  // First, get list of used assets
  bool assetUsed[MAX_ASSETS];
  for (int i = 0; i < MAX_ASSETS; i++) {
    assetUsed[i] = false;
  }
  
  // Mark assets that are already in use
  for (int i = 0; i < mappingCount; i++) {
    for (int j = 0; j < MAX_ASSETS; j++) {
      if (tagMappings[i].assetId == String(available_assets[j])) {
        assetUsed[j] = true;
        break;
      }
    }
  }
  
  // Find first free asset
  for (int i = 0; i < MAX_ASSETS; i++) {
    if (!assetUsed[i]) {
      // Found a free asset, assign it
      if (mappingCount < 10) {  // Check array bounds
        tagMappings[mappingCount].macAddress = macAddress;
        tagMappings[mappingCount].assetId = String(available_assets[i]);
        tagMappings[mappingCount].isUsed = true;
        mappingCount++;
        
        Serial.println("NEW TAG ASSIGNED:");
        Serial.println("  MAC: " + macAddress);
        Serial.println("  Asset ID: " + String(available_assets[i]));
        Serial.println("  Total tags mapped: " + String(mappingCount));
        
        return String(available_assets[i]);
      }
    }
  }
  
  // No free assets available
  Serial.println("WARNING: No free assets available for MAC: " + macAddress);
  return "";  // Return empty string if no assets available
}

// Publish batched tagDetections to beacon asset (V5 BATCHED VERSION)
bool publishTagDetections(String macAddress, String assetId, int rssi, 
                          uint16_t temp, uint16_t humid, 
                          uint16_t gx, uint16_t gy, uint16_t gz) {
  
  // Build JSON for tagDetections attribute
  // Format: {"MAC_ADDRESS": {"assetId": "...", "rssi": -65, "temperature": 2350, ...}}
  DynamicJsonDocument doc(2048);
  JsonObject root = doc.to<JsonObject>();
  
  // Create nested object for this tag
  JsonObject tagObj = root.createNestedObject(macAddress);
  tagObj["assetId"] = assetId;
  tagObj["rssi"] = rssi;
  tagObj["temperature"] = temp;
  tagObj["humidity"] = humid;
  
  JsonObject gyroObj = tagObj.createNestedObject("gyro");
  gyroObj["x"] = gx;
  gyroObj["y"] = gy;
  gyroObj["z"] = gz;
  
  tagObj["timestamp"] = millis();
  
  // Serialize to string
  String jsonPayload;
  serializeJson(root, jsonPayload);
  
  // Build topic: {realm}/{clientId}/writeattributevalue/tagDetections/{beaconAssetId}
  String topic = String(mqtt_realm) + "/" + String(mqtt_client_id) + 
                 "/writeattributevalue/tagDetections/" + String(beacon_asset_id);
  
  Serial.println("\n========================================");
  Serial.println("Publishing batched tagDetections to Beacon Asset");
  Serial.println("========================================");
  Serial.println("Beacon Asset ID: " + String(beacon_asset_id));
  Serial.println("Topic: " + topic);
  Serial.println("Payload: " + jsonPayload);
  Serial.println("========================================");
  
  bool result = client.publish(topic.c_str(), jsonPayload.c_str());
  
  if (result) {
    Serial.println("✓ Published tagDetections successfully");
  } else {
    Serial.println("✗ Failed to publish tagDetections");
  }
  
  return result;
}

void setup() {
  Serial.begin(115200);
  Serial.println("\n\n========================================");
  Serial.println("  ESP32 OpenRemote MQTT Client");
  Serial.println("  BLE Beacon Scanner v5 (Batched)");
  Serial.println("  Publishes to Beacon Asset");
  Serial.println("========================================");
  Serial.println("Beacon Asset ID: " + String(beacon_asset_id));
  Serial.println("Available Tag Assets: " + String(MAX_ASSETS));
  for (int i = 0; i < MAX_ASSETS; i++) {
    Serial.println("  " + String(i+1) + ". " + String(available_assets[i]));
  }
  Serial.println("========================================\n");

  // Initialize tag mappings
  for (int i = 0; i < 10; i++) {
    tagMappings[i].macAddress = "";
    tagMappings[i].assetId = "";
    tagMappings[i].isUsed = false;
  }
  mappingCount = 0;

  // Initialize random seed
  randomSeed(analogRead(0));

  setup_wifi();
  client.setServer(mqtt_server, mqtt_port);

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
  
  Serial.println("BLE scanning started...");
}


void loop() {

  // Handle BLE connection
  if (doConnect == true) {
    if (connectToServer()) {
      Serial.println("Successfully connected to BLE Tag: " + deviceMacAddress);
    } else {
      Serial.println("Connection failed - scanning for next tag...");
      
      // Clear the MAC address from failed connection
      deviceMacAddress = "";
      
      // Clean up failed connection attempt
      if (pClient != nullptr) {
        delete pClient;
        pClient = nullptr;
      }
      
      // Clear the device that didn't work
      if (myDevice) {
        delete myDevice;
        myDevice = nullptr;
      }
      
      // Clear scan results
      BLEScan* pBLEScan = BLEDevice::getScan();
      if (pBLEScan != nullptr) {
        pBLEScan->clearResults();
      }
      
      // Immediately restart scanning
      connected = false;
      messageSent = false;
      isScanning = false;
      doScan = true;
    }
    doConnect = false;
  }
  
  // Maintain MQTT connection
  if (!client.connected()) {
    reconnect();
  }
  client.loop();  // Required to maintain the connection and process incoming/outgoing messages

  if (connected && !messageSent) {
    // Get or assign asset ID for this tag's MAC address
    currentAssetId = getAssetIdForMac(deviceMacAddress);
    
    if (currentAssetId == "") {
      Serial.println("ERROR: No available assets for this tag. Skipping...");
      messageSent = true;
      return;
    }
    
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

    Serial.println("\n========================================");
    Serial.println("Preparing sensor data for publishing...");
    Serial.println("========================================");
    Serial.println("Tag Info:");
    Serial.println("  MAC Address: " + deviceMacAddress);
    Serial.println("  Asset ID: " + currentAssetId);
    Serial.println("Sensor values:");
    Serial.println("  Temperature: " + String(tempValue / 100.0) + " °C");
    Serial.println("  Humidity: " + String(humidValue / 100.0) + " %");
    Serial.println("  Gyro X: " + String(gyroX_Value));
    Serial.println("  Gyro Y: " + String(gyroY_Value));
    Serial.println("  Gyro Z: " + String(gyroZ_Value));
    Serial.println("  RSSI: " + String(rssiValue) + " dBm");
    Serial.println("----------------------------------------");
    
    // Publish batched detection to beacon's tagDetections attribute
    publishTagDetections(deviceMacAddress, currentAssetId, rssiValue,
                        tempValue, humidValue, 
                        gyroX_Value, gyroY_Value, gyroZ_Value);
    
    Serial.println("========================================");
    Serial.println("TAG: " + deviceMacAddress + " -> ASSET: " + currentAssetId);
    Serial.println("========================================\n");
    
    messageSent = true;
    currentAssetId = "";  // Clear current asset ID
    
  } else if (doScan && !isScanning && !connected) {
    Serial.println("Starting BLE scan...");
    
    // Clear previous scan results to prevent memory buildup
    BLEScan* pBLEScan = BLEDevice::getScan();
    if (pBLEScan != nullptr) {
      pBLEScan->clearResults();
      delay(100); // Small delay before starting scan
      
      // Start scanning (0 = continuous, false = not a duplicate filter)
      pBLEScan->start(0, false); 
      isScanning = true;
      Serial.println("Scan started - looking for tags...");
    }
    doScan = false; 
  }

  // Disconnect and reconnect cycle after message sent
  if (messageSent && connected) {
    Serial.println("Disconnecting from BLE Tag: " + deviceMacAddress);
    deviceMacAddress = "";  // Clear MAC address after disconnect
    disconnectFromServer();
    delay(2000); // Longer wait before scanning again to ensure cleanup
  }

  delay(100);
}


