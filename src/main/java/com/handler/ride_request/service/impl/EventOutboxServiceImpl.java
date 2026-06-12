package com.handler.ride_request.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.handler.ride_request.entity.EventOutboxEntity;
import com.handler.ride_request.entity.RideRequestEntity;
import com.handler.ride_request.enums.OutboxEventStatus;
import com.handler.ride_request.enums.RideRequestEventType;
import com.handler.ride_request.repository.EventOutboxRepository;
import com.handler.ride_request.service.EventOutboxService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class EventOutboxServiceImpl implements EventOutboxService {

    private final EventOutboxRepository eventOutboxRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public EventOutboxEntity recordRideRequestEvent(RideRequestEventType eventType, RideRequestEntity rideRequest) {
        return recordEvent(eventType, rideRequest, null);
    }

    @Override
    @Transactional
    public EventOutboxEntity recordRiderEvent(RideRequestEventType eventType, RideRequestEntity rideRequest, String riderIdentifier) {
        return recordEvent(eventType, rideRequest, riderIdentifier);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventOutboxEntity> findPendingEvents() {
        return eventOutboxRepository.findByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventOutboxEntity> findPendingEvents(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        return eventOutboxRepository.findByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING, PageRequest.of(0, limit));
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventOutboxEntity> findPendingEventsByRideRequestId(Long rideRequestId) {
        return eventOutboxRepository.findByRideRequestIdAndStatusOrderByCreatedAtAsc(rideRequestId, OutboxEventStatus.PENDING);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventOutboxEntity> findPendingEventsByRequesterId(String requesterId) {
        return eventOutboxRepository.findByRequesterIdAndStatusOrderByCreatedAtAsc(requesterId, OutboxEventStatus.PENDING);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventOutboxEntity> findPendingEventsByRiderId(String riderId) {
        return eventOutboxRepository.findByRiderIdAndStatusOrderByCreatedAtAsc(riderId, OutboxEventStatus.PENDING);
    }

    @Override
    @Transactional
    public EventOutboxEntity markProcessed(Long eventId) {
        EventOutboxEntity event = loadEvent(eventId);
        OffsetDateTime now = OffsetDateTime.now();
        event.setStatus(OutboxEventStatus.PROCESSED);
        event.setProcessedAt(now);
        event.setUpdatedAt(now);
        event.setLastError(null);
        return eventOutboxRepository.save(event);
    }

    @Override
    @Transactional
    public EventOutboxEntity recordFailure(Long eventId, String errorMessage) {
        EventOutboxEntity event = loadEvent(eventId);
        event.setStatus(OutboxEventStatus.PENDING);
        event.setRetryCount(event.getRetryCount() + 1);
        event.setLastError(errorMessage);
        event.setUpdatedAt(OffsetDateTime.now());
        return eventOutboxRepository.save(event);
    }

    private EventOutboxEntity recordEvent(RideRequestEventType eventType, RideRequestEntity rideRequest, String riderIdentifier) {
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(rideRequest, "rideRequest must not be null");

        OffsetDateTime now = OffsetDateTime.now();
        EventOutboxEntity event = EventOutboxEntity.builder()
                .eventType(eventType)
                .status(OutboxEventStatus.PENDING)
                .rideRequestId(rideRequest.getId())
                .rideRequestIdentifier(rideRequest.getIdentifier())
                .requesterId(rideRequest.getUser().getIdentifier())
                .riderId(riderIdentifier)
                .payload(toPayload(eventType, rideRequest, riderIdentifier, now))
                .createdAt(now)
                .updatedAt(now)
                .build();

        return eventOutboxRepository.save(event);
    }

    private String toPayload(RideRequestEventType eventType,
                             RideRequestEntity rideRequest,
                             String riderIdentifier,
                             OffsetDateTime eventTimestamp) {
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

    private EventOutboxEntity loadEvent(Long eventId) {
        return eventOutboxRepository.findById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Outbox event not found for id " + eventId));
    }
}
