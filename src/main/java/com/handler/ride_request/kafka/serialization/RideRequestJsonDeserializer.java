package com.handler.ride_request.kafka.serialization;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.handler.ride_request.domain.RideRequest;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;

import java.util.Map;

/**
 * Dedicated deserializer to avoid deprecated Spring Kafka JSON serde classes.
 */
public class RideRequestJsonDeserializer implements Deserializer<RideRequest> {

    private final ObjectMapper objectMapper;

    public RideRequestJsonDeserializer() {
        this(new JsonMapper());
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
        } catch (JacksonException ex) {
            throw new SerializationException("Failed to deserialize RideRequest", ex);
        }
    }

    @Override
    public void close() {
        // no resources to close
    }
}

