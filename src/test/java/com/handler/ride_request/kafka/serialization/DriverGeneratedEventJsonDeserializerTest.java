package com.handler.ride_request.kafka.serialization;

import org.apache.kafka.common.errors.SerializationException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DriverGeneratedEventJsonDeserializerTest {

    private final DriverGeneratedEventJsonDeserializer deserializer =
            new DriverGeneratedEventJsonDeserializer();

    @Test
    void deserializesDriverGeneratedEvent() {
        String payload = """
                {
                  "driverId": "driver-101",
                  "driverDisplayId": "DRV-DRIVER-101",
                  "scenario": "AIRPORT_RUSH"
                }
                """;

        var event = deserializer.deserialize("driver.generated", payload.getBytes(StandardCharsets.UTF_8));

        assertThat(event.getDriverId()).isEqualTo("driver-101");
        assertThat(event.getDriverDisplayId()).isEqualTo("DRV-DRIVER-101");
        assertThat(event.getScenario()).isEqualTo("AIRPORT_RUSH");
    }

    @Test
    void rejectsMalformedPayload() {
        assertThatThrownBy(() -> deserializer.deserialize(
                "driver.generated",
                "{not-json}".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(SerializationException.class);
    }
}
