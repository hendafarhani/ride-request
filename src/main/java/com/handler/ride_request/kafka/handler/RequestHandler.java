package com.handler.ride_request.kafka.handler;

import com.handler.ride_request.model.RideRequest;
import com.handler.ride_request.service.ProcessRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class RequestHandler {

    private final ProcessRequestService processService;

    @KafkaListener(
            id = "rideListener",
            topics = "ride.requests",
            groupId = "driver.matching.group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void listen(RideRequest rideRequest){
        log.info("Received ride request from identifier: {}", rideRequest.userIdentifier());
        processService.processRideRequest(rideRequest);
    }

}
