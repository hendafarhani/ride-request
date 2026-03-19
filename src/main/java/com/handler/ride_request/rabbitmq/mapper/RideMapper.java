package com.handler.ride_request.rabbitmq.mapper;


import com.handler.ride_request.entity.RideRequestEntity;
import com.handler.ride_request.model.Rider;
import com.handler.ride_request.rabbitmq.model.RideNotification;
import com.handler.ride_request.enums.StatusEnum;

import java.math.BigDecimal;

public abstract class RideMapper {

    private RideMapper(){
        // Private constructor to prevent instantiation
    }

    public static RideNotification mapToRideNotification(Rider rider, RideRequestEntity rideRequestEntity, StatusEnum status){
        return mapToRideNotification(rider.getIdentifier(), rideRequestEntity, status);
    }

    public static RideNotification mapToRideNotification(String riderIdentifier, RideRequestEntity rideRequestEntity, StatusEnum status) {
        return RideNotification.builder()
                .price(BigDecimal.ZERO)
                .riderIdentifier(riderIdentifier)
                .status(status)
                .userIdentifier(rideRequestEntity.getIdentifier())
                .userName(rideRequestEntity.getUser().getName())
                .userLocation(rideRequestEntity.getLocation())
                .build();
    }
}
