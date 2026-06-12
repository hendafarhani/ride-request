package com.handler.ride_request.rabbitmq.listener;

import com.handler.ride_request.rabbitmq.model.RideResponseMessage;
import com.handler.ride_request.rabbitmq.model.RideResponseType;
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
public class RideResponseListener {

    private final RideResponseService rideResponseService;

    @RabbitListener(queues = "${ride.response.queue:ride.response.queue}")
    public void onRideResponse(RideResponseMessage message) {
        if (message == null) {
            log.warn("Received null ride response message");
            return;
        }

        if (!isValid(message)) {
            log.warn("Ride response message missing required fields: {}", message);
            return;
        }

        try {
            if (RideResponseType.ACCEPTED.equals(message.response())) {
                rideResponseService.acceptRide(message.rideRequestIdentifier(), message.riderIdentifier());
            } else {
                rideResponseService.declineRide(message.rideRequestIdentifier(), message.riderIdentifier());
            }
        } catch (EntityNotFoundException | IllegalStateException | IllegalArgumentException ex) {
            log.warn("Ignoring ride response for {} due to {}", message.rideRequestIdentifier(), ex.getMessage());
        }
    }

    private boolean isValid(RideResponseMessage message) {
        return StringUtils.hasText(message.rideRequestIdentifier())
                && StringUtils.hasText(message.riderIdentifier())
                && message.response() != null;
    }
}
