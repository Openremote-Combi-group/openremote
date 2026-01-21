package org.openremote.setup.demo.rules.indoor_tracking

import org.openremote.manager.rules.RulesBuilder
import org.openremote.model.asset.Asset
import org.openremote.model.asset.impl.IndoorBeacon
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

/**
 * Beacon Location Converter Rule
 * 
 * Purpose: Convert beacon locationMeters to location (GeoJSON Point) for map display
 * 
 * Trigger: When any IndoorBeacon's locationMeters attribute changes
 * 
 * Logic:
 * 1. Detect changes in beacon locationMeters attributes
 * 2. Get the tracking group's referencePoint
 * 3. Convert locationMeters (x, y, z) to lat/lng using the reference point
 * 4. Update the beacon's location attribute
 */

rules.add()
        .name("Indoor Tracking: Beacon Location Converter")
        .when(
                { facts ->
                    List<AttributeInfo> changes = Collections.synchronizedList(new ArrayList<>())
                    List<AttributeEvent> locationUpdates = []
                    
                    // Check all beacon locationMeters attributes for changes
                    facts.matchAssetState(
                            new AssetQuery()
                                    .types(IndoorBeacon)
                                    .attributeNames(IndoorBeacon.LOCATION_METERS.name)
                    ).each { state ->
                        def stateKey = "beacon-loc-" + state.id + "-" + state.name
                        def previous = facts.matchFirst(stateKey) as Optional<AttributeInfo>
                        
                        // Check if this is new or updated
                        if (previous.isEmpty() || state.timestamp > previous.map { it.timestamp }.orElse(0)) {
                            changes.add(state)
                            
                            def locationMeters = state.value.orElse(null) as LocationMeters
                            
                            if (locationMeters != null) {
                                LOG.info("Beacon ${state.id} locationMeters changed, converting to location")
                                
                                // Get tracking group reference point
                                def trackingGroupStates = facts.matchAssetState(
                                        new AssetQuery()
                                                .types(IndoorTrackingGroup)
                                ).collect()
                                
                                if (!trackingGroupStates.isEmpty()) {
                                    def trackingGroupId = trackingGroupStates[0].id
                                    
                                    // Read referencePoint
                                    def referencePointState = facts.matchAssetState(
                                            new AssetQuery()
                                                    .ids(trackingGroupId)
                                                    .attributeNames(IndoorTrackingGroup.REFERENCE_POINT.name)
                                    ).findFirst().orElse(null)
                                    
                                    def referencePoint = referencePointState?.value?.orElse(null) as GeoJSONPoint
                                    
                                    if (referencePoint != null) {
                                        // Read conversion parameters
                                        def metersPerDegreeLat = facts.matchAssetState(
                                                new AssetQuery().ids(trackingGroupId).attributeNames(IndoorTrackingGroup.METERS_PER_DEGREE_LAT.name)
                                        ).findFirst().map{it.value.orElse(111320.0)}.orElse(111320.0) as Double
                                        
                                        def metersPerDegreeLng = facts.matchAssetState(
                                                new AssetQuery().ids(trackingGroupId).attributeNames(IndoorTrackingGroup.METERS_PER_DEGREE_LNG.name)
                                        ).findFirst().map{it.value.orElse(111320.0)}.orElse(111320.0) as Double
                                        
                                        // Convert locationMeters to lat/lng
                                        double deltaLat = locationMeters.y / metersPerDegreeLat
                                        double deltaLng = locationMeters.x / metersPerDegreeLng
                                        
                                        double newLat = referencePoint.y + deltaLat
                                        double newLng = referencePoint.x + deltaLng
                                        
                                        def geoLocation = new GeoJSONPoint(newLng, newLat)
                                        
                                        // Create location update event
                                        locationUpdates.add(new AttributeEvent(state.id, Asset.LOCATION.name, geoLocation))
                                        
                                        LOG.info("Beacon ${state.id} location calculated: (${String.format('%.6f', newLat)}, ${String.format('%.6f', newLng)}) from meters (${locationMeters.x}, ${locationMeters.y}, ${locationMeters.z})")
                                    } else {
                                        LOG.warning("Cannot convert beacon location: no reference point in tracking group")
                                    }
                                } else {
                                    LOG.warning("Cannot convert beacon location: no tracking group found")
                                }
                            }
                        }
                    }
                    
                    if (!changes.isEmpty()) {
                        facts.bind("changes", changes)
                        
                        if (!locationUpdates.isEmpty()) {
                            facts.bind("locationUpdates", locationUpdates)
                        }
                        return true
                    }
                    
                    return false
                })
        .then(
                { facts ->
                    def changes = facts.bound("changes") as List<AttributeInfo>
                    def locationUpdates = facts.bound("locationUpdates") as List<AttributeEvent>
                    
                    // Store updated state for future comparisons
                    changes.each { state ->
                        def stateKey = "beacon-loc-" + state.id + "-" + state.name
                        facts.put(stateKey, state)
                    }
                    
                    // Dispatch location updates if we have any
                    if (locationUpdates != null && !locationUpdates.isEmpty()) {
                        LOG.info("Dispatching ${locationUpdates.size()} beacon location updates")
                        locationUpdates.each { update ->
                            assets.dispatch(update)
                        }
                        LOG.info("Beacon location conversion complete")
                    }
                })



