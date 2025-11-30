# MQTT Broker Setup Guide for OpenRemote

This guide will walk you through setting up and using the MQTT broker in OpenRemote.

## 📋 Table of Contents

- [Prerequisites](#prerequisites)
- [Configuration](#configuration)
- [Step-by-Step Setup](#step-by-step-setup)
- [Testing the Connection](#testing-the-connection)
- [MQTT Topics and Usage](#mqtt-topics-and-usage)
- [Troubleshooting](#troubleshooting)
- [Additional Resources](#additional-resources)

---

## Prerequisites

- Docker Desktop v18+ installed
- OpenRemote instance running
- Basic understanding of MQTT protocol

---

## Configuration

### MQTT Ports

The MQTT broker supports two connection modes:

- **Port 1883**: Non-SSL MQTT (for development/testing)
- **Port 8883**: MQTTS with SSL/TLS (for production)

### Environment Variables (Optional)

You can customize the MQTT broker using these environment variables in your docker-compose profile:

```yaml
environment:
  MQTT_SERVER_LISTEN_HOST: "0.0.0.0" # Default: 0.0.0.0
  MQTT_SERVER_LISTEN_PORT: "1883" # Default: 1883
  MQTT_FORCE_USER_DISCONNECT_DEBOUNCE_MILLIS: "5000" # Default: 5000
```

---

## Step-by-Step Setup

### Step 1: Expose MQTT Ports in Docker

#### For Development (using dev-ui.yml)

Add the MQTT ports to the manager service in your `profile/dev-ui.yml`:

```yaml
manager:
  extends:
    file: deploy.yml
    service: manager
  ports:
    - "8080:8080"
    - "1883:1883" # Add this line for non-SSL MQTT
    - "8883:8883" # Add this line for SSL MQTT (optional)
```

#### For Production (using docker-compose.yml)

The default `docker-compose.yml` already exposes port 8883 (MQTTS) through the proxy. To add non-SSL support, add to the proxy service:

```yaml
proxy:
  ports:
    - "80:80"
    - "${OR_SSL_PORT:-443}:443"
    - "8883:8883"
    - "1883:1883" # Add this line if needed
```

### Step 2: Start/Restart OpenRemote

```bash
# Navigate to your OpenRemote directory
cd /path/to/openremote

# For development with dev-ui.yml
docker compose -f profile/dev-ui.yml -p openremote up -d

# For production with docker-compose.yml
docker compose -p openremote up -d

# Or if you need to set a hostname
OR_HOSTNAME=192.168.1.1 docker compose -p openremote up -d
```

Wait for all services to start (usually 1-2 minutes).

### Step 3: Access the Manager UI

1. Open your browser: `https://localhost` (or your configured hostname)
2. Accept the self-signed certificate warning
3. Login with credentials:
   - **Username**: `admin`
   - **Password**: `secret`

### Step 4: Create a Service User

> **Important**: MQTT authentication requires a **Service User**, not a regular user.

1. Click **"Users"** in the left sidebar (requires admin/superuser privileges)
2. Scroll down to the **"Service Users"** section
3. Click **"Add User"** button (in the Service Users section)
4. Fill in the details:
   - **Username**: e.g., `mqtt-client1`
   - **Tag**: Optional, e.g., `mqtt-device`
   - **Enabled**: ✓ (checked)
5. Set **Roles/Permissions**:
   - Minimum: Select `read` and `write`
   - For asset access: Also select `read:assets` and `write:assets`
6. Click **"Create"**
7. **⚠️ CRITICAL**: Copy the **secret** immediately! It's only shown once.
   - Example: `a8f3c9e2-4b1d-4e6f-9a2c-7d8e3f1b4c5a`

### Step 5: Gather Connection Details

Note these details for your MQTT client:

```
Host: localhost (or your OR_HOSTNAME)
Port: 1883 (non-SSL) or 8883 (SSL)
Username: {realm}:{service-username}
Password: {secret from step 4}
Client ID: {any unique identifier}
TLS/SSL: false (port 1883) or true (port 8883)
```

**Example Connection Details**:

```
Host: localhost
Port: 1883
Username: master:mqtt-client1
Password: a8f3c9e2-4b1d-4e6f-9a2c-7d8e3f1b4c5a
Client ID: my-device-001
TLS/SSL: false
```

> **Note**: The default realm is `master`. If you created custom realms, use that realm name instead.

---

## Testing the Connection

### Option 1: Using Mosquitto Command Line Tools

Install mosquitto clients:

```bash
# Ubuntu/Debian
sudo apt-get install mosquitto-clients

# macOS
brew install mosquitto

# Windows (using scoop)
scoop install mosquitto
```

#### Test Subscribe (Listen for messages)

```bash
mosquitto_sub -h localhost -p 1883 \
  -u "master:mqtt-client" \
  -P "qLYFpnoyC6fDUs3DYn7aUnI0cJVnQjzT" \
  -i "test-client-001" \
  -t "master/test-client-001/#" \
  -v
```

#### Test Publish (Send a message)

First, get an asset ID from the Manager UI:

1. Go to **Assets** page
2. Click on any asset
3. Copy the **Asset ID** from the URL or details panel

Then publish to an attribute:

```bash
# Replace YOUR_ASSET_ID and 4nis8PsBHiMFYNMuFM6l0pp8t7Lw49Mk with actual values
mosquitto_pub -h localhost -p 1883 \
  -u "master:mqtt-client" \
  -P "qLYFpnoyC6fDUs3DYn7aUnI0cJVnQjzT" \
  -i "test-client-001" \
  -t "master/test-client-001/writeattributevalue/temperature/6sWq4q4TSAibky322EJz06" \
  -m "23.5"
```

### Option 2: Using MQTT Explorer (GUI)

1. Download [MQTT Explorer](http://mqtt-explorer.com/)
2. Click **"+ New Connection"**
3. Configure:
   - **Name**: OpenRemote Local
   - **Host**: `localhost`
   - **Port**: `1883`
   - **Username**: `master:mqtt-client1`
   - **Password**: `{your-secret}`
   - **Client ID**: `mqtt-explorer-001`
4. Click **"CONNECT"**

You should see the connection succeed and topics appear in the left panel.

---

## MQTT Topics and Usage

### Topic Structure

All topics follow this pattern:

```
{realm}/{clientId}/[type]/...
```

- `{realm}`: Your realm name (default: `master`)
- `{clientId}`: Your MQTT client ID (must match connection)

### Subscribe Topics

#### Asset Events

Subscribe to asset creation, updates, and deletion:

```
{realm}/{clientId}/asset/{assetId}
```

**Examples**:

- `master/my-client/asset/#` - All asset events in realm
- `master/my-client/asset/+` - Asset events for direct children of realm
- `master/my-client/asset/abc123def456` - Events for specific asset
- `master/my-client/asset/abc123def456/#` - Events for asset and descendants

#### Attribute Events

Subscribe to attribute value changes:

```
{realm}/{clientId}/attribute/{attributeName}/{assetId}
```

**Examples**:

- `master/my-client/attribute/+/#` - All attribute events in realm
- `master/my-client/attribute/temperature/#` - All temperature attributes
- `master/my-client/attribute/temperature/abc123` - Specific asset's temperature
- `master/my-client/attribute/+/abc123` - All attributes of specific asset

#### Attribute Values Only

To receive just the value (not the full event object), use `attributevalue` instead:

```
{realm}/{clientId}/attributevalue/{attributeName}/{assetId}
```

**Example**:

```bash
mosquitto_sub -h localhost -p 1883 \
  -u "master:mqtt-client1" \
  -P "YOUR_SECRET" \
  -i "sensor-001" \
  -t "master/sensor-001/attributevalue/temperature/abc123" \
  -v
```

### Publish Topics

#### Write Attribute Value

Publish just the value:

```
{realm}/{clientId}/writeattributevalue/{attributeName}/{assetId}
```

Payload: JSON value (string, number, boolean, object, etc.)

**Example**:

```bash
# Write a number
mosquitto_pub -h localhost -p 1883 \
  -u "master:mqtt-client1" -P "YOUR_SECRET" \
  -i "sensor-001" \
  -t "master/sensor-001/writeattributevalue/temperature/abc123" \
  -m "23.5"

# Write a boolean
mosquitto_pub -h localhost -p 1883 \
  -u "master:mqtt-client1" -P "YOUR_SECRET" \
  -i "sensor-001" \
  -t "master/sensor-001/writeattributevalue/enabled/abc123" \
  -m "true"

# Write an object
mosquitto_pub -h localhost -p 1883 \
  -u "master:mqtt-client1" -P "YOUR_SECRET" \
  -i "sensor-001" \
  -t "master/sensor-001/writeattributevalue/location/abc123" \
  -m '{"type":"Point","coordinates":[5.4604,51.4408]}'
```

#### Write Attribute with Timestamp

Publish value with a custom timestamp:

```
{realm}/{clientId}/writeattribute/{attributeName}/{assetId}
```

Payload: `{"value": <VALUE>, "timestamp": <TIMESTAMP_IN_MILLIS>}`

**Example**:

```bash
mosquitto_pub -h localhost -p 1883 \
  -u "master:mqtt-client1" -P "YOUR_SECRET" \
  -i "sensor-001" \
  -t "master/sensor-001/writeattribute/temperature/abc123" \
  -m '{"value": 23.5, "timestamp": 1700000000000}'
```

### Last Will and Testament

Configure MQTT last will to update an attribute when your client disconnects unexpectedly:

**Example using mosquitto**:

```bash
mosquitto_pub -h localhost -p 1883 \
  -u "master:mqtt-client1" -P "YOUR_SECRET" \
  -i "sensor-001" \
  --will-topic "master/sensor-001/writeattributevalue/connected/abc123" \
  --will-payload "false" \
  --will-qos 1 \
  --will-retain \
  -t "master/sensor-001/writeattributevalue/connected/abc123" \
  -m "true"
```

---

## Updating Multiple Attributes

### ⚠️ Important: One Attribute Per MQTT Message

The MQTT API is designed to update **one attribute per message**. You cannot update multiple attributes of an asset in a single MQTT publish.

### Options for Multi-Attribute Updates

#### Option 1: Publish Multiple MQTT Messages (Fastest with MQTT)

Send multiple messages in quick succession:

```bash
#!/bin/bash
# Configuration
REALM="master"
CLIENT_ID="sensor-001"
USERNAME="master:mqtt-client"
PASSWORD="YOUR_SECRET"
ASSET_ID="YOUR_ASSET_ID"
HOST="localhost"
PORT="1883"

# Update multiple attributes
mosquitto_pub -h $HOST -p $PORT -u "$USERNAME" -P "$PASSWORD" -i "$CLIENT_ID" \
  -t "$REALM/$CLIENT_ID/writeattributevalue/temperature/$ASSET_ID" -m "23.5"

mosquitto_pub -h $HOST -p $PORT -u "$USERNAME" -P "$PASSWORD" -i "$CLIENT_ID" \
  -t "$REALM/$CLIENT_ID/writeattributevalue/humidity/$ASSET_ID" -m "65"

mosquitto_pub -h $HOST -p $PORT -u "$USERNAME" -P "$PASSWORD" -i "$CLIENT_ID" \
  -t "$REALM/$CLIENT_ID/writeattributevalue/pressure/$ASSET_ID" -m "1013.25"
```

**Python Example:**

```python
import paho.mqtt.client as mqtt

# Connection details
broker = "localhost"
port = 1883
username = "master:mqtt-client"
password = "YOUR_SECRET"
client_id = "sensor-001"
asset_id = "YOUR_ASSET_ID"

# Create client
client = mqtt.Client(client_id=client_id)
client.username_pw_set(username, password)
client.connect(broker, port)

# Update multiple attributes
updates = {
    "temperature": 23.5,
    "humidity": 65,
    "pressure": 1013.25,
    "co2": 420
}

for attr_name, value in updates.items():
    topic = f"master/{client_id}/writeattributevalue/{attr_name}/{asset_id}"
    client.publish(topic, str(value))

client.disconnect()
```

#### Option 2: Use the HTTP REST API

For true batch updates, use the REST API which supports updating multiple attributes in one request:

```bash
# First, get an access token
TOKEN=$(curl -X POST "https://localhost/auth/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=YOUR_SERVICE_USER" \
  -d "client_secret=YOUR_SECRET" \
  | jq -r '.access_token')

# Update multiple attributes at once
curl -X PUT "https://localhost/api/master/asset" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "id": "YOUR_ASSET_ID",
    "attributes": {
      "temperature": {"value": 23.5},
      "humidity": {"value": 65},
      "pressure": {"value": 1013.25}
    }
  }'
```

#### Option 3: Use the WebSocket API

Connect to the WebSocket endpoint and send multiple AttributeEvents for near real-time batch updates.

#### Option 4: Create a Custom MQTT Handler

If you absolutely need batch MQTT updates, you can implement a custom `MQTTHandler`:

1. Extend the `MQTTHandler` abstract class
2. Register it in `resources/META-INF/services/org.openremote.manager.mqtt.MQTTHandler`
3. Define a custom topic format that accepts JSON arrays or objects

See the [MQTT custom handlers documentation](https://docs.openremote.io/docs/user-guide/manager-apis#mqtt-custom-handlers) for details.

---

## Troubleshooting

### Connection Refused

**Symptoms**: Cannot connect to MQTT broker

**Solutions**:

- ✅ Verify OpenRemote is running: `docker ps`
- ✅ Check MQTT ports are exposed in your docker-compose profile
- ✅ Verify port is accessible: `telnet localhost 1883`
- ✅ Check firewall settings
- ✅ Ensure you're using the correct host/port

### Authentication Failed

**Symptoms**: "Connection Refused: Not authorized" or similar error

**Solutions**:

- ✅ Verify username format: `{realm}:{username}` (e.g., `master:mqtt-client1`)
- ✅ Confirm you created a **Service User** (not a regular user)
- ✅ Check the secret was copied correctly (no extra spaces)
- ✅ Ensure service user is **Enabled** in the UI
- ✅ Verify service user has appropriate roles/permissions

### No Data Received When Subscribed

**Symptoms**: Connected successfully but no messages arrive

**Solutions**:

- ✅ Verify asset ID is correct
- ✅ Check service user has **read** permissions for the assets
- ✅ Confirm client ID in topic matches your MQTT connection client ID
- ✅ Ensure attribute name matches exactly (case-sensitive)
- ✅ Test with wildcard topics first: `master/your-client-id/#`

### Cannot Publish/Update Attributes

**Symptoms**: Publish succeeds but attribute doesn't update

**Solutions**:

- ✅ Verify service user has **write** permissions
- ✅ Check attribute name and asset ID are correct
- ✅ Ensure attribute exists on the asset
- ✅ Verify JSON payload format is correct
- ✅ Check manager logs: `docker logs openremote-manager-1`

### Port Already in Use

**Symptoms**: Docker fails to start with "port is already allocated"

**Solutions**:

```bash
# Check what's using the port
sudo lsof -i :1883
# or
netstat -an | grep 1883

# Stop the conflicting service or change the port mapping in docker-compose
# Example: "1884:1883" to use port 1884 externally
```

### SSL/TLS Issues (Port 8883)

**Symptoms**: Connection fails with SSL errors

**Solutions**:

- ✅ Use `--capath /etc/ssl/certs` or specify CA certificate
- ✅ For self-signed certs, use `--insecure` flag (testing only!)
- ✅ Ensure proxy service is running when using port 8883

---

## Additional Resources

- [OpenRemote MQTT API Documentation](https://docs.openremote.io/docs/user-guide/manager-apis#mqtt-api-mqtt-broker)
- [OpenRemote Manager UI Guide](https://docs.openremote.io/docs/user-guide/manager-ui/)
- [OpenRemote Documentation](https://docs.openremote.io)
- [MQTT Protocol Specification](https://mqtt.org/)
- [Mosquitto Client Tools](https://mosquitto.org/man/mosquitto_pub-1.html)

---

## Quick Reference

### Connection Template

```
Host: localhost
Port: 1883 (dev) / 8883 (prod)
Username: master:{service-user-name}
Password: {service-user-secret}
Client ID: {unique-identifier}
```

### Subscribe Template

```bash
mosquitto_sub -h HOST -p PORT \
  -u "REALM:USERNAME" -P "SECRET" \
  -i "CLIENT_ID" \
  -t "REALM/CLIENT_ID/attribute/ATTRIBUTE_NAME/ASSET_ID"
```

### Publish Template

```bash
mosquitto_pub -h HOST -p PORT \
  -u "REALM:USERNAME" -P "SECRET" \
  -i "CLIENT_ID" \
  -t "REALM/CLIENT_ID/writeattributevalue/ATTRIBUTE_NAME/ASSET_ID" \
  -m "VALUE"
```

---

**Happy MQTT-ing with OpenRemote! 🚀**

For questions or issues, visit the [OpenRemote Forum](https://forum.openremote.io/).
