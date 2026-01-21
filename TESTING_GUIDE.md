# Indoor Tracking System Testing Guide

This guide provides step-by-step testing procedures for the indoor tracking system.

## Test Environment Setup

### Prerequisites
- OpenRemote instance running
- Python 3.7+ with paho-mqtt installed
- 3 terminal windows available

## Test 1: Emulator-Based Triangulation Test

This test simulates 3 beacons detecting 1 tag to verify the entire system works.

### Test Scenario
- **Room**: 15m x 15m virtual room
- **Beacons**: 3 beacons at known positions
- **Tags**: 1 tag moving within the room
- **Expected outcome**: Tag position calculated and displayed on map

### Setup Steps

#### 1. Configure OpenRemote Assets

Create in OpenRemote UI:

**IndoorTrackingGroup:**
```
Name: Test Tracking System
Type: IndoorTrackingGroup
Attributes:
  - referencePoint: {"type": "Point", "coordinates": [-74.0, 40.0]}
  - metersPerDegreeLat: 111320
  - metersPerDegreeLng: 85000 (approximate for 40° latitude)
  - minBeaconsForTrilateration: 3
  - referenceRssi: -45.0
  - pathLossExponent: 2.0
```

**IndoorBeacon 1:**
```
Name: Beacon 1 (Corner)
Type: IndoorBeacon
Parent: Test Tracking System
Attributes:
  - locationMeters: {"x": 0, "y": 0, "z": 2.0}
  - location: Calculate from reference point (roughly -74.0, 40.0)
  - detectionRadius: 10
```

**IndoorBeacon 2:**
```
Name: Beacon 2 (Opposite Corner)
Type: IndoorBeacon
Parent: Test Tracking System
Attributes:
  - locationMeters: {"x": 15, "y": 0, "z": 2.0}
  - location: Calculate from reference point
  - detectionRadius: 10
```

**IndoorBeacon 3:**
```
Name: Beacon 3 (Far Wall)
Type: IndoorBeacon
Parent: Test Tracking System
Attributes:
  - locationMeters: {"x": 7.5, "y": 15, "z": 2.0}
  - location: Calculate from reference point
  - detectionRadius: 10
```

**IndoorTag 1:**
```
Name: Tag 1 (Test Person)
Type: IndoorTag
Parent: Test Tracking System
Attributes:
  - macAddress: "AA:BB:CC:DD:EE:01"
```

#### 2. Deploy Groovy Rules

Via OpenRemote UI → Rules → Create Rule → Groovy:

1. **Rule 1: SensorForwarding**
   - Copy content from `setup/src/demo/resources/demo/rules/indoor_tracking/SensorForwarding.groovy`
   - Name: "Sensor Forwarding"
   - Scope: Global

2. **Rule 2: TrilaterationEngine**
   - Copy content from `setup/src/demo/resources/demo/rules/indoor_tracking/TrilaterationEngine.groovy`
   - Name: "Trilateration Engine"
   - Scope: Global
   - **Note**: Must also include TrilaterationUtils class (copy at top of file)

#### 3. Launch Beacon Emulators

**Terminal 1 - Beacon 1:**
```bash
python3 mqtt_beacon_emulator.py

# Inputs:
MQTT Server IP [localhost]: localhost
MQTT Port [1883]: 1883
Realm [master]: master
Service Username [mqtt]: mqtt
Service Password: <your_mqtt_password>

Beacon ID [esp32-beacon-001]: test-beacon-001
Beacon Asset ID in OpenRemote: <Beacon_1_Asset_ID>

X coordinate [0.0]: 0
Y coordinate [0.0]: 0
Z coordinate [0.0]: 2.0

Number of tags [2]: 1

Tag 1:
  MAC Address [AA:BB:CC:DD:EE:01]: AA:BB:CC:DD:EE:01
  Asset ID: <Tag_1_Asset_ID>

Update interval in seconds [2]: 2
```

**Terminal 2 - Beacon 2:**
```bash
python3 mqtt_beacon_emulator.py

# Inputs (same as Beacon 1, except):
Beacon ID [esp32-beacon-001]: test-beacon-002
Beacon Asset ID in OpenRemote: <Beacon_2_Asset_ID>
X coordinate [0.0]: 15
Y coordinate [0.0]: 0
Z coordinate [0.0]: 2.0

# SAME Tag 1 MAC and Asset ID!
```

**Terminal 3 - Beacon 3:**
```bash
python3 mqtt_beacon_emulator.py

# Inputs (same as Beacon 1, except):
Beacon ID [esp32-beacon-001]: test-beacon-003
Beacon Asset ID in OpenRemote: <Beacon_3_Asset_ID>
X coordinate [0.0]: 7.5
Y coordinate [0.0]: 15
Z coordinate [0.0]: 2.0

# SAME Tag 1 MAC and Asset ID!
```

### Expected Results

#### 1. MQTT Messages
Monitor MQTT traffic:
```bash
mosquitto_sub -h localhost -t 'master/#' -v
```

You should see messages like:
```
master/test-beacon-001/writeattributevalue/tagDetections/<Beacon_1_Asset_ID> {"AA:BB:CC:DD:EE:01": {"assetId": "...", "rssi": -65, ...}}
master/test-beacon-002/writeattributevalue/tagDetections/<Beacon_2_Asset_ID> {"AA:BB:CC:DD:EE:01": {"assetId": "...", "rssi": -58, ...}}
master/test-beacon-003/writeattributevalue/tagDetections/<Beacon_3_Asset_ID> {"AA:BB:CC:DD:EE:01": {"assetId": "...", "rssi": -62, ...}}
```

#### 2. Beacon Asset Updates
In OpenRemote UI, check Beacon assets:
- `tagDetections` attribute should show JSON with detected tags
- Should update every 2 seconds

#### 3. Tag Asset Updates
In OpenRemote UI, check Tag asset:
- `temperature`: ~23.0°C (varies)
- `humidity`: ~60.0% (varies)
- `gyro`: `{"x": ..., "y": ..., "z": ...}`
- `lastSeen`: Recent timestamp
- `beaconCount`: 3 (after all 3 beacons detect it)
- `location`: GeoJSON Point (calculated)
- `locationMeters`: `{"x": ..., "y": ..., "z": ...}` (calculated, should be near center ~7.5, 7.5)

#### 4. Map Visualization
In OpenRemote Dashboard with Map widget:
- 3 blue beacon markers at static positions
- 1 red tag marker that updates position
- Tag should appear roughly in the middle of the triangle formed by beacons

### Validation

**Position Accuracy Check:**

The emulator simulates a tag moving slightly around a central position. Expected position:
- X: 3-7 meters (varies)
- Y: 2-6 meters (varies)
- Z: ~1.5 meters

Since RSSI is calculated from distance, the trilateration should place the tag within ~1-2 meters of its simulated position.

**Log Verification:**

Check OpenRemote logs for:
```
INFO: Sensor forwarding triggered for 3 beacon(s)
INFO: Trilateration triggered for 3 beacon(s)
INFO: Calculated position for tag AA:BB:CC:DD:EE:01: x=5.23, y=4.87, z=1.50
INFO: Tag AA:BB:CC:DD:EE:01 (...): position=(5.23, 4.87, 1.50), geo=(40.000044, -73.999939)
```

## Test 2: Multiple Tags Test

Test with 2 tags detected simultaneously.

### Modifications:
1. Create a second Tag asset (Tag 2) with MAC "AA:BB:CC:DD:EE:02"
2. Restart all 3 emulator instances
3. Configure 2 tags per emulator (same MAC addresses for all)

### Expected Results:
- Both tags should have independent calculated positions
- Both tags should appear on map
- Each tag updates independently

## Test 3: Insufficient Beacons Test

Test behavior when fewer than 3 beacons detect a tag.

### Test Steps:
1. Stop Beacon 3 emulator (Ctrl+C)
2. Observe only 2 beacons running

### Expected Results:
- Tag's `beaconCount`: 2
- Tag's `location` and `locationMeters` should NOT update (insufficient beacons)
- Sensor data (temp, humidity, gyro) should still update
- OpenRemote logs should show: "Tag AA:BB:CC:DD:EE:01 detected by only 2 beacon(s), need 3"

### Recovery:
1. Restart Beacon 3 emulator
2. Tag positioning should resume

## Test 4: RSSI Variation Test

Test how positioning changes with different RSSI values (simulating tag movement).

### Test Steps:
1. Run all 3 emulators
2. Observe tag position over time (should vary slightly as simulated RSSI changes)
3. Check that position updates are smooth and reasonable

### Expected Results:
- Tag position should change gradually (not jump wildly)
- Position should stay within room bounds (0-15m x, 0-15m y)
- RSSI values should vary realistically (-35 to -90 dBm)

## Test 5: Path Loss Exponent Tuning

Test effect of pathLossExponent on positioning accuracy.

### Test Steps:
1. Update IndoorTrackingGroup's `pathLossExponent`:
   - Try 2.0 (free space)
   - Try 2.5 (light indoor)
   - Try 3.0 (indoor with obstacles)
2. Observe how tag position changes

### Expected Results:
- Lower exponent (2.0): Tag appears closer to beacons
- Higher exponent (3.0): Tag appears further from beacons
- Adjust to match your real environment

## Test 6: Real Hardware Test

Once emulator tests pass, test with real ESP32 hardware.

### Test Steps:
1. Flash `beacon_v5_batched.ino` to 3 ESP32 boards
2. Update each with:
   - Unique `mqtt_client_id`
   - Correct `beacon_asset_id`
   - WiFi credentials
   - MQTT credentials
3. Place at known positions matching OpenRemote configuration
4. Power on BLE tags

### Expected Results:
- Same as emulator test, but with real sensor data
- Positions should be more accurate with stable hardware
- RSSI values may be more variable than simulation

## Troubleshooting Tests

### Issue: Rules not executing
**Test:**
```bash
# Check OpenRemote logs
docker logs -f openremote_manager_1 | grep -i "indoor\|trilateration\|sensor"
```

**Look for:**
- Rule registration messages
- Rule execution logs
- Error messages

### Issue: MQTT not connecting
**Test:**
```bash
# Test MQTT broker connectivity
mosquitto_pub -h localhost -t 'test/topic' -m 'test message'
mosquitto_sub -h localhost -t 'test/topic' -v
```

### Issue: Wrong positions calculated
**Test:**
```bash
# Calculate expected distance from RSSI
# Formula: distance = 10 ^ ((-45 - RSSI) / (10 * 2.0))

# Example: RSSI = -65
# distance = 10 ^ ((-45 - (-65)) / 20) = 10 ^ (20 / 20) = 10 ^ 1 = 10 meters

# Verify beacon distances match expected
```

## Performance Tests

### Test 7: High Frequency Updates

Test system with rapid updates.

### Test Steps:
1. Set emulator update interval to 0.5 seconds
2. Monitor OpenRemote CPU/memory usage
3. Check for missed updates or delays

### Expected Results:
- System should handle 2 updates/second per beacon without issues
- No significant lag in UI updates

### Test 8: Many Tags

Test scalability with multiple tags.

### Test Steps:
1. Create 5-10 tag assets
2. Configure emulators to detect all tags
3. Monitor system performance

### Expected Results:
- All tags should update correctly
- Position calculation may take slightly longer
- System should remain responsive

## Test Results Template

```
Test Date: _________
OpenRemote Version: _________
Test Performed By: _________

Test 1: Emulator-Based Triangulation
- [ ] MQTT messages received
- [ ] Beacon tagDetections updated
- [ ] Tag sensor data forwarded
- [ ] Tag position calculated
- [ ] Map display correct
- Position accuracy: ______ meters error
- Notes: _________

Test 2: Multiple Tags
- [ ] Both tags positioned
- [ ] Independent updates
- Notes: _________

Test 3: Insufficient Beacons
- [ ] Positioning stopped with <3 beacons
- [ ] Sensor data still forwarded
- [ ] Positioning resumed when beacon restarted
- Notes: _________

Test 4: RSSI Variation
- [ ] Position updates smooth
- [ ] Values realistic
- Notes: _________

Test 5: Path Loss Tuning
- pathLossExponent tested: 2.0, 2.5, 3.0
- Best value: _____
- Notes: _________

Test 6: Real Hardware (if applicable)
- [ ] ESP32 connected
- [ ] BLE tags detected
- [ ] Position calculated
- Position accuracy: ______ meters error
- Notes: _________

Overall Result: PASS / FAIL
Issues Encountered: _________
```

## Next Steps After Testing

Once all tests pass:
1. Document your optimal pathLossExponent and referenceRssi values
2. Create production beacon and tag assets with real IDs
3. Deploy to physical environment
4. Calibrate positions by measuring real tag locations vs calculated
5. Implement RSSI smoothing if jitter is observed
6. Set up alerting/notifications based on tag locations



