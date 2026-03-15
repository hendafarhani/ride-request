package com.handler.ride_request.scheduler;

import com.handler.ride_request.entity.RideRequestEntity;
import com.handler.ride_request.rabbitmq.service.NotificationService;
import com.handler.ride_request.repository.RideRequestRepository;
import com.handler.ride_request.service.RidersSearchService;
import com.handler.ride_request.enums.StatusEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;
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
    private static final int AWAITING_TIME_MIN = 4;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final RideRequestRepository rideRequestRepository;
    private final NotificationService notificationService;
    private final RidersSearchService ridersSearchService;


    public void scheduleRidersSearch(Long rideRequestId) {
        AtomicInteger executionCount = new AtomicInteger();
        ScheduledFuture<?>[] futureHolder = new ScheduledFuture<?>[1];
        futureHolder[0] = scheduler.scheduleAtFixedRate(() ->
                handleRetry(rideRequestId, executionCount, futureHolder[0]),
                AWAITING_TIME_MIN,
                AWAITING_TIME_MIN,
                TimeUnit.MINUTES);
    }

    private void handleRetry(Long rideRequestId, AtomicInteger executionCount, ScheduledFuture<?> future) {
        RideRequestEntity request = rideRequestRepository.findById(rideRequestId).orElse(null);
        if (isRequestEmpty(request)) {
            log.warn("Ride request {} not found anymore, canceling scheduler", rideRequestId);
            cancelFuture(future);
            return;
        }

        if (!isRequestInPending(request)) {
            log.info("Ride request {} moved to status {}, stopping retries", request.getIdentifier(), request.getStatus());
            cancelFuture(future);
            return;
        }

        if (isRetryTimesGreaterThanMaxRetries(executionCount)) {
            log.info("No confirmation received. Max retry times is exceeded.");
            updateRideRequestCaseOfError(request, executionCount.get());
            cancelFuture(future);
            return;
        }

        log.info("No confirmation received within {} minutes. Relaunching search for ride {}.",
                AWAITING_TIME_MIN, request.getIdentifier());
        notificationService.sendRabbitMqNotification(ridersSearchService.findNearestVehicles(request.getLocation()), request);
        executionCount.incrementAndGet();
    }

    private void updateRideRequestCaseOfError(RideRequestEntity request, int executionCount) {
        request.setStatus(StatusEnum.CANCELED);
        rideRequestRepository.save(request);
        log.info("Update of rideRequest with identifier {}, " +
                "into canceled because the number of execution count is {}, and no rider is found.",
                request.getIdentifier(), executionCount);
    }

    private void cancelFuture(ScheduledFuture<?> future) {
        if (Objects.nonNull(future) && !future.isCancelled()) {
            future.cancel(false);
        }
    }

    private boolean isRequestEmpty(RideRequestEntity request) {
        return Objects.isNull(request);
    }

    private boolean isRetryTimesGreaterThanMaxRetries(AtomicInteger executionCount){
        return executionCount.get() >= MAX_RETRIES;
    }

    private boolean isRequestInPending(RideRequestEntity request){
        return StatusEnum.PENDING.equals(request.getStatus());
    }
}
/**
 * Les testes unitaires et les tests d'intégration
 * jenkins
 * Article Kafka
 * Microservices
 * Docker
 * Kubernates
 * **/