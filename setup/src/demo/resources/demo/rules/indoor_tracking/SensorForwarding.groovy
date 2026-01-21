package org.openremote.setup.demo.rules.indoor_tracking

import org.openremote.manager.rules.RulesBuilder
import org.openremote.model.asset.impl.IndoorBeacon
import org.openremote.model.asset.impl.IndoorTag
import org.openremote.model.attribute.AttributeEvent
import org.openremote.model.attribute.AttributeInfo
import org.openremote.model.query.AssetQuery
import org.openremote.model.rules.Assets
import org.openremote.model.rules.Notifications
import org.openremote.model.rules.Users
import org.openremote.model.value.impl.GyroData

import java.util.logging.Logger

Logger LOG = binding.LOG
RulesBuilder rules = binding.rules
Users users = binding.users
Notifications notifications = binding.notifications
Assets assets = binding.assets

/**
 * Sensor Data Forwarding Rule
 * 
 * Purpose: Forward sensor data from Beacon's tagDetections attribute to individual Tag assets
 * 
 * Trigger: When any IndoorBeacon's tagDetections attribute changes
 * 
 * Logic:
 * 1. Monitor beacon tagDetections attributes for changes
 * 2. Parse tagDetections JSON from changed beacons
 * 3. For each detected tag (by MAC address):
 *    a. Extract assetId, temperature, humidity, gyro, timestamp
 *    b. Forward these values to the corresponding Tag asset
 */

rules.add()
        .name("Indoor Tracking: Sensor Data Forwarding")
        .when(
                { facts ->
                    List<AttributeInfo> changes = Collections.synchronizedList(new ArrayList<>())
                    List<AttributeEvent> updates = []
                    
                    // Check all beacon's tagDetections attributes for changes
                    facts.matchAssetState(
                            new AssetQuery()
                                    .types(IndoorBeacon)
                                    .attributeNames(IndoorBeacon.TAG_DETECTIONS.name)
                    ).each { state ->
                        def stateKey = state.id + "-" + state.name
                        def previous = facts.matchFirst(stateKey) as Optional<AttributeInfo>
                        
                        // Check if this is new or updated
                        if (previous.isEmpty() || state.timestamp > previous.map { it.timestamp }.orElse(0)) {
                            changes.add(state)
                            
                            def tagDetections = state.value.orElse(null)
                            
                            if (tagDetections != null && tagDetections instanceof Map && !tagDetections.isEmpty()) {
                                LOG.info("Processing tagDetections from beacon ${state.id}: ${tagDetections.size()} tag(s)")
                                
                                // Iterate through each detected tag
                                tagDetections.each { macAddress, detection ->
                                    def assetId = detection.assetId
                                    
                                    if (assetId == null || assetId.toString().isEmpty()) {
                                        LOG.warning("Tag ${macAddress} has no assetId")
                                        return
                                    }
                                    
                                    LOG.fine("Forwarding data for tag ${macAddress} -> asset ${assetId}")
                                    
                                    // Create attribute update events for the tag asset
                                    // Temperature (in hundredths of °C, but store as decimal °C)
                                    if (detection.temperature != null) {
                                        def tempCelsius = (detection.temperature as Integer) / 100.0
                                        updates.add(new AttributeEvent(assetId, IndoorTag.TEMPERATURE.name, tempCelsius))
                                    }
                                    
                                    // Humidity (in hundredths of %, but store as decimal %)
                                    if (detection.humidity != null) {
                                        def humidityPercent = (detection.humidity as Integer) / 100.0
                                        updates.add(new AttributeEvent(assetId, IndoorTag.HUMIDITY.name, humidityPercent))
                                    }
                                    
                                    // Gyro data (as GyroData object)
                                    if (detection.gyro != null && detection.gyro instanceof Map) {
                                        def gyroData = new GyroData(
                                                detection.gyro.x as double,
                                                detection.gyro.y as double,
                                                detection.gyro.z as double
                                        )
                                        updates.add(new AttributeEvent(assetId, IndoorTag.GYRO.name, gyroData))
                                    }
                                    
                                    // Last seen timestamp
                                    if (detection.timestamp != null) {
                                        updates.add(new AttributeEvent(assetId, IndoorTag.LAST_SEEN.name, detection.timestamp as Long))
                                    }
                                    
                                    // Signal strength (RSSI) - forward from this beacon
                                    if (detection.rssi != null) {
                                        updates.add(new AttributeEvent(assetId, IndoorTag.SIGNAL_STRENGTH.name, detection.rssi as Integer))
                                    }
                                }
                            }
                        }
                    }
                    
                    if (!changes.isEmpty()) {
                        facts.bind("changes", changes)
                        
                        if (!updates.isEmpty()) {
                            LOG.info("Generated ${updates.size()} attribute updates for tags")
                            facts.bind("sensorUpdates", updates)
                        }
                        return true
                    }
                    
                    return false
                })
        .then(
                { facts ->
                    def changes = facts.bound("changes") as List<AttributeInfo>
                    def updates = facts.bound("sensorUpdates") as List<AttributeEvent>
                    
                    // Store updated state for future comparisons
                    changes.each { state ->
                        def stateKey = state.id + "-" + state.name
                        facts.put(stateKey, state)
                    }
                    
                    // Dispatch attribute updates if we have any
                    if (updates != null && !updates.isEmpty()) {
                        LOG.info("Dispatching ${updates.size()} sensor data updates to tag assets")
                        updates.each { update ->
                            assets.dispatch(update)
                        }
                        LOG.info("Sensor data forwarding complete")
                    }
                })

