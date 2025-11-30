package org.openremote.model.asset.impl;

import static org.openremote.model.Constants.UNITS_CELSIUS;
import static org.openremote.model.Constants.UNITS_PERCENTAGE;


import org.openremote.model.asset.Asset;
import org.openremote.model.asset.AssetDescriptor;
import org.openremote.model.value.AttributeDescriptor;
import org.openremote.model.value.ValueType;
import org.openremote.model.value.impl.GyroData;

import jakarta.persistence.Entity;

@Entity
public class IndoorTag extends Asset<IndoorTag> {

    // Attributes
    public static final AttributeDescriptor<Double> TEMPERATURE = 
        new AttributeDescriptor<>("temperature", ValueType.NUMBER).withUnits(UNITS_CELSIUS);

    public static final AttributeDescriptor<Double> HUMIDITY = 
        new AttributeDescriptor<>("humidity", ValueType.NUMBER).withUnits(UNITS_PERCENTAGE);
    
    public static final AttributeDescriptor<GyroData> GYRO =
        new AttributeDescriptor<>("gyro", ValueType.GYRO_DATA);

    public static final AttributeDescriptor<Double> SIGNAL_STRENGTH =
        new AttributeDescriptor<>("signalStrength", ValueType.NUMBER);

    // Define the descriptor with icon, color, and class
    public static final AssetDescriptor<IndoorTag> DESCRIPTOR = 
        new AssetDescriptor<>("map-marker", "DB8412", IndoorTag.class);

    // Required no-arg constructor for JPA/Jackson
    protected IndoorTag() {
    }

    // Public constructor with name
    public IndoorTag(String name) {
        super(name);
    }
}