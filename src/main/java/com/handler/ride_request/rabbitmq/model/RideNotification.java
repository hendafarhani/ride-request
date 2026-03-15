package com.handler.ride_request.rabbitmq.model;

import com.handler.ride_request.enums.StatusEnum;
import lombok.Builder;
import org.springframework.data.geo.Point;

import java.math.BigDecimal;

@Builder
public record RideNotification(
   String userIdentifier,
   String riderIdentifier,
   String userName,
   Point userLocation,
   BigDecimal price,
   StatusEnum status
) {}
