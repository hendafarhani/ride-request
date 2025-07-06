package com.handler.ride_request.rabbitmq.mapper;


import com.handler.ride_request.entity.RideRequestEntity;
import com.handler.ride_request.model.Rider;
import com.handler.ride_request.rabbitmq.model.RideNotification;
import com.handler.ride_request.tools.StatusEnum;

import java.math.BigDecimal;

public class RideMapper {


    public static RideNotification mapToRideNotification(Rider rider, RideRequestEntity rideRequestEntity, StatusEnum status){
        return RideNotification.builder()
                .price(new BigDecimal(0))
                .riderIdentifier(rider.getIdentifier())
                .status(status)
                .userIdentifier(rideRequestEntity.getIdentifier())
                .userName(rideRequestEntity.getUser().getName())
                .userLocation(rideRequestEntity.getLocation())
                .build();
    }
}
