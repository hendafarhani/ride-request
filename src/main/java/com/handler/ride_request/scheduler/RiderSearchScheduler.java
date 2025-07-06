package com.handler.ride_request.scheduler;

import com.handler.ride_request.entity.RideRequestEntity;
import com.handler.ride_request.rabbitmq.service.impl.NotificationServiceImpl;
import com.handler.ride_request.repository.RideRequestRepository;
import com.handler.ride_request.service.RidersSearchService;
import com.handler.ride_request.tools.StatusEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@RequiredArgsConstructor
@Slf4j
@Component
public class RiderSearchScheduler {

    private final int MAX_RETRIES = 3;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final RideRequestRepository rideRequestRepository;
    private final NotificationServiceImpl notificationService;
    private final RidersSearchService ridersSearchService;


    public void scheduleRidersSearch(Long rideRequestId) {
        //In a period of 4 minutes if no rider confirms => repeat the search and notify the riders,
        // => Repeat it for 3 times
        AtomicInteger executionCount = new AtomicInteger();
        // Start the timer
        int AWAITING_TIME_MIN = 4;
        scheduler.scheduleAtFixedRate(() -> {
            RideRequestEntity request = rideRequestRepository.findById(rideRequestId).orElse(null);
            if (isRequestEmpty(request)) return;

            if (isRetryTimesGreaterThanMaxRetries(executionCount)) {
                log.info("No confirmation received. Max retry times is exceeded.");
                updateRideRequestCaseOfError(request, executionCount.get());
            } else {
                if (!isRequestInPending(request)) {
                    log.info("No confirmation received within 4 minutes. Relaunching search.");

                    // Relaunch search for 5 new nearest drivers
                    //Once find the 5 closest riders => send them a notification via rabbitMq
                    // (use direct exchange where the key is rider id) with the ride request.
                    //send rabbitMq requests where the key = rider identifier
                    notificationService.sendRabbitMqNotification(ridersSearchService.findNearestVehicles(request.getLocation()), request);
                }
                executionCount.getAndIncrement();
            }
        }, MAX_RETRIES, AWAITING_TIME_MIN, TimeUnit.MINUTES);
    }

    private void updateRideRequestCaseOfError(RideRequestEntity request, int executionCount) {
        request.setStatus(StatusEnum.CANCELED);
        rideRequestRepository.save(request);
        log.info("Update of rideRequest with identifier {}, " +
                "into canceled because the number of execution count is {}, and no rider is found.",
                request.getIdentifier(), executionCount);
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