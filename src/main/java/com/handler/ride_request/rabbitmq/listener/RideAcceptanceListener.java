package com.handler.ride_request.rabbitmq.listener;

import com.handler.ride_request.rabbitmq.model.RideAcceptanceMessage;
import com.handler.ride_request.service.RideResponseService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class RideAcceptanceListener {

    private final RideResponseService rideResponseService;

    @RabbitListener(queues = "${ride.acceptance.queue:ride.acceptance.queue}")
    public void onRideAccepted(RideAcceptanceMessage message) {
        if (message == null) {
            log.warn("Received null ride acceptance message");
            return;
        }

        if (!StringUtils.hasText(message.rideRequestIdentifier()) || !StringUtils.hasText(message.riderIdentifier())) {
            log.warn("Ride acceptance message missing identifiers: {}", message);
            return;
        }

        try {
            rideResponseService.acceptRide(message.rideRequestIdentifier(), message.riderIdentifier());
        } catch (EntityNotFoundException | IllegalStateException | IllegalArgumentException ex) {
            log.warn("Ignoring ride acceptance for {} due to {}", message.rideRequestIdentifier(), ex.getMessage());
        }
    }
}
