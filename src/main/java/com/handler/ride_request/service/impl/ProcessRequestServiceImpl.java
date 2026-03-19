package com.handler.ride_request.service.impl;

import com.handler.ride_request.entity.RideRequestEntity;
import com.handler.ride_request.entity.UserEntity;
import com.handler.ride_request.mapper.RideRequestMapper;
import com.handler.ride_request.model.RideRequest;
import com.handler.ride_request.model.Rider;
import com.handler.ride_request.rabbitmq.service.NotificationService;
import com.handler.ride_request.repository.RideRequestRepository;
import com.handler.ride_request.repository.UserRepository;
import com.handler.ride_request.scheduler.RiderSearchScheduler;
import com.handler.ride_request.service.ProcessRequestService;
import com.handler.ride_request.service.RidersSearchService;
import com.handler.ride_request.enums.StatusEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@Service
public class ProcessRequestServiceImpl implements ProcessRequestService {

    private final UserRepository userRepository;
    private final RideRequestRepository rideRequestRepository;
    private final NotificationService notificationService;
    private final RidersSearchService ridersSearchService;
    private final RiderSearchScheduler scheduleRidersSearch;
    private final RideRequestDriverAttemptService attemptService;

    @Override
    public void processRideRequest(RideRequest rideRequest) {
        if (rideRequest == null) {
            log.error("Received null ride request payload");
            return;
        }

        log.info("Processing new ride request for user {}", rideRequest.userIdentifier());
        RideRequestEntity savedRequest = persistRideRequest(rideRequest)
                .orElseThrow(() -> new IllegalStateException("Unable to persist ride request for user " + rideRequest.userIdentifier()));

        List<Rider> nearbyRiders = findNearbyRiders(savedRequest);
        if (nearbyRiders.isEmpty()) {
            log.warn("Skipping ride request processing because no nearby riders were found for request {}", savedRequest.getId());
            return;
        }

        log.debug("Found {} nearby riders for request {}", nearbyRiders.size(), savedRequest.getId());
        List<Rider> persistedCandidates = attemptService.createAttemptsForRound(savedRequest, nearbyRiders, 1);
        if (persistedCandidates.isEmpty()) {
            log.warn("No rider attempts were persisted for request {}, notification skipped", savedRequest.getId());
            return;
        }

        notificationService.sendRabbitMqNotification(persistedCandidates, savedRequest);
        scheduleRidersSearch.scheduleRidersSearch(savedRequest.getId());
        log.info("Scheduled rider search follow-up for request {}", savedRequest.getId());
    }

    private Optional<RideRequestEntity> persistRideRequest(RideRequest rideRequest) {
        return userRepository.findByIdentifier(rideRequest.userIdentifier())
                .map(user -> saveRideRequest(rideRequest, user));
    }

    private RideRequestEntity saveRideRequest(RideRequest rideRequest, UserEntity userEntity) {
        RideRequestEntity entity = RideRequestMapper.mapToRideRequestEntity(userEntity, rideRequest, StatusEnum.PENDING);
        RideRequestEntity savedEntity = rideRequestRepository.save(entity);
        log.debug("Persisted ride request {} for user {}", savedEntity.getId(), rideRequest.userIdentifier());
        return savedEntity;
    }

    private List<Rider> findNearbyRiders(RideRequestEntity rideRequest) {
        List<Rider> riders = ridersSearchService.findNearestVehicles(rideRequest.getLocation(), Collections.emptySet());
        return riders == null ? List.of() : riders;
    }
}
