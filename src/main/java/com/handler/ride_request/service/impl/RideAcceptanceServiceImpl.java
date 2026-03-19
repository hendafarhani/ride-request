package com.handler.ride_request.service.impl;

import com.handler.ride_request.entity.RideRequestEntity;
import com.handler.ride_request.entity.RiderEntity;
import com.handler.ride_request.rabbitmq.service.NotificationService;
import com.handler.ride_request.repository.RideRequestRepository;
import com.handler.ride_request.repository.RiderRepository;
import com.handler.ride_request.service.RideAcceptanceService;
import com.handler.ride_request.enums.StatusEnum;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class RideAcceptanceServiceImpl implements RideAcceptanceService {

    private final RideRequestRepository rideRequestRepository;
    private final RiderRepository riderRepository;
    private final NotificationService notificationService;
    private final RideRequestDriverAttemptServiceImpl attemptService;

    @Override
    @Transactional
    public void acceptRide(String rideRequestIdentifier, String riderIdentifier) {

        validateIdentifiers(rideRequestIdentifier, riderIdentifier);

        RideRequestEntity rideRequest = loadRideRequest(rideRequestIdentifier);
        ensureRequestIsPending(rideRequest);

        RiderEntity rider = loadRider(riderIdentifier);

        OffsetDateTime acceptedAt = OffsetDateTime.now();

        registerAcceptedAttempt(rideRequest, rider, acceptedAt);
        updateRideRequest(rideRequest, rider, acceptedAt);
        notifyRequester(rideRequest, rider);
    }

    private void validateIdentifiers(String rideRequestIdentifier, String riderIdentifier) {
        if (!StringUtils.hasText(rideRequestIdentifier)) {
            throw new IllegalArgumentException("rideRequestIdentifier must not be blank");
        }
        if (!StringUtils.hasText(riderIdentifier)) {
            throw new IllegalArgumentException("riderIdentifier must not be blank");
        }
    }

    private RideRequestEntity loadRideRequest(String rideRequestIdentifier) {
        return rideRequestRepository.findByIdentifier(rideRequestIdentifier)
                .orElseThrow(() ->
                        new EntityNotFoundException("Ride request not found for identifier " + rideRequestIdentifier));
    }

    private RiderEntity loadRider(String riderIdentifier) {
        return riderRepository.findByIdentifier(riderIdentifier)
                .orElseThrow(() ->
                        new EntityNotFoundException("Rider not found for identifier " + riderIdentifier));
    }

    private void ensureRequestIsPending(RideRequestEntity rideRequest) {
        if (!StatusEnum.PENDING.equals(rideRequest.getStatus())) {
            throw new IllegalStateException("Ride request " + rideRequest.getIdentifier() + " is not pending");
        }
    }

    private void registerAcceptedAttempt(RideRequestEntity rideRequest, RiderEntity rider, OffsetDateTime acceptedAt) {
        attemptService.markAccepted(rideRequest.getId(), rider.getIdentifier(), acceptedAt);
        attemptService.markOtherOpenAttemptsAsCanceled(rideRequest.getId(), rider.getIdentifier(), acceptedAt);
    }

    private void updateRideRequest(RideRequestEntity rideRequest, RiderEntity rider, OffsetDateTime acceptedAt) {
        rideRequest.setStatus(StatusEnum.ACCEPTED);
        rideRequest.setAcceptedRiderIdentifier(rider.getIdentifier());
        rideRequest.setAcceptedAt(acceptedAt);
        rideRequestRepository.save(rideRequest);
    }

    private void notifyRequester(RideRequestEntity rideRequest, RiderEntity rider) {
        log.info("Ride request {} accepted by rider {}", rideRequest.getIdentifier(), rider.getIdentifier());
        notificationService.notifyRideAccepted(rideRequest, rider.getIdentifier());
    }
}
