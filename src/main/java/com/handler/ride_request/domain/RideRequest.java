package com.handler.ride_request.domain;

import lombok.Builder;

@Builder
public record RideRequest(
        String userIdentifier,
        Location location
) {}
