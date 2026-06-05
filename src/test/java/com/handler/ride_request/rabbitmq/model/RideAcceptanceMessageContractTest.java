package com.handler.ride_request.rabbitmq.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.handler.ride_request.enums.StatusEnum;
import org.junit.jupiter.api.Test;
import org.springframework.data.geo.Point;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class RideAcceptanceMessageContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void rideAcceptanceMessageSerializesWithExpectedFieldNames() throws Exception {
        RideAcceptanceMessage message = new RideAcceptanceMessage("ride-123", "rider-456");

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(message));

        assertThat(json.get("rideRequestIdentifier").asText()).isEqualTo("ride-123");
        assertThat(json.get("riderIdentifier").asText()).isEqualTo("rider-456");
        assertThat(json).hasSize(2);
    }

    @Test
    void rideAcceptanceMessageDeserializesFromExpectedJson() throws Exception {
        String json = """
                {
                  "rideRequestIdentifier": "ride-123",
                  "riderIdentifier": "rider-456"
                }
                """;

        RideAcceptanceMessage message = objectMapper.readValue(json, RideAcceptanceMessage.class);

        assertThat(message.rideRequestIdentifier()).isEqualTo("ride-123");
        assertThat(message.riderIdentifier()).isEqualTo("rider-456");
    }

    @Test
    void rideNotificationSerializesWithExpectedJsonShape() throws Exception {
        RideNotification notification = RideNotification.builder()
                .userIdentifier("ride-123")
                .riderIdentifier("rider-456")
                .userName("Alice")
                .userLocation(new Point(2.3522, 48.8566))
                .price(BigDecimal.ZERO)
                .status(StatusEnum.PENDING)
                .build();

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(notification));

        assertThat(json.get("userIdentifier").asText()).isEqualTo("ride-123");
        assertThat(json.get("riderIdentifier").asText()).isEqualTo("rider-456");
        assertThat(json.get("userName").asText()).isEqualTo("Alice");
        assertThat(json.get("userLocation").get("x").asDouble()).isEqualTo(2.3522);
        assertThat(json.get("userLocation").get("y").asDouble()).isEqualTo(48.8566);
        assertThat(json.get("price").asText()).isEqualTo("0");
        assertThat(json.get("status").asText()).isEqualTo("PENDING");
    }
}
