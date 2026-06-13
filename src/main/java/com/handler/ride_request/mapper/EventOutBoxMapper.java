package com.handler.ride_request.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.handler.ride_request.entity.EventOutboxEntity;
import com.handler.ride_request.entity.RideRequestEntity;
import com.handler.ride_request.enums.OutboxEventStatus;
import com.handler.ride_request.enums.RideRequestEventType;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public class EventOutBoxMapper {



    private EventOutBoxMapper() {
        // Private constructor to prevent instantiation
    }

    public static EventOutboxEntity mapToEventOutboxEntity(RideRequestEventType eventType,
                                                    RideRequestEntity rideRequest,
                                                    String riderIdentifier,
                                                    ObjectMapper objectMapper
                                                    ) {
        OffsetDateTime now = OffsetDateTime.now();

        return EventOutboxEntity.builder()
                .eventType(eventType)
                .status(OutboxEventStatus.PENDING)
                .rideRequestId(rideRequest.getId())
                .rideRequestIdentifier(rideRequest.getIdentifier())
                .requesterId(rideRequest.getUser().getIdentifier())
                .riderId(riderIdentifier)
                .payload(toPayload(eventType, rideRequest, riderIdentifier, now, objectMapper))
                .createdAt(now)
                .updatedAt(now)
                .build();

    }

    public static EventOutboxEntity markEventAsProcessed(EventOutboxEntity event) {
        OffsetDateTime now = OffsetDateTime.now();
        event.setStatus(OutboxEventStatus.PROCESSED);
        event.setProcessedAt(now);
        event.setUpdatedAt(now);
        event.setLastError(null);
        return event;
    }

    public static EventOutboxEntity markEventAsPending(EventOutboxEntity event, String errorMessage) {
        event.setStatus(OutboxEventStatus.PENDING);
        event.setRetryCount(event.getRetryCount() + 1);
        event.setLastError(errorMessage);
        event.setUpdatedAt(OffsetDateTime.now());
        return event;
    }

    private static String toPayload(RideRequestEventType eventType,
                             RideRequestEntity rideRequest,
                             String riderIdentifier,
                             OffsetDateTime eventTimestamp,
                             ObjectMapper objectMapper) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", eventType);
        payload.put("eventTimestamp", eventTimestamp);
        payload.put("rideRequestId", rideRequest.getId());
        payload.put("rideRequestIdentifier", rideRequest.getIdentifier());
        payload.put("requesterId", rideRequest.getUser().getIdentifier());
        payload.put("riderId", riderIdentifier);
        payload.put("rideStatus", rideRequest.getStatus());

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize outbox payload for ride request " + rideRequest.getIdentifier(), ex);
        }
    }
}