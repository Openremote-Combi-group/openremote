# Indoor Tracking System - Quick Reference

## Quick Start Commands

### Build OpenRemote
```bash
cd /home/damian/Documents/School\ Projects/Semester\ 3/Group\ Project/openremote
./gradlew clean build
```

### Start OpenRemote
```bash
docker-compose up -d
```

### Monitor Logs
```bash
docker logs -f openremote_manager_1 | grep -i "indoor\|trilateration"
```

### Monitor MQTT
```bash
mosquitto_sub -h localhost -t 'master/#' -v
```

### Test MQTT Connection
```bash
mosquitto_pub -h localhost -t 'test/topic' -m 'test'
mosquitto_sub -h localhost -t 'test/topic'
```

## Asset Configuration Quick Reference

### IndoorTrackingGroup Attributes
```json
{
  "referencePoint": {"type": "Point", "coordinates": [-74.0, 40.0]},
  "metersPerDegreeLat": 111320,
  "metersPerDegreeLng": 85000,
  "minBeaconsForTrilateration": 3,
  "referenceRssi": -45.0,
  "pathLossExponent": 2.0
}
```

### IndoorBeacon Attributes
```json
{
  "locationMeters": {"x": 0, "y": 0, "z": 2.0},
  "location": {"type": "Point", "coordinates": [-74.0, 40.0]},
  "detectionRadius": 10,
  "tagDetections": {}
}
```

### IndoorTag Attributes
```json
{
  "macAddress": "AA:BB:CC:DD:EE:01",
  "temperature": 23.0,
  "humidity": 60.0,
  "gyro": {"x": 0, "y": 0, "z": 0},
  "location": {"type": "Point", "coordinates": [-74.0, 40.0]},
  "locationMeters": {"x": 5.0, "y": 5.0, "z": 1.5},
  "beaconCount": 3
}
```

## Python Emulator Quick Start

```bash
# Terminal 1 - Beacon 1
python3 mqtt_beacon_emulator.py
# Server: localhost, Port: 1883
# Beacon ID: test-beacon-001
# Beacon Asset ID: <from OpenRemote>
# Location: 0, 0, 2.0
# Tags: 1, MAC: AA:BB:CC:DD:EE:01, Asset ID: <from OpenRemote>

# Terminal 2 - Beacon 2
python3 mqtt_beacon_emulator.py
# Same config except: Beacon ID: test-beacon-002, Location: 15, 0, 2.0

# Terminal 3 - Beacon 3
python3 mqtt_beacon_emulator.py
# Same config except: Beacon ID: test-beacon-003, Location: 7.5, 15, 2.0
```

## ESP32 Configuration Template

```cpp
// WiFi
const char *ssid = "YOUR_WIFI_SSID";
const char *password = "YOUR_WIFI_PASSWORD";

// MQTT
const char *mqtt_server = "YOUR_OPENREMOTE_IP";
const char* mqtt_username = "master:mqtt";
const char* mqtt_password = "YOUR_SERVICE_USER_SECRET";
const char* mqtt_client_id = "esp32-beacon-001";  // Unique per beacon

// Asset IDs (from OpenRemote UI)
const char* beacon_asset_id = "YOUR_BEACON_ASSET_ID";
const char* available_tag_assets[] = {
  "TAG_ASSET_ID_1",
  "TAG_ASSET_ID_2"
};
```

## MQTT Topic Structure

### Beacon Publishes
```
Topic: master/beacon-id/writeattributevalue/tagDetections/BEACON_ASSET_ID
Payload: {
  "MAC_ADDRESS": {
    "assetId": "TAG_ASSET_ID",
    "rssi": -65,
    "temperature": 2350,
    "humidity": 6050,
    "gyro": {"x": 100, "y": 150, "z": 120},
    "timestamp": 1703012345678
  }
}
```

## Groovy Rules

### Rule 1: SensorForwarding
- **Trigger**: Beacon.tagDetections changes
- **Action**: Forward temp, humid, gyro, lastSeen to Tag assets
- **Location**: `setup/src/demo/resources/demo/rules/indoor_tracking/SensorForwarding.groovy`

### Rule 2: TrilaterationEngine
- **Trigger**: Beacon.tagDetections changes
- **Action**: Calculate tag position using trilateration
- **Requirements**: 3+ beacons detect same tag
- **Location**: `setup/src/demo/resources/demo/rules/indoor_tracking/TrilaterationEngine.groovy`

### Rule 3: TrilaterationUtils (Helper)
- **Purpose**: Utility functions
- **Functions**: rssiToDistance, calculatePosition, metersToLatLng
- **Location**: `setup/src/demo/resources/demo/rules/indoor_tracking/TrilaterationUtils.groovy`

## Key Formulas

### RSSI to Distance
```
distance = 10 ^ ((referenceRssi - actualRssi) / (10 * pathLossExponent))
```

### Meters to GPS
```
lat = refLat + (roomY / metersPerDegreeLat)
lng = refLng + (roomX / metersPerDegreeLng)
```

### Meters per Degree Longitude
```
metersPerDegreeLng = 111320 * cos(latitude * PI / 180)
```

## Typical Values

### RSSI Values
- **-35 to -50 dBm**: Very close (< 1m)
- **-50 to -70 dBm**: Close (1-5m)
- **-70 to -85 dBm**: Medium (5-15m)
- **-85 to -100 dBm**: Far (> 15m)

### Path Loss Exponent
- **2.0**: Free space / line of sight
- **2.5**: Office / light indoor
- **3.0**: Indoor with obstacles
- **3.5**: Dense indoor / many walls

### Beacon Positions (15m x 15m room)
- **Beacon 1**: (0, 0, 2.0) - Corner
- **Beacon 2**: (15, 0, 2.0) - Opposite corner
- **Beacon 3**: (7.5, 15, 2.0) - Middle of far wall
- **Optional Beacon 4**: (0, 15, 2.0) - Fourth corner

## Troubleshooting

### No Position Calculated
```bash
# Check beacon count
# Tag.beaconCount should be >= 3

# Check logs
docker logs -f openremote_manager_1 | grep "Tag.*detected by only"

# Verify beacon locations
# All beacons should have locationMeters set
```

### Inaccurate Positions
```bash
# Increase path loss exponent
# In IndoorTrackingGroup: pathLossExponent = 2.5 or 3.0

# Check beacon positions
# Verify physical positions match locationMeters in OpenRemote
```

### Rules Not Firing
```bash
# Check rule scope
# Should be "Global" or on IndoorTrackingGroup asset

# Check logs for errors
docker logs openremote_manager_1 | grep -i error | grep -i rule
```

### MQTT Not Connected
```bash
# Test broker
mosquitto_sub -h localhost -t 'test' -v

# Check service user
# Verify username: "master:mqtt" and correct password

# Check firewall
# Port 1883 should be open
```

## File Locations

### Asset Models
```
model/src/main/java/org/openremote/model/asset/impl/
├── IndoorBeacon.java
├── IndoorTag.java
└── IndoorTrackingGroup.java
```

### Value Types
```
model/src/main/java/org/openremote/model/value/impl/
├── LocationMeters.java
├── TagDetection.java
└── GyroData.java (already existed)
```

### Groovy Rules
```
setup/src/demo/resources/demo/rules/indoor_tracking/
├── SensorForwarding.groovy
├── TrilaterationEngine.groovy
└── TrilaterationUtils.groovy
```

### ESP32 Code
```
beacon_v5_batched.ino (new, use this)
beacon_v4.ino (deprecated)
```

### Python Emulator
```
mqtt_beacon_emulator.py (updated)
```

### Documentation
```
INDOOR_TRACKING_SETUP.md (full setup guide)
TESTING_GUIDE.md (testing procedures)
IMPLEMENTATION_SUMMARY.md (architecture & implementation)
QUICK_REFERENCE.md (this file)
```

## Common Tasks

### Add New Beacon
1. Create IndoorBeacon asset in OpenRemote
2. Set locationMeters to physical position
3. Flash ESP32 with code
4. Update mqtt_client_id and beacon_asset_id
5. Power on and verify MQTT connection

### Add New Tag
1. Create IndoorTag asset in OpenRemote
2. Set macAddress to BLE tag's MAC
3. Add tag asset ID to beacon code/emulator
4. Tag will appear once 3+ beacons detect it

### Change Room Layout
1. Measure new beacon positions
2. Update locationMeters in Beacon assets
3. Update metersPerDegreeLng if latitude changed
4. Restart ESP32 beacons (no code change needed)

### Calibrate for Environment
1. Place tag at known positions (measure)
2. Compare calculated vs actual position
3. Adjust pathLossExponent:
   - Too close? Increase exponent
   - Too far? Decrease exponent
4. Repeat until accuracy acceptable

## Performance Targets

- **Latency**: < 500ms beacon to map display
- **Accuracy**: ±1-2 meters (office environment)
- **Update Rate**: 0.5-2 seconds per beacon
- **Beacon Count**: 3-10 beacons per area
- **Tag Count**: Up to 50 tags per system
- **Coverage**: Up to 500m² per deployment

## Support

### Documentation
- Full Setup: [INDOOR_TRACKING_SETUP.md](INDOOR_TRACKING_SETUP.md)
- Testing: [TESTING_GUIDE.md](TESTING_GUIDE.md)
- Summary: [IMPLEMENTATION_SUMMARY.md](IMPLEMENTATION_SUMMARY.md)

### OpenRemote
- Docs: https://docs.openremote.io
- Forum: https://forum.openremote.io
- GitHub: https://github.com/openremote/openremote

### Project Files
- Asset models: `model/src/main/java/.../impl/`
- Rules: `setup/src/demo/resources/demo/rules/indoor_tracking/`
- ESP32: `beacon_v5_batched.ino`
- Emulator: `mqtt_beacon_emulator.py`



