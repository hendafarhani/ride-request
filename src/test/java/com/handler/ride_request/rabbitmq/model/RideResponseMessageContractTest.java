package com.handler.ride_request.rabbitmq.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RideResponseMessageContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesResponseMessage() throws Exception {
        RideResponseMessage message = new RideResponseMessage("ride-123", "rider-456", RideResponseType.DECLINED);

        String json = objectMapper.writeValueAsString(message);

        assertThat(json).contains("\"rideRequestIdentifier\":\"ride-123\"");
        assertThat(json).contains("\"riderIdentifier\":\"rider-456\"");
        assertThat(json).contains("\"response\":\"DECLINED\"");
    }

    @Test
    void deserializesResponseMessage() throws Exception {
        String json = """
                {
                  "rideRequestIdentifier": "ride-123",
                  "riderIdentifier": "rider-456",
                  "response": "ACCEPTED"
                }
                """;

        RideResponseMessage message = objectMapper.readValue(json, RideResponseMessage.class);

        assertThat(message.rideRequestIdentifier()).isEqualTo("ride-123");
        assertThat(message.riderIdentifier()).isEqualTo("rider-456");
        assertThat(message.response()).isEqualTo(RideResponseType.ACCEPTED);
    }
}
