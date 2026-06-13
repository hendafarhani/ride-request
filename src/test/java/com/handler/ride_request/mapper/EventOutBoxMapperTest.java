package com.handler.ride_request.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.handler.ride_request.entity.EventOutboxEntity;
import com.handler.ride_request.entity.RideRequestEntity;
import com.handler.ride_request.entity.UserEntity;
import com.handler.ride_request.enums.OutboxEventStatus;
import com.handler.ride_request.enums.RideRequestEventType;
import com.handler.ride_request.enums.StatusEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventOutBoxMapperTest {

    private ObjectMapper objectMapper;
    private RideRequestEntity rideRequest;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        rideRequest = RideRequestEntity.builder()
                .id(42L)
                .identifier("ride-42")
                .status(StatusEnum.PENDING)
                .user(UserEntity.builder().identifier("requester-42").build())
                .build();
    }

    @Test
    void mapToEventOutboxEntityShouldBuildPendingEventWithPayloadSnapshot() throws Exception {
        EventOutboxEntity mapped = EventOutBoxMapper.mapToEventOutboxEntity(
                RideRequestEventType.RIDER_NOTIFIED,
                rideRequest,
                "rider-1",
                objectMapper
        );

        assertEquals(RideRequestEventType.RIDER_NOTIFIED, mapped.getEventType());
        assertEquals(OutboxEventStatus.PENDING, mapped.getStatus());
        assertEquals(42L, mapped.getRideRequestId());
        assertEquals("ride-42", mapped.getRideRequestIdentifier());
        assertEquals("requester-42", mapped.getRequesterId());
        assertEquals("rider-1", mapped.getRiderId());
        assertNotNull(mapped.getCreatedAt());
        assertEquals(mapped.getCreatedAt(), mapped.getUpdatedAt());
        assertNull(mapped.getProcessedAt());
        assertEquals(0, mapped.getRetryCount());
        assertNull(mapped.getLastError());

        JsonNode payload = objectMapper.readTree(mapped.getPayload());
        assertEquals("RIDER_NOTIFIED", payload.get("eventType").asText());
        assertNotNull(payload.get("eventTimestamp"));
        assertEquals(42L, payload.get("rideRequestId").asLong());
        assertEquals("ride-42", payload.get("rideRequestIdentifier").asText());
        assertEquals("requester-42", payload.get("requesterId").asText());
        assertEquals("rider-1", payload.get("riderId").asText());
        assertEquals("PENDING", payload.get("rideStatus").asText());
    }

    @Test
    void mapToEventOutboxEntityShouldPreserveNullRiderIdentifier() throws Exception {
        EventOutboxEntity mapped = EventOutBoxMapper.mapToEventOutboxEntity(
                RideRequestEventType.REQUEST_CREATED,
                rideRequest,
                null,
                objectMapper
        );

        assertNull(mapped.getRiderId());

        JsonNode payload = objectMapper.readTree(mapped.getPayload());
        assertTrue(payload.get("riderId").isNull());
    }

    @Test
    void mapToEventOutboxEntityShouldWrapPayloadSerializationFailure() {
        ObjectMapper failingObjectMapper = new ObjectMapper() {
            @Override
            public String writeValueAsString(Object value) throws JsonProcessingException {
                throw new JsonProcessingException("boom") {
                };
            }
        };

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> EventOutBoxMapper.mapToEventOutboxEntity(
                        RideRequestEventType.REQUEST_CREATED,
                        rideRequest,
                        null,
                        failingObjectMapper
                )
        );

        assertEquals("Unable to serialize outbox payload for ride request ride-42", thrown.getMessage());
        assertTrue(thrown.getCause() instanceof JsonProcessingException);
    }
}
