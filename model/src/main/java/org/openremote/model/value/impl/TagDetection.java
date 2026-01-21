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
package org.openremote.model.value.impl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.Objects;

/**
 * Represents a single tag detection by a beacon including sensor data and RSSI
 */
public class TagDetection implements Serializable {

    @JsonProperty("assetId")
    protected String assetId;

    @JsonProperty("rssi")
    protected int rssi;

    @JsonProperty("temperature")
    protected int temperature;

    @JsonProperty("humidity")
    protected int humidity;

    @JsonProperty("gyro")
    protected GyroData gyro;

    @JsonProperty("timestamp")
    protected long timestamp;

    // Required no-arg constructor for Jackson
    protected TagDetection() {
    }

    @JsonCreator
    public TagDetection(@JsonProperty("assetId") String assetId,
                       @JsonProperty("rssi") int rssi,
                       @JsonProperty("temperature") int temperature,
                       @JsonProperty("humidity") int humidity,
                       @JsonProperty("gyro") GyroData gyro,
                       @JsonProperty("timestamp") long timestamp) {
        this.assetId = assetId;
        this.rssi = rssi;
        this.temperature = temperature;
        this.humidity = humidity;
        this.gyro = gyro;
        this.timestamp = timestamp;
    }

    public String getAssetId() {
        return assetId;
    }

    public void setAssetId(String assetId) {
        this.assetId = assetId;
    }

    public int getRssi() {
        return rssi;
    }

    public void setRssi(int rssi) {
        this.rssi = rssi;
    }

    public int getTemperature() {
        return temperature;
    }

    public void setTemperature(int temperature) {
        this.temperature = temperature;
    }

    public int getHumidity() {
        return humidity;
    }

    public void setHumidity(int humidity) {
        this.humidity = humidity;
    }

    public GyroData getGyro() {
        return gyro;
    }

    public void setGyro(GyroData gyro) {
        this.gyro = gyro;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TagDetection that = (TagDetection) o;
        return rssi == that.rssi &&
               temperature == that.temperature &&
               humidity == that.humidity &&
               timestamp == that.timestamp &&
               Objects.equals(assetId, that.assetId) &&
               Objects.equals(gyro, that.gyro);
    }

    @Override
    public int hashCode() {
        return Objects.hash(assetId, rssi, temperature, humidity, gyro, timestamp);
    }

    @Override
    public String toString() {
        return "TagDetection{" +
            "assetId='" + assetId + '\'' +
            ", rssi=" + rssi +
            ", temperature=" + temperature +
            ", humidity=" + humidity +
            ", gyro=" + gyro +
            ", timestamp=" + timestamp +
            '}';
    }
}



