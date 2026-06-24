package com.handler.ride_request.kafka.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.handler.ride_request.kafka.model.DriverGeneratedEvent;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;

import java.io.IOException;

public class DriverGeneratedEventJsonDeserializer implements Deserializer<DriverGeneratedEvent> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public DriverGeneratedEvent deserialize(String topic, byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        try {
            return objectMapper.readValue(data, DriverGeneratedEvent.class);
        } catch (IOException exception) {
            throw new SerializationException(
                    "Failed to deserialize DriverGeneratedEvent from topic " + topic,
                    exception);
        }
    }
}
