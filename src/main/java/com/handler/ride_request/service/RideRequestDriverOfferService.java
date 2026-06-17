package com.handler.ride_request.service;

import com.handler.ride_request.entity.RideRequestEntity;
import com.handler.ride_request.model.Rider;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

/**
 * Manages rider notifications for a ride request.
 * The public type keeps the existing "offer" wording to avoid a wider refactor.
 */
public interface RideRequestDriverOfferService {

    List<Rider> createOffersForRound(RideRequestEntity rideRequestEntity, List<Rider> riders, int notificationRound);
    Set<String> getOfferedRiderIdentifiers(Long rideRequestId);
    int getNextNotificationRound(Long rideRequestId);
    void markOutstandingOffersAsTimedOut(Long rideRequestId);
    void markAccepted(Long rideRequestId, String riderIdentifier, OffsetDateTime respondedAt);
    void markDeclined(Long rideRequestId, String riderIdentifier, OffsetDateTime respondedAt);
    void markOtherOpenOffersAsCanceled(Long rideRequestId, String acceptedRiderIdentifier, OffsetDateTime respondedAt);
}
