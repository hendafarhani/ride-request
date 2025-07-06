package com.handler.ride_request.kafka.helper;

import com.handler.ride_request.model.Location;
import com.handler.ride_request.model.RideRequest;

public class RequestHandlerTestHelper {


    public static RideRequest getRideRequest(){
        return RideRequest.builder()
                .location(getLocation())
                .userIdentifier("45464-test")
                .build();
    }


    static Location getLocation(){
        return Location.builder()
                .latitude(1256)
                .longitude(15568)
                .build();
    }
}
