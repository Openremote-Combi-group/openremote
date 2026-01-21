package org.openremote.model.asset.impl;


import org.openremote.model.asset.Asset;
import org.openremote.model.asset.AssetDescriptor;
import org.openremote.model.attribute.MetaItem;
import org.openremote.model.value.AttributeDescriptor;
import org.openremote.model.value.MetaItemType;
import org.openremote.model.value.ValueType;
import org.openremote.model.value.impl.LocationMeters;

import jakarta.persistence.Entity;

@Entity
public class IndoorBeacon extends Asset<IndoorBeacon> {

    // Attributes
    // location attribute is inherited from Asset base class (GeoJSONPoint)
    
    public static final AttributeDescriptor<LocationMeters> LOCATION_METERS = 
        new AttributeDescriptor<>("locationMeters", ValueType.LOCATION_METERS,
            new MetaItem<>(MetaItemType.RULE_STATE, true)
        );

    public static final AttributeDescriptor<ValueType.ObjectMap> TAG_DETECTIONS = 
        new AttributeDescriptor<>("tagDetections", ValueType.TAG_DETECTIONS,
            new MetaItem<>(MetaItemType.RULE_STATE, true)
        );

    public static final AttributeDescriptor<Double> DETECTION_RADIUS = 
        new AttributeDescriptor<>("detectionRadius", ValueType.POSITIVE_NUMBER);

    // Define the descriptor with icon, color, and class
    public static final AssetDescriptor<IndoorBeacon> DESCRIPTOR = 
        new AssetDescriptor<>("sitemap", "3B82F6", IndoorBeacon.class);

    // Required no-arg constructor for JPA/Jackson
    protected IndoorBeacon() {
    }

    // Public constructor with name
    public IndoorBeacon(String name) {
        super(name);
    }
}