package com.handler.ride_request.service.serviceimpl;

import com.handler.ride_request.entity.RideRequestEntity;
import com.handler.ride_request.entity.RiderEntity;
import com.handler.ride_request.enums.RideRequestEventType;
import com.handler.ride_request.mapper.RideRequestMapper;
import com.handler.ride_request.rabbitmq.service.NotificationService;
import com.handler.ride_request.repository.RideRequestRepository;
import com.handler.ride_request.repository.RiderRepository;
import com.handler.ride_request.service.EventOutboxService;
import com.handler.ride_request.service.RideRequestDriverOfferService;
import com.handler.ride_request.service.RideResponseService;
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
public class RideResponseServiceImpl implements RideResponseService {

    private final RideRequestRepository rideRequestRepository;
    private final RiderRepository riderRepository;
    private final NotificationService notificationService;
    private final RideRequestDriverOfferService offerService;
    private final EventOutboxService eventOutboxService;

    @Override
    @Transactional
    public void acceptRide(String rideRequestIdentifier, String riderIdentifier) {

        validateIdentifiers(rideRequestIdentifier, riderIdentifier);

        RideRequestEntity rideRequest = loadRideRequest(rideRequestIdentifier);
        ensureRequestIsPending(rideRequest);

        RiderEntity rider = loadRider(riderIdentifier);

        OffsetDateTime acceptedAt = OffsetDateTime.now();

        registerAcceptedOffer(rideRequest, rider, acceptedAt);
        updateRideRequest(rideRequest, rider, acceptedAt);
        eventOutboxService.recordRideRequestEvent(RideRequestEventType.REQUEST_ACCEPTED, rideRequest);
        notifyRequester(rideRequest, rider);
    }

    @Override
    @Transactional
    public void declineRide(String rideRequestIdentifier, String riderIdentifier) {
        validateIdentifiers(rideRequestIdentifier, riderIdentifier);

        RideRequestEntity rideRequest = loadRideRequest(rideRequestIdentifier);
        ensureRequestIsPending(rideRequest);

        OffsetDateTime declinedAt = OffsetDateTime.now();
        offerService.markDeclined(rideRequest.getId(), riderIdentifier, declinedAt);
        eventOutboxService.recordRiderEvent(RideRequestEventType.RIDER_DECLINED, rideRequest, riderIdentifier);

        log.info("Ride request {} declined by rider {}", rideRequest.getIdentifier(), riderIdentifier);
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
        return riderRepository.findByDriverIdentifier(riderIdentifier)
                .orElseThrow(() ->
                        new EntityNotFoundException("Rider not found for identifier " + riderIdentifier));
    }

    private void ensureRequestIsPending(RideRequestEntity rideRequest) {
        if (!StatusEnum.PENDING.equals(rideRequest.getStatus())) {
            throw new IllegalStateException("Ride request " + rideRequest.getIdentifier() + " is not pending");
        }
    }

    private void registerAcceptedOffer(RideRequestEntity rideRequest, RiderEntity rider, OffsetDateTime acceptedAt) {
        offerService.markAccepted(rideRequest.getId(), driverIdentifierOf(rider), acceptedAt);
        offerService.markOtherOpenOffersAsCanceled(rideRequest.getId(), driverIdentifierOf(rider), acceptedAt);
    }

    private void updateRideRequest(RideRequestEntity rideRequest, RiderEntity rider, OffsetDateTime acceptedAt) {
        RideRequestMapper.markRideRequestAsAccepted(rideRequest, rider, acceptedAt);
        rideRequestRepository.save(rideRequest);
    }

    private void notifyRequester(RideRequestEntity rideRequest, RiderEntity rider) {
        log.info("Ride request {} accepted by rider {}", rideRequest.getIdentifier(), driverIdentifierOf(rider));
        notificationService.notifyRideAccepted(rideRequest, driverIdentifierOf(rider));
    }

    private String driverIdentifierOf(RiderEntity rider) {
        return rider.getDriverIdentifier() != null ? rider.getDriverIdentifier() : rider.getIdentifier();
    }
}
