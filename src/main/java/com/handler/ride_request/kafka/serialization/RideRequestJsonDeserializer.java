package com.handler.ride_request.kafka.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.handler.ride_request.model.RideRequest;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;

import java.io.IOException;
import java.util.Map;

/**
 * Dedicated deserializer to avoid deprecated Spring Kafka JSON serde classes.
 */
public class RideRequestJsonDeserializer implements Deserializer<RideRequest> {

    private final ObjectMapper objectMapper;

    public RideRequestJsonDeserializer() {
        this(new ObjectMapper());
    }

    // Visible for testing
    RideRequestJsonDeserializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        // no-op
    }

    @Override
    public RideRequest deserialize(String topic, byte[] data) {
        if (data == null) {
            return null;
        }
        try {
            return objectMapper.readValue(data, RideRequest.class);
        } catch (IOException ex) {
            throw new SerializationException("Failed to deserialize RideRequest", ex);
        }
    }

    @Override
    public void close() {
        // no resources to close
    }
}

