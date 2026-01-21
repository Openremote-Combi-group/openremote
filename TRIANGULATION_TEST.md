# Triangulation Testing Guide

This guide explains how to test indoor positioning/triangulation using multiple beacon emulators.

## Concept

**Triangulation Mode:**

- Multiple beacons (different locations)
- All beacons detect the same tags (same MAC addresses & asset IDs)
- Each beacon reports its own location along with tag sensor data
- OpenRemote can use beacon locations + signal strength to triangulate tag positions

## Quick Start

### Automated (3 Beacons in Triangle Pattern)

```bash
./quick_test.sh
```

This launches 3 beacons:

- **Beacon 1**: Location (0.0, 0.0, 2.5)
- **Beacon 2**: Location (10.0, 0.0, 2.5)
- **Beacon 3**: Location (5.0, 8.66, 2.5)

All beacons detect the same 2 tags and report their location.

### Manual Setup

**Terminal 1 - Beacon at Corner 1:**

```bash
python3 mqtt_beacon_emulator.py
```

Configuration:

- Beacon ID: `beacon-001`
- Beacon Location: X=0, Y=0, Z=2.5
- Number of tags: 2
  - Tag 1: MAC=`AA:BB:CC:DD:EE:01`, Asset=`2Ft96CqxPz3sA5m2tp2l4p`
  - Tag 2: MAC=`AA:BB:CC:DD:EE:02`, Asset=`4hx4f9g8RjlAj2vqs7gOPV`

**Terminal 2 - Beacon at Corner 2:**

```bash
python3 mqtt_beacon_emulator.py
```

Configuration:

- Beacon ID: `beacon-002`
- Beacon Location: X=10, Y=0, Z=2.5
- Number of tags: 2
  - Tag 1: MAC=`AA:BB:CC:DD:EE:01`, Asset=`2Ft96CqxPz3sA5m2tp2l4p`
  - Tag 2: MAC=`AA:BB:CC:DD:EE:02`, Asset=`4hx4f9g8RjlAj2vqs7gOPV`

**Terminal 3 - Beacon at Corner 3:**

```bash
python3 mqtt_beacon_emulator.py
```

Configuration:

- Beacon ID: `beacon-003`
- Beacon Location: X=5, Y=8.66, Z=2.5
- Number of tags: 2
  - Tag 1: MAC=`AA:BB:CC:DD:EE:01`, Asset=`2Ft96CqxPz3sA5m2tp2l4p`
  - Tag 2: MAC=`AA:BB:CC:DD:EE:02`, Asset=`4hx4f9g8RjlAj2vqs7gOPV`

## Data Published

Each beacon publishes to MQTT:

### For Tag 1 (Asset: 2Ft96CqxPz3sA5m2tp2l4p):

```
master/beacon-001/writeattributevalue/temperature/2Ft96CqxPz3sA5m2tp2l4p
master/beacon-001/writeattributevalue/humidity/2Ft96CqxPz3sA5m2tp2l4p
master/beacon-001/writeattributevalue/gyro/2Ft96CqxPz3sA5m2tp2l4p
master/beacon-001/writeattributevalue/signalStrength/2Ft96CqxPz3sA5m2tp2l4p
master/beacon-001/writeattributevalue/beaconLocation/2Ft96CqxPz3sA5m2tp2l4p
```

### For Tag 2 (Asset: 4hx4f9g8RjlAj2vqs7gOPV):

```
master/beacon-001/writeattributevalue/temperature/4hx4f9g8RjlAj2vqs7gOPV
master/beacon-001/writeattributevalue/humidity/4hx4f9g8RjlAj2vqs7gOPV
master/beacon-001/writeattributevalue/gyro/4hx4f9g8RjlAj2vqs7gOPV
master/beacon-001/writeattributevalue/signalStrength/4hx4f9g8RjlAj2vqs7gOPV
master/beacon-001/writeattributevalue/beaconLocation/4hx4f9g8RjlAj2vqs7gOPV
```

**Key Attribute - beaconLocation:**

```json
{ "x": 0.0, "y": 0.0, "z": 2.5 }
```

This tells OpenRemote where the beacon that detected this tag is located.

## Example Output

```
[14:30:15] Publish #1
Beacon: beacon-001 at (0.0, 0.0, 2.5)
------------------------------------------------------------

Tag: AA:BB:CC:DD:EE:01 -> Asset: 2Ft96CqxPz3sA5m2tp2l4p
  Temperature: 23.45 °C
  Humidity: 62.30 %
  Gyro: X=125, Y=340, Z=89
  Signal: -64 dBm (distance: 5.23m)
  ✓ All attributes published

Tag: AA:BB:CC:DD:EE:02 -> Asset: 4hx4f9g8RjlAj2vqs7gOPV
  Temperature: 21.30 °C
  Humidity: 58.20 %
  Gyro: X=45, Y=120, Z=210
  Signal: -58 dBm (distance: 3.85m)
  ✓ All attributes published
------------------------------------------------------------
```

**Different beacons report different signal strengths:**

```
Beacon 1 at (0, 0, 2.5):    Tag X → RSSI: -64 dBm
Beacon 2 at (10, 0, 2.5):   Tag X → RSSI: -52 dBm
Beacon 3 at (5, 8.66, 2.5): Tag X → RSSI: -58 dBm
```

This simulates realistic triangulation where each beacon is at a different distance from the tag.

## Triangulation Scenarios

### Scenario 1: Triangle Pattern (Default)

```
Beacon 1: (0, 0, 2.5)         Beacon 2: (10, 0, 2.5)
     *                              *
      \                            /
       \                          /
        \                        /
         \                      /
          \                    /
           \                  /
            *Beacon 3: (5, 8.66, 2.5)
```

Good for: General room coverage

### Scenario 2: Grid Pattern

```
Beacon 1: (0, 0, 2.5)    Beacon 2: (10, 0, 2.5)
     *-------------------------*

     *-------------------------*
Beacon 3: (0, 10, 2.5)   Beacon 4: (10, 10, 2.5)
```

Good for: Rectangular rooms, higher precision

### Scenario 3: Linear Pattern

```
Beacon 1: (0, 0, 2.5)
     *

Beacon 2: (5, 0, 2.5)
           *

Beacon 3: (10, 0, 2.5)
                 *
```

Good for: Corridor/hallway tracking

## Configuration Tips

### Beacon Spacing

- **Minimum**: 3 meters apart
- **Optimal**: 5-10 meters apart
- **Maximum**: 30 meters (depends on signal strength)

### Height (Z coordinate)

- Typically 2-3 meters (ceiling mounted)
- All beacons at same height for simpler 2D calculations
- Different heights for true 3D positioning

### Signal Strength Simulation

The emulator calculates realistic RSSI values based on:

- **Path Loss Model**: RSSI = -40 - (10 _ n _ √d) + noise
  - n = 2.5 (path loss exponent for indoor)
  - d = 3D distance between beacon and tag
  - noise = ±3 dBm random variation
- **Range**: -30 dBm (0.5m away) to -85 dBm (>15m away)
- **Tag Movement**: Tags slowly drift to simulate person walking
- **Different RSSI per beacon**: Each beacon calculates distance from its position

## Adding More Tags

Edit the configuration when running:

```
Number of tags [2]: 5

Tag 1:
  MAC Address [AA:BB:CC:DD:EE:01]: AA:BB:CC:DD:EE:01
  Asset ID: 2Ft96CqxPz3sA5m2tp2l4p

Tag 2:
  MAC Address [AA:BB:CC:DD:EE:02]: AA:BB:CC:DD:EE:02
  Asset ID: 4hx4f9g8RjlAj2vqs7gOPV

Tag 3:
  MAC Address [AA:BB:CC:DD:EE:03]: AA:BB:CC:DD:EE:03
  Asset ID: YOUR_THIRD_ASSET_ID

... etc
```

## Using in OpenRemote

### Asset Configuration

Each tag (asset) should have these attributes:

- `temperature` (Number)
- `humidity` (Number)
- `gyro` (Object with x, y, z)
- `signalStrength` (Number)
- `beaconLocation` (Object with x, y, z) ← **New for triangulation**

### Triangulation Logic

You can implement triangulation in OpenRemote using:

1. **Rules** - Process beacon locations + signal strengths
2. **Groovy Scripts** - Calculate tag position from multiple beacon reports
3. **External Service** - Send data to positioning engine

Example calculation:

- Beacon 1 at (0,0) reports Tag X with RSSI -50 dBm
- Beacon 2 at (10,0) reports Tag X with RSSI -60 dBm
- Beacon 3 at (5,8.66) reports Tag X with RSSI -55 dBm
  → Tag X is likely at position (3, 2)

## Stopping Emulators

```bash
# Stop all beacon emulators
pkill -f mqtt_beacon_emulator.py

# Or press Ctrl+C in each terminal
```

## Troubleshooting

### All beacons show same location

- Check each beacon has unique location coordinates
- Verify you're running different instances, not the same one

### Tags not appearing in OpenRemote

- Verify asset IDs exist in OpenRemote
- Check MQTT credentials
- Ensure assets have `beaconLocation` attribute defined

### Signal strength always the same

- This is random - it will vary over time
- Check multiple publish cycles

---

**Happy Triangulation Testing! 📡**
