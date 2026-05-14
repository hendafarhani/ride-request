package com.handler.ride_request.kafka.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.handler.ride_request.model.Location;
import com.handler.ride_request.model.RideRequest;
import org.apache.kafka.common.errors.SerializationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RideRequestJsonDeserializerTest {

    private ObjectMapper objectMapper;
    private RideRequestJsonDeserializer deserializer;

    @BeforeEach
    void setUp() {
        objectMapper = mock(ObjectMapper.class);
        deserializer = new RideRequestJsonDeserializer(objectMapper);
    }

    @Test
    void deserializeShouldReturnNullWhenPayloadIsNull() {
        RideRequest result = deserializer.deserialize("ride-topic", null);

        assertNull(result);
        verifyNoInteractions(objectMapper);
    }

    @Test
    void deserializeShouldMapPayloadToRideRequest() throws Exception {
        byte[] payload = "{\"userIdentifier\":\"user-1\",\"location\":{\"latitude\":10.5,\"longitude\":20.7}}".getBytes();
        RideRequest expected = RideRequest.builder()
                .userIdentifier("user-1")
                .location(Location.builder().latitude(10.5).longitude(20.7).build())
                .build();
        when(objectMapper.readValue(payload, RideRequest.class)).thenReturn(expected);

        RideRequest result = deserializer.deserialize("ride-topic", payload);

        assertSame(expected, result);
        verify(objectMapper).readValue(payload, RideRequest.class);
    }

    @Test
    void deserializeShouldWrapIOExceptionInSerializationException() throws Exception {
        byte[] payload = "invalid-json".getBytes();
        IOException cause = new IOException("boom");
        when(objectMapper.readValue(payload, RideRequest.class)).thenThrow(cause);

        SerializationException exception = assertThrows(
                SerializationException.class,
                () -> deserializer.deserialize("ride-topic", payload)
        );

        assertEquals("Failed to deserialize RideRequest", exception.getMessage());
        assertSame(cause, exception.getCause());
    }

    @Test
    void configureShouldBeNoOp() {
        assertDoesNotThrow(() -> deserializer.configure(Map.of("key", "value"), false));
    }

    @Test
    void closeShouldBeNoOp() {
        assertDoesNotThrow(() -> deserializer.close());
    }
}

