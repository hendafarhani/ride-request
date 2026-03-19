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

import java.util.List;
import java.util.Objects;
import java.util.Set;

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
        RideRequestEntity rideRequestEntity = saveRideRequest(rideRequest);
        if (isRideRequestEntityEmpty(rideRequestEntity)) {
            log.warn("Skipping ride request processing because the entity could not be persisted (user may be unknown)");
            return;
        }

        // Search for the 5 nearest riders to the requesters => By reading from database Redis.
        List<Rider> riderData = ridersSearchService.findNearestVehicles(rideRequestEntity.getLocation(), Set.of());
        if (riderData == null || riderData.isEmpty()) {
            log.warn("Skipping ride request processing because no nearby riders were found for request {}", rideRequestEntity.getId());
            return;
        }

        log.debug("Found {} nearby riders for request {}", riderData.size(), rideRequestEntity.getId());

        List<Rider> persistedCandidates = attemptService.createAttemptsForRound(rideRequestEntity, riderData, 1);

        //Once the 5 nearest riders are found => send them a notification via RabbitMq
        //send RabbitMq requests where the key = rider identifier
        notificationService.sendRabbitMqNotification(persistedCandidates, rideRequestEntity);
        log.info("Notifications dispatched for ride request {}", rideRequestEntity.getId());

        scheduleRidersSearch.scheduleRidersSearch(rideRequestEntity.getId());
        log.info("Scheduled rider search follow-up for request {}", rideRequestEntity.getId());

    }


    private RideRequestEntity saveRideRequest(RideRequest rideRequest) {
        UserEntity userEntity = userRepository.findByIdentifier(rideRequest.userIdentifier()).orElse(null);

        if (Objects.isNull(userEntity)) {
            log.warn("No user found with identifier {}, cannot create ride request", rideRequest.userIdentifier());
            return null;
        }

        try {
            RideRequestEntity entity = RideRequestMapper.mapToRideRequestEntity(userEntity, rideRequest, StatusEnum.PENDING);
            RideRequestEntity savedEntity = rideRequestRepository.save(entity);
            log.debug("Persisted ride request {} for user {}", savedEntity.getId(), rideRequest.userIdentifier());
            return savedEntity;
        } catch (Exception ex) {
            log.error("Failed to persist ride request for user {}", rideRequest.userIdentifier(), ex);
            return null;
        }
    }

    private boolean isRideRequestEntityEmpty(RideRequestEntity rideRequestEntity){
        return Objects.isNull(rideRequestEntity);
    }
}
