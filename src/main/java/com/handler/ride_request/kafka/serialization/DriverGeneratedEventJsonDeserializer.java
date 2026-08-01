package com.handler.ride_request.kafka.serialization;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.handler.ride_request.kafka.model.DriverGeneratedEvent;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;

public class DriverGeneratedEventJsonDeserializer implements Deserializer<DriverGeneratedEvent> {

    private final ObjectMapper objectMapper = new JsonMapper();

    @Override
    public DriverGeneratedEvent deserialize(String topic, byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        try {
            return objectMapper.readValue(data, DriverGeneratedEvent.class);
        } catch (JacksonException exception) {
            throw new SerializationException(
                    "Failed to deserialize DriverGeneratedEvent from topic " + topic,
                    exception);
        }
    }
}
