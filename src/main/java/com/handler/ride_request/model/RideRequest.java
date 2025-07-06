package com.handler.ride_request.model;

import lombok.Builder;

@Builder
public record RideRequest(
        String userIdentifier,
        Location location
) {}
