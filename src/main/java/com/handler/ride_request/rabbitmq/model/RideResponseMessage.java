package com.handler.ride_request.rabbitmq.model;

import java.io.Serializable;

public record RideResponseMessage(
        String rideRequestIdentifier,
        String riderIdentifier,
        RideResponseType response
) implements Serializable {}
