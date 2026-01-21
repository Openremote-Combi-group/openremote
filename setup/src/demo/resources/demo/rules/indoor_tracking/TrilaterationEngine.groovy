package org.openremote.setup.demo.rules.indoor_tracking

import org.openremote.manager.rules.RulesBuilder
import org.openremote.model.asset.Asset
import org.openremote.model.asset.impl.IndoorBeacon
import org.openremote.model.asset.impl.IndoorTag
import org.openremote.model.asset.impl.IndoorTrackingGroup
import org.openremote.model.attribute.AttributeEvent
import org.openremote.model.attribute.AttributeInfo
import org.openremote.model.geo.GeoJSONPoint
import org.openremote.model.query.AssetQuery
import org.openremote.model.rules.Assets
import org.openremote.model.rules.Notifications
import org.openremote.model.rules.Users
import org.openremote.model.value.impl.LocationMeters

import java.util.logging.Logger

Logger LOG = binding.LOG
RulesBuilder rules = binding.rules
Users users = binding.users
Notifications notifications = binding.notifications
Assets assets = binding.assets

// ============================================
// Trilateration Utility Functions
// ============================================

/**
 * Utility functions for indoor positioning trilateration
 */
class TrilaterationUtils {
    
    /**
     * Convert RSSI to distance using log-distance path loss model
     */
    static double rssiToDistance(int rssi, double referenceRssi = -45.0, double pathLossExponent = 2.0) {
        return Math.pow(10, (referenceRssi - rssi) / (10.0 * pathLossExponent))
    }
    
    /**
     * Calculate 3D Euclidean distance between two points
     */
    static double distance3D(Map point1, Map point2) {
        double dx = point1.x - point2.x
        double dy = point1.y - point2.y
        double dz = point1.z - point2.z
        return Math.sqrt(dx * dx + dy * dy + dz * dz)
    }
    
    /**
     * Convert room meters (x, y, z) to fake GPS coordinates (lat, lng)
     */
    static GeoJSONPoint metersToLatLng(
            double roomX, 
            double roomY, 
            GeoJSONPoint referencePoint, 
            double metersPerDegreeLat, 
            double metersPerDegreeLng) {
        
        double deltaLat = roomY / metersPerDegreeLat
        double deltaLng = roomX / metersPerDegreeLng
        
        double newLat = referencePoint.y + deltaLat
        double newLng = referencePoint.x + deltaLng
        
        return new GeoJSONPoint(newLng, newLat)
    }
    
    /**
     * Calculate average position from a list of points
     */
    static Map averagePosition(List<Map> points) {
        if (points.isEmpty()) {
            return [x: 0.0, y: 0.0, z: 0.0]
        }
        
        double sumX = 0.0
        double sumY = 0.0
        double sumZ = 0.0
        
        points.each { point ->
            sumX += point.x
            sumY += point.y
            sumZ += point.z
        }
        
        int count = points.size()
        return [
            x: sumX / count,
            y: sumY / count,
            z: sumZ / count
        ]
    }
    
    /**
     * Calculate tag position using 3D trilateration with iterative optimization
     */
    static Map calculatePosition(
            List<Map> beaconPositions, 
            List<Double> distances,
            int maxIterations = 20,
            double learningRate = 0.1) {
        
        if (beaconPositions.size() < 3 || beaconPositions.size() != distances.size()) {
            return null
        }
        
        // Initial guess: average of beacon positions
        Map guess = averagePosition(beaconPositions)
        
        // Iterative optimization using gradient descent
        for (int iteration = 0; iteration < maxIterations; iteration++) {
            double totalError = 0.0
            double gradientX = 0.0
            double gradientY = 0.0
            double gradientZ = 0.0
            
            // Calculate error and gradients for each beacon
            for (int i = 0; i < beaconPositions.size(); i++) {
                Map beacon = beaconPositions[i]
                double measuredDistance = distances[i]
                
                // Calculate estimated distance from current guess to beacon
                double estimatedDistance = distance3D(guess, beacon)
                
                // Avoid division by zero
                if (estimatedDistance < 0.01) {
                    estimatedDistance = 0.01
                }
                
                // Calculate error
                double error = estimatedDistance - measuredDistance
                totalError += error * error
                
                // Calculate gradient (derivative of squared error)
                double dx = 2.0 * error * (guess.x - beacon.x) / estimatedDistance
                double dy = 2.0 * error * (guess.y - beacon.y) / estimatedDistance
                double dz = 2.0 * error * (guess.z - beacon.z) / estimatedDistance
                
                gradientX += dx
                gradientY += dy
                gradientZ += dz
            }
            
            // Update guess using gradient descent
            guess.x -= learningRate * gradientX
            guess.y -= learningRate * gradientY
            guess.z -= learningRate * gradientZ
            
            // Clamp z to reasonable values (assume tags are at ground/table level)
            guess.z = Math.max(0.0, Math.min(3.0, guess.z))
        }
        
        return guess
    }
}

// ============================================
// Trilateration Engine Rule
// ============================================

/**
 * Trilateration Engine Rule
 * 
 * Purpose: Aggregate signal strengths from multiple beacons and calculate tag position using trilateration
 * 
 * Trigger: When any IndoorBeacon's tagDetections attribute changes
 * 
 * Logic:
 * 1. Monitor beacon tagDetections attributes for changes
 * 2. When changes detected, query all Beacon assets for their current tagDetections
 * 3. Group detections by tag MAC address
 * 4. For each tag detected by >= minBeaconsForTrilateration beacons:
 *    a. Extract beacon positions (x, y, z in meters) and RSSI values
 *    b. Convert RSSI to distance using path loss formula
 *    c. Run trilateration algorithm (gradient descent optimization)
 *    d. Convert calculated (x, y, z) to fake lat/lng using referencePoint
 *    e. Update Tag asset's location and locationMeters attributes
 */

rules.add()
        .name("Indoor Tracking: Trilateration Engine")
        .when(
                { facts ->
                    List<AttributeInfo> changes = Collections.synchronizedList(new ArrayList<>())
                    boolean hasChanges = false
                    
                    // Check all beacon's tagDetections attributes for changes
                    facts.matchAssetState(
                            new AssetQuery()
                                    .types(IndoorBeacon)
                                    .attributeNames(IndoorBeacon.TAG_DETECTIONS.name)
                    ).each { state ->
                        def stateKey = "trilateration-" + state.id + "-" + state.name
                        def previous = facts.matchFirst(stateKey) as Optional<AttributeInfo>
                        
                        // Check if this is new or updated
                        if (previous.isEmpty() || state.timestamp > previous.map { it.timestamp }.orElse(0)) {
                            changes.add(state)
                            hasChanges = true
                        }
                    }
                    
                    if (!hasChanges) {
                        return false
                    }
                    
                    LOG.info("Trilateration engine triggered by ${changes.size()} beacon update(s)")
                    
                    // Query all beacon assets to get their current state
                    def beaconList = assets.getResults(
                            new AssetQuery()
                                    .types(IndoorBeacon)
                    ).collect()  // Convert stream to list immediately
                    
                    if (beaconList == null || beaconList.isEmpty()) {
                        LOG.warning("No beacons found for trilateration")
                        return false
                    }
                    
                    if (beaconList.size() < 3) {
                        LOG.fine("Not enough beacons (${beaconList.size()}) for trilateration, need at least 3")
                        return false
                    }
                    
                    facts.bind("changes", changes)
                    LOG.info("Trilateration using ${beaconList.size()} beacon(s)")
                    
                    // Get tracking group configuration from asset state facts
                    def trackingGroupStates = facts.matchAssetState(
                            new AssetQuery()
                                    .types(IndoorTrackingGroup)
                    ).collect()
                    
                    if (trackingGroupStates.isEmpty()) {
                        LOG.warning("No IndoorTrackingGroup found - cannot perform trilateration")
                        return false
                    }
                    
                    // Get the tracking group asset ID
                    def trackingGroupId = trackingGroupStates[0].id
                    
                    // Read configuration from asset state facts
                    def referencePointState = facts.matchAssetState(
                            new AssetQuery()
                                    .ids(trackingGroupId)
                                    .attributeNames(IndoorTrackingGroup.REFERENCE_POINT.name)
                    ).findFirst().orElse(null)
                    
                    def referencePoint = referencePointState?.value?.orElse(null) as GeoJSONPoint
                    
                    if (referencePoint == null) {
                        LOG.warning("No reference point configured in tracking group ${trackingGroupId}")
                        return false
                    }
                    
                    // Read other configuration values with defaults
                    def minBeacons = facts.matchAssetState(
                            new AssetQuery().ids(trackingGroupId).attributeNames(IndoorTrackingGroup.MIN_BEACONS_FOR_TRILATERATION.name)
                    ).findFirst().map{it.value.orElse(3)}.orElse(3) as Integer
                    
                    def referenceRssi = facts.matchAssetState(
                            new AssetQuery().ids(trackingGroupId).attributeNames(IndoorTrackingGroup.REFERENCE_RSSI.name)
                    ).findFirst().map{it.value.orElse(-45.0)}.orElse(-45.0) as Double
                    
                    def pathLossExponent = facts.matchAssetState(
                            new AssetQuery().ids(trackingGroupId).attributeNames(IndoorTrackingGroup.PATH_LOSS_EXPONENT.name)
                    ).findFirst().map{it.value.orElse(2.0)}.orElse(2.0) as Double
                    
                    def metersPerDegreeLat = facts.matchAssetState(
                            new AssetQuery().ids(trackingGroupId).attributeNames(IndoorTrackingGroup.METERS_PER_DEGREE_LAT.name)
                    ).findFirst().map{it.value.orElse(111320.0)}.orElse(111320.0) as Double
                    
                    def metersPerDegreeLng = facts.matchAssetState(
                            new AssetQuery().ids(trackingGroupId).attributeNames(IndoorTrackingGroup.METERS_PER_DEGREE_LNG.name)
                    ).findFirst().map{it.value.orElse(111320.0)}.orElse(111320.0) as Double
                    
                    LOG.info("Tracking group config: minBeacons=${minBeacons}, referenceRssi=${referenceRssi}, pathLoss=${pathLossExponent}")
                    
                    // Group tag detections by MAC address across all beacons
                    Map<String, List<Map>> tagDetectionsByMac = [:]
                    int beaconsWithLocation = 0
                    int beaconsWithDetections = 0
                    
                    beaconList.each { beacon ->
                        def beaconId = beacon.id
                        def beaconName = beacon.name
                        
                        // Read locationMeters from asset state facts
                        def locationState = facts.matchAssetState(
                                new AssetQuery()
                                        .ids(beaconId)
                                        .attributeNames(IndoorBeacon.LOCATION_METERS.name)
                        ).findFirst().orElse(null)
                        
                        def beaconLocation = locationState?.value?.orElse(null) as LocationMeters
                        
                        if (beaconLocation == null) {
                            LOG.warning("Beacon ${beaconId} (${beaconName}) has no locationMeters - skipping for trilateration")
                            return
                        }
                        
                        beaconsWithLocation++
                        
                        // Read tagDetections from asset state facts
                        def tagDetectionsState = facts.matchAssetState(
                                new AssetQuery()
                                        .ids(beaconId)
                                        .attributeNames(IndoorBeacon.TAG_DETECTIONS.name)
                        ).findFirst().orElse(null)
                        
                        def tagDetections = tagDetectionsState?.value?.orElse(null)
                        
                        if (tagDetections == null || !(tagDetections instanceof Map)) {
                            LOG.fine("Beacon ${beaconId} (${beaconName}) has no tagDetections")
                            return
                        }
                        
                        if (!tagDetections.isEmpty()) {
                            beaconsWithDetections++
                            LOG.fine("Beacon ${beaconId} (${beaconName}) has ${tagDetections.size()} tag detection(s)")
                        }
                        
                        // Add each tag detection to the grouped map
                        tagDetections.each { macAddress, detection ->
                            if (!tagDetectionsByMac.containsKey(macAddress)) {
                                tagDetectionsByMac[macAddress] = []
                            }
                            
                            tagDetectionsByMac[macAddress].add([
                                beaconId: beaconId,
                                beaconLocation: [x: beaconLocation.x, y: beaconLocation.y, z: beaconLocation.z],
                                rssi: detection.rssi as Integer,
                                assetId: detection.assetId,
                                timestamp: detection.timestamp
                            ])
                        }
                    }
                    
                    LOG.info("Trilateration summary: ${beaconList.size()} total beacons, ${beaconsWithLocation} with location, ${beaconsWithDetections} with detections, ${tagDetectionsByMac.size()} unique tag(s) detected")
                    
                    // Calculate positions for tags detected by sufficient beacons
                    List<Map> positionUpdates = []
                    
                    tagDetectionsByMac.each { macAddress, detections ->
                        if (detections.size() < minBeacons) {
                            LOG.fine("Tag ${macAddress} detected by only ${detections.size()} beacon(s), need ${minBeacons}")
                            return
                        }
                        
                        LOG.info("Calculating position for tag ${macAddress} using ${detections.size()} beacon(s)")
                        
                        // Extract beacon positions and convert RSSI to distances
                        List<Map> beaconPositions = []
                        List<Double> distances = []
                        
                        detections.each { detection ->
                            beaconPositions.add(detection.beaconLocation)
                            
                            // Convert RSSI to distance
                            double distance = TrilaterationUtils.rssiToDistance(
                                    detection.rssi, 
                                    referenceRssi, 
                                    pathLossExponent
                            )
                            distances.add(distance)
                            
                            LOG.fine("Beacon ${detection.beaconId}: RSSI=${detection.rssi} -> distance=${String.format('%.2f', distance)}m")
                        }
                        
                        // Calculate tag position using trilateration
                        Map calculatedPosition = TrilaterationUtils.calculatePosition(
                                beaconPositions,
                                distances,
                                20,  // maxIterations
                                0.1  // learningRate
                        )
                        
                        if (calculatedPosition == null) {
                            LOG.warning("Failed to calculate position for tag ${macAddress}")
                            return
                        }
                        
                        LOG.info("Calculated position for tag ${macAddress}: " +
                                "x=${String.format('%.2f', calculatedPosition.x)}, " +
                                "y=${String.format('%.2f', calculatedPosition.y)}, " +
                                "z=${String.format('%.2f', calculatedPosition.z)}")
                        
                        // Convert to lat/lng
                        GeoJSONPoint geoPosition = TrilaterationUtils.metersToLatLng(
                                calculatedPosition.x,
                                calculatedPosition.y,
                                referencePoint,
                                metersPerDegreeLat,
                                metersPerDegreeLng
                        )
                        
                        // Store for update
                        positionUpdates.add([
                            assetId: detections[0].assetId,
                            macAddress: macAddress,
                            locationMeters: new LocationMeters(calculatedPosition.x, calculatedPosition.y, calculatedPosition.z),
                            location: geoPosition,
                            beaconCount: detections.size()
                        ])
                    }
                    
                    if (!positionUpdates.isEmpty()) {
                        LOG.info("Generated ${positionUpdates.size()} position update(s)")
                        facts.bind("positionUpdates", positionUpdates)
                        return true
                    }
                    
                    return false
                })
        .then(
                { facts ->
                    def changes = facts.bound("changes") as List<AttributeInfo>
                    def positionUpdates = facts.bound("positionUpdates") as List<Map>
                    
                    // Store updated state for future comparisons
                    changes.each { state ->
                        def stateKey = "trilateration-" + state.id + "-" + state.name
                        facts.put(stateKey, state)
                    }
                    
                    // Dispatch position updates if we have any
                    if (positionUpdates != null && !positionUpdates.isEmpty()) {
                        LOG.info("Dispatching ${positionUpdates.size()} position updates to tag assets")
                        
                        List<AttributeEvent> updates = []
                        
                        positionUpdates.each { update ->
                            // Update locationMeters
                            updates.add(new AttributeEvent(
                                    update.assetId, 
                                    IndoorTag.LOCATION_METERS.name, 
                                    update.locationMeters
                            ))
                            
                            // Update location (GeoJSON Point)
                            updates.add(new AttributeEvent(
                                    update.assetId, 
                                    Asset.LOCATION.name, 
                                    update.location
                            ))
                            
                            // Update beacon count
                            updates.add(new AttributeEvent(
                                    update.assetId, 
                                    IndoorTag.BEACON_COUNT.name, 
                                    update.beaconCount
                            ))
                            
                            LOG.info("Tag ${update.macAddress} (${update.assetId}): " +
                                    "position=(${String.format('%.2f', update.locationMeters.x)}, " +
                                    "${String.format('%.2f', update.locationMeters.y)}, " +
                                    "${String.format('%.2f', update.locationMeters.z)}), " +
                                    "geo=(${String.format('%.6f', update.location.y)}, " +
                                    "${String.format('%.6f', update.location.x)})")
                        }
                        
                        // Dispatch all updates
                        updates.each { update ->
                            assets.dispatch(update)
                        }
                        
                        LOG.info("Trilateration complete")
                    }
                })

