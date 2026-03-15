package com.handler.ride_request.rabbitmq.model;

import java.io.Serializable;

public record RideAcceptanceMessage(
        String rideRequestIdentifier,
        String riderIdentifier
) implements Serializable {}

