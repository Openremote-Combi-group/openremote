package org.openremote.model.asset.impl;


import org.openremote.model.asset.Asset;
import org.openremote.model.asset.AssetDescriptor;

import jakarta.persistence.Entity;

@Entity
public class IndoorBeacon extends Asset<IndoorBeacon> {

    // Define the descriptor with icon, color, and class
    public static final AssetDescriptor<IndoorBeacon> DESCRIPTOR = 
        new AssetDescriptor<>("sitemap", "DB8412", IndoorBeacon.class);

    // Required no-arg constructor for JPA/Jackson
    protected IndoorBeacon() {
    }

    // Public constructor with name
    public IndoorBeacon(String name) {
        super(name);
    }
}