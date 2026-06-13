package com.handler.ride_request.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.handler.ride_request.entity.EventOutboxEntity;
import com.handler.ride_request.entity.RideRequestEntity;
import com.handler.ride_request.enums.OutboxEventStatus;
import com.handler.ride_request.enums.RideRequestEventType;
import com.handler.ride_request.mapper.EventOutBoxMapper;
import com.handler.ride_request.repository.EventOutboxRepository;
import com.handler.ride_request.service.EventOutboxService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
        EventOutboxEntity event = EventOutBoxMapper.markEventAsProcessed(loadEvent(eventId));
        return eventOutboxRepository.save(event);
    }

    @Override
    @Transactional
    public EventOutboxEntity recordFailure(Long eventId, String errorMessage) {
        EventOutboxEntity event = EventOutBoxMapper.markEventAsPending(loadEvent(eventId), errorMessage);
        return eventOutboxRepository.save(event);
    }

    private EventOutboxEntity recordEvent(RideRequestEventType eventType, RideRequestEntity rideRequest, String riderIdentifier) {
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(rideRequest, "rideRequest must not be null");

        EventOutboxEntity event = EventOutBoxMapper.mapToEventOutboxEntity(eventType, rideRequest, riderIdentifier, objectMapper);

        return eventOutboxRepository.save(event);
    }



    private EventOutboxEntity loadEvent(Long eventId) {
        return eventOutboxRepository.findById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Outbox event not found for id " + eventId));
    }
}
