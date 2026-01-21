# MQTT Beacon Emulator

Python script that emulates BLE beacons sending sensor data to OpenRemote via MQTT.

## Features

- ✅ Emulates realistic sensor data (temperature, humidity, gyroscope, signal strength)
- ✅ Each script instance = 1 beacon/tag combination
- ✅ Run multiple instances for multiple beacons
- ✅ Interactive configuration on startup
- ✅ Continuous data transmission until stopped
- ✅ Matches the exact MQTT topic/payload format of `beacon_v4.ino`

## Installation

1. **Install Python dependencies:**
```bash
pip install -r requirements.txt
```

Or install manually:
```bash
pip install paho-mqtt
```

## Usage

### Single Beacon

Run the script and follow the prompts:

```bash
python3 mqtt_beacon_emulator.py
```

You'll be prompted for:
- MQTT Server IP (default: 10.191.208.71)
- MQTT Port (default: 1883)
- Realm (default: master)
- Service Username (default: mqtt)
- Service Password (required)
- Beacon ID (default: esp32-beacon-001)
- Tag MAC Address (default: AA:BB:CC:DD:EE:FF)
- Asset ID (required)
- Update interval in seconds (default: 2)

### Multiple Beacons

Open multiple terminals and run the script in each:

**Terminal 1 - Beacon/Tag 1:**
```bash
python3 mqtt_beacon_emulator.py
```
- Tag MAC: `AA:BB:CC:DD:EE:01`
- Asset ID: `6UGj4OxCfhtOxqHaeh22Up`

**Terminal 2 - Beacon/Tag 2:**
```bash
python3 mqtt_beacon_emulator.py
```
- Tag MAC: `AA:BB:CC:DD:EE:02`
- Asset ID: `4qwfK0RjA8YF2bVJZUrrZH`

**Terminal 3 - Beacon/Tag 3:**
```bash
python3 mqtt_beacon_emulator.py
```
- Tag MAC: `AA:BB:CC:DD:EE:03`
- Asset ID: `YOUR_THIRD_ASSET_ID`

## Example Session

```
============================================================
  MQTT Beacon Emulator for OpenRemote
============================================================

MQTT Server IP [10.191.208.71]: 
MQTT Port [1883]: 
Realm [master]: 
Service Username [mqtt]: 
Service Password: cB2zFq3e53XR1cSKsbt8lR4ZxozCD30q
Beacon ID [esp32-beacon-001]: beacon-test-001
Tag MAC Address [AA:BB:CC:DD:EE:FF]: AA:BB:CC:DD:11:22
Asset ID: 6UGj4OxCfhtOxqHaeh22Up
Update interval in seconds [2]: 1

============================================================
Configuration Summary:
  MQTT Server: 10.191.208.71:1883
  Realm: master
  Username: master:mqtt
  Beacon ID: beacon-test-001
  Tag MAC: AA:BB:CC:DD:11:22
  Asset ID: 6UGj4OxCfhtOxqHaeh22Up
  Update Interval: 1.0s
============================================================

Connecting to MQTT broker at 10.191.208.71:1883...
✓ Connected to MQTT broker at 10.191.208.71
  Client ID: beacon-test-001
  Tag MAC: AA:BB:CC:DD:11:22
  Asset ID: 6UGj4OxCfhtOxqHaeh22Up

Starting data transmission...
------------------------------------------------------------

[14:30:15] Publish #1
Tag: AA:BB:CC:DD:11:22 -> Asset: 6UGj4OxCfhtOxqHaeh22Up
  Temperature: 23.45 °C
  Humidity: 62.30 %
  Gyro: X=125, Y=340, Z=89
  Signal: -64 dBm
  ✓ All attributes published successfully

[14:30:16] Publish #2
Tag: AA:BB:CC:DD:11:22 -> Asset: 6UGj4OxCfhtOxqHaeh22Up
  Temperature: 23.52 °C
  Humidity: 61.85 %
  Gyro: X=110, Y=355, Z=95
  Signal: -62 dBm
  ✓ All attributes published successfully
```

## Data Format

The emulator publishes to these MQTT topics with the same format as the real beacon:

```
master/beacon-test-001/writeattributevalue/temperature/6UGj4OxCfhtOxqHaeh22Up
master/beacon-test-001/writeattributevalue/humidity/6UGj4OxCfhtOxqHaeh22Up
master/beacon-test-001/writeattributevalue/gyro/6UGj4OxCfhtOxqHaeh22Up
master/beacon-test-001/writeattributevalue/signalStrength/6UGj4OxCfhtOxqHaeh22Up
```

**Payloads:**
- `temperature`: `2345` (integer, hundredths of °C)
- `humidity`: `6230` (integer, hundredths of %)
- `gyro`: `{"x":125,"y":340,"z":89}` (JSON object)
- `signalStrength`: `-64` (integer, dBm)

## Sensor Value Ranges

- **Temperature**: 18-28°C (smooth random walk)
- **Humidity**: 40-80% (gradual changes)
- **Gyroscope**: 0-1000 per axis (occasional movement spikes)
- **Signal Strength**: -40 to -80 dBm

## Stopping the Emulator

Press `Ctrl+C` in any terminal to stop that beacon instance.

## Troubleshooting

### Connection Failed
- ✅ Check MQTT server IP is correct
- ✅ Verify port 1883 is accessible
- ✅ Check firewall settings
- ✅ Ensure OpenRemote is running

### Authentication Error
- ✅ Verify service user credentials
- ✅ Check username format: `master:mqtt` (realm:username)
- ✅ Ensure service user exists in OpenRemote
- ✅ Confirm service user has proper permissions

### No Data in OpenRemote
- ✅ Verify Asset ID is correct
- ✅ Check service user has write permissions
- ✅ Ensure asset exists in the specified realm
- ✅ Check OpenRemote manager logs

## Tips

1. **Different Beacon IDs**: Use unique beacon IDs for each instance (e.g., `beacon-001`, `beacon-002`)
2. **Unique MAC Addresses**: Use different MAC addresses for each tag to test multi-asset mapping
3. **Update Intervals**: Use faster intervals (0.5-1s) for stress testing, slower (5-10s) for realistic simulation
4. **Screen/Tmux**: Use `screen` or `tmux` to run multiple instances in one terminal window

## Advanced: Running in Background

Use `nohup` to run in background:

```bash
nohup python3 mqtt_beacon_emulator.py > beacon1.log 2>&1 &
nohup python3 mqtt_beacon_emulator.py > beacon2.log 2>&1 &
```

Kill background processes:
```bash
pkill -f mqtt_beacon_emulator.py
```

---

**Happy Testing! 🚀**




