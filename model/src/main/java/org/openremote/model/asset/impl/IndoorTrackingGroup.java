/*
 * Copyright 2024, OpenRemote Inc.
 *
 * See the CONTRIBUTORS.txt file in the distribution for a
 * full listing of individual contributors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.openremote.model.asset.impl;

import org.openremote.model.asset.Asset;
import org.openremote.model.asset.AssetDescriptor;
import org.openremote.model.attribute.MetaItem;
import org.openremote.model.geo.GeoJSONPoint;
import org.openremote.model.value.AttributeDescriptor;
import org.openremote.model.value.MetaItemType;
import org.openremote.model.value.ValueType;

import jakarta.persistence.Entity;

/**
 * An asset for grouping indoor tracking beacons and tags together.
 * Stores configuration for coordinate conversion and triangulation settings.
 */
@Entity
public class IndoorTrackingGroup extends Asset<IndoorTrackingGroup> {

    // Attributes for coordinate conversion
    public static final AttributeDescriptor<GeoJSONPoint> REFERENCE_POINT = 
        new AttributeDescriptor<>("referencePoint", ValueType.GEO_JSON_POINT,
            new MetaItem<>(MetaItemType.RULE_STATE, true)
        );

    public static final AttributeDescriptor<Double> METERS_PER_DEGREE_LAT = 
        new AttributeDescriptor<>("metersPerDegreeLat", ValueType.POSITIVE_NUMBER,
            new MetaItem<>(MetaItemType.RULE_STATE, true)
        ); // Default: ~111320

    public static final AttributeDescriptor<Double> METERS_PER_DEGREE_LNG = 
        new AttributeDescriptor<>("metersPerDegreeLng", ValueType.POSITIVE_NUMBER,
            new MetaItem<>(MetaItemType.RULE_STATE, true)
        ); // Will be calculated based on latitude

    // Triangulation configuration
    public static final AttributeDescriptor<Integer> MIN_BEACONS_FOR_TRILATERATION = 
        new AttributeDescriptor<>("minBeaconsForTrilateration", ValueType.POSITIVE_INTEGER,
            new MetaItem<>(MetaItemType.RULE_STATE, true)
        ); // Default: 3

    public static final AttributeDescriptor<Double> TRILATERATION_UPDATE_INTERVAL = 
        new AttributeDescriptor<>("trilaterationUpdateInterval", ValueType.POSITIVE_NUMBER,
            new MetaItem<>(MetaItemType.RULE_STATE, true)
        ); // Default: 2.0 seconds

    // RSSI to distance conversion parameters
    public static final AttributeDescriptor<Double> REFERENCE_RSSI = 
        new AttributeDescriptor<>("referenceRssi", ValueType.NUMBER,
            new MetaItem<>(MetaItemType.RULE_STATE, true)
        ); // Default: -45.0 (RSSI at 1 meter)

    public static final AttributeDescriptor<Double> PATH_LOSS_EXPONENT = 
        new AttributeDescriptor<>("pathLossExponent", ValueType.POSITIVE_NUMBER,
            new MetaItem<>(MetaItemType.RULE_STATE, true)
        ); // Default: 2.0 for free space, 2.5-3.5 for indoor

    // Define the descriptor with icon, color, and class
    public static final AssetDescriptor<IndoorTrackingGroup> DESCRIPTOR = 
        new AssetDescriptor<>("map-marked-alt", "10B981", IndoorTrackingGroup.class);

    // Required no-arg constructor for JPA/Jackson
    protected IndoorTrackingGroup() {
    }

    // Public constructor with name
    public IndoorTrackingGroup(String name) {
        super(name);
    }
}

