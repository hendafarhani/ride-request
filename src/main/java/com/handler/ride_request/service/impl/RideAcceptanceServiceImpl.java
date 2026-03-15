package com.handler.ride_request.service.impl;

import com.handler.ride_request.entity.RideRequestEntity;
import com.handler.ride_request.rabbitmq.service.NotificationService;
import com.handler.ride_request.repository.RideRequestRepository;
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
    private final NotificationService notificationService;

    @Override
    @Transactional
    public void acceptRide(String rideRequestIdentifier, String riderIdentifier) {
        if (!StringUtils.hasText(rideRequestIdentifier)) {
            throw new IllegalArgumentException("rideRequestIdentifier must not be blank");
        }
        if (!StringUtils.hasText(riderIdentifier)) {
            throw new IllegalArgumentException("riderIdentifier must not be blank");
        }

        RideRequestEntity rideRequest = rideRequestRepository.findByIdentifier(rideRequestIdentifier)
                .orElseThrow(() -> new EntityNotFoundException("Ride request not found for identifier " + rideRequestIdentifier));

        if (!StatusEnum.PENDING.equals(rideRequest.getStatus())) {
            throw new IllegalStateException("Ride request " + rideRequestIdentifier + " is not pending");
        }

        rideRequest.setStatus(StatusEnum.ACCEPTED);
        rideRequest.setAcceptedRiderIdentifier(riderIdentifier);
        rideRequest.setAcceptedAt(OffsetDateTime.now());
        rideRequestRepository.save(rideRequest);

        log.info("Ride request {} accepted by rider {}", rideRequestIdentifier, riderIdentifier);
        notificationService.notifyRideAccepted(rideRequest, riderIdentifier);
    }
}
