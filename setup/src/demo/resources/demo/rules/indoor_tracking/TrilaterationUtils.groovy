package org.openremote.setup.demo.rules.indoor_tracking

import org.openremote.model.geo.GeoJSONPoint
import org.openremote.model.value.impl.LocationMeters

/**
 * Utility functions for indoor positioning trilateration
 */
class TrilaterationUtils {
    
    /**
     * Convert RSSI to distance using log-distance path loss model
     * Formula: distance = 10 ^ ((referenceRSSI - actualRSSI) / (10 * pathLossExponent))
     * 
     * @param rssi The measured RSSI value in dBm
     * @param referenceRssi The RSSI at 1 meter reference distance (typically -45 for BLE)
     * @param pathLossExponent The path loss exponent (2.0 for free space, 2.5-3.5 for indoor)
     * @return Distance in meters
     */
    static double rssiToDistance(int rssi, double referenceRssi = -45.0, double pathLossExponent = 2.0) {
        return Math.pow(10, (referenceRssi - rssi) / (10.0 * pathLossExponent))
    }
    
    /**
     * Calculate 3D Euclidean distance between two points
     * 
     * @param point1 First point with x, y, z coordinates
     * @param point2 Second point with x, y, z coordinates
     * @return Distance in meters
     */
    static double distance3D(Map point1, Map point2) {
        double dx = point1.x - point2.x
        double dy = point1.y - point2.y
        double dz = point1.z - point2.z
        return Math.sqrt(dx * dx + dy * dy + dz * dz)
    }
    
    /**
     * Calculate 3D Euclidean distance between two LocationMeters objects
     */
    static double distance3D(LocationMeters point1, LocationMeters point2) {
        double dx = point1.x - point2.x
        double dy = point1.y - point2.y
        double dz = point1.z - point2.z
        return Math.sqrt(dx * dx + dy * dy + dz * dz)
    }
    
    /**
     * Convert room meters (x, y, z) to fake GPS coordinates (lat, lng)
     * 
     * @param roomX X coordinate in room (meters)
     * @param roomY Y coordinate in room (meters)
     * @param referencePoint GeoJSONPoint with reference latitude and longitude
     * @param metersPerDegreeLat Meters per degree latitude (~111320 constant)
     * @param metersPerDegreeLng Meters per degree longitude (varies by latitude)
     * @return GeoJSONPoint with calculated fake lat/lng
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
     * Used as initial guess for trilateration
     * 
     * @param points List of maps with x, y, z coordinates
     * @return Map with average x, y, z
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
     * Uses gradient descent to minimize error between measured and estimated distances
     * 
     * @param beaconPositions List of beacon positions (maps with x, y, z in meters)
     * @param distances List of distances from tag to each beacon (in meters)
     * @param maxIterations Maximum number of optimization iterations (default 20)
     * @param learningRate Learning rate for gradient descent (default 0.1)
     * @return Map with calculated x, y, z position, or null if insufficient data
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
    
    /**
     * Calculate meters per degree longitude based on latitude
     * Formula: 111320 * cos(latitude * PI / 180)
     * 
     * @param latitude Latitude in degrees
     * @return Meters per degree longitude at this latitude
     */
    static double calculateMetersPerDegreeLng(double latitude) {
        return 111320.0 * Math.cos(Math.toRadians(latitude))
    }
}



