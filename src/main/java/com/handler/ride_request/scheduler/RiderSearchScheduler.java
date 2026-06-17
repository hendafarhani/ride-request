package com.handler.ride_request.scheduler;

import com.handler.ride_request.entity.RideRequestEntity;
import com.handler.ride_request.enums.RideRequestEventType;
import com.handler.ride_request.mapper.RideRequestMapper;
import com.handler.ride_request.model.Rider;
import com.handler.ride_request.rabbitmq.service.NotificationService;
import com.handler.ride_request.repository.RideRequestRepository;
import com.handler.ride_request.service.EventOutboxService;
import com.handler.ride_request.service.RideRequestDriverOfferService;
import com.handler.ride_request.service.RidersSearchService;
import com.handler.ride_request.enums.StatusEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@RequiredArgsConstructor
@Slf4j
@Component
public class RiderSearchScheduler {

    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MINUTES = 4;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final RideRequestRepository rideRequestRepository;
    private final NotificationService notificationService;
    private final RidersSearchService ridersSearchService;
    private final RideRequestDriverOfferService offerService;
    private final EventOutboxService eventOutboxService;


    public void scheduleRidersSearch(Long rideRequestId) {
        ScheduledFuture<?>[] futureHolder = new ScheduledFuture<?>[1];
        AtomicInteger retryCount = new AtomicInteger();

        futureHolder[0] = scheduler.scheduleAtFixedRate(
                () -> handleRetry(rideRequestId, retryCount, futureHolder[0]),
                RETRY_DELAY_MINUTES,
                RETRY_DELAY_MINUTES,
                TimeUnit.MINUTES);
    }

    private void handleRetry(Long rideRequestId, AtomicInteger retryCount, ScheduledFuture<?> future) {
        RideRequestEntity request = findRideRequest(rideRequestId);

        if (shouldStopBecauseRequestIsMissing(request, rideRequestId, future)) {
            return;
        }

        if (shouldStopBecauseRequestIsNoLongerPending(request, future)) {
            return;
        }

        if (shouldTimeoutRequest(retryCount)) {
            timeoutRideRequest(request, retryCount.get());
            stopScheduledSearch(future);
            return;
        }

        relaunchRiderSearch(request);
        retryCount.incrementAndGet();
    }

    private RideRequestEntity findRideRequest(Long rideRequestId) {
        return rideRequestRepository.findById(rideRequestId).orElse(null);
    }

    private boolean shouldStopBecauseRequestIsMissing(
            RideRequestEntity request,
            Long rideRequestId,
            ScheduledFuture<?> future) {
        if (request != null) {
            return false;
        }

        log.warn("Ride request {} not found anymore, canceling scheduler", rideRequestId);
        stopScheduledSearch(future);
        return true;
    }

    private boolean shouldStopBecauseRequestIsNoLongerPending(RideRequestEntity request, ScheduledFuture<?> future) {
        if (isPending(request)) {
            return false;
        }

        log.info("Ride request {} moved to status {}, stopping retries", request.getIdentifier(), request.getStatus());
        stopScheduledSearch(future);
        return true;
    }

    private boolean shouldTimeoutRequest(AtomicInteger retryCount) {
        return retryCount.get() >= MAX_RETRIES;
    }

    private void timeoutRideRequest(RideRequestEntity request, int retryCount) {
        log.info("No confirmation received. Max retry times is exceeded.");
        offerService.markOutstandingOffersAsTimedOut(request.getId());
        saveTimedOutRideRequest(request, retryCount);
        eventOutboxService.recordRideRequestEvent(RideRequestEventType.REQUEST_TIMED_OUT, request);
        notificationService.notifyRideTimedOut(request);
    }

    private void saveTimedOutRideRequest(RideRequestEntity request, int retryCount) {
        RideRequestMapper.markRideRequestAsTimedOut(request);
        rideRequestRepository.save(request);
        log.info("Update of rideRequest with identifier {}, " +
                "into timed out because the number of execution count is {}, and no rider is found.",
                request.getIdentifier(), retryCount);
    }

    private void relaunchRiderSearch(RideRequestEntity request) {
        List<Rider> persistedCandidates = findNextPersistedCandidates(request);
        logRiderSearchRelaunched(request);
        recordRiderNotificationEvents(request, persistedCandidates);
        notificationService.sendRabbitMqNotification(persistedCandidates, request);
    }

    private List<Rider> findNextPersistedCandidates(RideRequestEntity request) {
        Set<String> offeredRiderIdentifiers = offerService.getOfferedRiderIdentifiers(request.getId());
        List<Rider> nextRiders = ridersSearchService.findNearestVehicles(request.getLocation(), offeredRiderIdentifiers);
        return offerService.createOffersForRound(
                request,
                nextRiders,
                offerService.getNextNotificationRound(request.getId())
        );
    }

    private void logRiderSearchRelaunched(RideRequestEntity request) {
        log.info("No confirmation received within {} minutes. Relaunching search for ride {}.",
                RETRY_DELAY_MINUTES, request.getIdentifier());
    }

    private void recordRiderNotificationEvents(RideRequestEntity request, List<Rider> persistedCandidates) {
        persistedCandidates.forEach(rider -> eventOutboxService.recordRiderEvent(
                RideRequestEventType.RIDER_NOTIFIED,
                request,
                rider.getIdentifier()));
    }

    private void stopScheduledSearch(ScheduledFuture<?> future) {
        if (Objects.nonNull(future) && !future.isCancelled()) {
            future.cancel(false);
        }
    }

    private boolean isPending(RideRequestEntity request) {
        return StatusEnum.PENDING.equals(request.getStatus());
    }
}
