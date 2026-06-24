package com.handler.ride_request.service.serviceimpl;

import com.handler.ride_request.entity.RideRequestEntity;
import com.handler.ride_request.entity.UserEntity;
import com.handler.ride_request.enums.RideRequestEventType;
import com.handler.ride_request.mapper.RideRequestMapper;
import com.handler.ride_request.domain.RideRequest;
import com.handler.ride_request.domain.Rider;
import com.handler.ride_request.rabbitmq.service.NotificationService;
import com.handler.ride_request.repository.RideRequestRepository;
import com.handler.ride_request.repository.UserRepository;
import com.handler.ride_request.scheduler.RiderSearchScheduler;
import com.handler.ride_request.service.EventOutboxService;
import com.handler.ride_request.service.ProcessRequestService;
import com.handler.ride_request.service.RideRequestDriverOfferService;
import com.handler.ride_request.service.RidersSearchService;
import com.handler.ride_request.enums.StatusEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@Service
public class ProcessRequestServiceImpl implements ProcessRequestService {

    private static final int INITIAL_NOTIFICATION_ROUND = 1;

    private final UserRepository userRepository;
    private final RideRequestRepository rideRequestRepository;
    private final NotificationService notificationService;
    private final RidersSearchService ridersSearchService;
    private final RiderSearchScheduler scheduleRidersSearch;
    private final RideRequestDriverOfferService offerService;
    private final EventOutboxService eventOutboxService;

    @Override
    @Transactional
    public void processRideRequest(RideRequest rideRequest) {
        if (isInvalidRequest(rideRequest)) {
            return;
        }

        logRequestProcessingStarted(rideRequest);
        RideRequestEntity savedRequest = persistRideRequestOrThrow(rideRequest);
        notifyInitialRiderCandidates(savedRequest);
    }

    private boolean isInvalidRequest(RideRequest rideRequest) {
        if (rideRequest != null) {
            return false;
        }
        log.error("Received null ride request payload");
        return true;
    }

    private void logRequestProcessingStarted(RideRequest rideRequest) {
        log.info("Processing new ride request for user {}", rideRequest.userIdentifier());
    }

    private RideRequestEntity persistRideRequestOrThrow(RideRequest rideRequest) {
        return persistRideRequest(rideRequest)
                .orElseThrow(() -> new IllegalStateException(
                        "Unable to persist ride request for user " + rideRequest.userIdentifier()));
    }

    private void notifyInitialRiderCandidates(RideRequestEntity savedRequest) {
        List<Rider> nearbyRiders = findNearbyRiders(savedRequest);
        if (hasNoNearbyRiders(savedRequest, nearbyRiders)) {
            return;
        }

        logNearbyRidersFound(savedRequest, nearbyRiders);
        List<Rider> persistedCandidates = createInitialNotificationOffers(savedRequest, nearbyRiders);
        if (hasNoPersistedCandidates(savedRequest, persistedCandidates)) {
            return;
        }

        notifyPersistedCandidates(savedRequest, persistedCandidates);
        scheduleFollowUpSearch(savedRequest);
    }

    private boolean hasNoNearbyRiders(RideRequestEntity savedRequest, List<Rider> nearbyRiders) {
        if (!nearbyRiders.isEmpty()) {
            return false;
        }
        log.warn("Skipping ride request processing because no nearby riders were found for request {}", savedRequest.getId());
        return true;
    }

    private void logNearbyRidersFound(RideRequestEntity savedRequest, List<Rider> nearbyRiders) {
        log.debug("Found {} nearby riders for request {}", nearbyRiders.size(), savedRequest.getId());
    }

    private List<Rider> createInitialNotificationOffers(RideRequestEntity savedRequest, List<Rider> nearbyRiders) {
        return offerService.createOffersForRound(savedRequest, nearbyRiders, INITIAL_NOTIFICATION_ROUND);
    }

    private boolean hasNoPersistedCandidates(RideRequestEntity savedRequest, List<Rider> persistedCandidates) {
        if (!persistedCandidates.isEmpty()) {
            return false;
        }
        log.warn("No rider offers were persisted for request {}, notification skipped", savedRequest.getId());
        return true;
    }

    private void notifyPersistedCandidates(RideRequestEntity savedRequest, List<Rider> persistedCandidates) {
        recordRiderNotificationEvents(savedRequest, persistedCandidates);
        notificationService.sendRabbitMqNotification(persistedCandidates, savedRequest);
    }

    private void recordRiderNotificationEvents(RideRequestEntity savedRequest, List<Rider> persistedCandidates) {
        persistedCandidates.forEach(rider -> recordRiderNotifiedEvent(savedRequest, rider));
    }

    private void recordRiderNotifiedEvent(RideRequestEntity savedRequest, Rider rider) {
        eventOutboxService.recordRiderEvent(
                RideRequestEventType.RIDER_NOTIFIED,
                savedRequest,
                rider.getIdentifier());
    }

    private void scheduleFollowUpSearch(RideRequestEntity savedRequest) {
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
        recordRideRequestCreatedEvents(savedEntity);
        logPersistedRideRequest(rideRequest, savedEntity);
        return savedEntity;
    }

    private void recordRideRequestCreatedEvents(RideRequestEntity savedEntity) {
        eventOutboxService.recordRideRequestEvent(RideRequestEventType.REQUEST_CREATED, savedEntity);
        eventOutboxService.recordRideRequestEvent(RideRequestEventType.REQUEST_STATUS_PENDING, savedEntity);
    }

    private void logPersistedRideRequest(RideRequest rideRequest, RideRequestEntity savedEntity) {
        log.debug("Persisted ride request {} for user {}", savedEntity.getId(), rideRequest.userIdentifier());
    }

    private List<Rider> findNearbyRiders(RideRequestEntity rideRequest) {
        List<Rider> riders = ridersSearchService.findNearestVehicles(rideRequest.getLocation(), Collections.emptySet());
        return riders == null ? List.of() : riders;
    }
}
