package com.handler.ride_request.mapper;

import com.handler.ride_request.entity.RideRequestEntity;
import com.handler.ride_request.entity.UserEntity;
import com.handler.ride_request.model.RideRequest;
import com.handler.ride_request.enums.StatusEnum;
import org.springframework.data.geo.Point;

import java.util.UUID;

public class RideRequestMapper {


    public static RideRequestEntity mapToRideRequestEntity(UserEntity userEntity, RideRequest rideRequest, StatusEnum status){
        return RideRequestEntity.builder()
                .user(userEntity)
                .status(status)
                .location(new Point(rideRequest.location().getLongitude(), rideRequest.location().getLatitude()))
                .identifier(rideRequest.userIdentifier() + UUID.randomUUID())
                .build();
    }
}
