package com.handler.ride_request.service;

import com.handler.ride_request.entity.RideRequestEntity;
import com.handler.ride_request.model.Rider;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

public interface RideRequestDriverAttemptService {

    List<Rider> createAttemptsForRound(RideRequestEntity rideRequestEntity, List<Rider> riders, int notificationRound);
    Set<String> getAttemptedRiderIdentifiers(Long rideRequestId);
    int getNextNotificationRound(Long rideRequestId);
    void markOutstandingAttemptsAsTimedOut(Long rideRequestId);
    void markAccepted(Long rideRequestId, String riderIdentifier, OffsetDateTime respondedAt);
    void markOtherOpenAttemptsAsCanceled(Long rideRequestId, String acceptedRiderIdentifier, OffsetDateTime respondedAt);
}
