package com.handler.ride_request.mapper;

import com.handler.ride_request.entity.RideRequestDriverAttemptEntity;
import com.handler.ride_request.entity.RideRequestEntity;
import com.handler.ride_request.entity.RiderEntity;
import com.handler.ride_request.entity.UserEntity;
import com.handler.ride_request.enums.AttemptStatus;
import com.handler.ride_request.model.RideRequest;
import com.handler.ride_request.enums.StatusEnum;
import org.springframework.data.geo.Point;

import java.time.OffsetDateTime;
import java.util.UUID;

public class RideRequestMapper {

    private RideRequestMapper() {
        // Private constructor to prevent instantiation
    }

    public static RideRequestEntity mapToRideRequestEntity(UserEntity userEntity, RideRequest rideRequest, StatusEnum status){
        return RideRequestEntity.builder()
                .user(userEntity)
                .status(status)
                .location(new Point(rideRequest.location().getLongitude(), rideRequest.location().getLatitude()))
                .identifier(rideRequest.userIdentifier() + UUID.randomUUID())
                .build();
    }

    public static RideRequestDriverAttemptEntity buildAttempt(RideRequestEntity request, RiderEntity rider, int round, OffsetDateTime notifiedAt) {
        return RideRequestDriverAttemptEntity.builder()
                .rideRequest(request)
                .rider(rider)
                .notificationRound(round)
                .notifiedAt(notifiedAt)
                .status(AttemptStatus.NOTIFIED)
                .build();
    }
}
