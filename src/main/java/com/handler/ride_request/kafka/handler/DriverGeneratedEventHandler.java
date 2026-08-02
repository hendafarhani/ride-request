package com.handler.ride_request.kafka.handler;

import com.handler.ride_request.kafka.model.DriverGeneratedEvent;
import com.handler.ride_request.service.DriverProjectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DriverGeneratedEventHandler {

    private final DriverProjectionService driverProjectionService;

    @KafkaListener(
            id = "${kafka.listeners.driver-generated.id}",
            topics = "${microgo.topics.driver-generated}",
            groupId = "${kafka.consumers.driver-generated.group-id}",
            containerFactory = "driverGeneratedListenerFactory"
    )
    public void onDriverGenerated(DriverGeneratedEvent event) {
        log.info("Projecting generated driver {} into the ride-request database", event.getDriverId());
        driverProjectionService.upsertDriver(event);
    }
}
