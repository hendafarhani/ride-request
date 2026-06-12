package com.handler.ride_request.service;

import com.handler.ride_request.entity.EventOutboxEntity;
import com.handler.ride_request.entity.RideRequestEntity;
import com.handler.ride_request.enums.RideRequestEventType;

import java.util.List;

public interface EventOutboxService {

    EventOutboxEntity recordRideRequestEvent(RideRequestEventType eventType, RideRequestEntity rideRequest);

    EventOutboxEntity recordRiderEvent(RideRequestEventType eventType, RideRequestEntity rideRequest, String riderIdentifier);

    List<EventOutboxEntity> findPendingEvents();

    List<EventOutboxEntity> findPendingEvents(int limit);

    List<EventOutboxEntity> findPendingEventsByRideRequestId(Long rideRequestId);

    List<EventOutboxEntity> findPendingEventsByRequesterId(String requesterId);

    List<EventOutboxEntity> findPendingEventsByRiderId(String riderId);

    EventOutboxEntity markProcessed(Long eventId);

    EventOutboxEntity recordFailure(Long eventId, String errorMessage);
}
