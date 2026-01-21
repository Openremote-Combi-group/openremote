#!/usr/bin/env python3
"""
MQTT Beacon Emulator for OpenRemote
Emulates a BLE beacon sending sensor data through MQTT
Each instance acts as one beacon/tag combination
"""

import json
import random
import sys
import time

import paho.mqtt.client as mqtt

# ============================================
# CONFIGURATION
# ============================================


def get_config():
    """Get configuration from user input"""
    print("=" * 60)
    print("  MQTT Beacon Emulator for OpenRemote")
    print("  Triangulation Testing Mode")
    print("=" * 60)
    print()

    # MQTT Broker Configuration
    mqtt_server = input("MQTT Server IP [localhost]: ").strip() or "localhost"
    mqtt_port = input("MQTT Port [1883]: ").strip() or "1883"
    mqtt_port = int(mqtt_port)

    # OpenRemote Credentials
    realm = input("Realm [master]: ").strip() or "master"
    username = input("Service Username [signal mqtt]: ").strip() or "mqtt"
    password = input("Service Password: ").strip()
    if not password:
        print("ERROR: Password is required!")
        sys.exit(1)

    # Beacon Configuration (unique per instance)
    print()
    print("=" * 60)
    print("Beacon Configuration (unique per instance):")
    print("=" * 60)
    beacon_id = input("Beacon ID [esp32-beacon-001]: ").strip() or "esp32-beacon-001"
    beacon_asset_id = input("Beacon Asset ID in OpenRemote: ").strip()
    if not beacon_asset_id:
        print("ERROR: Beacon Asset ID is required!")
        sys.exit(1)

    # Beacon Location (for triangulation)
    print()
    print("Beacon Location (for triangulation):")
    print("  Enter coordinates where this beacon is located")
    beacon_x = input("  X coordinate [0.0]: ").strip() or "0.0"
    beacon_y = input("  Y coordinate [0.0]: ").strip() or "0.0"
    beacon_z = input("  Z coordinate [0.0]: ").strip() or "0.0"
    beacon_x = float(beacon_x)
    beacon_y = float(beacon_y)
    beacon_z = float(beacon_z)

    # Tags Configuration (same across all beacons)
    print()
    print("=" * 60)
    print("Tags Configuration (same for all beacon instances):")
    print("=" * 60)
    print("How many tags should this beacon detect?")
    num_tags = input("Number of tags [2]: ").strip() or "2"
    num_tags = int(num_tags)

    tags = []
    for i in range(num_tags):
        print(f"\nTag {i + 1}:")
        default_mac = f"AA:BB:CC:DD:EE:{i + 1:02d}"
        tag_mac = input(f"  MAC Address [{default_mac}]: ").strip() or default_mac
        tag_mac = tag_mac.upper()

        asset_id = input("  Asset ID: ").strip()
        if not asset_id:
            print(f"ERROR: Asset ID is required for tag {i + 1}!")
            sys.exit(1)

        tags.append({"mac": tag_mac, "asset_id": asset_id})

    # Update interval
    print()
    interval = input("Update interval in seconds [2]: ").strip() or "2"
    interval = float(interval)

    print()
    print("=" * 60)
    print("Configuration Summary:")
    print(f"  MQTT Server: {mqtt_server}:{mqtt_port}")
    print(f"  Realm: {realm}")
    print(f"  Username: {realm}:{username}")
    print(f"  Beacon ID: {beacon_id}")
    print(f"  Beacon Asset ID: {beacon_asset_id}")
    print(f"  Beacon Location: ({beacon_x}, {beacon_y}, {beacon_z})")
    print(f"  Tags to detect: {num_tags}")
    for i, tag in enumerate(tags):
        print(f"    Tag {i + 1}: {tag['mac']} -> {tag['asset_id']}")
    print(f"  Update Interval: {interval}s")
    print("=" * 60)
    print()

    return {
        "mqtt_server": mqtt_server,
        "mqtt_port": mqtt_port,
        "realm": realm,
        "username": f"{realm}:{username}",
        "password": password,
        "beacon_id": beacon_id,
        "beacon_asset_id": beacon_asset_id,
        "beacon_location": {"x": beacon_x, "y": beacon_y, "z": beacon_z},
        "tags": tags,
        "interval": interval,
    }


# ============================================
# SENSOR DATA GENERATION
# ============================================


class SensorSimulator:
    """Simulates realistic sensor data"""

    def __init__(self, beacon_location=None):
        self.temp_base = 23.0  # Base temperature in °C
        self.humid_base = 60.0  # Base humidity in %
        self.gyro_x = 0
        self.gyro_y = 0
        self.gyro_z = 0
        self.beacon_location = beacon_location or {"x": 0, "y": 0, "z": 0}

        # Simulated tag position (tags move slightly over time)
        # Position tags in the middle area where beacons can detect them
        self.tag_x = random.uniform(3, 7)
        self.tag_y = random.uniform(2, 6)
        self.tag_z = 1.5  # Tags typically at waist/chest height

    def get_temperature(self):
        """Generate temperature (18-28°C) in hundredths"""
        self.temp_base += random.uniform(-0.5, 0.5)
        self.temp_base = max(18.0, min(28.0, self.temp_base))
        return int(self.temp_base * 100)

    def get_humidity(self):
        """Generate humidity (40-80%) in hundredths"""
        self.humid_base += random.uniform(-1.0, 1.0)
        self.humid_base = max(40.0, min(80.0, self.humid_base))
        return int(self.humid_base * 100)

    def get_gyro(self):
        """Generate gyroscope data with occasional movement"""
        # 20% chance of bigger movement
        if random.random() < 0.2:
            self.gyro_x = random.randint(-500, 500)
            self.gyro_y = random.randint(-500, 500)
            self.gyro_z = random.randint(-500, 500)
        else:
            # Small drift
            self.gyro_x += random.randint(-50, 50)
            self.gyro_y += random.randint(-50, 50)
            self.gyro_z += random.randint(-50, 50)

        # Clamp values
        self.gyro_x = max(-1000, min(1000, self.gyro_x))
        self.gyro_y = max(-1000, min(1000, self.gyro_y))
        self.gyro_z = max(-1000, min(1000, self.gyro_z))

        return {"x": abs(self.gyro_x), "y": abs(self.gyro_y), "z": abs(self.gyro_z)}

    def calculate_distance(self):
        """Calculate 3D distance between beacon and tag"""
        dx = self.beacon_location["x"] - self.tag_x
        dy = self.beacon_location["y"] - self.tag_y
        dz = self.beacon_location["z"] - self.tag_z
        return (dx**2 + dy**2 + dz**2) ** 0.5

    def distance_to_rssi(self, distance):
        """Convert distance to RSSI using path loss model
        RSSI = -10 * n * log10(d) + A
        where n = path loss exponent (2-4 for indoor)
              d = distance in meters
              A = RSSI at 1 meter reference distance
        """
        if distance < 0.5:
            distance = 0.5  # Minimum distance

        import math

        A = -45  # RSSI at 1 meter (BLE typical)
        n = 2.0  # Path loss exponent (2.0 for free space, 2-3 for indoor)

        # Calculate RSSI using proper path loss formula
        rssi = A - (10 * n * math.log10(distance))
        rssi += random.uniform(-3, 3)  # Add small random noise

        # Clamp to realistic BLE range
        rssi = max(-90, min(-35, rssi))

        return int(rssi)

    def get_signal_strength(self):
        """Generate realistic RSSI based on beacon-tag distance"""
        # Tag moves slightly (simulating person walking)
        self.tag_x += random.uniform(-0.1, 0.1)
        self.tag_y += random.uniform(-0.1, 0.1)

        # Keep tag in reasonable bounds
        self.tag_x = max(0, min(15, self.tag_x))
        self.tag_y = max(0, min(15, self.tag_y))

        distance = self.calculate_distance()
        rssi = self.distance_to_rssi(distance)

        return rssi


# ============================================
# MQTT CLIENT
# ============================================


class BeaconEmulator:
    """Emulates a BLE beacon publishing to MQTT"""

    def __init__(self, config):
        self.config = config
        self.client = mqtt.Client(client_id=config["beacon_id"])
        self.client.username_pw_set(config["username"], config["password"])
        self.client.on_connect = self.on_connect
        self.client.on_disconnect = self.on_disconnect
        self.client.on_publish = self.on_publish
        # Create a simulator for each tag (with beacon location for RSSI calculation)
        self.simulators = {}
        for tag in config["tags"]:
            self.simulators[tag["mac"]] = SensorSimulator(config["beacon_location"])
        self.connected = False
        self.publish_count = 0

    def on_connect(self, client, userdata, flags, rc):
        """Callback when connected to MQTT broker"""
        if rc == 0:
            self.connected = True
            print(f"✓ Connected to MQTT broker at {self.config['mqtt_server']}")
            print(f"  Beacon ID: {self.config['beacon_id']}")
            print(f"  Beacon Asset ID: {self.config['beacon_asset_id']}")
            loc = self.config["beacon_location"]
            print(f"  Beacon Location: ({loc['x']}, {loc['y']}, {loc['z']})")
            print(f"  Detecting {len(self.config['tags'])} tag(s):")
            for i, tag in enumerate(self.config["tags"]):
                print(f"    {i + 1}. {tag['mac']} -> {tag['asset_id']}")
            print()
            print("Starting data transmission...")
            print("  Publishing format: Batched tagDetections to Beacon Asset")
            print("-" * 60)
        else:
            print(f"✗ Connection failed with code {rc}")
            self.connected = False

    def on_disconnect(self, client, userdata, rc):
        """Callback when disconnected from MQTT broker"""
        self.connected = False
        if rc != 0:
            print(f"Unexpected disconnect! Code: {rc}")

    def on_publish(self, client, userdata, mid):
        """Callback when message is published"""
        pass

    def build_topic(self, attribute_name, asset_id):
        """Build MQTT topic for an attribute"""
        return f"{self.config['realm']}/{self.config['beacon_id']}/writeattributevalue/{attribute_name}/{asset_id}"

    def publish_tag_detections(self, tag_detections):
        """Publish batched tag detections to Beacon asset"""
        topic = self.build_topic("tagDetections", self.config["beacon_asset_id"])

        # tag_detections is a dict: {mac_address: detection_data}
        payload = json.dumps(tag_detections)

        result = self.client.publish(topic, payload)

        if result.rc == mqtt.MQTT_ERR_SUCCESS:
            return True
        else:
            print("  ✗ Failed to publish tagDetections to beacon asset")
            return False

    def publish_sensor_data(self):
        """Publish sensor data for all tags in batched format"""
        self.publish_count += 1

        print(f"\n[{time.strftime('%H:%M:%S')}] Publish #{self.publish_count}")
        loc = self.config["beacon_location"]
        print(
            f"Beacon: {self.config['beacon_id']} (Asset: {self.config['beacon_asset_id']}) at ({loc['x']}, {loc['y']}, {loc['z']})"
        )
        print("-" * 60)

        # Build batched tagDetections payload
        tag_detections = {}

        # Publish data for each tag
        for tag in self.config["tags"]:
            tag_mac = tag["mac"]
            asset_id = tag["asset_id"]
            simulator = self.simulators[tag_mac]

            # Generate sensor values for this tag
            temp = simulator.get_temperature()
            humid = simulator.get_humidity()
            gyro = simulator.get_gyro()
            rssi = simulator.get_signal_strength()

            # Calculate distance for debugging
            distance = simulator.calculate_distance()

            print(f"\nTag: {tag_mac} -> Asset: {asset_id}")
            print(f"  Temperature: {temp / 100:.2f} °C")
            print(f"  Humidity: {humid / 100:.2f} %")
            print(f"  Gyro: X={gyro['x']}, Y={gyro['y']}, Z={gyro['z']}")
            print(f"  Signal: {rssi} dBm (distance: {distance:.2f}m)")

            # Add to batched payload
            tag_detections[tag_mac] = {
                "assetId": asset_id,
                "rssi": rssi,
                "temperature": temp,
                "humidity": humid,
                "gyro": gyro,
                "timestamp": int(time.time() * 1000),  # Milliseconds since epoch
            }

        # Publish batched tag detections to beacon asset
        print(
            f"\nPublishing batched tagDetections to Beacon Asset: {self.config['beacon_asset_id']}"
        )
        success = self.publish_tag_detections(tag_detections)

        if success:
            print("  ✓ Batched tag detections published")
        else:
            print("  ✗ Failed to publish batched tag detections")

        print("-" * 60)
        return success

    def connect(self):
        """Connect to MQTT broker"""
        print(
            f"Connecting to MQTT broker at {self.config['mqtt_server']}:{self.config['mqtt_port']}..."
        )
        try:
            self.client.connect(
                self.config["mqtt_server"], self.config["mqtt_port"], 60
            )
            self.client.loop_start()

            # Wait for connection
            timeout = 10
            start_time = time.time()
            while not self.connected and (time.time() - start_time) < timeout:
                time.sleep(0.1)

            if not self.connected:
                print("✗ Connection timeout!")
                return False

            return True
        except Exception as e:
            print(f"✗ Connection error: {e}")
            return False

    def disconnect(self):
        """Disconnect from MQTT broker"""
        print("\nDisconnecting...")
        self.client.loop_stop()
        self.client.disconnect()
        print("✓ Disconnected")

    def run(self):
        """Main loop - continuously publish sensor data"""
        if not self.connect():
            return

        try:
            while True:
                if self.connected:
                    self.publish_sensor_data()
                    time.sleep(self.config["interval"])
                else:
                    print("Not connected, reconnecting...")
                    self.connect()
                    time.sleep(5)

        except KeyboardInterrupt:
            print("\n\nStopping beacon emulator...")
            self.disconnect()


# ============================================
# MAIN
# ============================================


def main():
    """Main entry point"""
    try:
        # Get configuration from user
        config = get_config()

        # Create and run beacon emulator
        beacon = BeaconEmulator(config)

        print("Press Ctrl+C to stop\n")
        beacon.run()

    except KeyboardInterrupt:
        print("\n\nShutdown requested by user")
    except Exception as e:
        print(f"\nError: {e}")
        import traceback

        traceback.print_exc()


if __name__ == "__main__":
    main()
