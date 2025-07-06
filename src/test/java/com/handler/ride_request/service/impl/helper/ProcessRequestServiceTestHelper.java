package com.handler.ride_request.service.impl.helper;

import com.handler.ride_request.entity.RideRequestEntity;
import com.handler.ride_request.entity.UserEntity;
import com.handler.ride_request.model.Location;
import com.handler.ride_request.model.RideRequest;
import com.handler.ride_request.model.Rider;
import com.handler.ride_request.tools.StatusEnum;
import org.springframework.data.geo.Point;

import java.util.List;

public class ProcessRequestServiceTestHelper {

    public static RideRequest getRideRequest(){
        return RideRequest.builder()
                .location(Location.builder()
                        .latitude(1256)
                        .longitude(15568)
                        .build())
                .userIdentifier("45464-test")
                .build();
    }

    public static UserEntity getUserEntity(){
        return UserEntity.builder()
                .id(125L)
                .name("test-user")
                .identifier("1125-testUser")
                .build();
    }

    public static RideRequestEntity getRideRequestEntity(){
        return RideRequestEntity
                .builder()
                .id(null)
                .user(getUserEntity())
                .status(StatusEnum.PENDING)
                .identifier("45464-testa6396b09-bf9e-460a-b770-10a1d4fb7456")
                .location(new Point(15568.000000, 1256.000000))
                .build();
    }

    public static List<Rider> getListOfRiders(){
        return List.of(Rider.builder()
                        .identifier("HHJFU-1156")
                        .userName("RiderName")
                        .averageDistance("54646")
                        .point(new Point(15568.000000, 1256.000000))
                        .hash("jkdlfjsldf")
                .build());
    }

}
