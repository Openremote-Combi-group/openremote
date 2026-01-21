# Indoor Tracking System Setup Guide

This guide explains how to set up and use the indoor tracking system in OpenRemote.

## Overview

The indoor tracking system uses ESP32 beacons to detect BLE tags via signal strength (RSSI), publishes data to OpenRemote via MQTT, calculates tag positions using trilateration, and visualizes everything on a map.

## Architecture

```
Tag (BLE) -> Beacon (ESP32) -> MQTT -> OpenRemote -> Groovy Rules -> Map Visualization
```

## Step 1: Compile and Deploy Asset Models

The following asset types have been created:

1. **IndoorBeacon** - Represents a physical beacon (ESP32 board)

   - Attributes: location, locationMeters, tagDetections, detectionRadius

2. **IndoorTag** - Represents a movable tag

   - Attributes: macAddress, temperature, humidity, gyro, signalStrength, location, locationMeters, lastSeen, beaconCount

3. **IndoorTrackingGroup** - Parent group for beacons and tags
   - Attributes: referencePoint, metersPerDegreeLat, metersPerDegreeLng, minBeaconsForTrilateration, etc.

### Compile the project

```bash
cd /home/damian/Documents/School\ Projects/Semester\ 3/Group\ Project/openremote
./gradlew clean build
```

## Step 2: Create Assets in OpenRemote UI

1. **Create IndoorTrackingGroup asset:**

   - Name: "Indoor Tracking System"
   - Type: IndoorTrackingGroup
   - Configure attributes:
     - `referencePoint`: Set to your building's GPS coordinates (or use a default like `[40.0, -74.0]`)
     - `metersPerDegreeLat`: 111320.0
     - `metersPerDegreeLng`: Calculate as `111320 * cos(latitude * PI / 180)` or use 111320 as default
     - `minBeaconsForTrilateration`: 3
     - `referenceRssi`: -45.0
     - `pathLossExponent`: 2.0 (adjust to 2.5-3.5 for indoor with obstacles)

2. **Create IndoorBeacon assets (minimum 3):**

   - Name: "Beacon 1", "Beacon 2", "Beacon 3"
   - Type: IndoorBeacon
   - Parent: Indoor Tracking System
   - Configure for each beacon:
     - `location`: Fake GPS coordinates calculated from your reference point
     - `locationMeters`: Physical position in room coordinates (e.g., `{x: 0, y: 0, z: 2.0}` for beacon 1)
     - `detectionRadius`: 10.0 (meters)

   Example beacon positions for a 15m x 15m room:

   - Beacon 1: `{x: 0, y: 0, z: 2.0}` (corner)
   - Beacon 2: `{x: 15, y: 0, z: 2.0}` (opposite corner)
   - Beacon 3: `{x: 7.5, y: 15, z: 2.0}` (middle of far wall)

3. **Create IndoorTag assets:**
   - Name: "Tag 1", "Tag 2", etc.
   - Type: IndoorTag
   - Parent: Indoor Tracking System
   - Configure:
     - `macAddress`: BLE MAC address of the tag (e.g., "AA:BB:CC:DD:EE:01")

## Step 3: Deploy Groovy Rules

The following rules have been created in `setup/src/demo/resources/demo/rules/indoor_tracking/`:

1. **SensorForwarding.groovy** - Forwards sensor data from Beacon's tagDetections to Tag assets
2. **TrilaterationUtils.groovy** - Utility functions for RSSI-to-distance and coordinate conversion
3. **TrilaterationEngine.groovy** - Calculates tag positions using trilateration

### Deploy rules:

Copy the Groovy files to OpenRemote's rules directory or create them via the OpenRemote UI:

- Go to Rules → Create Rule → Groovy
- Copy the content from each .groovy file
- Name them appropriately
- Set scope to "Global" or to the Indoor Tracking System asset

## Step 4: Configure MQTT Service User

In OpenRemote UI:

1. Go to Settings → Users → Service Users
2. Create a service user (or use existing):
   - Username: `mqtt`
   - Realm: `master`
   - Note the generated secret (password)

## Step 5: ESP32 Beacon Setup

### Hardware:

- ESP32 boards (minimum 3 for trilateration)
- BLE tags with sensor capabilities

### Software:

1. **Install Arduino IDE** with ESP32 support

2. **Install required libraries:**

   - BLE (built-in)
   - WiFi (built-in)
   - PubSubClient
   - ArduinoJson

3. **Upload code:**

   - Use `beacon_v5_batched.ino` for ESP32 beacons
   - Configure in the file:
     - WiFi credentials (ssid, password)
     - MQTT broker IP (mqtt_server)
     - MQTT credentials (mqtt_username, mqtt_password)
     - Beacon asset ID (beacon_asset_id) - get from OpenRemote UI
     - Available tag asset IDs (available_tag_assets array)

4. **Flash to each ESP32:**
   - Update `mqtt_client_id` to be unique for each beacon (e.g., "esp32-beacon-001", "esp32-beacon-002")
   - Update `beacon_asset_id` to match the corresponding beacon asset in OpenRemote
   - Position beacons at known locations matching the locationMeters configured in OpenRemote

## Step 6: Python Emulator Testing (Alternative to Hardware)

For testing without physical hardware:

```bash
# Install dependencies
pip install paho-mqtt

# Run emulator for each beacon (in separate terminals)
# Beacon 1
python3 mqtt_beacon_emulator.py
# Follow prompts:
#   MQTT Server: localhost (or your OpenRemote IP)
#   Beacon ID: esp32-beacon-001
#   Beacon Asset ID: <beacon 1 asset ID from OpenRemote>
#   Beacon Location: x=0, y=0, z=2.0
#   Tags: Configure 1-2 tags with MAC addresses and asset IDs
#   Update interval: 2 seconds

# Beacon 2 (in new terminal)
python3 mqtt_beacon_emulator.py
# Configure with different beacon location (e.g., x=15, y=0, z=2.0)
# Use SAME tag MAC addresses as Beacon 1

# Beacon 3 (in new terminal)
python3 mqtt_beacon_emulator.py
# Configure with different beacon location (e.g., x=7.5, y=15, z=2.0)
# Use SAME tag MAC addresses as Beacon 1 and 2
```

## Step 7: Configure Map Dashboard

1. **Create new dashboard:**

   - Name: "Indoor Tracking"
   - Add OpenStreetMap widget

2. **Configure map widget:**

   - Asset selection: Select "Indoor Tracking System" group
   - Show children: Yes
   - Center map on reference point coordinates

3. **View real-time tracking:**
   - Beacons appear as blue markers (static)
   - Tags appear as red markers (moving based on calculated position)
   - Tags update position in real-time as RSSI values change

## Step 8: Verification Checklist

- [ ] All asset types compile and appear in OpenRemote
- [ ] IndoorTrackingGroup created with reference point configured
- [ ] At least 3 IndoorBeacon assets created with correct locationMeters
- [ ] IndoorTag assets created with MAC addresses
- [ ] Groovy rules deployed and active
- [ ] MQTT service user created
- [ ] ESP32 beacons (or emulators) connecting to MQTT broker
- [ ] Beacon assets show tagDetections updates in OpenRemote
- [ ] Tag assets show temperature, humidity, gyro, lastSeen updates
- [ ] Tag assets show location and locationMeters updates (after 3+ beacons detect)
- [ ] Tags visible on map widget
- [ ] Tag position updates in real-time on map

## Troubleshooting

### Issue: Tags not showing location

- **Check**: Are at least 3 beacons detecting the tag?
- **Check**: Is `beaconCount` attribute >= minBeaconsForTrilateration?
- **Check**: Do all beacons have valid locationMeters configured?
- **Check**: Is referencePoint set in IndoorTrackingGroup?
- **Check**: Are Groovy rules enabled and running?

### Issue: Beacons not receiving tag detections

- **Check**: ESP32 WiFi connection
- **Check**: MQTT broker connectivity
- **Check**: MQTT credentials correct
- **Check**: Beacon asset ID matches in ESP32 code and OpenRemote

### Issue: Inaccurate positions

- **Adjust**: pathLossExponent (increase for more obstacles, typical: 2.0-3.5)
- **Adjust**: referenceRssi (typical BLE: -45 to -50)
- **Check**: Beacon positions are accurately measured
- **Check**: Beacons are spread out (not all on same wall)

### Issue: Rules not triggering

- **Check**: Rules scope (should be Global or on IndoorTrackingGroup)
- **Check**: OpenRemote logs for rule execution errors
- **Check**: tagDetections attribute format matches expected structure

## Advanced Configuration

### Custom Indoor Map Widget (Phase 7 - Future)

For better visualization with floor plans:

1. Create custom TypeScript widget in `ui/component/`
2. Display floor plan image as background
3. Overlay beacon and tag positions
4. Real-time updates via WebSocket

### RSSI Filtering

To reduce jitter, implement smoothing in ESP32 code:

- Moving average filter
- Kalman filter
- Median filter

### Multiple Floors

For multi-floor buildings:

- Create separate IndoorTrackingGroup per floor
- Use Z coordinate (locationMeters.z) to differentiate floors
- Beacons on each floor only track tags on that floor

## File Reference

### Created/Modified Files:

**Asset Models:**

- `model/src/main/java/org/openremote/model/asset/impl/IndoorBeacon.java`
- `model/src/main/java/org/openremote/model/asset/impl/IndoorTag.java`
- `model/src/main/java/org/openremote/model/asset/impl/IndoorTrackingGroup.java`
- `model/src/main/java/org/openremote/model/value/impl/LocationMeters.java`
- `model/src/main/java/org/openremote/model/value/impl/TagDetection.java`
- `model/src/main/java/org/openremote/model/value/ValueType.java` (added LOCATION_METERS, TAG_DETECTIONS)

**Rules:**

- `setup/src/demo/resources/demo/rules/indoor_tracking/SensorForwarding.groovy`
- `setup/src/demo/resources/demo/rules/indoor_tracking/TrilaterationUtils.groovy`
- `setup/src/demo/resources/demo/rules/indoor_tracking/TrilaterationEngine.groovy`

**ESP32 Code:**

- `beacon_v5_batched.ino` (new batched format)
- `beacon_v4.ino` (original - now deprecated)

**Python Emulator:**

- `mqtt_beacon_emulator.py` (updated to new format)

## Support

For issues or questions:

1. Check OpenRemote logs: `docker logs openremote_manager_1`
2. Check MQTT messages: `mosquitto_sub -t 'master/#' -v`
3. Review Groovy rule execution logs in OpenRemote UI

## Next Steps

1. Calibrate pathLossExponent for your specific environment
2. Test with multiple tags simultaneously
3. Add RSSI smoothing for more stable positioning
4. Create custom indoor map widget with floor plan
5. Implement alerting when tags enter/exit zones


